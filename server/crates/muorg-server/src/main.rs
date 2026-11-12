use muorg_server::config::Config;
use muorg_server::state::AppState;
use muorg_server::build_router;
use muorg_core::catalog::Catalog;
use std::net::SocketAddr;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use tracing_appender::non_blocking::WorkerGuard;

fn init_logging(log_dir: &Path) -> WorkerGuard {
    use tracing_subscriber::{fmt, layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

    std::fs::create_dir_all(log_dir).ok();

    // Rolling daily log file: muorg-server.YYYY-MM-DD.log
    let file_appender = match tracing_appender::rolling::Builder::new()
        .rotation(tracing_appender::rolling::Rotation::DAILY)
        .filename_prefix("muorg-server")
        .filename_suffix("log")
        .max_log_files(14)
        .build(log_dir)
    {
        Ok(a) => a,
        Err(e) => {
            eprintln!("Warning: could not open log file in {}: {e}", log_dir.display());
            // Fall back to stdout-only by using a writer that discards file output.
            // We still need a guard, so we create a no-op non-blocking writer.
            let (_, guard) = tracing_appender::non_blocking(std::io::sink());
            tracing_subscriber::registry()
                .with(EnvFilter::try_from_env("RUST_LOG")
                    .unwrap_or_else(|_| EnvFilter::new("muorg_server=info,muorg_core=info,warn")))
                .with(fmt::Layer::new().with_ansi(true))
                .init();
            return guard;
        }
    };

    let (non_blocking_file, guard) = tracing_appender::non_blocking(file_appender);

    let filter = EnvFilter::try_from_env("RUST_LOG")
        .unwrap_or_else(|_| EnvFilter::new("muorg_server=info,muorg_core=info,warn"));

    tracing_subscriber::registry()
        .with(filter)
        // Console: colored, compact
        .with(fmt::Layer::new().with_ansi(true).compact())
        // File: plain text, same format
        .with(fmt::Layer::new().with_ansi(false).with_writer(non_blocking_file))
        .init();

    guard
}

fn find_config() -> Option<PathBuf> {
    let args: Vec<String> = std::env::args().collect();
    for i in 0..args.len().saturating_sub(1) {
        if args[i] == "--config" {
            return Some(PathBuf::from(&args[i + 1]));
        }
    }
    let local = PathBuf::from("./muorg-server.toml");
    if local.exists() { return Some(local); }

    #[cfg(target_os = "windows")]
    {
        if let Ok(appdata) = std::env::var("APPDATA") {
            let p = PathBuf::from(appdata).join("muorg-server").join("config.toml");
            if p.exists() { return Some(p); }
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        if let Some(home) = std::env::var_os("HOME") {
            let p = PathBuf::from(home).join(".config").join("muorg-server").join("config.toml");
            if p.exists() { return Some(p); }
        }
    }
    None
}

#[tokio::main]
async fn main() {
    let config = match find_config() {
        Some(path) => {
            // Logging not yet up; use println for this one message.
            println!("Loading config from {}", path.display());
            Config::load(&path).unwrap_or_else(|e| {
                eprintln!("Config error: {e}");
                std::process::exit(1);
            })
        }
        None => {
            eprintln!("No config file found. Using built-in defaults.");
            Config::default()
        }
    };

    // Derive log directory from the DB path parent (e.g. /data/logs in Docker).
    let log_dir = config.storage.db_path
        .parent()
        .unwrap_or_else(|| Path::new("."))
        .join("logs");

    let _log_guard = init_logging(&log_dir);

    tracing::info!(
        version = env!("CARGO_PKG_VERSION"),
        log_dir = %log_dir.display(),
        "muorg-server starting"
    );

    // Built before the catalog opens so a typo'd endpoint or a missing key is a
    // startup failure rather than a silently empty library.
    let remotes = Arc::new(
        muorg_server::storage::RemoteStores::from_config(&config.library.remotes)
            .unwrap_or_else(|e| {
                eprintln!("Remote storage config error: {e}");
                std::process::exit(1);
            }),
    );

    let cover_cache_dir = config.storage.cover_cache_dir.clone().unwrap_or_else(|| {
        config
            .storage
            .db_path
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .join("covers")
    });

    // Ensure storage dirs exist
    if let Some(parent) = config.storage.db_path.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    std::fs::create_dir_all(&config.storage.backup_dir).ok();
    std::fs::create_dir_all(&cover_cache_dir).ok();

    let catalog = Arc::new(Catalog::new(&config.storage.db_path).unwrap_or_else(|e| {
        tracing::error!("Failed to open database: {e}");
        std::process::exit(1);
    }));

    // The Favorites playlist is located by its exact name, so it must exist
    // and keep that name. Idempotent: safe to run on every startup.
    {
        let conn = catalog.db.lock().unwrap();
        if let Err(e) = muorg_core::catalog::ensure_favorites_playlist(&conn) {
            tracing::error!("Failed to ensure the Favorites playlist: {e}");
            std::process::exit(1);
        }
    }

    // Prune soft-deleted tracks older than 30 days
    {
        let conn = catalog.db.lock().unwrap();
        let thirty_days = 30 * 24 * 60 * 60;
        let _ = muorg_core::catalog::gc_deleted_tracks(&conn, thirty_days);
    }

    // Prune play history older than 90 days
    {
        let conn = catalog.db.lock().unwrap();
        let ninety_days = 90 * 24 * 60 * 60;
        let _ = muorg_core::catalog::prune_play_history(&conn, ninety_days);
    }

    let addr: SocketAddr = format!("{}:{}", config.server.host, config.server.port)
        .parse()
        .unwrap_or_else(|_| "127.0.0.1:7700".parse().unwrap());

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap_or_else(|e| {
        tracing::error!("Failed to bind {addr}: {e}");
        std::process::exit(1);
    });
    let listen_addr = listener.local_addr().unwrap_or(addr);
    let server_port = listen_addr.port();

    // When the API listener is loopback-only (the desktop app's sidecar binds
    // 127.0.0.1), a Chromecast on the LAN still needs to fetch the audio a cast
    // session points it at. Bind a second, all-interfaces listener serving
    // only the token-protected stream route; /api/* stays loopback-only
    // behind the API key.
    let stream_listener = muorg_server::bind_stream_listener_if_loopback(listen_addr).await;
    let stream_port = stream_listener
        .as_ref()
        .and_then(|l| l.local_addr().ok())
        .map(|a| a.port());

    let state_builder = AppState::new(
        catalog.clone(),
        config.storage.backup_dir.clone(),
        config.storage.backup_retention_count,
        config.server.api_key.clone(),
        server_port,
        config.transcoding,
        remotes,
        Arc::new(muorg_server::storage::covers::CoverCache::new(
            cover_cache_dir,
            config.storage.cover_cache_max_bytes,
        )),
        config.library.remote_scan_concurrency,
    ).with_image_fetch(config.images.clone())
     .with_rate_limiter(
        config.server.rate_limit_requests,
        config.server.rate_limit_window_secs,
    )
     .with_browse(muorg_server::state::BrowseConfig {
        content_paths: config.library.content_paths.clone(),
        browse_roots: config.library.browse_roots.clone(),
    });
    let state_builder = match stream_port {
        Some(port) => state_builder.with_stream_port(port),
        None => state_builder,
    };
    let state = Arc::new(state_builder);

    // mDNS discovery starts here rather than in `AppState::new` so a test
    // server never opens the sweep's per-interface sockets.
    state
        .cast_discovery
        .start(muorg_server::cast::NoDiscoveryObserver);

    let app = build_router(state.clone(), &config.cors.allowed_origins);

    if let Some(stream_listener) = stream_listener {
        let stream_app = muorg_server::build_stream_router(state.clone());
        let stream_addr = stream_listener.local_addr().unwrap_or(listen_addr);
        tracing::info!(address = %stream_addr, "stream listener bound for cast devices");
        tokio::spawn(async move {
            axum::serve(stream_listener, stream_app)
                .with_graceful_shutdown(shutdown_signal())
                .await
                .ok();
        });
    }

    // The startup scan runs in the background so the listener is up
    // immediately; /api/health reports "Scanning" until it finishes.
    if config.library.scan_on_startup {
        let catalog = catalog.clone();
        let scanning = state.scanning.clone();
        let roots = config.library.content_paths.clone();
        tokio::task::spawn_blocking(move || {
            scanning.store(true, std::sync::atomic::Ordering::SeqCst);
            for root in &roots {
                tracing::info!(path = root, "scanning library");
                let conn = catalog.db.lock().unwrap();
                if let Err(e) = muorg_core::catalog::save_roots(&conn, std::slice::from_ref(root)) {
                    tracing::error!(path = root, "save_roots failed: {e}");
                    continue;
                }
                match muorg_core::catalog::rescan_root(&conn, root) {
                    Ok(n) => tracing::info!(path = root, tracks = n, "scan complete"),
                    Err(e) => tracing::error!(path = root, "scan failed: {e}"),
                }
            }
            scanning.store(false, std::sync::atomic::Ordering::SeqCst);
        });
    }

    // Remote scans run in the background: a first-time index of a large bucket
    // must not delay the listener, and the track list fills in as it goes.
    if config.library.scan_on_startup && !state.remotes.is_empty() {
        let st = state.clone();
        let conc = state.remote_scan_concurrency;
        tokio::spawn(async move {
            for remote in st.remotes.all() {
                let root = remote.root_uri();
                tracing::info!(root = %root, "scanning remote library");
                match muorg_server::storage::scan::scan_remote_root(&st, &remote, conc).await {
                    Ok((upserted, removed)) => {
                        tracing::info!(root = %root, upserted, removed, "remote scan complete")
                    }
                    Err(e) => tracing::error!(root = %root, "remote scan failed: {e}"),
                }
            }
        });
    }

    // Clean-up runs daily in the background. The startup run above already
    // happened, so the first tick is 24 h out.
    {
        let catalog = catalog.clone();
        tokio::spawn(async move {
            let interval = std::time::Duration::from_secs(24 * 60 * 60);
            loop {
                tokio::time::sleep(interval).await;
                let conn = catalog.db.lock().unwrap();
                let thirty_days = 30 * 24 * 60 * 60;
                if let Err(e) = muorg_core::catalog::gc_deleted_tracks(&conn, thirty_days) {
                    tracing::error!("daily gc of deleted tracks failed: {e}");
                }
                let ninety_days = 90 * 24 * 60 * 60;
                if let Err(e) = muorg_core::catalog::prune_play_history(&conn, ninety_days) {
                    tracing::error!("daily play-history prune failed: {e}");
                }
            }
        });
    }

    // Backfill content hashes for tracks scanned before hashing existed, so
    // hash-based move detection works and the suffix fallback is rarely needed.
    {
        let catalog = catalog.clone();
        tokio::task::spawn_blocking(move || {
            let mut after_id = 0i64;
            let mut done = 0u64;
            loop {
                let batch = {
                    let conn = catalog.db.lock().unwrap();
                    match muorg_core::catalog::tracks_missing_content_hash(&conn, after_id, 500) {
                        Ok(batch) => batch,
                        Err(e) => {
                            tracing::error!("content-hash backfill failed to list tracks: {e}");
                            return;
                        }
                    }
                };
                if batch.is_empty() {
                    break;
                }
                for (id, path) in batch {
                    after_id = id;
                    // Remote tracks have no local file; the remote scanner
                    // hashes them from the object store instead.
                    if path.starts_with("remote://") {
                        continue;
                    }
                    match muorg_core::catalog::compute_content_hash(std::path::Path::new(&path)) {
                        Ok(hash) => {
                            let conn = catalog.db.lock().unwrap();
                            if let Err(e) =
                                muorg_core::catalog::set_track_content_hash(&conn, id, &hash)
                            {
                                tracing::warn!(track_id = id, "content-hash backfill update failed: {e}");
                            }
                        }
                        Err(e) => {
                            // Missing or unreadable file: leave the hash NULL.
                            // The cursor still advances, so the walk terminates.
                            tracing::debug!(path = %path, "content-hash backfill skipped: {e}");
                        }
                    }
                    done += 1;
                    if done.is_multiple_of(500) {
                        tracing::info!(done, "content-hash backfill progress");
                    }
                }
            }
            tracing::info!(done, "content-hash backfill complete");
        });
    }

    tracing::info!(address = %listen_addr, "listening");

    let shutdown_timeout = config.server.shutdown_timeout_secs.max(1);
    let (shutdown_started_tx, shutdown_started_rx) = tokio::sync::oneshot::channel::<()>();
    let server = axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>())
        .with_graceful_shutdown(async move {
            shutdown_signal().await;
            tracing::info!("shutting down gracefully, waiting for in-flight requests");
            let _ = shutdown_started_tx.send(());
        });

    let server_fut = async {
        if let Err(e) = server.await {
            tracing::error!("server error: {e}");
        }
    };
    tokio::pin!(server_fut);

    // Wait for either a clean drain or the shutdown timeout, whichever first.
    let outcome = tokio::select! {
        _ = &mut server_fut => "drained",
        _ = async {
            let _ = shutdown_started_rx.await;
            tokio::time::sleep(std::time::Duration::from_secs(shutdown_timeout)).await;
        } => "timeout",
    };

    // Flush the async log writer before the process exits
    if outcome == "timeout" {
        tracing::warn!(timeout_secs = shutdown_timeout, "shutdown timeout exceeded, forcing exit");
    }
    drop(_log_guard);
    if outcome == "timeout" {
        std::process::exit(1);
    }
}

async fn shutdown_signal() {
    let ctrl_c = async {
        tokio::signal::ctrl_c().await.ok();
    };

    #[cfg(unix)]
    let terminate = async {
        if let Ok(mut sig) =
            tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        {
            sig.recv().await;
        }
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
    tracing::info!("received shutdown signal");
}

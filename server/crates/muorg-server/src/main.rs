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

    // Ensure storage dirs exist
    if let Some(parent) = config.storage.db_path.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    std::fs::create_dir_all(&config.storage.backup_dir).ok();

    let catalog = Catalog::new(&config.storage.db_path).unwrap_or_else(|e| {
        tracing::error!("Failed to open database: {e}");
        std::process::exit(1);
    });

    // Prune soft-deleted tracks older than 30 days
    {
        let conn = catalog.db.lock().unwrap();
        let thirty_days = 30 * 24 * 60 * 60;
        let _ = muorg_core::catalog::gc_deleted_tracks(&conn, thirty_days);
    }

    if config.library.scan_on_startup {
        let conn = catalog.db.lock().unwrap();
        for root in &config.library.content_paths {
            tracing::info!(path = root, "scanning library");
            if let Err(e) = muorg_core::catalog::save_roots(&conn, std::slice::from_ref(root)) {
                tracing::error!(path = root, "save_roots failed: {e}");
                continue;
            }
            match muorg_core::catalog::rescan_root(&conn, root) {
                Ok(n) => tracing::info!(path = root, tracks = n, "scan complete"),
                Err(e) => tracing::error!(path = root, "scan failed: {e}"),
            }
        }
    }

    let addr: SocketAddr = format!("{}:{}", config.server.host, config.server.port)
        .parse()
        .unwrap_or_else(|_| "127.0.0.1:7700".parse().unwrap());

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap_or_else(|e| {
        tracing::error!("Failed to bind {addr}: {e}");
        std::process::exit(1);
    });
    let server_port = listener.local_addr().map(|a| a.port()).unwrap_or(addr.port());

    let state = Arc::new(AppState::new(
        Arc::new(catalog),
        config.storage.backup_dir.clone(),
        config.storage.backup_retention_count,
        config.server.api_key.clone(),
        server_port,
        config.transcoding,
    ));

    let app = build_router(state, &config.cors.allowed_origins);

    let listen_addr = listener.local_addr().unwrap_or(addr);
    tracing::info!(address = %listen_addr, "listening");

    axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>()).await.unwrap();
}

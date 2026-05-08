mod auth;
mod backup;
mod cast;
mod config;
mod routes;
mod state;
mod transcode;

use axum::{
    middleware,
    routing::{delete, get, patch, post, put},
    Router,
};
use config::Config;
use muorg_core::catalog::Catalog;
use state::AppState;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use tower_http::cors::{Any, CorsLayer};

fn find_config() -> Option<PathBuf> {
    // Check for --config <path> CLI flag
    let args: Vec<String> = std::env::args().collect();
    for i in 0..args.len().saturating_sub(1) {
        if args[i] == "--config" {
            return Some(PathBuf::from(&args[i + 1]));
        }
    }
    // ./muorg-server.toml
    let local = PathBuf::from("./muorg-server.toml");
    if local.exists() { return Some(local); }

    // XDG / platform config dir
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

fn build_cors(allowed_origins: &[String]) -> CorsLayer {
    if allowed_origins.is_empty() || allowed_origins.iter().any(|o| o == "*") {
        return CorsLayer::permissive();
    }
    let origins: Vec<axum::http::HeaderValue> = allowed_origins
        .iter()
        .filter_map(|o| o.parse().ok())
        .collect();
    CorsLayer::new()
        .allow_origin(origins)
        .allow_methods(Any)
        .allow_headers(Any)
}

fn build_router(state: Arc<AppState>, allowed_origins: &[String]) -> Router {
    let cors = build_cors(allowed_origins);

    // Routes that require Bearer token auth
    let protected = Router::new()
        // Library
        .route("/api/roots", get(routes::library::get_roots))
        .route("/api/tracks", get(routes::library::get_tracks))
        .route("/api/search", get(routes::library::search_tracks))
        .route("/api/stats", get(routes::library::get_stats))
        // Per-track
        .route("/api/tracks/:id/cover", get(routes::tracks::get_cover))
        .route("/api/tracks/:id/metadata",
            get(routes::tracks::get_metadata).patch(routes::tracks::patch_metadata))
        .route("/api/tracks/:id/rating", post(routes::tracks::set_rating))
        .route("/api/tracks/:id/play", post(routes::tracks::record_play))
        .route("/api/tracks/:id/backup", get(routes::tracks::get_backup))
        .route("/api/tracks/:id/restore", post(routes::tracks::restore_backup))
        .route("/api/tracks/:id/rename", post(routes::tracks::rename_file))
        .route("/api/tracks/:id/stream-token", get(routes::stream::issue_token))
        // Playlists — order matters: literal segments before :id
        .route("/api/playlists", get(routes::playlists::list).post(routes::playlists::create))
        .route("/api/playlists/order", put(routes::playlists::reorder))
        .route("/api/playlists/smart", post(routes::playlists::create_smart))
        .route("/api/playlists/smart/:id/rules", patch(routes::playlists::update_smart_rules))
        .route("/api/playlists/smart/:id/tracks", get(routes::playlists::get_smart_tracks))
        .route("/api/playlists/:id",
            patch(routes::playlists::update).delete(routes::playlists::delete))
        .route("/api/playlists/:id/tracks",
            get(routes::playlists::get_tracks)
            .post(routes::playlists::add_tracks)
            .delete(routes::playlists::remove_tracks))
        .route("/api/playlists/:id/entries", get(routes::playlists::get_entries))
        .route("/api/playlists/:id/entries/:entry_id",
            delete(routes::playlists::remove_entry))
        // Admin
        .route("/api/admin/rescan", post(routes::admin::rescan))
        .route("/api/admin/remove-folder", post(routes::admin::remove_folder))
        .route("/api/admin/clear-cache", post(routes::admin::clear_cache))
        // Cast
        .route("/api/cast/devices", get(routes::cast::get_devices))
        .route("/api/cast/discovery/start", post(routes::cast::start_discovery))
        .route("/api/cast/discovery/stop", post(routes::cast::stop_discovery))
        .route("/api/cast/status", get(routes::cast::get_status))
        .route("/api/cast/play", post(routes::cast::play))
        .route("/api/cast/pause", post(routes::cast::pause))
        .route("/api/cast/resume", post(routes::cast::resume))
        .route("/api/cast/stop", post(routes::cast::stop))
        .route("/api/cast/seek", post(routes::cast::seek))
        .route("/api/cast/volume", post(routes::cast::set_volume))
        // Util
        .route("/api/fetch-image", post(routes::util::fetch_image))
        .layer(middleware::from_fn_with_state(state.clone(), auth::auth_middleware));

    // Public routes (no auth)
    let public = Router::new()
        .route("/", get(routes::util::home))
        .route("/api/health", get(routes::util::health))
        .route("/stream/:id", get(routes::stream::stream_audio));

    Router::new()
        .merge(public)
        .merge(protected)
        .with_state(state)
        .layer(cors)
}

#[tokio::main]
async fn main() {
    let config = match find_config() {
        Some(path) => {
            println!("Loading config from {}", path.display());
            Config::load(&path).unwrap_or_else(|e| {
                eprintln!("Config error: {e}");
                std::process::exit(1);
            })
        }
        None => {
            eprintln!("No config file found. Copy muorg-server.toml.example to muorg-server.toml and edit it.");
            eprintln!("Using built-in defaults (api_key=change-me, port=7700, no content paths).");
            Config::default()
        }
    };

    // Ensure storage dirs exist
    if let Some(parent) = config.storage.db_path.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    std::fs::create_dir_all(&config.storage.backup_dir).ok();

    // Open / initialize the database
    let catalog = Catalog::new(&config.storage.db_path).unwrap_or_else(|e| {
        eprintln!("Failed to open database: {e}");
        std::process::exit(1);
    });

    // Prune soft-deleted tracks older than 30 days
    {
        let conn = catalog.db.lock().unwrap();
        let thirty_days = 30 * 24 * 60 * 60;
        let _ = muorg_core::catalog::gc_deleted_tracks(&conn, thirty_days);
    }

    // Scan content_paths on startup if requested
    if config.library.scan_on_startup {
        let conn = catalog.db.lock().unwrap();
        for root in &config.library.content_paths {
            println!("Scanning {root}…");
            if let Err(e) = muorg_core::catalog::save_roots(&conn, std::slice::from_ref(root)) {
                eprintln!("  save_roots failed: {e}");
                continue;
            }
            match muorg_core::catalog::rescan_root(&conn, root) {
                Ok(n) => println!("  {n} tracks indexed"),
                Err(e) => eprintln!("  scan failed: {e}"),
            }
        }
    }

    // Bind the listener first so we know the actual port before building state
    // (the Cast stream URL needs the server's real port number).
    let addr: SocketAddr = format!("{}:{}", config.server.host, config.server.port)
        .parse()
        .unwrap_or_else(|_| "127.0.0.1:7700".parse().unwrap());

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap_or_else(|e| {
        eprintln!("Failed to bind {addr}: {e}");
        std::process::exit(1);
    });
    let server_port = listener.local_addr().map(|a| a.port()).unwrap_or(addr.port());

    let state = Arc::new(AppState::new(
        Arc::new(catalog),
        config.storage.backup_dir.clone(),
        config.server.api_key.clone(),
        server_port,
    ));

    let app = build_router(state, &config.cors.allowed_origins);

    println!("muorg-server listening on http://{}", listener.local_addr().unwrap_or(addr));
    axum::serve(listener, app).await.unwrap();
}

pub mod auth;
pub mod backup;
pub mod cast;
pub mod config;
pub mod musicbrainz;
pub mod ratelimit;
pub mod routes;
pub mod state;
pub mod storage;
pub mod transcode;

use axum::{
    middleware,
    routing::{delete, get, patch, post, put},
    Router,
};
use config::Config;
use state::AppState;
use std::sync::Arc;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;

pub fn build_cors(allowed_origins: &[String]) -> CorsLayer {
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

pub fn build_router(state: Arc<AppState>, allowed_origins: &[String]) -> Router {
    let cors = build_cors(allowed_origins);

    let protected = Router::new()
        .route("/api/roots", get(routes::library::get_roots))
        .route("/api/tracks", get(routes::library::get_tracks))
        .route("/api/tracks/count", get(routes::library::get_tracks_count))
        .route("/api/tracks/recently-added", get(routes::library::get_recently_added))
        .route("/api/play-history/recent", get(routes::library::get_recent_play_history))
        .route("/api/play-history/top", get(routes::library::get_top_play_history))
        .route("/api/search", get(routes::library::search_tracks))
        .route("/api/stats", get(routes::library::get_stats))
        .route("/api/tracks/{id}/cover", get(routes::tracks::get_cover))
        .route("/api/tracks/{id}/lyrics", get(routes::tracks::get_lyrics))
        .route("/api/tracks/{id}/metadata",
            get(routes::tracks::get_metadata).patch(routes::tracks::patch_metadata))
        .route("/api/tracks/{id}/rating", post(routes::tracks::set_rating))
        .route("/api/tracks/{id}/play", post(routes::tracks::record_play))
        .route("/api/tracks/{id}/backup", get(routes::tracks::get_backup))
        .route("/api/tracks/{id}/restore", post(routes::tracks::restore_backup))
        .route("/api/tracks/{id}/rename", post(routes::tracks::rename_file))
        .route("/api/tracks/{id}/auto-tag-suggestions", post(routes::tracks::auto_tag_suggestions))
        .route("/api/tracks/{id}/stream-token", get(routes::stream::issue_token))
        .route("/api/tracks/metadata/batch", post(routes::tracks::batch_patch_metadata))
        .route("/api/playlists", get(routes::playlists::list).post(routes::playlists::create))
        .route("/api/playlists/order", put(routes::playlists::reorder))
        .route("/api/playlists/smart", post(routes::playlists::create_smart))
        .route("/api/playlists/smart/{id}/rules", patch(routes::playlists::update_smart_rules))
        .route("/api/playlists/smart/{id}/tracks", get(routes::playlists::get_smart_tracks))
        .route("/api/playlists/{id}",
            patch(routes::playlists::update).delete(routes::playlists::delete))
        .route("/api/playlists/{id}/tracks",
            get(routes::playlists::get_tracks)
            .post(routes::playlists::add_tracks)
            .delete(routes::playlists::remove_tracks))
        .route("/api/playlists/{id}/tracks/order", put(routes::playlists::reorder_tracks))
        .route("/api/playlists/{id}/entries", get(routes::playlists::get_entries))
        .route("/api/playlists/{id}/entries/{entry_id}",
            delete(routes::playlists::remove_entry))
        .route("/api/admin/rescan", post(routes::admin::rescan))
        .route("/api/admin/remove-folder", post(routes::admin::remove_folder))
        .route("/api/admin/clear-cache", post(routes::admin::clear_cache))
        .route("/api/admin/backup-directory", get(routes::admin::get_backup_directory))
        .route("/api/admin/health", get(routes::admin::health))
        .route("/api/admin/metrics", get(routes::admin::metrics))
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
        .route("/api/fetch-image", post(routes::util::fetch_image))
        // route_layer (not layer): middleware only runs when a route matches,
        // so unknown paths fall through to the 404 fallback instead of 401.
        .route_layer(middleware::from_fn_with_state(state.clone(), auth::auth_middleware))
        .route_layer(middleware::from_fn_with_state(state.clone(), auth::rate_limit_middleware));

    let public = Router::new()
        .route("/", get(routes::util::home))
        .route("/api/health", get(routes::util::health))
        .route("/stream/{id}", get(routes::stream::stream_audio));

    Router::new()
        .merge(public)
        .merge(protected)
        .with_state(state)
        .layer(
            TraceLayer::new_for_http()
                .make_span_with(|req: &axum::http::Request<_>| {
                    tracing::info_span!("http", method = %req.method(), path = req.uri().path())
                })
                .on_response(
                    tower_http::trace::DefaultOnResponse::new()
                        .level(tracing::Level::INFO),
                ),
        )
        .layer(cors)
}

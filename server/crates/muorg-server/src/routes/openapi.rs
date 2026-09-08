//! The OpenAPI document, derived from the handlers themselves.
//!
//! This used to be a hand-authored `serde_json::json!` blob that had to be kept
//! in sync with the router by hand, and carried no schemas at all — every
//! response was `"schema": {}`. So each client re-guessed the wire shapes, and
//! the same bugs (most memorably "a bare `/api/tracks` is only the first 500
//! rows") had to be found and fixed separately in the desktop, web and Android
//! apps.
//!
//! Now `utoipa` builds the document from the `#[utoipa::path]` attributes on the
//! handlers and the `ToSchema` derives on the structs those handlers actually
//! serialize, so the spec cannot drift from the code that serves it. The
//! checked-in `server/openapi.json` is a snapshot of this document (see the
//! `spec_snapshot_is_current` test) and is what the TypeScript and Kotlin client
//! models are generated from.

use utoipa::openapi::security::{HttpAuthScheme, HttpBuilder, SecurityScheme};
use utoipa::{Modify, OpenApi};

struct SecurityAddon;

impl Modify for SecurityAddon {
    fn modify(&self, openapi: &mut utoipa::openapi::OpenApi) {
        if let Some(components) = openapi.components.as_mut() {
            components.add_security_scheme(
                "BearerAuth",
                SecurityScheme::Http(
                    HttpBuilder::new()
                        .scheme(HttpAuthScheme::Bearer)
                        .description(Some("The server's configured `api_key`."))
                        .build(),
                ),
            );
        }
    }
}

#[derive(OpenApi)]
#[openapi(
    info(
        title = "Muorg Server API",
        description = "\
The Muorg music server HTTP API, shared by the desktop (Tauri), web and Android \
clients. Everything except `GET /api/health` and `GET /stream/{id}` requires an \
`Authorization: Bearer <api_key>` header.

`GET /api/tracks` is paginated and defaults to 500 rows — a bare call returns \
the first page, not the catalog. Follow `X-Total-Count`.",
    ),
    servers((url = "/")),
    modifiers(&SecurityAddon),
    tags(
        (name = "Catalog", description = "Tracks, roots, search, stats and play history"),
        (name = "Tracks", description = "Per-track resources: art, lyrics, tags, ratings, backups"),
        (name = "Playlists", description = "Regular and rule-driven smart playlists"),
        (name = "Stream", description = "Token issuing and audio delivery"),
        (name = "Cast", description = "Chromecast discovery and transport control"),
        (name = "Admin", description = "Scanning, cache, health and metrics"),
        (name = "System", description = "Liveness and helpers"),
    ),
    paths(
        // Catalog
        crate::routes::library::get_roots,
        crate::routes::library::get_tracks,
        crate::routes::library::get_tracks_count,
        crate::routes::library::get_recently_added,
        crate::routes::library::get_recent_play_history,
        crate::routes::library::get_top_play_history,
        crate::routes::library::search_tracks,
        crate::routes::library::get_stats,
        // Tracks
        crate::routes::tracks::get_cover,
        crate::routes::tracks::get_lyrics,
        crate::routes::tracks::get_metadata,
        crate::routes::tracks::patch_metadata,
        crate::routes::tracks::batch_patch_metadata,
        crate::routes::tracks::set_rating,
        crate::routes::tracks::record_play,
        crate::routes::tracks::get_backup,
        crate::routes::tracks::restore_backup,
        crate::routes::tracks::rename_file,
        crate::routes::tracks::auto_tag_suggestions,
        // Playlists
        crate::routes::playlists::list,
        crate::routes::playlists::create,
        crate::routes::playlists::update,
        crate::routes::playlists::delete,
        crate::routes::playlists::get_tracks,
        crate::routes::playlists::get_entries,
        crate::routes::playlists::add_tracks,
        crate::routes::playlists::remove_tracks,
        crate::routes::playlists::remove_entry,
        crate::routes::playlists::reorder_tracks,
        crate::routes::playlists::reorder,
        crate::routes::playlists::create_smart,
        crate::routes::playlists::update_smart_rules,
        crate::routes::playlists::get_smart_tracks,
        // Stream
        crate::routes::stream::issue_token,
        crate::routes::stream::stream_audio,
        // Cast
        crate::routes::cast::get_devices,
        crate::routes::cast::start_discovery,
        crate::routes::cast::stop_discovery,
        crate::routes::cast::get_status,
        crate::routes::cast::play,
        crate::routes::cast::pause,
        crate::routes::cast::resume,
        crate::routes::cast::stop,
        crate::routes::cast::seek,
        crate::routes::cast::set_volume,
        // Admin
        crate::routes::admin::rescan,
        crate::routes::admin::remove_folder,
        crate::routes::admin::clear_cache,
        crate::routes::admin::get_backup_directory,
        crate::routes::admin::health,
        crate::routes::admin::metrics,
        // System
        crate::routes::util::health,
        crate::routes::util::fetch_image,
    ),
)]
pub struct ApiDoc;

/// The OpenAPI document, served at `/api/openapi.json`.
pub fn spec() -> serde_json::Value {
    serde_json::to_value(ApiDoc::openapi()).expect("OpenAPI document is serializable")
}

/// Pretty-printed, newline-terminated — byte-identical to `server/openapi.json`.
pub fn spec_json() -> String {
    let mut s = serde_json::to_string_pretty(&spec()).expect("OpenAPI document is serializable");
    s.push('\n');
    s
}

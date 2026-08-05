use axum::{
    extract::State,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use crate::routes::ApiError;
use crate::state::AppState;

#[derive(Deserialize)]
pub struct RescanBody {
    pub root_path: Option<String>,
}

#[derive(Serialize)]
pub struct RescanResult {
    pub tracks_added: u64,
}

pub async fn rescan(
    State(state): State<Arc<AppState>>,
    body: Option<Json<RescanBody>>,
) -> Result<Json<RescanResult>, ApiError> {
    let root_path = body.and_then(|b| b.0.root_path);

    // Each DB section is scoped: a `MutexGuard` is `!Send` and cannot be held
    // across the remote scanner's `.await`s.
    let roots = match root_path {
        Some(path) => {
            // Upsert root (adds if new, resurrects if soft-deleted) then rescan.
            let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
            muorg_core::catalog::save_roots(&conn, std::slice::from_ref(&path))?;
            vec![path]
        }
        None => {
            let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
            muorg_core::catalog::load_roots(&conn)?
        }
    };

    let mut tracks_added = 0u64;
    for root in roots {
        if let Some(remote) = state.remotes.resolve_root(&root) {
            tracks_added +=
                crate::storage::scan::scan_remote_root(&state, &remote, state.remote_scan_concurrency)
                    .await?
                    .0;
        } else {
            let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
            tracks_added += muorg_core::catalog::rescan_root(&conn, &root)?;
        }
    }

    Ok(Json(RescanResult { tracks_added }))
}

#[derive(Deserialize)]
pub struct RemoveFolderBody {
    pub root_path: String,
}

pub async fn remove_folder(
    State(state): State<Arc<AppState>>,
    Json(body): Json<RemoveFolderBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::remove_root(&conn, &body.root_path)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

pub async fn clear_cache(
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::gc_deleted_tracks(&conn, 0)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

pub async fn get_backup_directory(
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let path = state.backup_dir.display().to_string();
    Ok(Json(serde_json::json!({"path": path})))
}

// GET /api/admin/health
pub async fn health(
    State(state): State<Arc<AppState>>,
) -> Json<serde_json::Value> {
    let db_ok = (|| -> Result<(), String> {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        let _ = muorg_core::catalog::load_roots(&conn)?;
        Ok(())
    })().is_ok();

    Json(serde_json::json!({
        "status": if db_ok { "ok" } else { "degraded" },
        "server": "muorg-server",
        "version": env!("CARGO_PKG_VERSION"),
    }))
}

// GET /api/admin/metrics — Prometheus-formatted metrics text
pub async fn metrics(
    State(state): State<Arc<AppState>>,
) -> Result<axum::response::Response, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let tracks = muorg_core::catalog::load_tracks(&conn)?;
    let roots = muorg_core::catalog::load_roots(&conn)?;

    let track_count = tracks.len();
    let root_count = roots.len();
    let artist_count = {
        let mut artists = std::collections::HashSet::new();
        for t in &tracks {
            if let Some(ref a) = t.artist { artists.insert(a.clone()); }
        }
        artists.len()
    };
    let album_count = {
        let mut albums = std::collections::HashSet::new();
        for t in &tracks {
            if let Some(ref a) = t.album { albums.insert(a.clone()); }
        }
        albums.len()
    };
    let total_duration: i64 = tracks.iter().map(|t| t.duration_secs.unwrap_or(0)).sum();

    let body = format!(
        "# HELP muorg_track_count Total number of tracks in the catalog\n\
         # TYPE muorg_track_count gauge\n\
         muorg_track_count {}\n\n\
         # HELP muorg_artist_count Total number of unique artists\n\
         # TYPE muorg_artist_count gauge\n\
         muorg_artist_count {}\n\n\
         # HELP muorg_album_count Total number of unique albums\n\
         # TYPE muorg_album_count gauge\n\
         muorg_album_count {}\n\n\
         # HELP muorg_root_count Number of content roots/folders\n\
         # TYPE muorg_root_count gauge\n\
         muorg_root_count {}\n\n\
         # HELP muorg_total_duration_secs Total playback duration across all tracks\n\
         # TYPE muorg_total_duration_secs gauge\n\
         muorg_total_duration_secs {}\n",
        track_count, artist_count, album_count, root_count, total_duration,
    );

    let headers = [(
        "Content-Type",
        "text/plain; version=0.0.4; charset=utf-8",
    )];

    Ok((headers, body).into_response())
}

use axum::{extract::State, Json};
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
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;

    let tracks_added = if let Some(path) = root_path {
        // Upsert root (adds if new, resurrects if soft-deleted) then rescan.
        muorg_core::catalog::save_roots(&conn, std::slice::from_ref(&path))?;
        muorg_core::catalog::rescan_root(&conn, &path)?
    } else {
        // Rescan all active roots.
        let roots = muorg_core::catalog::load_roots(&conn)?;
        let mut total = 0u64;
        for root in roots {
            total += muorg_core::catalog::rescan_root(&conn, &root)?;
        }
        total
    };

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

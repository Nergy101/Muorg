use axum::{
    extract::{Path, State},
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use crate::routes::ApiError;
use crate::state::AppState;
use muorg_core::catalog::{Playlist, PlaylistTrackEntry};

// GET /api/playlists
pub async fn list(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<Playlist>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::load_playlists(&conn)?))
}

#[derive(Deserialize)]
pub struct CreateBody {
    pub name: String,
}

// POST /api/playlists
pub async fn create(
    State(state): State<Arc<AppState>>,
    Json(body): Json<CreateBody>,
) -> Result<Json<Playlist>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::create_playlist(&conn, &body.name)?))
}

#[derive(Deserialize)]
pub struct UpdateBody {
    pub name: Option<String>,
    pub icon: Option<Option<String>>,
}

// PATCH /api/playlists/:id
pub async fn update(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<UpdateBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    if let Some(name) = body.name {
        muorg_core::catalog::rename_playlist(&conn, id, &name)?;
    }
    if let Some(icon) = body.icon {
        muorg_core::catalog::set_playlist_icon(&conn, id, icon.as_deref())?;
    }
    Ok(Json(serde_json::json!({"ok": true})))
}

// DELETE /api/playlists/:id
pub async fn delete(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::delete_playlist(&conn, id)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

// GET /api/playlists/:id/tracks
pub async fn get_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<i64>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_playlist_tracks(&conn, id)?))
}

// GET /api/playlists/:id/entries
pub async fn get_entries(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<PlaylistTrackEntry>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_playlist_entries(&conn, id)?))
}

#[derive(Deserialize)]
pub struct TrackIdsBody {
    pub track_ids: Vec<i64>,
}

// POST /api/playlists/:id/tracks
pub async fn add_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<TrackIdsBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::add_tracks_to_playlist(&conn, id, &body.track_ids)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

// DELETE /api/playlists/:id/tracks
pub async fn remove_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<TrackIdsBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::remove_tracks_from_playlist(&conn, id, &body.track_ids)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

// DELETE /api/playlists/:id/entries/:entry_id
pub async fn remove_entry(
    Path((_id, entry_id)): Path<(i64, i64)>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::remove_playlist_entry_by_id(&conn, entry_id)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

#[derive(Deserialize)]
pub struct ReorderBody {
    pub ids: Vec<i64>,
}

// PUT /api/playlists/order
pub async fn reorder(
    State(state): State<Arc<AppState>>,
    Json(body): Json<ReorderBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::reorder_playlists(&conn, &body.ids)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

#[derive(Deserialize)]
pub struct SmartCreateBody {
    pub name: String,
    pub rules_json: String,
}

// POST /api/playlists/smart
pub async fn create_smart(
    State(state): State<Arc<AppState>>,
    Json(body): Json<SmartCreateBody>,
) -> Result<Json<Playlist>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::create_smart_playlist(&conn, &body.name, &body.rules_json)?))
}

#[derive(Deserialize)]
pub struct SmartRulesBody {
    pub rules_json: String,
}

// PATCH /api/playlists/smart/:id/rules
pub async fn update_smart_rules(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<SmartRulesBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::set_smart_playlist_rules(&conn, id, Some(&body.rules_json))?;
    Ok(Json(serde_json::json!({"ok": true})))
}

// GET /api/playlists/smart/:id/tracks
pub async fn get_smart_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<i64>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let rules: Option<String> = conn.query_row(
        "SELECT smart_rules FROM playlists WHERE id = ?1",
        [id],
        |r| r.get(0),
    ).map_err(|e| e.to_string())?;
    match rules {
        Some(r) => Ok(Json(muorg_core::catalog::resolve_smart_playlist_track_ids(&conn, &r)?)),
        None => Err(ApiError::not_found("Not a smart playlist")),
    }
}

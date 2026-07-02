use axum::{
    body::Body,
    extract::{Path, State},
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use serde::Deserialize;
use std::path;
use std::sync::Arc;
use crate::backup;
use crate::musicbrainz::SearchQuery;
use crate::routes::ApiError;
use crate::state::AppState;
use muorg_core::catalog::TrackBackupRecord;
use muorg_core::metadata::{MetadataUpdate, TrackMetadata};

fn resolve_track(state: &AppState, id: i64) -> Result<String, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::get_track_path_by_id(&conn, id)?
        .ok_or_else(|| ApiError::not_found(format!("Track {id} not found")))
}

// GET /api/tracks/:id/cover — returns binary image with correct Content-Type
pub async fn get_cover(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Response, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let meta = muorg_core::metadata::read_metadata(path::Path::new(&track_path))?;

    let b64 = match meta.picture_base64 {
        Some(b) if !b.is_empty() => b,
        _ => return Err(ApiError::not_found("No cover art")),
    };
    let mime = meta.picture_mime.unwrap_or_else(|| "image/jpeg".to_string());
    let data = base64::Engine::decode(&base64::engine::general_purpose::STANDARD, &b64)
        .map_err(|e| e.to_string())?;

    let mut headers = HeaderMap::new();
    headers.insert("Content-Type", mime.parse().unwrap());
    headers.insert("Content-Length", data.len().to_string().parse().unwrap());
    Ok((StatusCode::OK, headers, Body::from(data)).into_response())
}

// GET /api/tracks/:id/metadata
pub async fn get_metadata(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<TrackMetadata>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let meta = muorg_core::metadata::read_metadata(path::Path::new(&track_path))?;
    Ok(Json(meta))
}

#[derive(Deserialize)]
pub struct PatchMetadataBody {
    #[serde(flatten)]
    pub update: MetadataUpdate,
    pub backup_before_write: Option<bool>,
}

// PATCH /api/tracks/:id/metadata
pub async fn patch_metadata(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<PatchMetadataBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let track_path = resolve_track(&state, id)?;

    if body.backup_before_write.unwrap_or(false) {
        let backup_path_str = backup::create_backup(&state.backup_dir, &track_path)?;
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::record_track_backup(&conn, &track_path, &backup_path_str)?;
        // Prune old backups for this source file
        let _ = backup::gc_old_backups(&state.backup_dir, state.backup_retention_count);
    }

    muorg_core::metadata::write_metadata(path::Path::new(&track_path), &body.update)?;

    {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::update_track_metadata(&conn, &track_path, &body.update)?;
        if let Ok(new_hash) = muorg_core::catalog::compute_content_hash(path::Path::new(&track_path)) {
            let _ = muorg_core::catalog::update_track_hash(&conn, &track_path, &new_hash);
        }
    }

    Ok(Json(serde_json::json!({"ok": true})))
}

#[derive(Deserialize)]
pub struct RatingBody {
    pub rating: Option<i64>,
}

// POST /api/tracks/:id/rating
pub async fn set_rating(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<RatingBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::set_track_rating(&conn, &track_path, body.rating)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

// POST /api/tracks/:id/play
pub async fn record_play(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::record_play(&conn, &track_path)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

// GET /api/tracks/:id/backup
pub async fn get_backup(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Option<TrackBackupRecord>>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let backup = muorg_core::catalog::get_latest_track_backup(&conn, &track_path)?;
    Ok(Json(backup))
}

// POST /api/tracks/:id/restore
pub async fn restore_backup(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let backup_record = {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::get_latest_track_backup(&conn, &track_path)?
            .ok_or_else(|| ApiError::not_found("No backup available"))?
    };
    std::fs::copy(&backup_record.backup_path, &track_path)
        .map_err(|e| format!("Restore failed: {e}"))?;
    let fresh = muorg_core::metadata::read_metadata(path::Path::new(&track_path))?;
    let update = MetadataUpdate {
        title: Some(fresh.title.map(Some).unwrap_or(None)),
        artist: Some(fresh.artist.map(Some).unwrap_or(None)),
        album: Some(fresh.album.map(Some).unwrap_or(None)),
        album_artist: Some(fresh.album_artist.map(Some).unwrap_or(None)),
        featuring: Some(fresh.featuring.map(Some).unwrap_or(None)),
        year: Some(fresh.year.map(Some).unwrap_or(None)),
        genre: Some(fresh.genre.map(Some).unwrap_or(None)),
        track_number: Some(fresh.track_number.map(Some).unwrap_or(None)),
        disc_number: Some(fresh.disc_number.map(Some).unwrap_or(None)),
        picture_base64: Some(fresh.picture_base64.map(Some).unwrap_or(None)),
    };
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::update_track_metadata(&conn, &track_path, &update)?;
    if let Ok(new_hash) = muorg_core::catalog::compute_content_hash(path::Path::new(&track_path)) {
        let _ = muorg_core::catalog::update_track_hash(&conn, &track_path, &new_hash);
    }
    Ok(Json(serde_json::json!({"ok": true})))
}

#[derive(Deserialize)]
pub struct RenameBody {
    pub new_path: String,
}

// POST /api/tracks/:id/rename
pub async fn rename_file(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<RenameBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let old_path = resolve_track(&state, id)?;
    let new = path::Path::new(&body.new_path);
    if let Some(parent) = new.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::fs::rename(&old_path, &body.new_path).map_err(|e| e.to_string())?;
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::update_track_path(&conn, &old_path, &body.new_path)?;
    Ok(Json(serde_json::json!({"ok": true})))
}

// POST /api/tracks/:id/auto-tag-suggestions
pub async fn auto_tag_suggestions(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    body: Option<Json<SearchQuery>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let meta = muorg_core::metadata::read_metadata(path::Path::new(&track_path))?;

    // Build query from request body or fall back to file metadata
    let query = match body {
        Some(Json(q)) => q,
        None => SearchQuery {
            artist: meta.artist.clone(),
            title: meta.title.clone(),
            album: meta.album.clone(),
            duration_secs: meta.duration_secs.map(|s| s as u32),
        },
    };

    let candidates = state.auto_tag.search(&query).await.map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, e))?;
    Ok(Json(serde_json::json!({"candidates": candidates})))
}

#[derive(Deserialize)]
pub struct BatchMetadataItem {
    pub id: i64,
    #[serde(flatten)]
    pub update: MetadataUpdate,
}

// POST /api/tracks/metadata/batch
pub async fn batch_patch_metadata(
    State(state): State<Arc<AppState>>,
    Json(items): Json<Vec<BatchMetadataItem>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    if items.is_empty() {
        return Ok(Json(serde_json::json!({"ok": true, "updated": 0})));
    }

    // Resolve all paths first
    let mut updates: Vec<(String, MetadataUpdate)> = Vec::with_capacity(items.len());
    for item in &items {
        let track_path = resolve_track(&state, item.id)?;
        updates.push((track_path, item.update.clone()));
    }

    // Write metadata to files
    for (track_path, update) in &updates {
        muorg_core::metadata::write_metadata(path::Path::new(&track_path), update)?;
    }

    // Batch update the DB in a single transaction
    {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        let batch: Vec<(&str, &MetadataUpdate)> = updates.iter().map(|(p, u)| (p.as_str(), u)).collect();
        muorg_core::catalog::batch_update_track_metadata(&conn, &batch)?;
    }

    Ok(Json(serde_json::json!({"ok": true, "updated": updates.len()})))
}

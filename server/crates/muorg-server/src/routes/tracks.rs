use axum::{
    body::Body,
    extract::{Path, Query, State},
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

fn resolve_track_with_mtime(state: &AppState, id: i64) -> Result<(String, i64), ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::get_track_path_and_mtime_by_id(&conn, id)?
        .ok_or_else(|| ApiError::not_found(format!("Track {id} not found")))
}

/// Reads tags from a track wherever it lives. Remote tracks are read through a
/// ranged reader, so only the bytes the tag parser touches leave the bucket.
pub async fn read_track_metadata(
    state: &AppState,
    track_path: &str,
) -> Result<TrackMetadata, ApiError> {
    if let Some((remote, key)) = state.remotes.resolve(track_path) {
        let format = path::Path::new(track_path)
            .extension()
            .and_then(|e| e.to_str())
            .and_then(muorg_core::metadata::format_from_ext)
            .ok_or_else(|| ApiError::bad_request("Unsupported format"))?;
        use object_store::ObjectStoreExt;
        let head = remote
            .store
            .head(&key)
            .await
            .map_err(|e| format!("Object store error: {e}"))?;
        let store = remote.store.clone();
        let handle = tokio::runtime::Handle::current();
        let size = head.size;
        return tokio::task::spawn_blocking(move || {
            let mut r = crate::storage::reader::RemoteReader::new(store, key, size, handle);
            muorg_core::metadata::read_metadata_from_reader(&mut r, format)
        })
        .await
        .map_err(|e| e.to_string())?
        .map_err(ApiError::from);
    }
    let p = track_path.to_string();
    tokio::task::spawn_blocking(move || muorg_core::metadata::read_metadata(path::Path::new(&p)))
        .await
        .map_err(|e| e.to_string())?
        .map_err(ApiError::from)
}

// GET /api/tracks/:id/cover — returns binary image with correct Content-Type
// Optionally `?size=N` (max edge in px) returns a downscaled JPEG thumbnail to
// cut mobile bandwidth for grids/list rows.
#[derive(Deserialize)]
pub struct CoverQuery {
    /// Requested max edge length in px; when provided the cover is downscaled.
    pub size: Option<u32>,
}

/// Downscales a cover to at most `max_edge` px on its longest side and returns
/// it as a quality-80 JPEG. Returns `None` when the source isn't decodable or
/// is already small enough to serve as-is.
fn downscale_cover(data: &[u8], max_edge: u32) -> Option<Vec<u8>> {
    use image::GenericImageView;
    let img = image::load_from_memory(data).ok()?;
    let (w, h) = img.dimensions();
    let largest = w.max(h);
    if largest <= max_edge {
        return None;
    }
    let scale = max_edge as f32 / largest as f32;
    let nw = ((w as f32 * scale).round().max(1.0)) as u32;
    let nh = ((h as f32 * scale).round().max(1.0)) as u32;
    let thumb = img.resize(nw, nh, image::imageops::FilterType::Triangle);
    let mut out = Vec::new();
    let mut cursor = std::io::Cursor::new(&mut out);
    image::codecs::jpeg::JpegEncoder::new_with_quality(&mut cursor, 80)
        .encode_image(&thumb.to_rgb8())
        .ok()?;
    Some(out)
}

pub async fn get_cover(
    Path(id): Path<i64>,
    Query(params): Query<CoverQuery>,
    State(state): State<Arc<AppState>>,
    req_headers: HeaderMap,
) -> Result<Response, ApiError> {
    let (track_path, track_mtime_secs) = resolve_track_with_mtime(&state, id)?;
    let is_remote = crate::storage::is_remote_uri(&track_path);
    let size = params.size.filter(|s| *s >= 16);

    // Remote covers key off the catalog's mtime so a cache hit costs no request
    // at all; local ones stay on the file's own mtime, as before.
    let mtime = if is_remote {
        Some(std::time::UNIX_EPOCH + std::time::Duration::from_secs(track_mtime_secs.max(0) as u64))
    } else {
        crate::routes::util::file_mtime(path::Path::new(&track_path))
    };
    let etag = format!(
        "\"cover-{id}-{}-{}\"",
        size.unwrap_or(0),
        mtime.map(|t| t.duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)).unwrap_or(0)
    );
    if let Some(resp) = crate::routes::util::check_not_modified(&etag, mtime, &req_headers) {
        return Ok(resp);
    }

    let (data, mime) = match state.cover_cache.get(id, track_mtime_secs).filter(|_| is_remote) {
        Some(hit) => hit,
        None => {
            let meta = read_track_metadata(&state, &track_path).await?;
            let b64 = match meta.picture_base64 {
                Some(b) if !b.is_empty() => b,
                _ => return Err(ApiError::not_found("No cover art")),
            };
            let mime = meta.picture_mime.unwrap_or_else(|| "image/jpeg".to_string());
            let data = base64::Engine::decode(&base64::engine::general_purpose::STANDARD, &b64)
                .map_err(|e| e.to_string())?;
            if is_remote {
                state.cover_cache.put(id, &mime, &data);
            }
            (data, mime)
        }
    };

    // Downscale on demand; thumbnails are always JPEG (covers are photo art, and
    // JPEG shrinks bytes far better than PNG for them).
    let (data, mime) = match size.and_then(|s| downscale_cover(&data, s)) {
        Some(thumb) => (thumb, "image/jpeg".to_string()),
        None => (data, mime),
    };

    let mut headers = HeaderMap::new();
    headers.insert("Content-Type", mime.parse().unwrap());
    headers.insert("Content-Length", data.len().to_string().parse().unwrap());
    headers.insert("Cache-Control", "public, max-age=86400".parse().unwrap());
    headers.insert("Vary", "Accept-Encoding".parse().unwrap());
    headers.insert("ETag", etag.parse().unwrap());
    if let Some(m) = mtime {
        headers.insert("Last-Modified", crate::routes::util::http_date(m).parse().unwrap());
    }
    Ok((StatusCode::OK, headers, Body::from(data)).into_response())
}

// GET /api/tracks/:id/lyrics — stored embedded lyrics, or 404 when the track
// has none. Sync format is `"lrc"` when the text carries `[mm:ss]` lines.
pub async fn get_lyrics(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    resolve_track(&state, id)?;
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    match muorg_core::catalog::get_track_lyrics(&conn, id).map_err(ApiError::from)? {
        Some(l) => Ok(Json(serde_json::json!({
            "track_id": l.track_id,
            "lyrics": l.lyrics,
            "sync_format": l.sync_format,
        }))),
        None => Err(ApiError::not_found("No lyrics for this track")),
    }
}

// GET /api/tracks/:id/metadata
pub async fn get_metadata(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<TrackMetadata>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let meta = read_track_metadata(&state, &track_path).await?;
    Ok(Json(meta))
}

#[derive(Deserialize)]
pub struct PatchMetadataBody {
    #[serde(flatten)]
    pub update: MetadataUpdate,
    pub backup_before_write: Option<bool>,
}

/// What a remote write changed, and what the caller must therefore persist.
struct RemoteWrite {
    /// The object's new last-modified time. `tracks.mtime_secs` must follow it:
    /// it is both the cover cache's validity key and the scanner's change key.
    new_mtime: i64,
    /// Hash of the bytes actually uploaded, computed locally from the temp file.
    new_hash: Option<String>,
}

/// Writes `update` into the track's file or object.
///
/// Local tracks are edited in place. Remote ones are fetched to a temp file,
/// rewritten and re-uploaded, because both taggers need a seekable read-write
/// file. Returns `Some(_)` only for remote tracks.
async fn write_track_metadata(
    state: &AppState,
    track_path: &str,
    update: &MetadataUpdate,
    backup: bool,
) -> Result<Option<RemoteWrite>, ApiError> {
    let record_backup = |state: &AppState, src: &path::Path| -> Result<(), ApiError> {
        let backup_path_str = backup::create_backup(&state.backup_dir, src, track_path)?;
        {
            let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
            muorg_core::catalog::record_track_backup(&conn, track_path, &backup_path_str)?;
        }
        // Prune old backups for this source file
        let _ = backup::gc_old_backups(&state.backup_dir, state.backup_retention_count);
        Ok(())
    };

    let Some((remote, key)) = state.remotes.resolve(track_path) else {
        if backup {
            record_backup(state, path::Path::new(track_path))?;
        }
        let (p, upd) = (track_path.to_string(), update.clone());
        tokio::task::spawn_blocking(move || {
            muorg_core::metadata::write_metadata(path::Path::new(&p), &upd)
        })
        .await
        .map_err(|e| e.to_string())??;
        return Ok(None);
    };

    let ext = path::Path::new(track_path)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("bin");
    let tmp = crate::storage::fetch_to_temp(&remote, &key, ext).await?;

    if backup {
        record_backup(state, tmp.path())?;
    }

    let (tmp_path, upd) = (tmp.path().to_path_buf(), update.clone());
    tokio::task::spawn_blocking(move || muorg_core::metadata::write_metadata(&tmp_path, &upd))
        .await
        .map_err(|e| e.to_string())??;

    let new_meta = crate::storage::put_from_file(&remote, &key, tmp.path()).await?;
    // Identical to the hash the scanner would compute from the bucket, because
    // both go through `content_hash_from_parts`.
    let new_hash = muorg_core::catalog::compute_content_hash(tmp.path()).ok();
    Ok(Some(RemoteWrite {
        new_mtime: new_meta.last_modified.timestamp(),
        new_hash,
    }))
}

// PATCH /api/tracks/:id/metadata
pub async fn patch_metadata(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<PatchMetadataBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let track_path = resolve_track(&state, id)?;
    let remote_write = write_track_metadata(
        &state,
        &track_path,
        &body.update,
        body.backup_before_write.unwrap_or(false),
    )
    .await?;

    {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::update_track_metadata(&conn, &track_path, &body.update)?;
        match &remote_write {
            Some(w) => {
                if let Some(h) = &w.new_hash {
                    let _ = muorg_core::catalog::update_track_hash(&conn, &track_path, h);
                }
                muorg_core::catalog::update_track_mtime(&conn, &track_path, w.new_mtime)?;
            }
            None => {
                if let Ok(new_hash) = muorg_core::catalog::compute_content_hash(path::Path::new(&track_path)) {
                    let _ = muorg_core::catalog::update_track_hash(&conn, &track_path, &new_hash);
                }
            }
        }
    }

    if remote_write.is_some() {
        state.cover_cache.invalidate(id);
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
    // The backup itself is always a local file; only the destination differs.
    let restored_mtime = match state.remotes.resolve(&track_path) {
        Some((remote, key)) => Some(
            crate::storage::put_from_file(
                &remote,
                &key,
                path::Path::new(&backup_record.backup_path),
            )
            .await?
            .last_modified
            .timestamp(),
        ),
        None => {
            std::fs::copy(&backup_record.backup_path, &track_path)
                .map_err(|e| format!("Restore failed: {e}"))?;
            None
        }
    };
    let fresh = muorg_core::metadata::read_metadata(path::Path::new(&backup_record.backup_path))?;
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
    {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::update_track_metadata(&conn, &track_path, &update)?;
        let hash_src = match restored_mtime {
            Some(m) => {
                muorg_core::catalog::update_track_mtime(&conn, &track_path, m)?;
                path::Path::new(&backup_record.backup_path)
            }
            None => path::Path::new(&track_path),
        };
        if let Ok(new_hash) = muorg_core::catalog::compute_content_hash(hash_src) {
            let _ = muorg_core::catalog::update_track_hash(&conn, &track_path, &new_hash);
        }
    }
    if restored_mtime.is_some() {
        state.cover_cache.invalidate(id);
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

    if let Some((remote, old_key)) = state.remotes.resolve(&old_path) {
        // A rename is a copy+delete inside one bucket; crossing backends would
        // be a transfer, which this endpoint does not do.
        let new_key = match state.remotes.resolve(&body.new_path) {
            Some((new_remote, k)) if new_remote.name == remote.name => k,
            _ => {
                return Err(ApiError::bad_request(
                    "Cannot move a track between storage backends",
                ))
            }
        };
        use object_store::ObjectStoreExt;
        remote
            .store
            .rename(&old_key, &new_key)
            .await
            .map_err(|e| format!("Rename failed: {e}"))?;
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::update_track_path(&conn, &old_path, &body.new_path)?;
        return Ok(Json(serde_json::json!({"ok": true})));
    }

    if crate::storage::is_remote_uri(&body.new_path) {
        return Err(ApiError::bad_request(
            "Cannot move a track between storage backends",
        ));
    }

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
    let meta = read_track_metadata(&state, &track_path).await?;

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
    let mut updates: Vec<(i64, String, MetadataUpdate)> = Vec::with_capacity(items.len());
    for item in &items {
        let track_path = resolve_track(&state, item.id)?;
        updates.push((item.id, track_path, item.update.clone()));
    }

    // Write metadata to each track's file or object
    let mut remote_writes: Vec<(usize, RemoteWrite)> = Vec::new();
    for (i, (_, track_path, update)) in updates.iter().enumerate() {
        if let Some(w) = write_track_metadata(&state, track_path, update, false).await? {
            remote_writes.push((i, w));
        }
    }

    // Batch update the DB in a single transaction
    {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        let batch: Vec<(&str, &MetadataUpdate)> =
            updates.iter().map(|(_, p, u)| (p.as_str(), u)).collect();
        muorg_core::catalog::batch_update_track_metadata(&conn, &batch)?;
        // Remote objects changed identity: mtime drives the cover cache and the
        // scanner, the hash drives move detection.
        for (i, w) in &remote_writes {
            let track_path = updates[*i].1.as_str();
            muorg_core::catalog::update_track_mtime(&conn, track_path, w.new_mtime)?;
            if let Some(h) = &w.new_hash {
                let _ = muorg_core::catalog::update_track_hash(&conn, track_path, h);
            }
        }
    }
    for (i, _) in &remote_writes {
        state.cover_cache.invalidate(updates[*i].0);
    }

    Ok(Json(serde_json::json!({"ok": true, "updated": updates.len()})))
}

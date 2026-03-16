use crate::catalog::{Catalog, CatalogTrack, Playlist, PlaylistTrackEntry};
use crate::metadata::{read_metadata, write_metadata, MetadataUpdate};
use base64::Engine;
use serde::Serialize;
use std::path::Path;
use std::sync::Arc;
use tauri::State;

#[derive(Serialize)]
pub struct AddFolderResult {
    pub roots: Vec<String>,
    pub tracks_added: u64,
}

/// If path is a file, return its parent directory; otherwise return the path (must be a directory).
fn normalize_to_folder(path: &str) -> Result<String, String> {
    let p = Path::new(path);
    if !p.exists() {
        return Err("Path does not exist".to_string());
    }
    let folder = if p.is_file() {
        p.parent().ok_or("Invalid file path")?.to_path_buf()
    } else {
        p.to_path_buf()
    };
    folder
        .to_str()
        .map(String::from)
        .ok_or_else(|| "Invalid path encoding".to_string())
}

#[tauri::command]
pub async fn add_folder(
    catalog: State<'_, Arc<Catalog>>,
    path: String,
) -> Result<AddFolderResult, String> {
    let folder = normalize_to_folder(&path)?;
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::save_roots(&conn, std::slice::from_ref(&folder))?;
    let tracks_added = crate::catalog::scan_and_insert(&conn, &folder)?;
    let roots = crate::catalog::load_roots(&conn)?;
    Ok(AddFolderResult {
        roots,
        tracks_added,
    })
}

#[tauri::command]
pub async fn path_to_folder(path: String) -> Result<String, String> {
    normalize_to_folder(&path)
}

#[tauri::command]
pub async fn get_roots(catalog: State<'_, Arc<Catalog>>) -> Result<Vec<String>, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::load_roots(&conn)
}

#[tauri::command]
pub async fn get_tracks(catalog: State<'_, Arc<Catalog>>) -> Result<Vec<CatalogTrack>, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::load_tracks(&conn)
}

#[tauri::command]
pub async fn rescan(catalog: State<'_, Arc<Catalog>>, root_path: String) -> Result<u64, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::rescan_root(&conn, &root_path)
}

#[tauri::command]
pub async fn remove_folder(
    catalog: State<'_, Arc<Catalog>>,
    root_path: String,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::remove_root(&conn, &root_path)
}

/// Hard-delete all soft-deleted tracks and their now-orphaned roots immediately,
/// bypassing the 30-day grace period used at startup.
#[tauri::command]
pub async fn clear_cache(catalog: State<'_, Arc<Catalog>>) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::gc_deleted_tracks(&conn, 0)
}

/// Cover art data returned to the frontend so it can use the correct MIME type in data URLs
/// (e.g. image/png) and show dimensions/size. Using the wrong MIME type (e.g. image/jpeg for PNG)
/// can prevent dimensions from loading and cause inconsistent display in table vs group headers.
#[derive(Serialize)]
pub struct CoverInfo {
    pub base64: String,
    pub mime: String,
    pub size_bytes: u32,
}

#[tauri::command]
pub async fn get_track_cover(path: String) -> Result<Option<CoverInfo>, String> {
    let meta = read_metadata(Path::new(&path))?;
    let base64 = match meta.picture_base64 {
        Some(b) => b,
        None => return Ok(None),
    };
    let mime = meta
        .picture_mime
        .unwrap_or_else(|| "image/jpeg".to_string());
    let size_bytes = meta.picture_size_bytes.unwrap_or(0);
    Ok(Some(CoverInfo {
        base64,
        mime,
        size_bytes,
    }))
}

/// Read an audio file and return its contents as base64 so the frontend can create a blob URL for playback.
#[tauri::command]
pub async fn read_audio_file(path: String) -> Result<String, String> {
    let path = Path::new(&path);
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_lowercase());
    if ext.as_deref() != Some("mp3") && ext.as_deref() != Some("flac") {
        return Err("Unsupported format".to_string());
    }
    let bytes = std::fs::read(path).map_err(|e| e.to_string())?;
    let b64 = base64::engine::general_purpose::STANDARD.encode(&bytes);
    Ok(b64)
}

#[tauri::command]
pub async fn write_track_metadata(
    catalog: State<'_, Arc<Catalog>>,
    path: String,
    update: MetadataUpdate,
) -> Result<(), String> {
    let file_path = std::path::Path::new(&path);
    write_metadata(file_path, &update)?;
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::update_track_metadata(&conn, &path, &update)?;
    // Recompute the content hash after the file has been modified so that a future
    // remove-and-re-add of the folder still matches this track's updated hash.
    if let Ok(new_hash) = crate::catalog::compute_content_hash(file_path) {
        let _ = crate::catalog::update_track_hash(&conn, &path, &new_hash);
    }
    Ok(())
}

// ── Playlist commands ──────────────────────────────────────────────────────

#[tauri::command]
pub async fn get_playlists(catalog: State<'_, Arc<Catalog>>) -> Result<Vec<Playlist>, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::load_playlists(&conn)
}

#[tauri::command]
pub async fn create_playlist(
    catalog: State<'_, Arc<Catalog>>,
    name: String,
) -> Result<Playlist, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::create_playlist(&conn, &name)
}

#[tauri::command]
pub async fn rename_playlist(
    catalog: State<'_, Arc<Catalog>>,
    id: i64,
    name: String,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::rename_playlist(&conn, id, &name)
}

#[tauri::command]
pub async fn delete_playlist(
    catalog: State<'_, Arc<Catalog>>,
    id: i64,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::delete_playlist(&conn, id)
}

#[tauri::command]
pub async fn get_playlist_tracks(
    catalog: State<'_, Arc<Catalog>>,
    playlist_id: i64,
) -> Result<Vec<i64>, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::get_playlist_tracks(&conn, playlist_id)
}

#[tauri::command]
pub async fn get_playlist_entries(
    catalog: State<'_, Arc<Catalog>>,
    playlist_id: i64,
) -> Result<Vec<PlaylistTrackEntry>, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::get_playlist_entries(&conn, playlist_id)
}

#[tauri::command]
pub async fn remove_playlist_entry(
    catalog: State<'_, Arc<Catalog>>,
    entry_id: i64,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::remove_playlist_entry_by_id(&conn, entry_id)
}

#[tauri::command]
pub async fn add_tracks_to_playlist(
    catalog: State<'_, Arc<Catalog>>,
    playlist_id: i64,
    track_ids: Vec<i64>,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::add_tracks_to_playlist(&conn, playlist_id, &track_ids)
}

#[tauri::command]
pub async fn remove_tracks_from_playlist(
    catalog: State<'_, Arc<Catalog>>,
    playlist_id: i64,
    track_ids: Vec<i64>,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::remove_tracks_from_playlist(&conn, playlist_id, &track_ids)
}

// ── Image fetch ────────────────────────────────────────────────────────────

/// Download an image from a URL and return base64-encoded data plus MIME type (e.g. for Wikipedia album art).
#[derive(serde::Serialize)]
pub struct FetchedImage {
    pub base64: String,
    pub mime: String,
}

static USER_AGENT: &str = "Muorg/1.0 (music organizer; album art from Wikipedia)";

#[tauri::command]
pub async fn fetch_image_url(url: String) -> Result<FetchedImage, String> {
    let client = reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .build()
        .map_err(|e| e.to_string())?;
    let response = client.get(&url).send().await.map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        return Err(format!("HTTP {}", response.status()));
    }
    let content_type = response
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("image/jpeg")
        .split(';')
        .next()
        .unwrap_or("image/jpeg")
        .trim()
        .to_string();
    let bytes = response.bytes().await.map_err(|e| e.to_string())?;
    let base64 = base64::engine::general_purpose::STANDARD.encode(bytes.as_ref());
    Ok(FetchedImage {
        base64,
        mime: content_type,
    })
}

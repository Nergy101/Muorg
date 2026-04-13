use crate::catalog::{Catalog, CatalogTrack, Playlist, PlaylistTrackEntry, TrackBackupRecord};
use crate::metadata::{read_metadata, write_metadata, MetadataUpdate, TrackMetadata};
use base64::Engine;
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::path::Path;
use std::sync::Arc;
use tauri::Manager;
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
pub async fn write_text_file(path: String, content: String) -> Result<(), String> {
    std::fs::write(&path, content).map_err(|e| e.to_string())
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
    app: tauri::AppHandle,
    catalog: State<'_, Arc<Catalog>>,
    path: String,
    update: MetadataUpdate,
    backup_before_write: Option<bool>,
) -> Result<(), String> {
    if backup_before_write.unwrap_or(false) {
        create_backup(&app, &path)?;
        let conn = catalog.db.lock().map_err(|e| e.to_string())?;
        if let Some(backup_path) = latest_backup_path(&app, &path)? {
            crate::catalog::record_track_backup(&conn, &path, &backup_path)?;
        }
    }
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

fn backup_file_name(path: &str) -> Result<String, String> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| "time error".to_string())?
        .as_secs();
    let mut hasher = Sha256::new();
    hasher.update(path.as_bytes());
    let hash = format!("{:x}", hasher.finalize());
    let ext = Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("bin");
    Ok(format!("{}-{}.{}", now, &hash[..12], ext))
}

fn backup_dir(app: &tauri::AppHandle) -> Result<std::path::PathBuf, String> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| format!("Failed to resolve app data dir: {e}"))?
        .join("backups");
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}

fn create_backup(app: &tauri::AppHandle, path: &str) -> Result<String, String> {
    let src = Path::new(path);
    if !src.exists() {
        return Err("Track file does not exist".to_string());
    }
    let backup_path = backup_dir(app)?.join(backup_file_name(path)?);
    std::fs::copy(src, &backup_path).map_err(|e| format!("Backup failed: {e}"))?;
    backup_path
        .to_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Invalid backup path".to_string())
}

fn latest_backup_path(app: &tauri::AppHandle, path: &str) -> Result<Option<String>, String> {
    let dir = backup_dir(app)?;
    let mut entries = std::fs::read_dir(dir)
        .map_err(|e| e.to_string())?
        .filter_map(|e| e.ok())
        .collect::<Vec<_>>();
    entries.sort_by_key(|e| e.file_name());
    let mut hasher = Sha256::new();
    hasher.update(path.as_bytes());
    let hash = format!("{:x}", hasher.finalize());
    let needle = &hash[..12];
    for entry in entries.into_iter().rev() {
        let name = entry.file_name().to_string_lossy().to_string();
        if name.contains(needle) {
            let p = entry.path();
            if let Some(s) = p.to_str() {
                return Ok(Some(s.to_string()));
            }
        }
    }
    Ok(None)
}

#[tauri::command]
pub async fn get_track_metadata(path: String) -> Result<TrackMetadata, String> {
    read_metadata(Path::new(&path))
}

#[tauri::command]
pub async fn get_latest_track_backup(
    catalog: State<'_, Arc<Catalog>>,
    path: String,
) -> Result<Option<TrackBackupRecord>, String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::get_latest_track_backup(&conn, &path)
}

#[tauri::command]
pub async fn restore_track_from_latest_backup(
    catalog: State<'_, Arc<Catalog>>,
    path: String,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    let backup = crate::catalog::get_latest_track_backup(&conn, &path)?
        .ok_or_else(|| "No backup found for this track".to_string())?;
    std::fs::copy(&backup.backup_path, &path).map_err(|e| format!("Restore failed: {e}"))?;
    let fresh = read_metadata(Path::new(&path))?;
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
    crate::catalog::update_track_metadata(&conn, &path, &update)?;
    if let Ok(new_hash) = crate::catalog::compute_content_hash(Path::new(&path)) {
        let _ = crate::catalog::update_track_hash(&conn, &path, &new_hash);
    }
    Ok(())
}

#[tauri::command]
pub async fn set_track_rating(
    catalog: State<'_, Arc<Catalog>>,
    path: String,
    rating: Option<i64>,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::set_track_rating(&conn, &path, rating)
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
pub async fn set_playlist_icon(
    catalog: State<'_, Arc<Catalog>>,
    id: i64,
    icon: Option<String>,
) -> Result<(), String> {
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::set_playlist_icon(&conn, id, icon.as_deref())
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

// ── Google Cast ─────────────────────────────────────────────────────────────

#[tauri::command]
pub async fn cast_start_discovery(
    app: tauri::AppHandle,
    discovery: State<'_, crate::cast::DiscoveryState>,
) -> Result<(), String> {
    discovery.start(app);
    Ok(())
}

#[tauri::command]
pub async fn cast_stop_discovery(
    discovery: State<'_, crate::cast::DiscoveryState>,
) -> Result<(), String> {
    discovery.stop();
    Ok(())
}

#[tauri::command]
pub async fn cast_get_devices(
    discovery: State<'_, crate::cast::DiscoveryState>,
) -> Result<Vec<crate::cast::discovery::CastDevice>, String> {
    Ok(discovery.devices.lock().unwrap().clone())
}

#[tauri::command]
pub async fn cast_play(
    app: tauri::AppHandle,
    device_id: String,
    track_path: String,
    discovery: State<'_, crate::cast::DiscoveryState>,
    server: State<'_, crate::cast::AudioServerState>,
    cast_state: State<'_, crate::cast::CastState>,
) -> Result<(), String> {
    let device = {
        let devs = discovery.devices.lock().unwrap();
        devs.iter()
            .find(|d| d.id == device_id)
            .cloned()
            .ok_or_else(|| format!("Cast device not found: {device_id}"))?
    };

    let port = server.start_if_needed().await?;
    server.add_to_allowlist(&track_path);

    let lan_ip = local_ip_address::local_ip().map_err(|e| e.to_string())?;
    let encoded = urlencoding::encode(&track_path);
    let stream_url = format!("http://{lan_ip}:{port}/track?path={encoded}");
    let is_flac = track_path.to_lowercase().ends_with(".flac");

    cast_state.start_session(device.address, device.port, stream_url, is_flac, app);
    Ok(())
}

#[tauri::command]
pub async fn cast_set_volume(
    cast_state: State<'_, crate::cast::CastState>,
    level: f32,
) -> Result<(), String> {
    let level = level.clamp(0.0, 1.0);
    cast_state.send_command(crate::cast::CastCommand::SetVolume(level))
}

#[tauri::command]
pub async fn cast_seek(
    cast_state: State<'_, crate::cast::CastState>,
    position_secs: f32,
    was_playing: bool,
) -> Result<(), String> {
    cast_state.send_command(crate::cast::CastCommand::Seek { secs: position_secs, was_playing })
}

#[tauri::command]
pub async fn cast_pause(
    cast_state: State<'_, crate::cast::CastState>,
) -> Result<(), String> {
    cast_state.send_command(crate::cast::CastCommand::Pause)
}

#[tauri::command]
pub async fn cast_resume(
    cast_state: State<'_, crate::cast::CastState>,
) -> Result<(), String> {
    cast_state.send_command(crate::cast::CastCommand::Resume)
}

#[tauri::command]
pub async fn cast_stop(
    server: State<'_, crate::cast::AudioServerState>,
    cast_state: State<'_, crate::cast::CastState>,
) -> Result<(), String> {
    cast_state.send_command(crate::cast::CastCommand::Stop)?;
    server.stop();
    Ok(())
}

// ── Image fetch ────────────────────────────────────────────────────────────

/// Move a track file to a new path, creating parent directories as needed, and update the catalog DB.
#[tauri::command]
pub async fn rename_track_file(
    catalog: State<'_, Arc<Catalog>>,
    old_path: String,
    new_path: String,
) -> Result<(), String> {
    let old = std::path::Path::new(&old_path);
    let new = std::path::Path::new(&new_path);
    if let Some(parent) = new.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::fs::rename(old, new).map_err(|e| e.to_string())?;
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::update_track_path(&conn, &old_path, &new_path)
}

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

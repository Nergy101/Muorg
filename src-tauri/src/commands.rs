use crate::catalog::{Catalog, CatalogTrack};
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
        p.parent()
            .ok_or("Invalid file path")?
            .to_path_buf()
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
pub async fn rescan(
    catalog: State<'_, Arc<Catalog>>,
    root_path: String,
) -> Result<u64, String> {
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

#[tauri::command]
pub async fn get_track_cover(path: String) -> Result<Option<String>, String> {
    let meta = read_metadata(Path::new(&path))?;
    Ok(meta.picture_base64)
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
    write_metadata(std::path::Path::new(&path), &update)?;
    let conn = catalog.db.lock().map_err(|e| e.to_string())?;
    crate::catalog::update_track_metadata(&conn, &path, &update)?;
    Ok(())
}
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};

pub fn backup_file_name(path: &str) -> Result<String, String> {
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

pub fn create_backup(backup_dir: &Path, path: &str) -> Result<String, String> {
    let src = Path::new(path);
    if !src.exists() {
        return Err("Track file does not exist".to_string());
    }
    std::fs::create_dir_all(backup_dir).map_err(|e| e.to_string())?;
    let backup_path = backup_dir.join(backup_file_name(path)?);
    std::fs::copy(src, &backup_path).map_err(|e| format!("Backup failed: {e}"))?;
    backup_path
        .to_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Invalid backup path".to_string())
}

#[allow(dead_code)]
pub fn latest_backup_path(backup_dir: &Path, path: &str) -> Result<Option<PathBuf>, String> {
    if !backup_dir.exists() {
        return Ok(None);
    }
    let mut entries = std::fs::read_dir(backup_dir)
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
            return Ok(Some(entry.path()));
        }
    }
    Ok(None)
}

use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};

/// Extract the 12-char source-file hash from a backup filename like `12345-a1b2c3d4e5f6.mp3`.
fn hash_from_filename(name: &str) -> Option<String> {
    // Format: {timestamp}-{hash[..12]}.{ext}
    let dash = name.find('-')?;
    let rest = name.get(dash + 1..)?;
    let dot = rest.rfind('.')?;
    let hash_part = rest.get(..dot)?;
    if hash_part.len() == 12 && hash_part.chars().all(|c| c.is_ascii_hexdigit()) {
        Some(hash_part.to_string())
    } else {
        None
    }
}

/// Remove old backups beyond the retention limit.
/// Groups backups by their source-file hash (embedded in the filename)
/// and keeps only the `retain` most recent entries per group.
pub fn gc_old_backups(backup_dir: &Path, retain: usize) -> Result<usize, String> {
    if retain == 0 || !backup_dir.exists() {
        return Ok(0);
    }
    let dir_entries = std::fs::read_dir(backup_dir)
        .map_err(|e| e.to_string())?
        .filter_map(|e| e.ok())
        .filter(|e| e.file_type().map(|t| t.is_file()).unwrap_or(false))
        .collect::<Vec<_>>();

    // Group by source-file hash
    let mut by_hash: std::collections::HashMap<String, Vec<(std::time::SystemTime, PathBuf)>> =
        std::collections::HashMap::new();
    for entry in &dir_entries {
        let name = entry.file_name().to_string_lossy().to_string();
        if let Some(hash) = hash_from_filename(&name) {
            if let Ok(meta) = entry.metadata() {
                if let Ok(created) = meta.created() {
                    by_hash.entry(hash).or_default().push((created, entry.path()));
                }
            }
        }
    }

    let mut removed = 0;
    for (_hash, mut entries) in by_hash {
        if entries.len() <= retain {
            continue;
        }
        // Sort newest-first by creation time
        entries.sort_by(|a, b| b.0.cmp(&a.0));
        // Remove all beyond the retention limit
        for (_, path) in entries.iter().skip(retain) {
            if std::fs::remove_file(path).is_ok() {
                removed += 1;
            }
        }
    }
    Ok(removed)
}

pub fn backup_file_name(path: &str) -> Result<String, String> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| "time error".to_string())?
        .as_secs();
    let mut hasher = Sha256::new();
    hasher.update(path.as_bytes());
    let hash = hasher.finalize().iter().map(|b| format!("{:02x}", b)).collect::<String>();
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
    let hash = hasher.finalize().iter().map(|b| format!("{:02x}", b)).collect::<String>();
    let needle = &hash[..12];
    for entry in entries.into_iter().rev() {
        let name = entry.file_name().to_string_lossy().to_string();
        if name.contains(needle) {
            return Ok(Some(entry.path()));
        }
    }
    Ok(None)
}

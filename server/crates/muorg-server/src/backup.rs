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
                // `created()` is `Unsupported` on some Linux filesystems, which
                // is where this actually runs (the Docker image). Taking only
                // `created()` meant the whole entry was skipped there, so
                // nothing was ever collected and backups grew forever.
                if let Ok(stamp) = meta.created().or_else(|_| meta.modified()) {
                    by_hash.entry(hash).or_default().push((stamp, entry.path()));
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
        entries.sort_by_key(|b| std::cmp::Reverse(b.0));
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

/// Copies `src` into `backup_dir`. `name_key` is the track's identity — its
/// path or `remote://` URI — and determines the backup file name, so backups
/// stay grouped per track even when the bytes come from a temp file.
pub fn create_backup(backup_dir: &Path, src: &Path, name_key: &str) -> Result<String, String> {
    if !src.exists() {
        return Err("Track file does not exist".to_string());
    }
    std::fs::create_dir_all(backup_dir).map_err(|e| e.to_string())?;
    let backup_path = backup_dir.join(backup_file_name(name_key)?);
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

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    /// Write a backup file whose name carries `hash` and whose mtime is
    /// `age_secs` in the past, so retention ordering is deterministic.
    fn write_backup(dir: &Path, stamp: u64, hash: &str, ext: &str) -> PathBuf {
        let path = dir.join(format!("{stamp}-{hash}.{ext}"));
        fs::write(&path, b"backup bytes").unwrap();
        path
    }

    // -- file naming --------------------------------------------------------

    #[test]
    fn a_backup_name_carries_the_source_hash_and_extension() {
        let name = backup_file_name("/music/song.mp3").unwrap();
        let (stamp, rest) = name.split_once('-').expect("timestamp-hash.ext");
        assert!(stamp.parse::<u64>().is_ok(), "leading field is a unix timestamp");
        assert!(name.ends_with(".mp3"), "the source extension is kept: {name}");
        assert_eq!(rest.len(), 12 + 4, "12 hex chars plus `.mp3`");
    }

    #[test]
    fn the_same_track_always_hashes_to_the_same_name_fragment() {
        // This is what groups a track's backups together, so it has to be
        // stable across runs and distinct per path.
        let a = hash_from_filename(&backup_file_name("/music/song.mp3").unwrap()).unwrap();
        let b = hash_from_filename(&backup_file_name("/music/song.mp3").unwrap()).unwrap();
        let c = hash_from_filename(&backup_file_name("/music/other.mp3").unwrap()).unwrap();
        assert_eq!(a, b);
        assert_ne!(a, c);
    }

    #[test]
    fn a_remote_uri_is_named_like_any_other_track() {
        let name = backup_file_name("remote://nas/Albums/song.flac").unwrap();
        assert!(name.ends_with(".flac"), "{name}");
        assert!(hash_from_filename(&name).is_some());
    }

    #[test]
    fn an_extensionless_path_falls_back_to_bin() {
        assert!(backup_file_name("/music/song").unwrap().ends_with(".bin"));
    }

    #[test]
    fn hash_extraction_rejects_names_that_are_not_backups() {
        assert!(hash_from_filename("song.mp3").is_none(), "no dash");
        assert!(hash_from_filename("1700000000-nothex123456.mp3").is_none());
        assert!(hash_from_filename("1700000000-abc.mp3").is_none(), "hash too short");
        assert!(hash_from_filename("1700000000-abcdef0123456789.mp3").is_none(), "too long");
        assert!(hash_from_filename("1700000000-abcdef012345").is_none(), "no extension");
    }

    // -- creating and finding ----------------------------------------------

    #[test]
    fn creating_a_backup_copies_the_bytes_and_reports_the_path() {
        let dir = tempfile::tempdir().unwrap();
        let src = dir.path().join("song.mp3");
        fs::write(&src, b"audio").unwrap();
        let backups = dir.path().join("backups");

        let written = create_backup(&backups, &src, "/music/song.mp3").unwrap();
        assert_eq!(fs::read(&written).unwrap(), b"audio");
        assert!(Path::new(&written).starts_with(&backups));
    }

    #[test]
    fn creating_a_backup_of_a_missing_file_reports_rather_than_panics() {
        let dir = tempfile::tempdir().unwrap();
        let err = create_backup(
            &dir.path().join("backups"),
            &dir.path().join("gone.mp3"),
            "/music/gone.mp3",
        )
        .unwrap_err();
        assert!(err.contains("does not exist"), "{err}");
    }

    #[test]
    fn the_backup_directory_is_created_on_demand() {
        let dir = tempfile::tempdir().unwrap();
        let src = dir.path().join("song.mp3");
        fs::write(&src, b"audio").unwrap();
        let nested = dir.path().join("a/b/c");

        create_backup(&nested, &src, "/music/song.mp3").unwrap();
        assert!(nested.is_dir());
    }

    #[test]
    fn the_name_key_decides_the_grouping_not_the_source_file() {
        // A remote track is backed up from a temp download, so the bytes come
        // from a path that has nothing to do with the track's identity.
        let dir = tempfile::tempdir().unwrap();
        let temp_download = dir.path().join("tmp-abc123.flac");
        fs::write(&temp_download, b"audio").unwrap();
        let backups = dir.path().join("backups");

        let written = create_backup(&backups, &temp_download, "remote://nas/song.flac").unwrap();
        let expected = hash_from_filename(&backup_file_name("remote://nas/song.flac").unwrap());
        let actual = hash_from_filename(Path::new(&written).file_name().unwrap().to_str().unwrap());
        assert_eq!(actual, expected, "the backup is filed under the track, not the temp file");
    }

    #[test]
    fn the_latest_backup_is_found_by_track_path() {
        let dir = tempfile::tempdir().unwrap();
        let backups = dir.path();
        let hash = hash_from_filename(&backup_file_name("/music/song.mp3").unwrap()).unwrap();
        write_backup(backups, 1_700_000_000, &hash, "mp3");
        let newest = write_backup(backups, 1_700_000_900, &hash, "mp3");

        let found = latest_backup_path(backups, "/music/song.mp3").unwrap().unwrap();
        assert_eq!(found, newest, "the highest timestamp wins");
    }

    #[test]
    fn no_backup_for_an_untouched_track() {
        let dir = tempfile::tempdir().unwrap();
        let hash = hash_from_filename(&backup_file_name("/music/song.mp3").unwrap()).unwrap();
        write_backup(dir.path(), 1_700_000_000, &hash, "mp3");
        assert!(latest_backup_path(dir.path(), "/music/other.mp3").unwrap().is_none());
    }

    #[test]
    fn a_missing_backup_directory_is_empty_not_an_error() {
        let dir = tempfile::tempdir().unwrap();
        let missing = dir.path().join("nope");
        assert!(latest_backup_path(&missing, "/music/song.mp3").unwrap().is_none());
        assert_eq!(gc_old_backups(&missing, 5).unwrap(), 0);
    }

    // -- retention ----------------------------------------------------------

    #[test]
    fn gc_keeps_the_newest_n_per_track_and_deletes_the_rest() {
        let dir = tempfile::tempdir().unwrap();
        let hash = hash_from_filename(&backup_file_name("/music/song.mp3").unwrap()).unwrap();
        // Five backups of one track, written oldest first so their mtimes
        // order the same way as their names.
        let mut written = Vec::new();
        for i in 0..5u64 {
            written.push(write_backup(dir.path(), 1_700_000_000 + i, &hash, "mp3"));
            std::thread::sleep(std::time::Duration::from_millis(15));
        }

        let removed = gc_old_backups(dir.path(), 2).unwrap();
        assert_eq!(removed, 3);
        assert!(!written[0].exists() && !written[1].exists() && !written[2].exists());
        assert!(written[3].exists() && written[4].exists(), "the two newest survive");
    }

    #[test]
    fn gc_counts_retention_per_track_not_across_the_directory() {
        // The bug this guards: grouping by nothing, so ten backups of one
        // track evict the single backup of another.
        let dir = tempfile::tempdir().unwrap();
        let one = hash_from_filename(&backup_file_name("/music/a.mp3").unwrap()).unwrap();
        let two = hash_from_filename(&backup_file_name("/music/b.mp3").unwrap()).unwrap();
        for i in 0..4u64 {
            write_backup(dir.path(), 1_700_000_000 + i, &one, "mp3");
            std::thread::sleep(std::time::Duration::from_millis(15));
        }
        let only_b = write_backup(dir.path(), 1_700_000_000, &two, "mp3");

        gc_old_backups(dir.path(), 1).unwrap();
        assert!(only_b.exists(), "the other track's one backup must survive");
        let left = fs::read_dir(dir.path()).unwrap().count();
        assert_eq!(left, 2, "one per track");
    }

    #[test]
    fn gc_with_a_zero_retention_does_nothing() {
        // Zero means "not configured", not "delete everything" — an
        // accidentally-unset value should not wipe the user's safety net.
        let dir = tempfile::tempdir().unwrap();
        let hash = hash_from_filename(&backup_file_name("/music/song.mp3").unwrap()).unwrap();
        write_backup(dir.path(), 1_700_000_000, &hash, "mp3");
        write_backup(dir.path(), 1_700_000_001, &hash, "mp3");

        assert_eq!(gc_old_backups(dir.path(), 0).unwrap(), 0);
        assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 2);
    }

    #[test]
    fn gc_leaves_files_it_does_not_recognise_alone() {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join("README.txt"), b"notes").unwrap();
        fs::write(dir.path().join("song.mp3"), b"audio").unwrap();
        assert_eq!(gc_old_backups(dir.path(), 1).unwrap(), 0);
        assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 2);
    }

    #[test]
    fn gc_does_not_recurse_into_subdirectories() {
        let dir = tempfile::tempdir().unwrap();
        let nested = dir.path().join("archive");
        fs::create_dir(&nested).unwrap();
        let hash = hash_from_filename(&backup_file_name("/music/song.mp3").unwrap()).unwrap();
        for i in 0..3u64 {
            write_backup(&nested, 1_700_000_000 + i, &hash, "mp3");
        }
        assert_eq!(gc_old_backups(dir.path(), 1).unwrap(), 0);
        assert_eq!(fs::read_dir(&nested).unwrap().count(), 3);
    }
}

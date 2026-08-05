//! On-disk cache for cover art extracted from remote tracks.
//!
//! Clients request a cover per visible row, and extracting one from a bucket
//! costs a ranged GET plus a tag parse. Cached files are keyed by track id and
//! validated against `tracks.mtime_secs`, so any write path that changes an
//! object must bump that column (it does) for the cache to stay correct.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};

/// Extensions the cache can hold, ordered as they are probed on lookup.
const EXTS: [&str; 3] = ["jpg", "png", "webp"];

/// Successful writes between garbage collection passes.
const GC_EVERY: u64 = 64;

pub struct CoverCache {
    dir: PathBuf,
    max_bytes: u64,
    writes: AtomicU64,
}

fn ext_for_mime(mime: &str) -> &'static str {
    match mime {
        "image/png" => "png",
        "image/webp" => "webp",
        _ => "jpg",
    }
}

fn mime_for_ext(ext: &str) -> &'static str {
    match ext {
        "png" => "image/png",
        "webp" => "image/webp",
        _ => "image/jpeg",
    }
}

fn mtime_secs(meta: &std::fs::Metadata) -> i64 {
    meta.modified()
        .ok()
        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

impl CoverCache {
    pub fn new(dir: PathBuf, max_bytes: u64) -> Self {
        Self {
            dir,
            max_bytes,
            writes: AtomicU64::new(0),
        }
    }

    /// The cached cover for `id`, or `None` when absent or older than the
    /// track it belongs to. A stale entry is deleted on the way out.
    pub fn get(&self, id: i64, track_mtime_secs: i64) -> Option<(Vec<u8>, String)> {
        for ext in EXTS {
            let path = self.dir.join(format!("{id}.{ext}"));
            let Ok(meta) = std::fs::metadata(&path) else {
                continue;
            };
            if mtime_secs(&meta) < track_mtime_secs {
                let _ = std::fs::remove_file(&path);
                continue;
            }
            match std::fs::read(&path) {
                Ok(data) => return Some((data, mime_for_ext(ext).to_string())),
                Err(_) => continue,
            }
        }
        None
    }

    pub fn put(&self, id: i64, mime: &str, data: &[u8]) {
        let ext = ext_for_mime(mime);
        // Drop any entry under a different extension so `get` cannot serve a
        // stale image of the other type.
        for other in EXTS.iter().filter(|e| **e != ext) {
            let _ = std::fs::remove_file(self.dir.join(format!("{id}.{other}")));
        }
        let final_path = self.dir.join(format!("{id}.{ext}"));
        let tmp_path = self.dir.join(format!("{id}.{ext}.tmp"));
        if std::fs::write(&tmp_path, data).is_err() {
            let _ = std::fs::remove_file(&tmp_path);
            return;
        }
        // Rename so a concurrent reader never observes a half-written file.
        if std::fs::rename(&tmp_path, &final_path).is_err() {
            let _ = std::fs::remove_file(&tmp_path);
            return;
        }
        if self.writes.fetch_add(1, Ordering::Relaxed) % GC_EVERY == GC_EVERY - 1 {
            self.gc();
        }
    }

    pub fn invalidate(&self, id: i64) {
        for ext in EXTS {
            let _ = std::fs::remove_file(self.dir.join(format!("{id}.{ext}")));
        }
    }

    /// Evicts oldest-first down to 90 % of `max_bytes` once the cache exceeds it.
    fn gc(&self) {
        let Ok(entries) = std::fs::read_dir(&self.dir) else {
            return;
        };
        let mut files: Vec<(PathBuf, i64, u64)> = Vec::new();
        let mut total = 0u64;
        for entry in entries.flatten() {
            let Ok(meta) = entry.metadata() else { continue };
            if !meta.is_file() {
                continue;
            }
            total += meta.len();
            files.push((entry.path(), mtime_secs(&meta), meta.len()));
        }
        if total <= self.max_bytes {
            return;
        }
        let target = self.max_bytes / 10 * 9;
        files.sort_by_key(|(_, mtime, _)| *mtime);
        for (path, _, len) in files {
            if total <= target {
                break;
            }
            if std::fs::remove_file(&path).is_ok() {
                total = total.saturating_sub(len);
            }
        }
    }
}

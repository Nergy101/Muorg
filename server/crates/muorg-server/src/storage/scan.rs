//! Indexes an object-storage bucket into the catalog.
//!
//! The listing is the cheap part: one paginated LIST gives every key plus its
//! `last_modified`, which is diffed against `tracks.mtime_secs`. Only changed or
//! new objects cost ranged GETs for their tags, so a recurring scan of an
//! unchanged bucket issues no per-object requests at all.

use super::reader::RemoteReader;
use super::RemoteStore;
use muorg_core::catalog::{RootUpsert, ScannedTrack};
use muorg_core::metadata::{AudioFormat, TrackMetadata};
use object_store::path::Path as ObjPath;
use object_store::{ObjectMeta, ObjectStore};
use std::collections::HashSet;
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;
use tokio_stream::StreamExt;

/// Rows per DB transaction. Pictures are stripped before batching, so a batch
/// holds a few hundred KB rather than hundreds of MB.
const BATCH_SIZE: usize = 200;

/// Upserts logged between progress lines.
const PROGRESS_EVERY: u64 = 500;

struct Scanned {
    uri: String,
    format: &'static str,
    mtime_secs: i64,
    content_hash: Option<String>,
    has_cover: bool,
    meta: TrackMetadata,
}

/// Scans `remote` into the catalog and returns `(upserted, removed)`.
pub async fn scan_remote_root(
    state: &Arc<crate::state::AppState>,
    remote: &Arc<RemoteStore>,
    concurrency: usize,
) -> Result<(u64, u64), String> {
    let root_uri = remote.root_uri();

    // A `std::sync::MutexGuard` is `!Send`, so every DB section below is a
    // scoped block that ends before the next `.await`.
    {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::save_roots(&conn, std::slice::from_ref(&root_uri))?;
    }

    let prefix = (!remote.prefix.is_empty()).then(|| ObjPath::from(remote.prefix.as_str()));
    let mut listing = remote.store.list(prefix.as_ref());
    let mut candidates: Vec<(String, ObjectMeta, AudioFormat)> = Vec::new();
    let mut seen_uris: HashSet<String> = HashSet::new();
    while let Some(item) = listing.next().await {
        let meta = item.map_err(|e| e.to_string())?;
        let key = meta.location.as_ref().to_string();
        let format = match std::path::Path::new(&key)
            .extension()
            .and_then(|e| e.to_str())
            .and_then(muorg_core::metadata::format_from_ext)
        {
            Some(f) => f,
            None => continue,
        };
        let uri = remote.uri_for(&key);
        seen_uris.insert(uri.clone());
        candidates.push((uri, meta, format));
    }

    // Diff against the catalog in one locked pass. Unchanged objects are dropped
    // here and never touched over the network.
    let changed: Vec<(String, ObjectMeta, AudioFormat)> = {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        let mut changed = Vec::new();
        for (uri, meta, format) in candidates {
            let remote_mtime = meta.last_modified.timestamp();
            match muorg_core::catalog::get_track_mtime_by_path(&conn, &uri)? {
                Some(db_mtime) if db_mtime == remote_mtime => continue,
                _ => changed.push((uri, meta, format)),
            }
        }
        changed
    };

    let total = changed.len() as u64;
    tracing::info!(root = %root_uri, total, "remote scan: objects needing a tag read");

    let sem = Arc::new(tokio::sync::Semaphore::new(concurrency.max(1)));
    let (tx, mut rx) = tokio::sync::mpsc::channel::<Scanned>(256);

    for (uri, meta, format) in changed {
        let sem = sem.clone();
        let tx = tx.clone();
        let store = remote.store.clone();
        tokio::spawn(async move {
            let Ok(_permit) = sem.acquire().await else { return };
            let handle = tokio::runtime::Handle::current();
            let key = meta.location.clone();
            let size = meta.size;
            let read = tokio::task::spawn_blocking(move || {
                read_tags_and_hash(store, key, size, format, handle)
            })
            .await;
            match read {
                Ok(Ok((meta_read, hash, has_cover))) => {
                    let _ = tx
                        .send(Scanned {
                            uri,
                            format: format.as_str(),
                            mtime_secs: meta.last_modified.timestamp(),
                            content_hash: hash,
                            has_cover,
                            meta: meta_read,
                        })
                        .await;
                }
                Ok(Err(e)) => tracing::warn!(key = %uri, "skipping unreadable object: {e}"),
                Err(e) => tracing::warn!(key = %uri, "tag read task failed: {e}"),
            }
        });
    }
    drop(tx);

    let mut upserted = 0u64;
    let mut batch: Vec<Scanned> = Vec::with_capacity(BATCH_SIZE);
    let mut last_logged = 0u64;
    loop {
        let item = rx.recv().await;
        let closed = item.is_none();
        if let Some(item) = item {
            batch.push(item);
        }
        if batch.len() >= BATCH_SIZE || (closed && !batch.is_empty()) {
            upserted += flush_batch(state, &root_uri, &batch)?;
            batch.clear();
            if upserted - last_logged >= PROGRESS_EVERY {
                last_logged = upserted;
                tracing::info!(root = %root_uri, done = upserted, total, "remote scan progress");
            }
        }
        if closed {
            break;
        }
    }

    let removed = {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::sweep_missing_tracks(&conn, &root_uri, &seen_uris)?
    };

    Ok((upserted, removed))
}

fn flush_batch(
    state: &Arc<crate::state::AppState>,
    root_uri: &str,
    batch: &[Scanned],
) -> Result<u64, String> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let up = RootUpsert::new(&conn, root_uri)?;
    let mut n = 0u64;
    for item in batch {
        up.upsert(&ScannedTrack {
            path: &item.uri,
            format: item.format,
            mtime_secs: item.mtime_secs,
            content_hash: item.content_hash.as_deref(),
            has_cover: item.has_cover,
            meta: &item.meta,
        })?;
        n += 1;
    }
    Ok(n)
}

/// Reads tags and the content hash off one object with a single reader, so both
/// come out of the same ranged GETs. Runs on a blocking thread.
fn read_tags_and_hash(
    store: Arc<dyn ObjectStore>,
    key: ObjPath,
    size: u64,
    format: AudioFormat,
    handle: tokio::runtime::Handle,
) -> Result<(TrackMetadata, Option<String>, bool), String> {
    let mut r = RemoteReader::new(store, key, size, handle);
    let mut meta = muorg_core::metadata::read_metadata_from_reader(&mut r, format)?;
    let has_cover = meta.picture_base64.as_ref().is_some_and(|s| !s.is_empty());
    // Only `has_cover` is persisted; drop the blob before this crosses a channel.
    meta.picture_base64 = None;
    meta.picture_mime = None;

    let hash = if size == 0 {
        Some(muorg_core::catalog::content_hash_from_parts(0, &[]))
    } else {
        let start = size.saturating_sub(muorg_core::catalog::CONTENT_HASH_TAIL_BYTES);
        r.seek(SeekFrom::Start(start))
            .ok()
            .and_then(|_| {
                let mut buf = Vec::new();
                r.read_to_end(&mut buf).ok().map(|_| buf)
            })
            .map(|buf| muorg_core::catalog::content_hash_from_parts(size, &buf))
    };

    Ok((meta, hash, has_cover))
}

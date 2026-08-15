use crate::cast::{CastState, DiscoveryState};
use crate::config::TranscodingConfig;
use crate::musicbrainz::AutoTagService;
use crate::ratelimit::RateLimiter;
use muorg_core::catalog::Catalog;
use std::collections::{HashMap, VecDeque};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use uuid::Uuid;

struct StreamToken {
    track_id: i64,
    expires_at: Instant,
}

pub struct StreamTokens {
    inner: Mutex<HashMap<String, StreamToken>>,
}

impl Default for StreamTokens {
    fn default() -> Self {
        Self::new()
    }
}

impl StreamTokens {
    pub fn new() -> Self {
        Self { inner: Mutex::new(HashMap::new()) }
    }

    pub fn issue(&self, track_id: i64, ttl_secs: u64) -> String {
        let token = Uuid::new_v4().to_string();
        let mut map = self.inner.lock().unwrap();
        map.retain(|_, v| v.expires_at > Instant::now());
        map.insert(token.clone(), StreamToken {
            track_id,
            expires_at: Instant::now() + Duration::from_secs(ttl_secs),
        });
        token
    }

    pub fn validate(&self, token: &str, track_id: i64) -> bool {
        let map = self.inner.lock().unwrap();
        match map.get(token) {
            Some(entry) => entry.track_id == track_id && entry.expires_at > Instant::now(),
            None => false,
        }
    }
}

/// Cache key for a transcoded track: identity plus the source's mtime, so a
/// re-scanned/re-uploaded file with the same id does not serve stale bytes.
#[derive(Clone, Copy, PartialEq, Eq, Hash)]
struct TranscodeKey {
    track_id: i64,
    mtime_secs: u64,
}

/// In-memory LRU of full FLAC→MP3 transcodes, keyed by `(track_id, mtime)`.
///
/// Serving a cached byte buffer with `Content-Length` + ranges (rather than a
/// live chunked stream) is what makes a FLAC track behave like a seekable file
/// in the browser, fixing the "audio skips back ~30s" desync.
pub struct TranscodeCache {
    inner: Mutex<HashMap<TranscodeKey, Arc<Vec<u8>>>>,
    order: Mutex<VecDeque<TranscodeKey>>,
    max_entries: usize,
}

impl TranscodeCache {
    pub fn new(max_entries: usize) -> Self {
        Self {
            inner: Mutex::new(HashMap::new()),
            order: Mutex::new(VecDeque::new()),
            max_entries: max_entries.max(1),
        }
    }

    pub fn get(&self, track_id: i64, mtime_secs: u64) -> Option<Arc<Vec<u8>>> {
        let key = TranscodeKey { track_id, mtime_secs };
        let map = self.inner.lock().unwrap();
        let hit = map.get(&key).cloned();
        if hit.is_some() {
            // Mark as most-recently-used.
            let mut order = self.order.lock().unwrap();
            order.retain(|k| *k != key);
            order.push_back(key);
        }
        hit
    }

    pub fn insert(&self, track_id: i64, mtime_secs: u64, bytes: Arc<Vec<u8>>) {
        let key = TranscodeKey { track_id, mtime_secs };
        let mut map = self.inner.lock().unwrap();
        let mut order = self.order.lock().unwrap();
        order.retain(|k| *k != key);
        order.push_back(key);
        map.insert(key, bytes);
        while order.len() > self.max_entries {
            if let Some(old) = order.pop_front() {
                map.remove(&old);
            }
        }
    }
}

pub struct AppState {
    pub catalog: Arc<Catalog>,
    pub backup_dir: PathBuf,
    pub backup_retention_count: usize,
    pub auto_tag: AutoTagService,
    pub api_key: String,
    pub tokens: StreamTokens,
    pub transcoding_config: TranscodingConfig,
    pub transcode_cache: TranscodeCache,
    pub server_port: u16,
    pub cast_discovery: DiscoveryState,
    pub cast_session: CastState,
    pub rate_limiter: RateLimiter,
    pub remotes: Arc<crate::storage::RemoteStores>,
    pub cover_cache: Arc<crate::storage::covers::CoverCache>,
    /// From `library.remote_scan_concurrency`, clamped to `>= 1`. Lets the
    /// rescan route drive a remote scan without carrying the whole `Config`.
    pub remote_scan_concurrency: usize,
}

impl AppState {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        catalog: Arc<Catalog>,
        backup_dir: PathBuf,
        backup_retention_count: usize,
        api_key: String,
        server_port: u16,
        transcoding_config: TranscodingConfig,
        remotes: Arc<crate::storage::RemoteStores>,
        cover_cache: Arc<crate::storage::covers::CoverCache>,
        remote_scan_concurrency: usize,
    ) -> Self {
        let cast_discovery = DiscoveryState::new();
        cast_discovery.start();
        Self {
            catalog,
            backup_dir,
            backup_retention_count,
            auto_tag: AutoTagService::new(),
            api_key,
            tokens: StreamTokens::new(),
            transcoding_config,
            transcode_cache: TranscodeCache::new(24),
            server_port,
            cast_discovery,
            cast_session: CastState::new(),
            rate_limiter: RateLimiter::new(100, 60),
            remotes,
            cover_cache,
            remote_scan_concurrency: remote_scan_concurrency.max(1),
        }
    }
}

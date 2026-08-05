use crate::cast::{CastState, DiscoveryState};
use crate::config::TranscodingConfig;
use crate::musicbrainz::AutoTagService;
use crate::ratelimit::RateLimiter;
use muorg_core::catalog::Catalog;
use std::collections::HashMap;
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

pub struct AppState {
    pub catalog: Arc<Catalog>,
    pub backup_dir: PathBuf,
    pub backup_retention_count: usize,
    pub auto_tag: AutoTagService,
    pub api_key: String,
    pub tokens: StreamTokens,
    pub server_port: u16,
    pub cast_discovery: DiscoveryState,
    pub cast_session: CastState,
    pub transcoding_config: TranscodingConfig,
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
            server_port,
            cast_discovery,
            cast_session: CastState::new(),
            transcoding_config,
            rate_limiter: RateLimiter::new(100, 60),
            remotes,
            cover_cache,
            remote_scan_concurrency: remote_scan_concurrency.max(1),
        }
    }
}

use crate::cast::{CastState, DiscoveryState};
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
    pub api_key: String,
    pub tokens: StreamTokens,
    pub server_port: u16,
    pub cast_discovery: DiscoveryState,
    pub cast_session: CastState,
}

impl AppState {
    pub fn new(
        catalog: Arc<Catalog>,
        backup_dir: PathBuf,
        api_key: String,
        server_port: u16,
    ) -> Self {
        Self {
            catalog,
            backup_dir,
            api_key,
            tokens: StreamTokens::new(),
            server_port,
            cast_discovery: DiscoveryState::new(),
            cast_session: CastState::new(),
        }
    }
}

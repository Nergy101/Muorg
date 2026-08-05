use serde::Deserialize;
use std::path::PathBuf;

#[derive(Debug, Deserialize, Default)]
pub struct Config {
    #[serde(default)]
    pub server: ServerConfig,
    #[serde(default)]
    pub library: LibraryConfig,
    #[serde(default)]
    pub storage: StorageConfig,
    #[serde(default)]
    pub cors: CorsConfig,
    #[serde(default)]
    pub transcoding: TranscodingConfig,
}

#[derive(Debug, Deserialize)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
    pub api_key: String,
    #[serde(default = "default_shutdown_timeout")]
    pub shutdown_timeout_secs: u64,
}

fn default_shutdown_timeout() -> u64 { 30 }

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            host: "127.0.0.1".to_string(),
            port: 7700,
            api_key: "change-me".to_string(),
            shutdown_timeout_secs: 30,
        }
    }
}

#[derive(Debug, Deserialize, Default)]
pub struct LibraryConfig {
    #[serde(default)]
    pub content_paths: Vec<String>,
    #[serde(default = "default_true")]
    pub scan_on_startup: bool,
    /// S3-compatible buckets indexed alongside `content_paths`.
    #[serde(default)]
    pub remotes: Vec<RemoteConfig>,
    /// Objects to read tags from in parallel during a remote scan.
    #[serde(default = "default_remote_scan_concurrency")]
    pub remote_scan_concurrency: usize,
}

fn default_true() -> bool { true }
fn default_remote_scan_concurrency() -> usize { 8 }

/// One S3-compatible bucket. Tracks from it are stored as
/// `remote://<name>/<object-key>`.
#[derive(Debug, Deserialize, Clone)]
pub struct RemoteConfig {
    /// Letters, digits, `-` and `_` only — it becomes part of the track URI.
    pub name: String,
    pub bucket: String,
    /// Provider endpoint, e.g. `https://nbg1.your-objectstorage.com`. Omit for AWS S3.
    #[serde(default)]
    pub endpoint: Option<String>,
    #[serde(default = "default_region")]
    pub region: String,
    #[serde(default)]
    pub access_key_id: Option<String>,
    #[serde(default)]
    pub secret_access_key: Option<String>,
    /// Optional sub-folder inside the bucket.
    #[serde(default)]
    pub prefix: Option<String>,
    /// `true` for providers that require `<bucket>.<host>` addressing (Hetzner).
    #[serde(default)]
    pub virtual_hosted_style: bool,
    /// Send `UNSIGNED-PAYLOAD` instead of signing request bodies. Some Ceph-based
    /// gateways need this for uploads.
    #[serde(default)]
    pub unsigned_payload: bool,
}

fn default_region() -> String { "auto".to_string() }

#[derive(Debug, Deserialize)]
pub struct StorageConfig {
    pub db_path: PathBuf,
    pub backup_dir: PathBuf,
    #[serde(default = "default_backup_retention")]
    pub backup_retention_count: usize,
    /// Where extracted cover art for remote tracks is cached. Defaults to
    /// `covers/` next to the database.
    #[serde(default)]
    pub cover_cache_dir: Option<PathBuf>,
    #[serde(default = "default_cover_cache_max_bytes")]
    pub cover_cache_max_bytes: u64,
}

fn default_backup_retention() -> usize { 5 }
fn default_cover_cache_max_bytes() -> u64 { 536_870_912 }

impl Default for StorageConfig {
    fn default() -> Self {
        Self {
            db_path: PathBuf::from("./muorg.db"),
            backup_dir: PathBuf::from("./muorg-backups"),
            backup_retention_count: 5,
            cover_cache_dir: None,
            cover_cache_max_bytes: 536_870_912,
        }
    }
}

#[derive(Debug, Deserialize)]
pub struct CorsConfig {
    pub allowed_origins: Vec<String>,
}

impl Default for CorsConfig {
    fn default() -> Self {
        Self {
            allowed_origins: vec![
                "tauri://localhost".to_string(),
                "http://tauri.localhost".to_string(),
            ],
        }
    }
}

#[derive(Debug, Deserialize, Clone)]
pub struct TranscodingConfig {
    /// Audio bitrate in kbps (default: 128)
    #[serde(default = "default_transcode_bitrate")]
    pub bitrate: u32,
    /// Output format: "mp3" only for now (default: "mp3")
    #[serde(default = "default_transcode_format")]
    pub format: String,
    /// Output sample rate in Hz (default: 44100)
    #[serde(default = "default_transcode_sample_rate")]
    pub sample_rate: u32,
}

fn default_transcode_bitrate() -> u32 { 128 }
fn default_transcode_format() -> String { "mp3".to_string() }
fn default_transcode_sample_rate() -> u32 { 44100 }

impl Default for TranscodingConfig {
    fn default() -> Self {
        Self {
            bitrate: 128,
            format: "mp3".to_string(),
            sample_rate: 44100,
        }
    }
}

impl Config {
    pub fn load(path: &std::path::Path) -> Result<Self, String> {
        let content = std::fs::read_to_string(path)
            .map_err(|e| format!("Failed to read config {}: {}", path.display(), e))?;
        toml::from_str(&content)
            .map_err(|e| format!("Failed to parse config: {}", e))
    }
}

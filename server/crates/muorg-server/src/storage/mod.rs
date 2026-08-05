//! S3-compatible object storage as a music source.
//!
//! Remote tracks live in the same `tracks.path` column as local ones, using the
//! URI form `remote://<remote-name>/<object-key>`, so every catalog query,
//! playlist and move-detection rule works unchanged. `<remote-name>` is
//! restricted to `[A-Za-z0-9_-]+` at config load, which makes the URI split on
//! the first `/` after the scheme unambiguous.

pub mod covers;
pub mod reader;

use object_store::aws::AmazonS3Builder;
use object_store::path::Path as ObjPath;
use object_store::{ObjectMeta, ObjectStore, ObjectStoreExt};
use std::collections::HashMap;
use std::sync::Arc;

pub const REMOTE_SCHEME: &str = "remote://";

pub fn is_remote_uri(p: &str) -> bool {
    p.starts_with(REMOTE_SCHEME)
}

pub struct RemoteStore {
    pub name: String,
    pub store: Arc<dyn ObjectStore>,
    /// Normalised: no leading or trailing `/`, empty when unset.
    pub prefix: String,
}

impl RemoteStore {
    /// The `roots.path` value for this remote.
    pub fn root_uri(&self) -> String {
        if self.prefix.is_empty() {
            format!("{REMOTE_SCHEME}{}", self.name)
        } else {
            format!("{REMOTE_SCHEME}{}/{}", self.name, self.prefix)
        }
    }

    /// Full object key -> `remote://<name>/<key>`.
    pub fn uri_for(&self, key: &str) -> String {
        format!("{REMOTE_SCHEME}{}/{}", self.name, key)
    }
}

#[derive(Default)]
pub struct RemoteStores {
    by_name: HashMap<String, Arc<RemoteStore>>,
}

impl RemoteStores {
    pub fn from_config(cfgs: &[crate::config::RemoteConfig]) -> Result<Self, String> {
        let mut by_name: HashMap<String, Arc<RemoteStore>> = HashMap::new();
        for c in cfgs {
            let name = c.name.as_str();
            let valid = !name.is_empty()
                && name
                    .chars()
                    .all(|ch| ch.is_ascii_alphanumeric() || ch == '-' || ch == '_');
            if !valid || by_name.contains_key(name) {
                return Err(format!("remote '{name}': invalid or duplicate name"));
            }

            let env_key: String = name
                .to_uppercase()
                .chars()
                .map(|ch| if ch.is_ascii_alphanumeric() { ch } else { '_' })
                .collect();
            let cred = |field: &str, from_toml: &Option<String>| -> Result<String, String> {
                let env_var = format!("MUORG_REMOTE_{env_key}_{}", field.to_uppercase());
                // Environment wins so secrets can stay out of the config file.
                std::env::var(&env_var)
                    .ok()
                    .filter(|v| !v.is_empty())
                    .or_else(|| from_toml.clone().filter(|v| !v.is_empty()))
                    .ok_or_else(|| {
                        format!(
                            "remote '{name}': missing {field} (set it in muorg-server.toml or the {env_var} environment variable)"
                        )
                    })
            };
            let key_id = cred("access_key_id", &c.access_key_id)?;
            let secret = cred("secret_access_key", &c.secret_access_key)?;

            // object_store uses a supplied endpoint verbatim in virtual-hosted
            // mode and only splices the bucket in for path-style, so do it here
            // rather than making users encode the quirk in their config.
            let endpoint = match &c.endpoint {
                None => None,
                Some(raw) => {
                    let raw = raw.trim_end_matches('/');
                    let (scheme, host) = raw.split_once("://").ok_or_else(|| {
                        format!("remote '{name}': endpoint must start with http:// or https://")
                    })?;
                    Some(
                        if c.virtual_hosted_style && !host.starts_with(&format!("{}.", c.bucket)) {
                            format!("{scheme}://{}.{host}", c.bucket)
                        } else {
                            raw.to_string()
                        },
                    )
                }
            };

            let mut b = AmazonS3Builder::new()
                .with_bucket_name(&c.bucket)
                .with_region(&c.region)
                .with_access_key_id(key_id)
                .with_secret_access_key(secret)
                .with_virtual_hosted_style_request(c.virtual_hosted_style)
                .with_unsigned_payload(c.unsigned_payload);
            if let Some(ep) = &endpoint {
                b = b.with_endpoint(ep).with_allow_http(ep.starts_with("http://"));
            }
            let store = b.build().map_err(|e| format!("remote '{name}': {e}"))?;

            let prefix = c
                .prefix
                .as_deref()
                .unwrap_or("")
                .trim_matches('/')
                .to_string();
            by_name.insert(
                name.to_string(),
                Arc::new(RemoteStore {
                    name: name.to_string(),
                    store: Arc::new(store),
                    prefix,
                }),
            );
        }
        Ok(Self { by_name })
    }

    pub fn is_empty(&self) -> bool {
        self.by_name.is_empty()
    }

    pub fn all(&self) -> Vec<Arc<RemoteStore>> {
        self.by_name.values().cloned().collect()
    }

    /// `remote://cloud/a/b.mp3` -> the store plus the object key. `None` for
    /// local paths and for remote names that are not configured.
    pub fn resolve(&self, uri: &str) -> Option<(Arc<RemoteStore>, ObjPath)> {
        let rest = uri.strip_prefix(REMOTE_SCHEME)?;
        let (name, key) = rest.split_once('/')?;
        let store = self.by_name.get(name)?.clone();
        Some((store, ObjPath::from(key)))
    }

    /// `remote://cloud` or `remote://cloud/prefix` -> the store, for dispatching
    /// on a `roots.path` value.
    pub fn resolve_root(&self, root: &str) -> Option<Arc<RemoteStore>> {
        let rest = root.strip_prefix(REMOTE_SCHEME)?;
        let name = rest.split('/').next()?;
        self.by_name.get(name).cloned()
    }
}

/// Downloads the whole object into a temp file whose name keeps `ext`, so the
/// extension-based dispatch in `muorg-core` still applies.
pub async fn fetch_to_temp(
    remote: &RemoteStore,
    key: &ObjPath,
    ext: &str,
) -> Result<tempfile::NamedTempFile, String> {
    let bytes = remote
        .store
        .get(key)
        .await
        .map_err(|e| e.to_string())?
        .bytes()
        .await
        .map_err(|e| e.to_string())?;
    let file = tempfile::Builder::new()
        .prefix("muorg-")
        .suffix(&format!(".{ext}"))
        .tempfile()
        .map_err(|e| e.to_string())?;
    std::fs::write(file.path(), &bytes).map_err(|e| e.to_string())?;
    Ok(file)
}

/// Uploads `file` to `key`, returning the object's new metadata. `PutResult`
/// carries no `last_modified`, so this heads the object afterwards — callers
/// need the timestamp to keep `tracks.mtime_secs` in sync.
pub async fn put_from_file(
    remote: &RemoteStore,
    key: &ObjPath,
    file: &std::path::Path,
) -> Result<ObjectMeta, String> {
    let bytes = tokio::fs::read(file).await.map_err(|e| e.to_string())?;
    remote
        .store
        .put(key, object_store::PutPayload::from(bytes::Bytes::from(bytes)))
        .await
        .map_err(|e| e.to_string())?;
    remote.store.head(key).await.map_err(|e| e.to_string())
}

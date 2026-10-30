use axum::{
    extract::{Query, State},
    response::IntoResponse,
    Json,
};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Arc;
use crate::routes::dto::{AdminHealthResponse, BackupDirectoryResponse, ErrorResponse, OkResponse};
use crate::routes::ApiError;
use crate::state::AppState;

#[derive(Deserialize, utoipa::ToSchema)]
pub struct RescanBody {
    pub root_path: Option<String>,
}

#[derive(Serialize, utoipa::ToSchema)]
pub struct RescanResult {
    pub tracks_added: u64,
}

/// Rescan one root, or every root when no body is sent.
#[utoipa::path(
    post,
    path = "/api/admin/rescan",
    tag = "Admin",
    request_body = Option<RescanBody>,
    responses(
        (status = 200, description = "Scan finished", body = RescanResult),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn rescan(
    State(state): State<Arc<AppState>>,
    body: Option<Json<RescanBody>>,
) -> Result<Json<RescanResult>, ApiError> {
    let root_path = body.and_then(|b| b.0.root_path);

    // Each DB section is scoped: a `MutexGuard` is `!Send` and cannot be held
    // across the remote scanner's `.await`s.
    let roots = match root_path {
        Some(path) => {
            // Upsert root (adds if new, resurrects if soft-deleted) then rescan.
            let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
            muorg_core::catalog::save_roots(&conn, std::slice::from_ref(&path))?;
            vec![path]
        }
        None => {
            let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
            muorg_core::catalog::load_roots(&conn)?
        }
    };

    let mut tracks_added = 0u64;
    for root in roots {
        if let Some(remote) = state.remotes.resolve_root(&root) {
            tracks_added +=
                crate::storage::scan::scan_remote_root(&state, &remote, state.remote_scan_concurrency)
                    .await?
                    .0;
        } else {
            let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
            tracks_added += muorg_core::catalog::rescan_root(&conn, &root)?;
        }
    }

    Ok(Json(RescanResult { tracks_added }))
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct RemoveFolderBody {
    pub root_path: String,
}

/// Drop a root and soft-delete the tracks under it.
#[utoipa::path(
    post,
    path = "/api/admin/remove-folder",
    tag = "Admin",
    request_body = RemoveFolderBody,
    responses(
        (status = 200, description = "Root removed", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn remove_folder(
    State(state): State<Arc<AppState>>,
    Json(body): Json<RemoveFolderBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::remove_root(&conn, &body.root_path)?;
    Ok(Json(OkResponse::new()))
}

/// Purge soft-deleted catalog rows immediately, bypassing the normal grace
/// period a `DELETE /api/tracks/{id}` leaves them in.
#[utoipa::path(
    post,
    path = "/api/admin/purge-deleted",
    tag = "Admin",
    responses(
        (status = 200, description = "Deleted rows purged", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn purge_deleted(
    State(state): State<Arc<AppState>>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::gc_deleted_tracks(&conn, 0)?;
    Ok(Json(OkResponse::new()))
}

/// Deprecated alias of [`purge_deleted`]: despite the name, this has never
/// cleared anything but soft-deleted rows. Kept for one release so existing
/// clients are not broken by the rename; new callers should use
/// `/api/admin/purge-deleted`.
#[utoipa::path(
    post,
    path = "/api/admin/clear-cache",
    tag = "Admin",
    responses(
        (status = 200, description = "Deleted rows purged (deprecated; use purge-deleted)", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
#[deprecated(note = "use purge_deleted / POST /api/admin/purge-deleted instead")]
#[allow(deprecated)]
pub async fn clear_cache(
    State(state): State<Arc<AppState>>,
) -> Result<Json<OkResponse>, ApiError> {
    purge_deleted(State(state)).await
}

/// Where pre-write backups are kept on the server.
#[utoipa::path(
    get,
    path = "/api/admin/backup-directory",
    tag = "Admin",
    responses(
        (status = 200, description = "Absolute backup directory path", body = BackupDirectoryResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_backup_directory(
    State(state): State<Arc<AppState>>,
) -> Result<Json<BackupDirectoryResponse>, ApiError> {
    let path = state.backup_dir.display().to_string();
    Ok(Json(BackupDirectoryResponse { path }))
}

/// Deeper health check than `/api/health`: also proves the catalog is readable.
#[utoipa::path(
    get,
    path = "/api/admin/health",
    // Distinct from `util::health`, which serves the public /api/health probe.
    operation_id = "admin_health",
    tag = "Admin",
    responses(
        (status = 200, description = "Server and catalog status", body = AdminHealthResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn health(
    State(state): State<Arc<AppState>>,
) -> Json<AdminHealthResponse> {
    let db_ok = (|| -> Result<(), String> {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        let _ = muorg_core::catalog::load_roots(&conn)?;
        Ok(())
    })().is_ok();

    Json(AdminHealthResponse {
        status: if db_ok { "ok" } else { "degraded" }.to_string(),
        server: "muorg-server".to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
    })
}

/// Prometheus exposition format.
#[utoipa::path(
    get,
    path = "/api/admin/metrics",
    operation_id = "admin_metrics",
    tag = "Admin",
    responses(
        (status = 200, description = "Prometheus metrics", content_type = "text/plain"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn metrics(
    State(state): State<Arc<AppState>>,
) -> Result<axum::response::Response, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let tracks = muorg_core::catalog::load_tracks(&conn)?;
    let roots = muorg_core::catalog::load_roots(&conn)?;

    let track_count = tracks.len();
    let root_count = roots.len();
    let artist_count = {
        let mut artists = std::collections::HashSet::new();
        for t in &tracks {
            if let Some(ref a) = t.artist { artists.insert(a.clone()); }
        }
        artists.len()
    };
    let album_count = {
        let mut albums = std::collections::HashSet::new();
        for t in &tracks {
            if let Some(ref a) = t.album { albums.insert(a.clone()); }
        }
        albums.len()
    };
    let total_duration: i64 = tracks.iter().map(|t| t.duration_secs.unwrap_or(0)).sum();

    let body = format!(
        "# HELP muorg_track_count Total number of tracks in the catalog\n\
         # TYPE muorg_track_count gauge\n\
         muorg_track_count {}\n\n\
         # HELP muorg_artist_count Total number of unique artists\n\
         # TYPE muorg_artist_count gauge\n\
         muorg_artist_count {}\n\n\
         # HELP muorg_album_count Total number of unique albums\n\
         # TYPE muorg_album_count gauge\n\
         muorg_album_count {}\n\n\
         # HELP muorg_root_count Number of content roots/folders\n\
         # TYPE muorg_root_count gauge\n\
         muorg_root_count {}\n\n\
         # HELP muorg_total_duration_secs Total playback duration across all tracks\n\
         # TYPE muorg_total_duration_secs gauge\n\
         muorg_total_duration_secs {}\n",
        track_count, artist_count, album_count, root_count, total_duration,
    );

    let headers = [(
        "Content-Type",
        "text/plain; version=0.0.4; charset=utf-8",
    )];

    Ok((headers, body).into_response())
}

/// Directories `GET /api/admin/browse` may list from. See `LibraryConfig::
/// browse_roots` for the default-fallback rule this implements: an explicit
/// `browse_roots` list wins outright; otherwise each configured content root
/// contributes itself and its parent directory, and nothing else. Remote
/// (`remote://...`) content roots are not local paths and are skipped.
fn allowed_browse_roots(state: &AppState) -> Vec<PathBuf> {
    if !state.browse.browse_roots.is_empty() {
        return state.browse.browse_roots.iter().map(PathBuf::from).collect();
    }
    let mut roots: Vec<PathBuf> = Vec::new();
    for content_path in &state.browse.content_paths {
        if crate::storage::is_remote_uri(content_path) {
            continue;
        }
        let p = PathBuf::from(content_path);
        if let Some(parent) = p.parent() {
            let parent = parent.to_path_buf();
            if !roots.contains(&parent) {
                roots.push(parent);
            }
        }
        if !roots.contains(&p) {
            roots.push(p);
        }
    }
    roots
}

/// Canonicalized, existing-directory subset of `allowed_browse_roots`. A root
/// that does not exist or cannot be canonicalized is dropped rather than
/// failing the request — content paths are often created lazily.
fn canonical_allowed_roots(state: &AppState) -> Vec<PathBuf> {
    allowed_browse_roots(state)
        .iter()
        .filter_map(|p| std::fs::canonicalize(p).ok())
        .filter(|p| p.is_dir())
        .collect()
}

/// Canonicalized, non-remote `content_paths`, for matching browse entries
/// against the configured library. Canonicalized the same way as
/// `canonical_allowed_roots`, so a root reached through a symlink (as temp
/// directories are on macOS) still matches rather than silently never
/// flagging as "already in the library".
fn canonical_content_roots(state: &AppState) -> Vec<PathBuf> {
    state
        .browse
        .content_paths
        .iter()
        .filter(|c| !crate::storage::is_remote_uri(c))
        .map(|c| std::fs::canonicalize(c).unwrap_or_else(|_| PathBuf::from(c)))
        .collect()
}

/// Is this directory one of `content_roots`, or does it already contain one?
/// Both answers matter to a client deciding whether "Use this folder" would
/// add a brand new root or one that is already in the library.
fn contains_or_is_root(dir: &std::path::Path, content_roots: &[PathBuf]) -> bool {
    content_roots.iter().any(|cp| cp == dir || cp.starts_with(dir))
}

#[derive(Deserialize, Default, utoipa::IntoParams)]
pub struct BrowseQuery {
    /// Absolute server-side directory to list. Omit to get the permitted
    /// starting points instead of one directory's contents.
    pub path: Option<String>,
}

#[derive(Serialize, utoipa::ToSchema)]
pub struct BrowseEntry {
    pub name: String,
    pub path: String,
    /// True when this directory already is, or already contains, a
    /// configured content root.
    pub is_root: bool,
}

/// Entries capped at this many per response; `truncated` reports the cut.
const BROWSE_ENTRY_LIMIT: usize = 500;

#[derive(Serialize, utoipa::ToSchema)]
pub struct BrowseResponse {
    /// The directory listed, or `null` for the top-level permitted roots.
    pub path: Option<String>,
    pub entries: Vec<BrowseEntry>,
    /// True when more than [`BROWSE_ENTRY_LIMIT`] sub-directories existed and
    /// the list was cut off.
    pub truncated: bool,
}

/// List immediate sub-directories of a server-side path. With no `path`,
/// lists the configured starting points instead (see `allowed_browse_roots`).
/// Never returns files, and refuses any path outside the allowlist — see the
/// module docs on `[library] browse_roots`.
#[utoipa::path(
    get,
    path = "/api/admin/browse",
    tag = "Admin",
    params(BrowseQuery),
    responses(
        (status = 200, description = "Directory entries, or the permitted starting points", body = BrowseResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 403, description = "Path is outside the configured browse roots", body = ErrorResponse),
        (status = 404, description = "Path does not exist or is not a directory", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn browse(
    State(state): State<Arc<AppState>>,
    Query(query): Query<BrowseQuery>,
) -> Result<Json<BrowseResponse>, ApiError> {
    let roots = canonical_allowed_roots(&state);
    let content_roots = canonical_content_roots(&state);

    let Some(requested) = query.path else {
        let mut entries: Vec<BrowseEntry> = roots
            .iter()
            .map(|root| BrowseEntry {
                name: root
                    .file_name()
                    .map(|n| n.to_string_lossy().into_owned())
                    .unwrap_or_else(|| root.display().to_string()),
                path: root.display().to_string(),
                is_root: contains_or_is_root(root, &content_roots),
            })
            .collect();
        entries.sort_by_key(|e| e.name.to_lowercase());
        return Ok(Json(BrowseResponse { path: None, entries, truncated: false }));
    };

    let canonical = std::fs::canonicalize(&requested)
        .map_err(|_| ApiError::not_found(format!("No such directory: {requested}")))?;
    if !canonical.is_dir() {
        return Err(ApiError::not_found(format!("Not a directory: {requested}")));
    }
    let confined = roots
        .iter()
        .any(|root| canonical == *root || canonical.starts_with(root));
    if !confined {
        return Err(ApiError::forbidden(format!(
            "{requested} is outside the configured browse roots"
        )));
    }

    let mut dir_entries: Vec<std::fs::DirEntry> = std::fs::read_dir(&canonical)
        .map_err(|e| ApiError::not_found(format!("Cannot read {requested}: {e}")))?
        .filter_map(|e| e.ok())
        .filter(|e| e.file_type().map(|t| t.is_dir()).unwrap_or(false))
        .collect();
    dir_entries.sort_by_key(|e| e.file_name());

    let truncated = dir_entries.len() > BROWSE_ENTRY_LIMIT;
    let entries = dir_entries
        .into_iter()
        .take(BROWSE_ENTRY_LIMIT)
        .map(|e| {
            let path = e.path();
            BrowseEntry {
                name: e.file_name().to_string_lossy().into_owned(),
                is_root: contains_or_is_root(&path, &content_roots),
                path: path.display().to_string(),
            }
        })
        .collect();

    Ok(Json(BrowseResponse {
        path: Some(canonical.display().to_string()),
        entries,
        truncated,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn mk(paths: &[&str]) -> Vec<String> {
        paths.iter().map(|s| s.to_string()).collect()
    }

    fn state_with(content_paths: &[&str], browse_roots: &[&str]) -> AppState {
        let mut state = test_app_state();
        state.browse = crate::state::BrowseConfig {
            content_paths: mk(content_paths),
            browse_roots: mk(browse_roots),
        };
        state
    }

    fn test_app_state() -> AppState {
        AppState::new(
            Arc::new(muorg_core::catalog::Catalog::new(&std::env::temp_dir().join(format!(
                "muorg-admin-test-{}.db",
                uuid::Uuid::new_v4()
            ))).unwrap()),
            std::env::temp_dir(),
            5,
            "test-key".to_string(),
            0,
            crate::config::TranscodingConfig::default(),
            Arc::new(crate::storage::RemoteStores::default()),
            Arc::new(crate::storage::covers::CoverCache::new(std::env::temp_dir(), 1024)),
            8,
        )
    }

    #[test]
    fn explicit_browse_roots_win_outright() {
        let state = state_with(&["/music", "/more-music"], &["/srv/allowed"]);
        assert_eq!(allowed_browse_roots(&state), vec![PathBuf::from("/srv/allowed")]);
    }

    #[test]
    fn default_falls_back_to_content_path_parents_and_themselves() {
        let state = state_with(&["/data/music"], &[]);
        assert_eq!(
            allowed_browse_roots(&state),
            vec![PathBuf::from("/data"), PathBuf::from("/data/music")],
        );
    }

    #[test]
    fn default_skips_remote_content_paths() {
        let state = state_with(&["remote://cloud/", "/data/music"], &[]);
        assert_eq!(
            allowed_browse_roots(&state),
            vec![PathBuf::from("/data"), PathBuf::from("/data/music")],
        );
    }

    #[test]
    fn no_configured_content_paths_means_nothing_browsable() {
        let state = state_with(&[], &[]);
        assert!(allowed_browse_roots(&state).is_empty());
    }

    #[test]
    fn home_directory_is_never_an_implicit_root() {
        let home = std::env::var("HOME").unwrap_or_default();
        let state = state_with(&["/data/music"], &[]);
        assert!(!allowed_browse_roots(&state).iter().any(|p| p == &PathBuf::from(&home)));
    }

    #[test]
    fn contains_or_is_root_matches_exact_and_ancestor() {
        let content = vec![PathBuf::from("/data/music/rock")];
        assert!(contains_or_is_root(std::path::Path::new("/data/music/rock"), &content));
        assert!(contains_or_is_root(std::path::Path::new("/data/music"), &content));
        assert!(!contains_or_is_root(std::path::Path::new("/data/other"), &content));
    }
}

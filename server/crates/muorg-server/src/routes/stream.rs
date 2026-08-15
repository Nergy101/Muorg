use axum::{
    body::Body,
    extract::{Path, Query, State},
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use crate::config::TranscodingConfig;
use crate::routes::ApiError;
use crate::state::AppState;
use crate::transcode;

#[derive(Serialize)]
pub struct TokenResponse {
    pub token: String,
}

// GET /api/tracks/:id/stream-token  (requires Bearer auth via middleware)
pub async fn issue_token(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<TokenResponse>, ApiError> {
    // Verify track exists
    {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::get_track_path_by_id(&conn, id)?
            .ok_or_else(|| ApiError::not_found(format!("Track {id} not found")))?;
    }
    let token = state.tokens.issue(id, 28800);
    Ok(Json(TokenResponse { token }))
}

#[derive(Deserialize)]
pub struct StreamQuery {
    pub token: Option<String>,
    pub start: Option<f32>,
}

struct ByteRange {
    start: u64,
    end: u64, // inclusive
}

fn parse_range(range: &str, total: u64) -> Option<ByteRange> {
    let s = range.strip_prefix("bytes=")?;
    let mut parts = s.splitn(2, '-');
    let start: u64 = parts.next()?.parse().ok()?;
    let end: u64 = match parts.next() {
        Some(e) if !e.is_empty() => e.parse().ok()?,
        _ => total.saturating_sub(1),
    };
    if start >= total || end < start { return None; }
    Some(ByteRange { start, end: end.min(total - 1) })
}

/// Distinguishes a deleted object from a broken bucket or credential, so a
/// misconfigured remote does not masquerade as a missing track.
fn remote_err(id: i64, e: object_store::Error) -> Response {
    match e {
        object_store::Error::NotFound { .. } => {
            tracing::warn!(track_id = id, "stream: object not found in bucket");
            StatusCode::NOT_FOUND.into_response()
        }
        other => {
            tracing::error!(track_id = id, "stream: object store error: {other}");
            StatusCode::BAD_GATEWAY.into_response()
        }
    }
}

// GET /stream/:id?token=<tok>  (no Bearer auth — uses short-lived token instead)
pub async fn stream_audio(
    Path(id): Path<i64>,
    Query(params): Query<StreamQuery>,
    State(state): State<Arc<AppState>>,
    req_headers: HeaderMap,
) -> Response {
    let token = match params.token {
        Some(t) => t,
        None => {
            tracing::warn!(track_id = id, "stream: missing token");
            return StatusCode::UNAUTHORIZED.into_response();
        }
    };

    if !state.tokens.validate(&token, id) {
        tracing::warn!(track_id = id, "stream: invalid token");
        return StatusCode::UNAUTHORIZED.into_response();
    }

    let track_path = match (|| -> Result<String, ApiError> {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::get_track_path_by_id(&conn, id)?
            .ok_or_else(|| ApiError::not_found(format!("Track {id} not found")))
    })() {
        Ok(p) => p,
        Err(e) => {
            tracing::warn!(track_id = id, "stream: track not found");
            return e.into_response();
        }
    };

    let is_flac = track_path.to_lowercase().ends_with(".flac");
    let start_secs = params.start.unwrap_or(0.0).max(0.0);

    if let Some((remote, key)) = state.remotes.resolve(&track_path) {
        return stream_remote(&state, id, remote, key, is_flac, start_secs, &req_headers).await;
    }

    // Shared validators: file mtime is the natural cache key
    let mtime = crate::routes::util::file_mtime(std::path::Path::new(&track_path));
    let etag = format!(
        "\"stream-{id}-{}\"",
        mtime.map(|t| t.duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)).unwrap_or(0)
    );
    if let Some(resp) = crate::routes::util::check_not_modified(&etag, mtime, &req_headers) {
        return resp;
    }

    if is_flac {
        // Serve the FLAC track as a seekable MP3: transcode once (cache by
        // track id + mtime), then reply with Content-Length + ranges exactly
        // like the MP3 branch. A length-less live chunked stream would make the
        // browser treat the track as a live stream and desync on buffer stalls.
        let mtime = crate::routes::util::file_mtime(std::path::Path::new(&track_path));
        let mtime_secs = mtime
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let etag = format!("\"stream-{id}-{mtime_secs}\"");
        if let Some(resp) = crate::routes::util::check_not_modified(&etag, mtime, &req_headers) {
            return resp;
        }

        let cfg = state.transcoding_config.clone();
        let cached = state.transcode_cache.get(id, mtime_secs);
        let bytes = match cached {
            Some(b) => b,
            None => match transcode_local(&cfg, &track_path).await {
                Ok(b) => {
                    state.transcode_cache.insert(id, mtime_secs, b.clone());
                    b
                }
                Err(e) => {
                    tracing::error!(track_id = id, "transcode failed: {e}");
                    return StatusCode::INTERNAL_SERVER_ERROR.into_response();
                }
            },
        };

        serve_transcoded_audio(&bytes, start_secs, &etag, mtime, &cfg, &req_headers)
    } else {
        let range_header = req_headers.get("range").and_then(|v| v.to_str().ok()).map(str::to_owned);
        tracing::info!(track_id = id, path = %track_path, range = ?range_header, "stream mp3");
        match tokio::fs::read(&track_path).await {
            Ok(data) => {
                let total = data.len() as u64;
                let range = range_header
                    .as_deref()
                    .and_then(|r| parse_range(r, total));

                let mut headers = HeaderMap::new();
                headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
                headers.insert("Accept-Ranges", "bytes".parse().unwrap());
                headers.insert("Cache-Control", "no-cache".parse().unwrap());
                headers.insert("Vary", "Accept-Encoding".parse().unwrap());
                headers.insert("ETag", etag.parse().unwrap());
                if let Some(m) = mtime {
                    headers.insert("Last-Modified", crate::routes::util::http_date(m).parse().unwrap());
                }

                if let Some(r) = range {
                    let body = data[r.start as usize..=r.end as usize].to_vec();
                    headers.insert("Content-Range", format!("bytes {}-{}/{}", r.start, r.end, total).parse().unwrap());
                    headers.insert("Content-Length", body.len().to_string().parse().unwrap());
                    (StatusCode::PARTIAL_CONTENT, headers, body).into_response()
                } else {
                    headers.insert("Content-Length", total.to_string().parse().unwrap());
                    (StatusCode::OK, headers, data).into_response()
                }
            }
            Err(_) => StatusCode::NOT_FOUND.into_response(),
        }
    }
}

/// Transcodes a local FLAC file to MP3 bytes on a blocking thread.
async fn transcode_local(
    cfg: &TranscodingConfig,
    path: &str,
) -> Result<Arc<Vec<u8>>, Box<dyn std::error::Error + Send + Sync>> {
    let cfg = cfg.clone();
    let path = path.to_owned();
    tokio::task::spawn_blocking(move || {
        transcode::transcode_to_mp3_bytes(transcode::TranscodeSource::LocalPath(path), &cfg)
    })
    .await
    .map_err(|e| Box::new(e) as Box<dyn std::error::Error + Send + Sync>)?
    .map(Arc::new)
}

/// Serves a cached FLAC transcode as a seekable MP3 file: full bytes with a
/// `Content-Length` (200) or a byte range (206), honouring `?start=` by slicing
/// the buffer to the requested second offset. The browser can then buffer and
/// seek natively, exactly like an MP3 file.
fn serve_transcoded_audio(
    bytes: &[u8],
    start_secs: f32,
    etag: &str,
    mtime: Option<std::time::SystemTime>,
    cfg: &TranscodingConfig,
    req_headers: &HeaderMap,
) -> Response {
    // CBR MP3: frame length (bytes) = 144 * bitrate_bps / sample_rate.
    let bitrate_bps = cfg.bitrate as u64 * 1000;
    let frame_bytes = (144u64 * bitrate_bps / cfg.sample_rate as u64).max(1);
    let bytes_per_sec = bitrate_bps / 8;
    let mut start_byte = if start_secs > 0.0 {
        (start_secs as u64 * bytes_per_sec).min(bytes.len().saturating_sub(1) as u64)
    } else {
        0
    };
    // Align to a frame boundary so the decoder syncs cleanly.
    if start_byte > 0 {
        start_byte = (start_byte / frame_bytes) * frame_bytes;
    }
    let data = &bytes[start_byte as usize..];
    let total = data.len() as u64;

    let range_header = req_headers.get("range").and_then(|v| v.to_str().ok()).map(str::to_owned);
    let range = range_header.as_deref().and_then(|r| parse_range(r, total));

    let mut headers = HeaderMap::new();
    headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
    headers.insert("Accept-Ranges", "bytes".parse().unwrap());
    headers.insert("Cache-Control", "no-cache".parse().unwrap());
    headers.insert("Vary", "Accept-Encoding".parse().unwrap());
    headers.insert("ETag", format!("{etag}-{}", start_byte).parse().unwrap());
    if let Some(m) = mtime {
        headers.insert("Last-Modified", crate::routes::util::http_date(m).parse().unwrap());
    }

    if let Some(r) = range {
        let body = data[r.start as usize..=r.end as usize].to_vec();
        headers.insert("Content-Range", format!("bytes {}-{}/{}", r.start, r.end, total).parse().unwrap());
        headers.insert("Content-Length", body.len().to_string().parse().unwrap());
        (StatusCode::PARTIAL_CONTENT, headers, body).into_response()
    } else {
        headers.insert("Content-Length", total.to_string().parse().unwrap());
        (StatusCode::OK, headers, data.to_vec()).into_response()
    }
}

/// Streams a track that lives in object storage.
///
/// A client `Range` becomes a ranged GET and the object-store byte stream is
/// piped straight through, so seeking never pulls more than the requested slice.
async fn stream_remote(
    state: &Arc<AppState>,
    id: i64,
    remote: Arc<crate::storage::RemoteStore>,
    key: object_store::path::Path,
    is_flac: bool,
    start_secs: f32,
    req_headers: &HeaderMap,
) -> Response {
    use object_store::{GetOptions, GetRange, ObjectStore, ObjectStoreExt};

    let head = match remote.store.head(&key).await {
        Ok(m) => m,
        Err(e) => return remote_err(id, e),
    };

    // Same validator shape as the local branch so client caches are unaffected.
    let secs = head.last_modified.timestamp().max(0) as u64;
    let mtime = Some(std::time::UNIX_EPOCH + std::time::Duration::from_secs(secs));
    let etag = format!("\"stream-{id}-{secs}\"");
    if let Some(resp) = crate::routes::util::check_not_modified(&etag, mtime, req_headers) {
        return resp;
    }

    let mut headers = HeaderMap::new();
    headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
    headers.insert("Cache-Control", "no-cache".parse().unwrap());
    headers.insert("Vary", "Accept-Encoding".parse().unwrap());
    headers.insert("ETag", etag.parse().unwrap());
    if let Some(m) = mtime {
        headers.insert("Last-Modified", crate::routes::util::http_date(m).parse().unwrap());
    }

    if is_flac {
        // Same seekable-file treatment as the local FLAC branch: transcode once
        // and serve the cached MP3 bytes with Content-Length + ranges.
        let cfg = state.transcoding_config.clone();
        let cached = state.transcode_cache.get(id, secs);
        let bytes = match cached {
            Some(b) => b,
            None => {
                let handle = tokio::runtime::Handle::current();
                let store = remote.store.clone();
                let size = head.size;
                let key = key.clone();
                let cfg_for_task = cfg.clone();
                match tokio::task::spawn_blocking(move || {
                    let src = crate::storage::reader::RemoteReader::new(store, key, size, handle);
                    transcode::transcode_to_mp3_bytes(
                        transcode::TranscodeSource::Remote(Box::new(src)),
                        &cfg_for_task,
                    )
                })
                .await
                {
                    Ok(Ok(b)) => {
                        let arc = Arc::new(b);
                        state.transcode_cache.insert(id, secs, arc.clone());
                        arc
                    }
                    Ok(Err(e)) => {
                        tracing::error!(track_id = id, "remote transcode failed: {e}");
                        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
                    }
                    Err(e) => {
                        tracing::error!(track_id = id, "remote transcode task failed: {e}");
                        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
                    }
                }
            }
        };
        return serve_transcoded_audio(&bytes, start_secs, &etag, mtime, &cfg, req_headers);
    }

    let total = head.size;
    let range = req_headers
        .get("range")
        .and_then(|v| v.to_str().ok())
        .and_then(|r| parse_range(r, total));
    tracing::info!(track_id = id, key = %key, total, range = ?range.as_ref().map(|r| (r.start, r.end)), "stream remote mp3");

    headers.insert("Accept-Ranges", "bytes".parse().unwrap());
    let opts = GetOptions {
        range: range.as_ref().map(|r| GetRange::Bounded(r.start..r.end + 1)),
        ..Default::default()
    };
    let res = match remote.store.get_opts(&key, opts).await {
        Ok(r) => r,
        Err(e) => return remote_err(id, e),
    };

    match range {
        Some(r) => {
            headers.insert(
                "Content-Range",
                format!("bytes {}-{}/{}", r.start, r.end, total).parse().unwrap(),
            );
            headers.insert("Content-Length", (r.end - r.start + 1).to_string().parse().unwrap());
            (StatusCode::PARTIAL_CONTENT, headers, Body::from_stream(res.into_stream())).into_response()
        }
        None => {
            headers.insert("Content-Length", total.to_string().parse().unwrap());
            (StatusCode::OK, headers, Body::from_stream(res.into_stream())).into_response()
        }
    }
}

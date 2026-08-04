use axum::{
    body::Body,
    extract::{Path, Query, State},
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use bytes::Bytes;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio_stream::wrappers::ReceiverStream;
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
    start: usize,
    end: usize, // inclusive
}

fn parse_range(range: &str, total: usize) -> Option<ByteRange> {
    let s = range.strip_prefix("bytes=")?;
    let mut parts = s.splitn(2, '-');
    let start: usize = parts.next()?.parse().ok()?;
    let end: usize = match parts.next() {
        Some(e) if !e.is_empty() => e.parse().ok()?,
        _ => total.saturating_sub(1),
    };
    if start >= total || end < start { return None; }
    Some(ByteRange { start, end: end.min(total - 1) })
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
        type StreamChunk = Result<Bytes, Box<dyn std::error::Error + Send + Sync>>;
        let (tx, rx) = tokio::sync::mpsc::channel::<StreamChunk>(128);
        let path = track_path.clone();
        let start_secs = params.start.unwrap_or(0.0).max(0.0);
        tracing::info!(track_id = id, path = %track_path, start_secs, "stream flac→mp3");
        let cfg = state.transcoding_config.clone();
        tokio::task::spawn_blocking(move || {
            transcode::transcode_to_mp3(&path, start_secs, &cfg, tx);
        });
        let stream = ReceiverStream::new(rx);
        let body = Body::from_stream(stream);
        let mut headers = HeaderMap::new();
        headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
        headers.insert("Cache-Control", "no-cache".parse().unwrap());
        headers.insert("Vary", "Accept-Encoding".parse().unwrap());
        headers.insert("ETag", etag.parse().unwrap());
        if let Some(m) = mtime {
            headers.insert("Last-Modified", crate::routes::util::http_date(m).parse().unwrap());
        }
        (StatusCode::OK, headers, body).into_response()
    } else {
        let range_header = req_headers.get("range").and_then(|v| v.to_str().ok()).map(str::to_owned);
        tracing::info!(track_id = id, path = %track_path, range = ?range_header, "stream mp3");
        match tokio::fs::read(&track_path).await {
            Ok(data) => {
                let total = data.len();
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
                    let body = data[r.start..=r.end].to_vec();
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

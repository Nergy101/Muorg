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
    let token = state.tokens.issue(id, 60);
    Ok(Json(TokenResponse { token }))
}

#[derive(Deserialize)]
pub struct StreamQuery {
    pub token: Option<String>,
}

fn parse_range_start(range: &str, total: usize) -> Option<usize> {
    let s = range.strip_prefix("bytes=")?;
    let start_str = s.split('-').next()?;
    let start: usize = start_str.parse().ok()?;
    if start < total { Some(start) } else { None }
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
        None => return StatusCode::UNAUTHORIZED.into_response(),
    };

    if !state.tokens.validate(&token, id) {
        return StatusCode::UNAUTHORIZED.into_response();
    }

    let track_path = match (|| -> Result<String, ApiError> {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::get_track_path_by_id(&conn, id)?
            .ok_or_else(|| ApiError::not_found(format!("Track {id} not found")))
    })() {
        Ok(p) => p,
        Err(e) => return e.into_response(),
    };

    let is_flac = track_path.to_lowercase().ends_with(".flac");

    if is_flac {
        type StreamChunk = Result<Bytes, Box<dyn std::error::Error + Send + Sync>>;
        let (tx, rx) = tokio::sync::mpsc::channel::<StreamChunk>(128);
        let path = track_path.clone();
        tokio::task::spawn_blocking(move || {
            transcode::transcode_to_mp3(&path, 0.0, tx);
        });
        let stream = ReceiverStream::new(rx);
        let body = Body::from_stream(stream);
        let mut headers = HeaderMap::new();
        headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
        (StatusCode::OK, headers, body).into_response()
    } else {
        match tokio::fs::read(&track_path).await {
            Ok(data) => {
                let total = data.len();
                let range_start = req_headers
                    .get("range")
                    .and_then(|v| v.to_str().ok())
                    .and_then(|r| parse_range_start(r, total));

                let mut headers = HeaderMap::new();
                headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
                headers.insert("Accept-Ranges", "bytes".parse().unwrap());

                if let Some(start) = range_start {
                    let end = total - 1;
                    let body = data[start..].to_vec();
                    headers.insert("Content-Range", format!("bytes {start}-{end}/{total}").parse().unwrap());
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

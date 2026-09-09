use axum::{
    extract::State,
    http::{HeaderMap, StatusCode},
    response::{Html, IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use crate::routes::dto::ErrorResponse;
use crate::routes::ApiError;
use crate::state::AppState;

/// Build ETag / Last-Modified validators for a file and return a 304 response
/// when the request's validators match. Returns `None` when the caller should
/// serve the full body. Callers should also set `Cache-Control` themselves.
pub fn check_not_modified(
    etag: &str,
    modified: Option<std::time::SystemTime>,
    req_headers: &HeaderMap,
) -> Option<Response> {
    if let Some(inm) = req_headers
        .get("if-none-match")
        .and_then(|v| v.to_str().ok())
    {
        if inm == "*" || inm.split(',').any(|t| t.trim() == etag) {
            return Some(StatusCode::NOT_MODIFIED.into_response());
        }
    } else if let Some(ims) = req_headers
        .get("if-modified-since")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| httpdate::parse_http_date(v).ok())
    {
        if let Some(m) = modified {
            if m.duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
                <= ims.duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
            {
                return Some(StatusCode::NOT_MODIFIED.into_response());
            }
        }
    }
    None
}

pub fn file_mtime(path: &std::path::Path) -> Option<std::time::SystemTime> {
    std::fs::metadata(path).ok()?.modified().ok()
}

pub fn http_date(t: std::time::SystemTime) -> String {
    httpdate::fmt_http_date(t)
}

pub async fn home() -> Html<String> {
    Html(include_str!("home.html").replace("{{VERSION}}", env!("CARGO_PKG_VERSION")))
}

/// Unauthenticated liveness probe.
#[utoipa::path(
    get,
    path = "/api/health",
    tag = "System",
    responses((status = 200, description = "Server is up", content_type = "text/plain")),
)]
pub async fn health() -> impl IntoResponse {
    (StatusCode::OK, "Healthy")
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct FetchImageBody {
    pub url: String,
}

#[derive(Serialize, utoipa::ToSchema)]
pub struct FetchedImage {
    pub base64: String,
    pub mime: String,
}

static USER_AGENT: &str = "Muorg/1.0 (music organizer; album art from Wikipedia)";

/// Proxy-fetch a remote image and return it base64-encoded, so the clients can
/// pull cover art from the web without tripping CORS.
#[utoipa::path(
    post,
    path = "/api/fetch-image",
    tag = "System",
    request_body = FetchImageBody,
    responses(
        (status = 200, description = "Base64 image plus its MIME type", body = FetchedImage),
        (status = 400, description = "URL unreachable or not an image", body = ErrorResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn fetch_image(
    State(_state): State<Arc<AppState>>,
    Json(body): Json<FetchImageBody>,
) -> Result<Json<FetchedImage>, ApiError> {
    let client = reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .build()
        .map_err(|e| e.to_string())?;
    let response = client.get(&body.url).send().await.map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        return Err(format!("HTTP {}", response.status()).into());
    }
    let content_type = response
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("image/jpeg")
        .split(';')
        .next()
        .unwrap_or("image/jpeg")
        .trim()
        .to_string();
    let bytes = response.bytes().await.map_err(|e| e.to_string())?;
    let b64 = base64::Engine::encode(&base64::engine::general_purpose::STANDARD, bytes.as_ref());
    Ok(Json(FetchedImage { base64: b64, mime: content_type }))
}

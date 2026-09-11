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
///
/// The URL comes from the caller, which makes this the server's one
/// caller-directed outbound request — see [`crate::urlguard`] for the guards
/// and why they are there. In short: the host must be on the allowlist, it
/// must not resolve anywhere internal, every redirect hop is re-checked, the
/// response must actually be an image, and the body is capped as it streams.
#[utoipa::path(
    post,
    path = "/api/fetch-image",
    tag = "System",
    request_body = FetchImageBody,
    responses(
        (status = 200, description = "Base64 image plus its MIME type", body = FetchedImage),
        (status = 400, description = "URL rejected, unreachable, not an image, or too large", body = ErrorResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn fetch_image(
    State(state): State<Arc<AppState>>,
    Json(body): Json<FetchImageBody>,
) -> Result<Json<FetchedImage>, ApiError> {
    let cfg = &state.image_fetch;

    let url = crate::urlguard::validate_url(&body.url, &cfg.allowed_hosts).map_err(|e| {
        tracing::warn!(url = %body.url, "fetch-image rejected: {e}");
        ApiError::bad_request(e)
    })?;
    crate::urlguard::assert_public_host(&url).await.map_err(|e| {
        tracing::warn!(url = %body.url, "fetch-image rejected: {e}");
        ApiError::bad_request(e)
    })?;

    // Cloned into the redirect policy, which outlives this borrow of `state`.
    let allowed = cfg.allowed_hosts.clone();
    let client = reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(std::time::Duration::from_secs(5))
        .timeout(std::time::Duration::from_secs(cfg.timeout_secs))
        // An allowlisted host redirecting to an internal one would otherwise
        // walk straight past the check above. The policy closure is sync, so it
        // re-runs the allowlist but not DNS — the allowlist is the control here.
        .redirect(reqwest::redirect::Policy::custom(move |attempt| {
            if attempt.previous().len() >= crate::urlguard::MAX_REDIRECTS {
                return attempt.error("too many redirects");
            }
            match crate::urlguard::validate_url(attempt.url().as_str(), &allowed) {
                Ok(_) => attempt.follow(),
                Err(e) => attempt.error(e),
            }
        }))
        .build()
        .map_err(|e| e.to_string())?;

    let response = client
        .get(url.clone())
        .send()
        .await
        .map_err(|e| ApiError::bad_request(format!("Could not fetch {url}: {e}")))?;

    if !response.status().is_success() {
        return Err(ApiError::bad_request(format!("HTTP {}", response.status())));
    }

    let content_type = response
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .map(|v| v.split(';').next().unwrap_or(v).trim().to_ascii_lowercase())
        .unwrap_or_default();
    // Previously this defaulted to `image/jpeg` and returned whatever came
    // back, so an HTML error page arrived as a broken "image".
    if !content_type.starts_with("image/") {
        return Err(ApiError::bad_request(if content_type.is_empty() {
            "Response had no Content-Type; expected an image".to_string()
        } else {
            format!("Expected an image, got `{content_type}`")
        }));
    }

    let max = cfg.max_bytes;
    if let Some(len) = response.content_length() {
        if len > max {
            return Err(ApiError::bad_request(format!(
                "Image is {len} bytes, over the {max} byte limit"
            )));
        }
    }

    // Streamed rather than `response.bytes()`: a declared length can be absent
    // or a lie, and buffering the whole body first is the memory-exhaustion
    // lever this is meant to remove.
    let mut buf: Vec<u8> = Vec::with_capacity(
        response.content_length().unwrap_or(0).min(max).min(1 << 20) as usize,
    );
    let mut stream = std::pin::pin!(response.bytes_stream());
    while let Some(chunk) = tokio_stream::StreamExt::next(&mut stream).await {
        let chunk = chunk.map_err(|e| ApiError::bad_request(format!("Download failed: {e}")))?;
        if buf.len() as u64 + chunk.len() as u64 > max {
            return Err(ApiError::bad_request(format!(
                "Image exceeds the {max} byte limit"
            )));
        }
        buf.extend_from_slice(&chunk);
    }

    let b64 = base64::Engine::encode(&base64::engine::general_purpose::STANDARD, &buf);
    Ok(Json(FetchedImage { base64: b64, mime: content_type }))
}

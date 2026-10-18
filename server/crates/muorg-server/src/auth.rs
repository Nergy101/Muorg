use axum::{
    body::Body,
    extract::{ConnectInfo, State},
    http::{header, Request, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
};
use std::net::SocketAddr;
use std::sync::Arc;
use crate::state::AppState;

pub async fn auth_middleware(
    State(state): State<Arc<AppState>>,
    request: Request<Body>,
    next: Next,
) -> Result<Response, StatusCode> {
    let auth = request
        .headers()
        .get("Authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "));

    match auth {
        Some(key) if constant_time_eq(key, &state.api_key) => Ok(next.run(request).await),
        _ => Err(StatusCode::UNAUTHORIZED),
    }
}

pub async fn rate_limit_middleware(
    State(state): State<Arc<AppState>>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    request: Request<Body>,
    next: Next,
) -> Result<Response, StatusCode> {
    // Apply rate limiting per IP. A refusal says when to come back, so a
    // well-behaved client backs off instead of hammering.
    if let Err(retry_after_secs) = state.rate_limiter.check_with_retry_after(addr) {
        let mut response = StatusCode::TOO_MANY_REQUESTS.into_response();
        if let Ok(value) = header::HeaderValue::from_str(&retry_after_secs.to_string()) {
            response.headers_mut().insert(header::RETRY_AFTER, value);
        }
        return Ok(response);
    }
    Ok(next.run(request).await)
}

fn constant_time_eq(a: &str, b: &str) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.bytes().zip(b.bytes()).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

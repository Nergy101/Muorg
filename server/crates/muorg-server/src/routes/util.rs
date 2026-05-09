use axum::{extract::State, response::{Html, IntoResponse}, http::StatusCode, Json};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use crate::routes::ApiError;
use crate::state::AppState;

pub async fn home() -> Html<String> {
    Html(include_str!("home.html").replace("{{VERSION}}", env!("CARGO_PKG_VERSION")))
}

pub async fn health() -> impl IntoResponse {
    (StatusCode::OK, "Healthy")
}

#[derive(Deserialize)]
pub struct FetchImageBody {
    pub url: String,
}

#[derive(Serialize)]
pub struct FetchedImage {
    pub base64: String,
    pub mime: String,
}

static USER_AGENT: &str = "Muorg/1.0 (music organizer; album art from Wikipedia)";

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

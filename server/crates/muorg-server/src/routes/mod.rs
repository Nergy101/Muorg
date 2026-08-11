pub mod admin;
pub mod cast;
pub mod library;
pub mod openapi;
pub mod playlists;
pub mod stream;
pub mod tracks;
pub mod util;

use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};

pub struct ApiError(StatusCode, String);

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(serde_json::json!({"error": self.1}))).into_response()
    }
}

impl ApiError {
    pub fn not_found(msg: impl ToString) -> Self {
        ApiError(StatusCode::NOT_FOUND, msg.to_string())
    }

    pub fn bad_request(msg: impl ToString) -> Self {
        ApiError(StatusCode::BAD_REQUEST, msg.to_string())
    }
}

impl From<String> for ApiError {
    fn from(s: String) -> Self {
        ApiError(StatusCode::INTERNAL_SERVER_ERROR, s)
    }
}

//! Response bodies that used to be ad-hoc `serde_json::json!` maps.
//!
//! Every one of these was previously an untyped `Json<serde_json::Value>`, so
//! its shape existed only in the handler body and each client re-guessed it.
//! Naming them here gives `utoipa` something to put in `components.schemas`,
//! which is what the generated TypeScript and Kotlin models are built from.

use serde::Serialize;
use utoipa::ToSchema;

/// The uniform acknowledgement for mutations that return no data.
#[derive(Debug, Clone, Serialize, ToSchema)]
pub struct OkResponse {
    /// Always `true`; failures come back as an [`ErrorResponse`] with a 4xx/5xx.
    pub ok: bool,
}

impl OkResponse {
    pub fn new() -> Self {
        OkResponse { ok: true }
    }
}

impl Default for OkResponse {
    fn default() -> Self {
        Self::new()
    }
}

/// Body of every non-2xx response.
#[derive(Debug, Clone, Serialize, ToSchema)]
pub struct ErrorResponse {
    pub error: String,
}

/// `GET /api/tracks/count`
#[derive(Debug, Clone, Serialize, ToSchema)]
pub struct CountResponse {
    pub count: i64,
}

/// `GET /api/admin/backup-directory`
#[derive(Debug, Clone, Serialize, ToSchema)]
pub struct BackupDirectoryResponse {
    pub path: String,
}

/// `GET /api/admin/health`
#[derive(Debug, Clone, Serialize, ToSchema)]
pub struct AdminHealthResponse {
    /// `"ok"` when the catalog is readable, `"degraded"` otherwise.
    pub status: String,
    pub server: String,
    pub version: String,
}

/// `POST /api/tracks/metadata/batch`
#[derive(Debug, Clone, Serialize, ToSchema)]
pub struct BatchUpdateResponse {
    pub ok: bool,
    /// Number of tracks whose tags were actually written.
    pub updated: usize,
}

/// `POST /api/tracks/{id}/auto-tag-suggestions`
#[derive(Debug, Clone, Serialize, ToSchema)]
pub struct AutoTagSuggestionsResponse {
    pub candidates: Vec<crate::musicbrainz::MatchCandidate>,
}

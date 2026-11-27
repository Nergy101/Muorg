//! Profile management (NER-326).
//!
//! Profiles optionally scope play history and listening statistics to a
//! person; the library, playlists and ratings stay shared. The default profile
//! (id 1, "You") always exists, and while it is the only one, clients hide
//! every trace of profiles — the feature turns itself on only when a second
//! profile is created here.

use axum::{
    extract::{Path, State},
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use crate::routes::dto::{ErrorResponse, OkResponse};
use crate::routes::ApiError;
use crate::state::AppState;
use muorg_core::catalog::{Profile, DEFAULT_PROFILE_NAME};

#[derive(Deserialize, utoipa::ToSchema)]
pub struct ProfileBody {
    /// Display name, 1 to 64 characters. Trimmed server-side.
    pub name: String,
}

// GET /api/profiles
/// All listening profiles, default first.
#[utoipa::path(
    get,
    path = "/api/profiles",
    tag = "Profiles",
    operation_id = "list_profiles",
    responses(
        (status = 200, description = "Profiles, default profile first", body = Vec<Profile>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn list(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<Profile>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::list_profiles(&conn)?))
}

// POST /api/profiles
/// Create a profile. Creating the second profile is what turns the feature on
/// in clients; one profile is the invisible default.
#[utoipa::path(
    post,
    path = "/api/profiles",
    tag = "Profiles",
    operation_id = "create_profile",
    request_body = ProfileBody,
    responses(
        (status = 200, description = "Created", body = Profile),
        (status = 400, description = "Invalid name", body = ErrorResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn create(
    State(state): State<Arc<AppState>>,
    Json(body): Json<ProfileBody>,
) -> Result<Json<Profile>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::create_profile(&conn, &body.name)?))
}

// PATCH /api/profiles/{id}
/// Rename a profile. The default profile may be renamed; only its id is fixed,
/// because play history rows point at it.
#[utoipa::path(
    patch,
    path = "/api/profiles/{id}",
    tag = "Profiles",
    operation_id = "rename_profile",
    params(("id" = i64, Path, description = "Profile id")),
    request_body = ProfileBody,
    responses(
        (status = 200, description = "Renamed", body = OkResponse),
        (status = 400, description = "Invalid name", body = ErrorResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such profile", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn rename(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<ProfileBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    match muorg_core::catalog::rename_profile(&conn, id, &body.name) {
        Ok(()) => Ok(Json(OkResponse::new())),
        Err(e) => Err(profile_error(e)),
    }
}

// DELETE /api/profiles/{id}
/// Delete a profile. Its play history and rollup rows move to the default
/// profile, so no listening history is lost. The default profile and the
/// last remaining profile cannot be deleted.
#[utoipa::path(
    delete,
    path = "/api/profiles/{id}",
    tag = "Profiles",
    operation_id = "delete_profile",
    params(("id" = i64, Path, description = "Profile id")),
    responses(
        (status = 200, description = "Deleted; plays moved to the default profile", body = OkResponse),
        (status = 400, description = "Default or last profile cannot be deleted", body = ErrorResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such profile", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn remove(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    match muorg_core::catalog::delete_profile(&conn, id) {
        Ok(()) => Ok(Json(OkResponse::new())),
        Err(e) => Err(profile_error(e)),
    }
}

/// Core errors say "not found" plainly; everything else is a bad request.
fn profile_error(e: String) -> ApiError {
    if e.contains("not found") {
        ApiError::not_found(e)
    } else {
        ApiError::bad_request(e)
    }
}

/// The name clients display for the default profile before they have fetched
/// the list — kept adjacent to the handlers that serve it.
pub const DEFAULT_PROFILE: &str = DEFAULT_PROFILE_NAME;

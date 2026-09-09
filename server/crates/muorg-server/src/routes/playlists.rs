use axum::{
    extract::{Path, State},
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use crate::routes::dto::{ErrorResponse, OkResponse};
use crate::routes::ApiError;
use crate::state::AppState;
use muorg_core::catalog::{Playlist, PlaylistTrackEntry};

/// All playlists, in user-defined order.
#[utoipa::path(
    get,
    path = "/api/playlists",
    operation_id = "list_playlists",
    tag = "Playlists",
    responses(
        (status = 200, description = "Playlists", body = Vec<Playlist>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn list(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<Playlist>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::load_playlists(&conn)?))
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct CreateBody {
    pub name: String,
}

/// Create an empty playlist.
#[utoipa::path(
    post,
    path = "/api/playlists",
    operation_id = "create_playlist",
    tag = "Playlists",
    request_body = CreateBody,
    responses(
        (status = 200, description = "The created playlist", body = Playlist),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn create(
    State(state): State<Arc<AppState>>,
    Json(body): Json<CreateBody>,
) -> Result<Json<Playlist>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::create_playlist(&conn, &body.name)?))
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct UpdateBody {
    pub name: Option<String>,
    pub icon: Option<Option<String>>,
}

/// Rename a playlist and/or set its icon.
#[utoipa::path(
    patch,
    path = "/api/playlists/{id}",
    operation_id = "update_playlist",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    request_body = UpdateBody,
    responses(
        (status = 200, description = "Updated", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn update(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<UpdateBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    if let Some(name) = body.name {
        muorg_core::catalog::rename_playlist(&conn, id, &name)?;
    }
    if let Some(icon) = body.icon {
        muorg_core::catalog::set_playlist_icon(&conn, id, icon.as_deref())?;
    }
    Ok(Json(OkResponse::new()))
}

/// Delete a playlist and its entries.
#[utoipa::path(
    delete,
    path = "/api/playlists/{id}",
    operation_id = "delete_playlist",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    responses(
        (status = 200, description = "Deleted", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn delete(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::delete_playlist(&conn, id)?;
    Ok(Json(OkResponse::new()))
}

/// Track ids in playlist order.
#[utoipa::path(
    get,
    path = "/api/playlists/{id}/tracks",
    // Explicit: `playlists::get_tracks` and `library::get_tracks` would
    // otherwise both derive the operationId `get_tracks`, and a duplicate id
    // silently collapses two endpoints into one in every generated client.
    operation_id = "get_playlist_tracks",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    responses(
        (status = 200, description = "Track ids, in playlist order", body = Vec<i64>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<i64>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_playlist_tracks(&conn, id)?))
}

/// Playlist entries, which carry their own id so the same track can appear twice.
#[utoipa::path(
    get,
    path = "/api/playlists/{id}/entries",
    operation_id = "get_playlist_entries",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    responses(
        (status = 200, description = "Entries, in playlist order", body = Vec<PlaylistTrackEntry>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_entries(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<PlaylistTrackEntry>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_playlist_entries(&conn, id)?))
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct TrackIdsBody {
    pub track_ids: Vec<i64>,
}

/// Append tracks to a playlist.
#[utoipa::path(
    post,
    path = "/api/playlists/{id}/tracks",
    operation_id = "add_playlist_tracks",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    request_body = TrackIdsBody,
    responses(
        (status = 200, description = "Added", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn add_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<TrackIdsBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::add_tracks_to_playlist(&conn, id, &body.track_ids)?;
    Ok(Json(OkResponse::new()))
}

/// Remove every entry for the given tracks.
#[utoipa::path(
    delete,
    path = "/api/playlists/{id}/tracks",
    operation_id = "remove_playlist_tracks",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    request_body = TrackIdsBody,
    responses(
        (status = 200, description = "Removed", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn remove_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<TrackIdsBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::remove_tracks_from_playlist(&conn, id, &body.track_ids)?;
    Ok(Json(OkResponse::new()))
}

/// Remove one entry, leaving other copies of the same track in place.
#[utoipa::path(
    delete,
    path = "/api/playlists/{id}/entries/{entry_id}",
    operation_id = "remove_playlist_entry",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id"), ("entry_id" = i64, Path, description = "Playlist entry id")),
    responses(
        (status = 200, description = "Removed", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn remove_entry(
    Path((_id, entry_id)): Path<(i64, i64)>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::remove_playlist_entry_by_id(&conn, entry_id)?;
    Ok(Json(OkResponse::new()))
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct ReorderBody {
    pub ids: Vec<i64>,
}

/// Reorder entries within a playlist.
#[utoipa::path(
    put,
    path = "/api/playlists/{id}/tracks/order",
    operation_id = "reorder_playlist_tracks",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    request_body = ReorderBody,
    responses(
        (status = 200, description = "Reordered", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn reorder_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<ReorderBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::reorder_playlist_tracks(&conn, id, &body.ids)?;
    Ok(Json(OkResponse::new()))
}

/// Reorder the playlists themselves.
#[utoipa::path(
    put,
    path = "/api/playlists/order",
    operation_id = "reorder_playlists",
    tag = "Playlists",
    request_body = ReorderBody,
    responses(
        (status = 200, description = "Reordered", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn reorder(
    State(state): State<Arc<AppState>>,
    Json(body): Json<ReorderBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::reorder_playlists(&conn, &body.ids)?;
    Ok(Json(OkResponse::new()))
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct SmartCreateBody {
    pub name: String,
    pub rules_json: String,
}

/// Create a rule-driven smart playlist.
#[utoipa::path(
    post,
    path = "/api/playlists/smart",
    operation_id = "create_smart_playlist",
    tag = "Playlists",
    request_body = SmartCreateBody,
    responses(
        (status = 200, description = "The created playlist", body = Playlist),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn create_smart(
    State(state): State<Arc<AppState>>,
    Json(body): Json<SmartCreateBody>,
) -> Result<Json<Playlist>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::create_smart_playlist(&conn, &body.name, &body.rules_json)?))
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct SmartRulesBody {
    pub rules_json: String,
}

/// Replace a smart playlist's rule set.
#[utoipa::path(
    patch,
    path = "/api/playlists/smart/{id}/rules",
    operation_id = "update_smart_playlist_rules",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    request_body = SmartRulesBody,
    responses(
        (status = 200, description = "Updated", body = OkResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn update_smart_rules(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
    Json(body): Json<SmartRulesBody>,
) -> Result<Json<OkResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    muorg_core::catalog::set_smart_playlist_rules(&conn, id, Some(&body.rules_json))?;
    Ok(Json(OkResponse::new()))
}

/// Evaluate the rules and return the matching track ids.
#[utoipa::path(
    get,
    path = "/api/playlists/smart/{id}/tracks",
    operation_id = "get_smart_playlist_tracks",
    tag = "Playlists",
    params(("id" = i64, Path, description = "Playlist id")),
    responses(
        (status = 200, description = "Matching track ids", body = Vec<i64>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such playlist", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_smart_tracks(
    Path(id): Path<i64>,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<i64>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let rules: Option<String> = conn.query_row(
        "SELECT smart_rules FROM playlists WHERE id = ?1",
        [id],
        |r| r.get(0),
    ).map_err(|e| e.to_string())?;
    match rules {
        Some(r) => Ok(Json(muorg_core::catalog::resolve_smart_playlist_track_ids(&conn, &r)?)),
        None => Err(ApiError::not_found("Not a smart playlist")),
    }
}

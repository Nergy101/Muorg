use axum::{
    extract::{Query, State},
    http::HeaderMap,
    response::{IntoResponse, Response},
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use crate::routes::dto::{CountResponse, ErrorResponse};
use crate::routes::ApiError;
use crate::state::AppState;
use muorg_core::catalog::{CatalogTrack, LibraryStats};

#[utoipa::path(
    get,
    path = "/api/roots",
    tag = "Catalog",
    responses(
        (status = 200, description = "Configured library root folders", body = Vec<String>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_roots(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<String>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let roots = muorg_core::catalog::load_roots(&conn)?;
    Ok(Json(roots))
}

#[derive(Deserialize, Default, utoipa::IntoParams)]
pub struct TracksQuery {
    /// Row to start at. Default 0.
    pub offset: Option<i64>,
    /// Rows to return. Default 500 — this is the page size every client must
    /// loop against; a bare `/api/tracks` is page one, not the whole catalog.
    pub limit: Option<i64>,
}

#[utoipa::path(
    get,
    path = "/api/tracks",
    tag = "Catalog / Tracks",
    params(TracksQuery),
    responses(
        (
            status = 200,
            description = "One page of the catalog. `X-Total-Count` carries the \
                           full catalog size, so a client knows how many more \
                           pages to fetch.",
            body = Vec<CatalogTrack>,
            headers(("X-Total-Count" = i64, description = "Total tracks in the catalog")),
        ),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_tracks(
    State(state): State<Arc<AppState>>,
    Query(params): Query<TracksQuery>,
) -> Result<Response, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let (tracks, total) = muorg_core::catalog::load_tracks_paginated(
        &conn,
        params.offset.unwrap_or(0),
        params.limit.unwrap_or(500),
    )?;
    let mut headers = HeaderMap::new();
    headers.insert("X-Total-Count", total.to_string().parse().unwrap());
    Ok((headers, Json(tracks)).into_response())
}

/// Total track count without loading rows.
#[utoipa::path(
    get,
    path = "/api/tracks/count",
    tag = "Catalog / Tracks",
    responses(
        (status = 200, description = "Total tracks in the catalog", body = CountResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_tracks_count(
    State(state): State<Arc<AppState>>,
) -> Result<Json<CountResponse>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let count = muorg_core::catalog::count_tracks(&conn)?;
    Ok(Json(CountResponse { count }))
}

#[derive(Deserialize, Default, utoipa::IntoParams)]
pub struct HistoryQuery {
    /// Rows to return. Default 50.
    pub limit: Option<i64>,
    /// Look-back window in days. Default 30. Only used by `/api/play-history/top`.
    pub days: Option<i64>,
}

#[derive(Deserialize, Default, utoipa::IntoParams)]
pub struct PlayHistoryQuery {
    /// Rows to return. Default 50.
    pub limit: Option<i64>,
    /// Look-back window in days. Default 30. Only used by `/api/play-history/top`.
    pub days: Option<i64>,
    /// Comma-separated profile ids (NER-326). Absent or empty covers all.
    pub profiles: Option<String>,
}

/// Same parsing rules as the stats routes: absent/empty/`all` covers every
/// profile, and anything else must be a comma-separated list of integers.
fn parse_profiles(raw: Option<&str>) -> Result<Option<Vec<i64>>, ApiError> {
    let raw = match raw {
        None => return Ok(None),
        Some(r) => r.trim(),
    };
    if raw.is_empty() || raw.eq_ignore_ascii_case("all") {
        return Ok(None);
    }
    let mut ids = Vec::new();
    for part in raw.split(',') {
        let part = part.trim();
        if part.is_empty() {
            continue;
        }
        ids.push(
            part.parse::<i64>()
                .map_err(|_| ApiError::bad_request(format!("Invalid profile id: {part}")))?,
        );
    }
    Ok(if ids.is_empty() { None } else { Some(ids) })
}

#[utoipa::path(
    get,
    path = "/api/play-history/recent",
    tag = "Catalog / Play History",
    params(PlayHistoryQuery),
    responses(
        (status = 200, description = "Most recently played tracks, newest first", body = Vec<CatalogTrack>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_recent_play_history(
    State(state): State<Arc<AppState>>,
    Query(params): Query<PlayHistoryQuery>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let profiles = parse_profiles(params.profiles.as_deref())?;
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::load_recently_played_profile(
        &conn,
        params.limit.unwrap_or(50),
        profiles.as_deref(),
    )?))
}

#[utoipa::path(
    get,
    path = "/api/play-history/top",
    tag = "Catalog / Play History",
    params(PlayHistoryQuery),
    responses(
        (status = 200, description = "Most played tracks within the window", body = Vec<CatalogTrack>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_top_play_history(
    State(state): State<Arc<AppState>>,
    Query(params): Query<PlayHistoryQuery>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let profiles = parse_profiles(params.profiles.as_deref())?;
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::load_most_played_profile(
        &conn,
        params.limit.unwrap_or(50),
        params.days.unwrap_or(30),
        profiles.as_deref(),
    )?))
}

#[utoipa::path(
    get,
    path = "/api/tracks/recently-added",
    tag = "Catalog / Tracks",
    params(HistoryQuery),
    responses(
        (status = 200, description = "Most recently scanned tracks, newest first", body = Vec<CatalogTrack>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_recently_added(
    State(state): State<Arc<AppState>>,
    Query(params): Query<HistoryQuery>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::load_recently_added(
        &conn,
        params.limit.unwrap_or(50),
    )?))
}

#[derive(Deserialize, utoipa::IntoParams)]
pub struct SearchQuery {
    /// Free-text query matched against title, artist and album.
    pub q: String,
}

#[utoipa::path(
    get,
    path = "/api/search",
    tag = "Catalog",
    params(SearchQuery),
    responses(
        (status = 200, description = "Matching tracks", body = Vec<CatalogTrack>),
        (status = 400, description = "Missing `q`", body = ErrorResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn search_tracks(
    State(state): State<Arc<AppState>>,
    Query(params): Query<SearchQuery>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let tracks = muorg_core::catalog::search_tracks(&conn, &params.q)?;
    Ok(Json(tracks))
}

#[utoipa::path(
    get,
    path = "/api/stats",
    tag = "Catalog / Stats",
    responses(
        (status = 200, description = "Catalog totals", body = LibraryStats),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_stats(
    State(state): State<Arc<AppState>>,
) -> Result<Json<LibraryStats>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let stats = muorg_core::catalog::get_library_stats(&conn)?;
    Ok(Json(stats))
}

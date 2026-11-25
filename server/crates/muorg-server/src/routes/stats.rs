//! Listening statistics (NER-325): what was played, for how long, and when.
//!
//! The catalog endpoints answer "what is in the library"; these answer "what
//! actually came out of the speakers". Every window is a look-back in days
//! from now, and every listening figure is seconds — measured when the client
//! reported it, estimated from the track's catalog duration otherwise.

use axum::{extract::{Query, State}, Json};
use serde::Deserialize;
use std::sync::Arc;
use crate::routes::dto::ErrorResponse;
use crate::routes::ApiError;
use crate::state::AppState;
use muorg_core::catalog::{
    ClockBucket, ListeningSummary, ListeningWeek, TopAlbumStat, TopArtistStat, TopTrackStat,
};

#[derive(Deserialize, Default, utoipa::IntoParams)]
pub struct WindowQuery {
    /// Look-back window in days. Default 30.
    pub days: Option<i64>,
}

#[derive(Deserialize, Default, utoipa::IntoParams)]
pub struct TopQuery {
    /// Look-back window in days. Default 30.
    pub days: Option<i64>,
    /// Rows to return. Default 10.
    pub limit: Option<i64>,
}

#[derive(Deserialize, Default, utoipa::IntoParams)]
pub struct WeeksQuery {
    /// Weeks to cover, oldest bucket first. Default 12.
    pub weeks: Option<i64>,
}

/// Listening totals for the window: plays, seconds listened, and how many
/// distinct tracks, artists and albums that covered.
#[utoipa::path(
    get,
    path = "/api/stats/summary",
    tag = "Catalog / Stats",
    params(WindowQuery),
    responses(
        (status = 200, description = "Listening totals inside the window", body = ListeningSummary),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_listening_summary(
    State(state): State<Arc<AppState>>,
    Query(params): Query<WindowQuery>,
) -> Result<Json<ListeningSummary>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_listening_summary(
        &conn,
        params.days.unwrap_or(30),
    )?))
}

/// Top tracks inside the window, ranked by seconds listened (plays break ties).
#[utoipa::path(
    get,
    path = "/api/stats/top/tracks",
    tag = "Catalog / Stats",
    params(TopQuery),
    responses(
        (status = 200, description = "Tracks with the most listening time inside the window", body = Vec<TopTrackStat>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_top_tracks_time(
    State(state): State<Arc<AppState>>,
    Query(params): Query<TopQuery>,
) -> Result<Json<Vec<TopTrackStat>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_top_tracks_by_time(
        &conn,
        params.limit.unwrap_or(10),
        params.days.unwrap_or(30),
    )?))
}

/// Top artists inside the window, ranked by seconds listened (plays break ties).
#[utoipa::path(
    get,
    path = "/api/stats/top/artists",
    tag = "Catalog / Stats",
    params(TopQuery),
    responses(
        (status = 200, description = "Artists with the most listening time inside the window", body = Vec<TopArtistStat>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_top_artists_time(
    State(state): State<Arc<AppState>>,
    Query(params): Query<TopQuery>,
) -> Result<Json<Vec<TopArtistStat>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_top_artists_by_time(
        &conn,
        params.limit.unwrap_or(10),
        params.days.unwrap_or(30),
    )?))
}

/// Top albums inside the window, ranked by seconds listened (plays break ties).
#[utoipa::path(
    get,
    path = "/api/stats/top/albums",
    tag = "Catalog / Stats",
    params(TopQuery),
    responses(
        (status = 200, description = "Albums with the most listening time inside the window", body = Vec<TopAlbumStat>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_top_albums_time(
    State(state): State<Arc<AppState>>,
    Query(params): Query<TopQuery>,
) -> Result<Json<Vec<TopAlbumStat>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_top_albums_by_time(
        &conn,
        params.limit.unwrap_or(10),
        params.days.unwrap_or(30),
    )?))
}

/// Listening time per week, read from the daily rollup so pruning play
/// history never eats the graph. Empty weeks included, oldest first.
#[utoipa::path(
    get,
    path = "/api/stats/listening-time",
    tag = "Catalog / Stats",
    params(WeeksQuery),
    responses(
        (status = 200, description = "Weekly listening buckets, oldest week first", body = Vec<ListeningWeek>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_listening_time(
    State(state): State<Arc<AppState>>,
    Query(params): Query<WeeksQuery>,
) -> Result<Json<Vec<ListeningWeek>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_listening_time_by_week(
        &conn,
        params.weeks.unwrap_or(12),
    )?))
}

/// How listening is spread over the hours of the day, in the server's local
/// timezone, inside the window. Only hours with plays are returned.
#[utoipa::path(
    get,
    path = "/api/stats/clock",
    tag = "Catalog / Stats",
    params(WindowQuery),
    responses(
        (status = 200, description = "Plays and listening time per hour of day", body = Vec<ClockBucket>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_play_clock(
    State(state): State<Arc<AppState>>,
    Query(params): Query<WindowQuery>,
) -> Result<Json<Vec<ClockBucket>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::get_play_clock(
        &conn,
        params.days.unwrap_or(30),
    )?))
}

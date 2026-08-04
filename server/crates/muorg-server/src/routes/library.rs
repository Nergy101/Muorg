use axum::{
    extract::{Query, State},
    http::HeaderMap,
    response::{IntoResponse, Response},
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use crate::routes::ApiError;
use crate::state::AppState;
use muorg_core::catalog::{CatalogTrack, LibraryStats};

pub async fn get_roots(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<String>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let roots = muorg_core::catalog::load_roots(&conn)?;
    Ok(Json(roots))
}

#[derive(Deserialize, Default)]
pub struct TracksQuery {
    pub offset: Option<i64>,
    pub limit: Option<i64>,
}

// GET /api/tracks?offset=&limit= — paginated, returns X-Total-Count header
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

// GET /api/tracks/count — total track count without loading rows
pub async fn get_tracks_count(
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let count = muorg_core::catalog::count_tracks(&conn)?;
    Ok(Json(serde_json::json!({ "count": count })))
}

#[derive(Deserialize, Default)]
pub struct HistoryQuery {
    pub limit: Option<i64>,
    pub days: Option<i64>,
}

// GET /api/play-history/recent?limit=50
pub async fn get_recent_play_history(
    State(state): State<Arc<AppState>>,
    Query(params): Query<HistoryQuery>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::load_recently_played(
        &conn,
        params.limit.unwrap_or(50),
    )?))
}

// GET /api/play-history/top?limit=50&days=30
pub async fn get_top_play_history(
    State(state): State<Arc<AppState>>,
    Query(params): Query<HistoryQuery>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    Ok(Json(muorg_core::catalog::load_most_played(
        &conn,
        params.limit.unwrap_or(50),
        params.days.unwrap_or(30),
    )?))
}

// GET /api/tracks/recently-added?limit=50
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

#[derive(Deserialize)]
pub struct SearchQuery {
    pub q: String,
}

pub async fn search_tracks(
    State(state): State<Arc<AppState>>,
    Query(params): Query<SearchQuery>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let tracks = muorg_core::catalog::search_tracks(&conn, &params.q)?;
    Ok(Json(tracks))
}

pub async fn get_stats(
    State(state): State<Arc<AppState>>,
) -> Result<Json<LibraryStats>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let stats = muorg_core::catalog::get_library_stats(&conn)?;
    Ok(Json(stats))
}

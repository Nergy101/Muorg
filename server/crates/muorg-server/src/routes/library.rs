use axum::{extract::{Query, State}, Json};
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

pub async fn get_tracks(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<CatalogTrack>>, ApiError> {
    let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
    let tracks = muorg_core::catalog::load_tracks(&conn)?;
    Ok(Json(tracks))
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

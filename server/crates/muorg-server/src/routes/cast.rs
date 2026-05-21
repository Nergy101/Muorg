use axum::{extract::State, http::StatusCode, Json};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use crate::cast::{CastCommand, CastSessionStatus};
use crate::routes::ApiError;
use crate::state::AppState;

// GET /api/cast/devices
pub async fn get_devices(State(state): State<Arc<AppState>>) -> Json<Vec<crate::cast::CastDevice>> {
    Json(state.cast_discovery.devices.lock().unwrap().clone())
}

// POST /api/cast/discovery/start
pub async fn start_discovery(State(state): State<Arc<AppState>>) -> StatusCode {
    state.cast_discovery.start();
    StatusCode::NO_CONTENT
}

// POST /api/cast/discovery/stop
pub async fn stop_discovery(State(state): State<Arc<AppState>>) -> StatusCode {
    state.cast_discovery.stop();
    StatusCode::NO_CONTENT
}

#[derive(Serialize)]
pub struct CastStatusResponse {
    session: CastSessionStatus,
    volume: f32,
}

// GET /api/cast/status
pub async fn get_status(State(state): State<Arc<AppState>>) -> Json<CastStatusResponse> {
    let session = state.cast_session.status.lock().unwrap().clone();
    let volume = *state.cast_session.volume.lock().unwrap();
    Json(CastStatusResponse { session, volume })
}

#[derive(Deserialize)]
pub struct PlayBody {
    device_id: String,
    // Device address and port are provided by the caller (discovered via Tauri-native mDNS).
    device_address: String,
    device_port: u16,
    track_id: i64,
}

// POST /api/cast/play
pub async fn play(
    State(state): State<Arc<AppState>>,
    Json(body): Json<PlayBody>,
) -> Result<StatusCode, ApiError> {
    let track_path = {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::get_track_path_by_id(&conn, body.track_id)?
            .ok_or_else(|| ApiError::not_found(format!("Track {} not found", body.track_id)))?
    };

    // 4-hour token — cast sessions can run for the duration of an album or playlist
    let token = state.tokens.issue(body.track_id, 4 * 60 * 60);
    let lan_ip = local_ip_address::local_ip().map_err(|e| e.to_string())?;
    let stream_url = format!(
        "http://{}:{}/stream/{}?token={}",
        lan_ip, state.server_port, body.track_id, token
    );
    let is_flac = track_path.to_lowercase().ends_with(".flac");

    state.cast_session.start_session(body.device_address, body.device_port, stream_url, is_flac);
    Ok(StatusCode::NO_CONTENT)
}

// POST /api/cast/pause
pub async fn pause(State(state): State<Arc<AppState>>) -> Result<StatusCode, ApiError> {
    state.cast_session.send_command(CastCommand::Pause)?;
    Ok(StatusCode::NO_CONTENT)
}

// POST /api/cast/resume
pub async fn resume(State(state): State<Arc<AppState>>) -> Result<StatusCode, ApiError> {
    state.cast_session.send_command(CastCommand::Resume)?;
    Ok(StatusCode::NO_CONTENT)
}

// POST /api/cast/stop
pub async fn stop(State(state): State<Arc<AppState>>) -> Result<StatusCode, ApiError> {
    state.cast_session.send_command(CastCommand::Stop)?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
pub struct SeekBody {
    position_secs: f32,
    was_playing: bool,
}

// POST /api/cast/seek
pub async fn seek(
    State(state): State<Arc<AppState>>,
    Json(body): Json<SeekBody>,
) -> Result<StatusCode, ApiError> {
    state.cast_session.send_command(CastCommand::Seek {
        secs: body.position_secs,
        was_playing: body.was_playing,
    })?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
pub struct VolumeBody {
    level: f32,
}

// POST /api/cast/volume
pub async fn set_volume(
    State(state): State<Arc<AppState>>,
    Json(body): Json<VolumeBody>,
) -> Result<StatusCode, ApiError> {
    let level = body.level.clamp(0.0, 1.0);
    state.cast_session.send_command(CastCommand::SetVolume(level))?;
    Ok(StatusCode::NO_CONTENT)
}

use axum::{extract::State, http::StatusCode, Json};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use crate::cast::{CastCommand, CastDevice, CastSessionStatus, NoDiscoveryObserver, NoObserver};
use crate::routes::dto::ErrorResponse;
use crate::routes::ApiError;
use crate::state::AppState;

/// Chromecast devices seen by the current mDNS sweep.
#[utoipa::path(
    get,
    path = "/api/cast/devices",
    operation_id = "cast_devices",
    tag = "Cast",
    responses(
        (status = 200, description = "Discovered devices", body = Vec<CastDevice>),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_devices(State(state): State<Arc<AppState>>) -> Json<Vec<CastDevice>> {
    Json(state.cast_discovery.devices.lock().unwrap().clone())
}

/// Begin mDNS discovery.
#[utoipa::path(
    post,
    path = "/api/cast/discovery/start",
    operation_id = "cast_start_discovery",
    tag = "Cast",
    responses(
        (status = 204, description = "Discovery started"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn start_discovery(State(state): State<Arc<AppState>>) -> StatusCode {
    state.cast_discovery.start(NoDiscoveryObserver);
    StatusCode::NO_CONTENT
}

/// Stop mDNS discovery.
#[utoipa::path(
    post,
    path = "/api/cast/discovery/stop",
    operation_id = "cast_stop_discovery",
    tag = "Cast",
    responses(
        (status = 204, description = "Discovery stopped"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn stop_discovery(State(state): State<Arc<AppState>>) -> StatusCode {
    state.cast_discovery.stop();
    StatusCode::NO_CONTENT
}

#[derive(Serialize, utoipa::ToSchema)]
pub struct CastStatusResponse {
    session: CastSessionStatus,
    volume: f32,
    /// Id of the current cast session, empty when idle. A client that started
    /// a cast remembers its own id; an active session carrying a different id
    /// means another device took the session over.
    session_id: String,
}

/// Current cast session state and device volume.
#[utoipa::path(
    get,
    path = "/api/cast/status",
    operation_id = "cast_status",
    tag = "Cast",
    responses(
        (status = 200, description = "Session status", body = CastStatusResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn get_status(State(state): State<Arc<AppState>>) -> Json<CastStatusResponse> {
    let session = state.cast_session.status.lock().unwrap().clone();
    let volume = *state.cast_session.volume.lock().unwrap();
    let session_id = state.cast_session.session_id();
    Json(CastStatusResponse { session, volume, session_id })
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct PlayBody {
    // Device address and port are provided by the caller (discovered via Tauri-native mDNS).
    device_address: String,
    device_port: u16,
    track_id: i64,
}

#[derive(Serialize, utoipa::ToSchema)]
pub struct CastPlayResponse {
    /// Id of the started session. While polling `GET /api/cast/status`, an
    /// active session whose `session_id` differs from this one means another
    /// device took the session over.
    session_id: String,
}

/// The URL a Chromecast is told to load. The stream route is public
/// (token-protected); when the API listener is loopback-only, the server also
/// runs a stream-only listener on all interfaces (see `main.rs`), and the
/// device fetches from that port — the main port would refuse the
/// connection. Otherwise the main listener already serves `/stream`.
fn cast_stream_url(state: &AppState, lan_ip: &std::net::IpAddr, track_id: i64, token: &str) -> String {
    let port = state.stream_port.unwrap_or(state.server_port);
    format!(
        "http://{}:{}/stream/{}?token={}",
        lan_ip, port, track_id, token
    )
}

/// Start casting a track to a device.
#[utoipa::path(
    post,
    path = "/api/cast/play",
    operation_id = "cast_play",
    tag = "Cast",
    request_body = PlayBody,
    responses(
        (status = 200, description = "Session started", body = CastPlayResponse),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
        (status = 404, description = "No such track", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn play(
    State(state): State<Arc<AppState>>,
    Json(body): Json<PlayBody>,
) -> Result<Json<CastPlayResponse>, ApiError> {
    let track_path = {
        let conn = state.catalog.db.lock().map_err(|e| e.to_string())?;
        muorg_core::catalog::get_track_path_by_id(&conn, body.track_id)?
            .ok_or_else(|| ApiError::not_found(format!("Track {} not found", body.track_id)))?
    };

    // 4-hour token — cast sessions can run for the duration of an album or playlist
    let token = state.tokens.issue(body.track_id, 4 * 60 * 60);
    let lan_ip = local_ip_address::local_ip().map_err(|e| e.to_string())?;
    let stream_url = cast_stream_url(&state, &lan_ip, body.track_id, &token);
    let needs_transcode = muorg_core::metadata::needs_transcode(&track_path);

    // The server reads its own state for GET /api/cast/status, so it has
    // nothing to be pushed.
    state.cast_session.start_session(
        body.device_address,
        body.device_port,
        stream_url,
        needs_transcode,
        NoObserver,
    );
    let session_id = state.cast_session.session_id();
    Ok(Json(CastPlayResponse { session_id }))
}

/// Pause the cast session.
#[utoipa::path(
    post,
    path = "/api/cast/pause",
    operation_id = "cast_pause",
    tag = "Cast",
    responses(
        (status = 204, description = "Command accepted"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn pause(State(state): State<Arc<AppState>>) -> Result<StatusCode, ApiError> {
    state.cast_session.send_command(CastCommand::Pause)?;
    Ok(StatusCode::NO_CONTENT)
}

/// Resume the cast session.
#[utoipa::path(
    post,
    path = "/api/cast/resume",
    operation_id = "cast_resume",
    tag = "Cast",
    responses(
        (status = 204, description = "Command accepted"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn resume(State(state): State<Arc<AppState>>) -> Result<StatusCode, ApiError> {
    state.cast_session.send_command(CastCommand::Resume)?;
    Ok(StatusCode::NO_CONTENT)
}

/// Tear down the cast session.
#[utoipa::path(
    post,
    path = "/api/cast/stop",
    operation_id = "cast_stop",
    tag = "Cast",
    responses(
        (status = 204, description = "Command accepted"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn stop(State(state): State<Arc<AppState>>) -> Result<StatusCode, ApiError> {
    state.cast_session.send_command(CastCommand::Stop)?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize, utoipa::ToSchema)]
pub struct SeekBody {
    position_secs: f32,
    was_playing: bool,
}

/// Seek within the casting track.
#[utoipa::path(
    post,
    path = "/api/cast/seek",
    operation_id = "cast_seek",
    tag = "Cast",
    request_body = SeekBody,
    responses(
        (status = 204, description = "Command accepted"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
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

#[derive(Deserialize, utoipa::ToSchema)]
pub struct VolumeBody {
    level: f32,
}

/// Set device volume, 0.0–1.0.
#[utoipa::path(
    post,
    path = "/api/cast/volume",
    operation_id = "cast_set_volume",
    tag = "Cast",
    request_body = VolumeBody,
    responses(
        (status = 204, description = "Command accepted"),
        (status = 401, description = "Missing or invalid API key", body = ErrorResponse),
    ),
    security(("BearerAuth" = [])),
)]
pub async fn set_volume(
    State(state): State<Arc<AppState>>,
    Json(body): Json<VolumeBody>,
) -> Result<StatusCode, ApiError> {
    let level = body.level.clamp(0.0, 1.0);
    state.cast_session.send_command(CastCommand::SetVolume(level))?;
    Ok(StatusCode::NO_CONTENT)
}

#[cfg(test)]
mod tests {
    use super::*;
    use muorg_core::catalog::Catalog;

    fn state_with_ports(server_port: u16, stream_port: Option<u16>) -> AppState {
        let dir = tempfile::tempdir().unwrap();
        let catalog = Arc::new(Catalog::new(&dir.path().join("test.db")).unwrap());
        let mut state = AppState::new(
            catalog,
            dir.path().join("backups"),
            5,
            "key".to_string(),
            server_port,
            crate::config::TranscodingConfig::default(),
            Arc::new(crate::storage::RemoteStores::default()),
            Arc::new(crate::storage::covers::CoverCache::new(
                dir.path().join("covers"),
                1024,
            )),
            8,
        );
        if let Some(port) = stream_port {
            state = state.with_stream_port(port);
        }
        state
    }

    #[test]
    fn the_stream_url_uses_the_main_port_when_no_stream_listener_runs() {
        let state = state_with_ports(7700, None);
        let ip: std::net::IpAddr = "192.168.1.10".parse().unwrap();
        assert_eq!(
            cast_stream_url(&state, &ip, 3, "tok"),
            "http://192.168.1.10:7700/stream/3?token=tok"
        );
    }

    #[test]
    fn the_stream_url_uses_the_stream_listener_port_when_the_api_is_loopback_only() {
        // The desktop sidecar binds 127.0.0.1; the Chromecast fetches from the
        // all-interfaces stream listener, not the loopback-only API port.
        let state = state_with_ports(7700, Some(41234));
        let ip: std::net::IpAddr = "192.168.1.10".parse().unwrap();
        assert_eq!(
            cast_stream_url(&state, &ip, 3, "tok"),
            "http://192.168.1.10:41234/stream/3?token=tok"
        );
    }
}

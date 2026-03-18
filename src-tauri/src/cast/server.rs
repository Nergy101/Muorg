use axum::{
    body::Body,
    extract::{Query, State},
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
    routing::get,
    Router,
};
use bytes::Bytes;
use serde::Deserialize;
use std::collections::HashSet;
use std::sync::{Arc, Mutex};
use tokio::sync::oneshot;
use tokio_stream::wrappers::ReceiverStream;

#[derive(Clone)]
struct ServerState {
    allowlist: Arc<Mutex<HashSet<String>>>,
}

#[derive(Deserialize)]
struct TrackQuery {
    path: String,
    /// Optional start offset in seconds (used for FLAC seek-by-reload).
    start: Option<f32>,
}

type StreamChunk = Result<Bytes, Box<dyn std::error::Error + Send + Sync>>;

/// Parse a `Range: bytes=<start>-[end]` header and return the start byte offset.
fn parse_range_start(range: &str, total: usize) -> Option<usize> {
    let s = range.strip_prefix("bytes=")?;
    let start_str = s.split('-').next()?;
    let start: usize = start_str.parse().ok()?;
    if start < total { Some(start) } else { None }
}

async fn serve_track(
    req_headers: HeaderMap,
    Query(params): Query<TrackQuery>,
    State(state): State<ServerState>,
) -> impl IntoResponse {
    let allowed = state.allowlist.lock().unwrap().contains(&params.path);
    if !allowed {
        return StatusCode::FORBIDDEN.into_response();
    }

    let start_secs = params.start.unwrap_or(0.0);
    let is_flac = params.path.to_lowercase().ends_with(".flac");

    if is_flac {
        // Transcode FLAC → MP3 on a blocking thread, stream chunks back.
        let (tx, rx) = tokio::sync::mpsc::channel::<StreamChunk>(128);
        let path = params.path.clone();

        tokio::task::spawn_blocking(move || {
            crate::cast::transcode::transcode_to_mp3(&path, start_secs, tx);
        });

        let stream = ReceiverStream::new(rx);
        let body = Body::from_stream(stream);
        let mut headers = HeaderMap::new();
        headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
        (StatusCode::OK, headers, body).into_response()
    } else {
        // Serve the MP3 with byte-range support so the Chromecast can resume
        // a paused stream without restarting from byte 0.
        match tokio::fs::read(&params.path).await {
            Ok(data) => {
                let total = data.len();

                // Check for Range request
                let range_start = req_headers
                    .get("range")
                    .and_then(|v| v.to_str().ok())
                    .and_then(|r| parse_range_start(r, total));

                let mut headers = HeaderMap::new();
                headers.insert("Content-Type", "audio/mpeg".parse().unwrap());
                headers.insert("Accept-Ranges", "bytes".parse().unwrap());

                if let Some(start) = range_start {
                    let end = total - 1;
                    let body = data[start..].to_vec();
                    headers.insert(
                        "Content-Range",
                        format!("bytes {start}-{end}/{total}").parse().unwrap(),
                    );
                    headers.insert("Content-Length", body.len().to_string().parse().unwrap());
                    (StatusCode::PARTIAL_CONTENT, headers, body).into_response()
                } else {
                    headers.insert("Content-Length", total.to_string().parse().unwrap());
                    (StatusCode::OK, headers, data).into_response()
                }
            }
            Err(_) => StatusCode::NOT_FOUND.into_response(),
        }
    }
}

pub struct AudioServerState {
    port: Arc<Mutex<Option<u16>>>,
    allowlist: Arc<Mutex<HashSet<String>>>,
    shutdown_tx: Arc<Mutex<Option<oneshot::Sender<()>>>>,
}

impl AudioServerState {
    pub fn new() -> Self {
        Self {
            port: Arc::new(Mutex::new(None)),
            allowlist: Arc::new(Mutex::new(HashSet::new())),
            shutdown_tx: Arc::new(Mutex::new(None)),
        }
    }

    /// Start the HTTP server if it isn't already running. Returns the bound port.
    pub async fn start_if_needed(&self) -> Result<u16, String> {
        {
            if let Some(p) = *self.port.lock().unwrap() {
                return Ok(p);
            }
        }

        let state = ServerState {
            allowlist: Arc::clone(&self.allowlist),
        };

        let router = Router::new()
            .route("/track", get(serve_track))
            .with_state(state);

        let listener = tokio::net::TcpListener::bind("0.0.0.0:0")
            .await
            .map_err(|e| format!("Failed to bind audio server: {e}"))?;

        let port = listener.local_addr().map_err(|e| e.to_string())?.port();
        *self.port.lock().unwrap() = Some(port);

        let (tx, rx) = oneshot::channel::<()>();
        *self.shutdown_tx.lock().unwrap() = Some(tx);

        tokio::spawn(async move {
            axum::serve(listener, router)
                .with_graceful_shutdown(async {
                    rx.await.ok();
                })
                .await
                .ok();
        });

        Ok(port)
    }

    pub fn add_to_allowlist(&self, path: &str) {
        self.allowlist.lock().unwrap().insert(path.to_string());
    }

    pub fn stop(&self) {
        if let Some(tx) = self.shutdown_tx.lock().unwrap().take() {
            let _ = tx.send(());
        }
        *self.port.lock().unwrap() = None;
        self.allowlist.lock().unwrap().clear();
    }
}

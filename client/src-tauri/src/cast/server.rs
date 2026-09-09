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

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::to_bytes;

    // ── Range parsing ─────────────────────────────────────────────────────
    //
    // The Chromecast resumes a paused stream with a Range request. Getting the
    // offset wrong restarts the track from the beginning, or panics on a slice
    // out of bounds.

    #[test]
    fn reads_the_start_offset_from_a_range_header() {
        assert_eq!(parse_range_start("bytes=100-", 1000), Some(100));
        assert_eq!(parse_range_start("bytes=100-500", 1000), Some(100));
        assert_eq!(parse_range_start("bytes=0-", 1000), Some(0));
    }

    #[test]
    fn rejects_an_offset_at_or_past_the_end() {
        // The handler slices `data[start..]`, so an out-of-range start would
        // panic rather than 416.
        assert_eq!(parse_range_start("bytes=1000-", 1000), None);
        assert_eq!(parse_range_start("bytes=1001-", 1000), None);
        assert_eq!(parse_range_start("bytes=0-", 0), None);
    }

    #[test]
    fn rejects_a_header_it_does_not_understand() {
        assert_eq!(parse_range_start("100-200", 1000), None); // no unit
        assert_eq!(parse_range_start("items=1-2", 1000), None); // wrong unit
        assert_eq!(parse_range_start("bytes=abc-", 1000), None); // not a number
        assert_eq!(parse_range_start("bytes=-500", 1000), None); // suffix range
        assert_eq!(parse_range_start("", 1000), None);
    }

    // ── Allowlist ─────────────────────────────────────────────────────────

    fn state_allowing(paths: &[&str]) -> ServerState {
        let allowlist: HashSet<String> = paths.iter().map(|p| p.to_string()).collect();
        ServerState {
            allowlist: Arc::new(Mutex::new(allowlist)),
        }
    }

    async fn get(state: ServerState, path: &str, range: Option<&str>) -> axum::response::Response {
        let mut headers = HeaderMap::new();
        if let Some(r) = range {
            headers.insert("range", r.parse().unwrap());
        }
        serve_track(
            headers,
            Query(TrackQuery {
                path: path.to_string(),
                start: None,
            }),
            State(state),
        )
        .await
        .into_response()
    }

    /// This server binds 0.0.0.0 so a Chromecast on the LAN can reach it, which
    /// means anything on the network can ask it for a file. The allowlist is
    /// the only thing stopping `?path=/etc/passwd` from being served.
    #[tokio::test]
    async fn refuses_a_path_that_was_never_allowlisted() {
        let response = get(state_allowing(&[]), "/etc/passwd", None).await;
        assert_eq!(response.status(), StatusCode::FORBIDDEN);
    }

    #[tokio::test]
    async fn refuses_a_path_outside_the_allowlist_even_when_it_exists() {
        let dir = tempfile::tempdir().unwrap();
        let secret = dir.path().join("secret.mp3");
        std::fs::write(&secret, b"not yours").unwrap();

        let response = get(
            state_allowing(&["/some/other/track.mp3"]),
            secret.to_str().unwrap(),
            None,
        )
        .await;
        assert_eq!(response.status(), StatusCode::FORBIDDEN);
    }

    #[tokio::test]
    async fn matches_the_allowlist_exactly_rather_than_by_prefix() {
        // "/music/a.mp3" must not authorise "/music/a.mp3.evil" or the parent.
        let state = state_allowing(&["/music/a.mp3"]);
        assert_eq!(
            get(state.clone(), "/music/a.mp3.evil", None).await.status(),
            StatusCode::FORBIDDEN,
        );
        assert_eq!(
            get(state, "/music", None).await.status(),
            StatusCode::FORBIDDEN,
        );
    }

    #[tokio::test]
    async fn serves_an_allowlisted_file_whole() {
        let dir = tempfile::tempdir().unwrap();
        let track = dir.path().join("a.mp3");
        std::fs::write(&track, b"0123456789").unwrap();
        let path = track.to_str().unwrap();

        let response = get(state_allowing(&[path]), path, None).await;
        assert_eq!(response.status(), StatusCode::OK);
        assert_eq!(response.headers()["Content-Length"], "10");
        assert_eq!(response.headers()["Accept-Ranges"], "bytes");

        let body = to_bytes(response.into_body(), usize::MAX).await.unwrap();
        assert_eq!(&body[..], b"0123456789");
    }

    #[tokio::test]
    async fn serves_a_range_request_as_partial_content() {
        let dir = tempfile::tempdir().unwrap();
        let track = dir.path().join("a.mp3");
        std::fs::write(&track, b"0123456789").unwrap();
        let path = track.to_str().unwrap();

        let response = get(state_allowing(&[path]), path, Some("bytes=4-")).await;
        assert_eq!(response.status(), StatusCode::PARTIAL_CONTENT);
        assert_eq!(response.headers()["Content-Range"], "bytes 4-9/10");
        assert_eq!(response.headers()["Content-Length"], "6");

        let body = to_bytes(response.into_body(), usize::MAX).await.unwrap();
        assert_eq!(&body[..], b"456789");
    }

    #[tokio::test]
    async fn falls_back_to_the_whole_file_when_the_range_is_unusable() {
        let dir = tempfile::tempdir().unwrap();
        let track = dir.path().join("a.mp3");
        std::fs::write(&track, b"0123456789").unwrap();
        let path = track.to_str().unwrap();

        // Past the end: serve the whole file rather than panicking on the slice.
        let response = get(state_allowing(&[path]), path, Some("bytes=99-")).await;
        assert_eq!(response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn reports_an_allowlisted_but_missing_file_as_not_found() {
        let response = get(
            state_allowing(&["/definitely/not/here.mp3"]),
            "/definitely/not/here.mp3",
            None,
        )
        .await;
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    #[tokio::test]
    async fn stopping_clears_the_allowlist_and_port() {
        let server = AudioServerState::new();
        let port = server.start_if_needed().await.expect("bind");
        assert!(port > 0);
        // A second call reuses the running server rather than binding again.
        assert_eq!(server.start_if_needed().await.expect("reuse"), port);

        server.add_to_allowlist("/music/a.mp3");
        server.stop();

        assert!(server.port.lock().unwrap().is_none());
        assert!(server.allowlist.lock().unwrap().is_empty());
    }
}

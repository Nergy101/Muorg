mod helpers;

use helpers::TestServer;

// ---------------------------------------------------------------------------
// Health endpoint (public)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn health_returns_200() {
    let srv = TestServer::start().await;
    let resp = srv.get("/api/health").await;
    assert_eq!(resp.status(), 200);
    let body = resp.text().await.unwrap();
    assert_eq!(body, "Healthy");
}

// The desktop app compares this header with its own version to spot a sidecar
// left over from an older release; without it, every sidecar looks outdated.
#[tokio::test]
async fn health_reports_the_server_version_in_a_header() {
    let srv = TestServer::start().await;
    let resp = srv.get("/api/health").await;
    assert_eq!(
        resp.headers().get("x-muorg-version").and_then(|v| v.to_str().ok()),
        Some(env!("CARGO_PKG_VERSION"))
    );
}

// ---------------------------------------------------------------------------
// Auth: protected endpoint without credentials
// ---------------------------------------------------------------------------

#[tokio::test]
async fn tracks_without_auth_returns_401() {
    let srv = TestServer::start().await;
    let resp = srv.get("/api/tracks").await;
    assert_eq!(resp.status(), 401);
}

// ---------------------------------------------------------------------------
// Auth: protected endpoint with valid API key
// ---------------------------------------------------------------------------

#[tokio::test]
async fn tracks_with_valid_key_returns_200() {
    let srv = TestServer::start().await;
    let resp = srv.get_auth("/api/tracks").await;
    assert_eq!(resp.status(), 200);
    // With a fresh DB the track list should be an empty JSON array.
    let body: serde_json::Value = resp.json().await.unwrap();
    assert_eq!(body, serde_json::json!([]), "expected empty track list");
}

// ---------------------------------------------------------------------------
// CORS: OPTIONS preflight requests
// ---------------------------------------------------------------------------

#[tokio::test]
async fn cors_permissive_origin_allowed() {
    let srv = TestServer::start().await;
    let resp = srv.options("/api/health", "http://example.com").await;
    assert_eq!(resp.status(), 200, "OPTIONS on public endpoint should succeed");

    // When CORS is permissive ("*") we still get Access-Control-Allow-Origin: *
    let allow_origin = resp
        .headers()
        .get("access-control-allow-origin")
        .and_then(|v| v.to_str().ok());
    assert_eq!(allow_origin, Some("*"), "expected permissive CORS on health");
}

#[tokio::test]
async fn cors_protected_route_with_origin() {
    let srv = TestServer::start().await;
    let resp = srv.options("/api/tracks", "http://localhost:5173").await;
    assert_eq!(resp.status(), 200, "OPTIONS on protected route should succeed");

    let allow_origin = resp
        .headers()
        .get("access-control-allow-origin")
        .and_then(|v| v.to_str().ok());
    assert_eq!(allow_origin, Some("*"), "expected permissive CORS on protected route");
}

// ---------------------------------------------------------------------------
// 404: non-existent route
// ---------------------------------------------------------------------------

#[tokio::test]
async fn nonexistent_route_returns_404() {
    let srv = TestServer::start().await;
    let resp = srv.get("/api/nonexistent").await;
    assert_eq!(resp.status(), 404);
}

// ---------------------------------------------------------------------------
// Additional: verify that the root path returns HTML (public)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn root_returns_html() {
    let srv = TestServer::start().await;
    let resp = srv.get("/").await;
    assert_eq!(resp.status(), 200);
    let content_type = resp
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    assert!(content_type.starts_with("text/html"), "expected HTML content");
}

// ---------------------------------------------------------------------------
// Auth: the shapes a wrong credential can take
// ---------------------------------------------------------------------------

#[tokio::test]
async fn a_wrong_key_of_the_right_length_is_rejected() {
    // The comparison is constant-time and hand-rolled; this is the case a
    // naive length check alone would let through.
    let srv = TestServer::start().await;
    let resp = srv.get_with_key("/api/tracks", "Bearer test-key-124").await;
    assert_eq!(resp.status(), 401);
}

#[tokio::test]
async fn a_key_without_the_bearer_prefix_is_rejected() {
    let srv = TestServer::start().await;
    let resp = srv.get_with_key("/api/tracks", "test-key-123").await;
    assert_eq!(resp.status(), 401, "the raw key is not a credential");
}

#[tokio::test]
async fn the_bearer_prefix_is_case_sensitive() {
    let srv = TestServer::start().await;
    let resp = srv.get_with_key("/api/tracks", "bearer test-key-123").await;
    assert_eq!(resp.status(), 401);
}

#[tokio::test]
async fn padding_around_the_credential_behaves_predictably() {
    let srv = TestServer::start().await;
    // Trailing whitespace never reaches the comparison: HTTP header values
    // carry optional trailing whitespace and hyper strips it per RFC 9110, so
    // a client that pads the key still authenticates.
    assert_eq!(
        srv.get_with_key("/api/tracks", "Bearer test-key-123 ").await.status(),
        200
    );
    // Extra internal space does reach it, because the prefix is stripped
    // literally — the remainder is " test-key-123", which is a different key.
    assert_eq!(
        srv.get_with_key("/api/tracks", "Bearer  test-key-123").await.status(),
        401
    );
}

#[tokio::test]
async fn an_empty_or_prefix_only_credential_is_rejected() {
    let srv = TestServer::start().await;
    assert_eq!(srv.get_with_key("/api/tracks", "Bearer ").await.status(), 401);
    assert_eq!(srv.get_with_key("/api/tracks", "").await.status(), 401);
}

#[tokio::test]
async fn a_truncated_key_is_rejected() {
    let srv = TestServer::start().await;
    let resp = srv.get_with_key("/api/tracks", "Bearer test-key-12").await;
    assert_eq!(resp.status(), 401);
}

#[tokio::test]
async fn every_mutating_route_is_behind_the_api_key() {
    // A route added to the router but outside the auth layer would be an open
    // write endpoint, and nothing else would notice.
    let srv = TestServer::start().await;
    for path in [
        "/api/admin/rescan",
        "/api/admin/remove-folder",
        "/api/admin/clear-cache",
        "/api/admin/purge-deleted",
        "/api/playlists",
        "/api/tracks/metadata/batch",
        "/api/fetch-image",
        "/api/cast/play",
    ] {
        let resp = srv.post(path, serde_json::json!({})).await;
        assert_eq!(resp.status(), 401, "{path} should require the API key");
    }
}

#[tokio::test]
async fn the_health_probe_and_stream_stay_public() {
    // The health check is hit by Docker and the stream carries its own
    // short-lived token, so neither takes the API key.
    let srv = TestServer::start().await;
    assert_eq!(srv.get("/api/health").await.status(), 200);
    // No token, so this is a 401 from the token check — not from the API key
    // middleware, which would have rejected it before reaching the handler.
    assert_eq!(srv.get("/stream/1").await.status(), 401);
}

// ---------------------------------------------------------------------------
// Stream tokens
// ---------------------------------------------------------------------------

#[tokio::test]
async fn a_stream_token_cannot_be_minted_for_a_track_that_does_not_exist() {
    let srv = TestServer::start().await;
    let resp = srv.get_auth("/api/tracks/999/stream-token").await;
    assert_eq!(resp.status(), 404);
}

#[tokio::test]
async fn a_forged_stream_token_is_rejected() {
    let srv = TestServer::start().await;
    let resp = srv
        .get("/stream/1?token=00000000-0000-0000-0000-000000000000")
        .await;
    assert_eq!(resp.status(), 401);
}

// ---------------------------------------------------------------------------
// fetch-image: the SSRF guards, end to end through the router
// ---------------------------------------------------------------------------

/// Every rejection the guard can produce, asserted against the live route so
/// the wiring is covered too — `urlguard`'s own tests cover the logic.
#[tokio::test]
async fn fetch_image_refuses_to_reach_inside_the_network() {
    let srv = TestServer::start().await;
    for url in [
        "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
        "http://127.0.0.1:7700/api/admin/metrics",
        "http://localhost/",
        "http://192.168.1.1/",
        "http://[::1]/",
        "http://10.0.0.5:8080/",
    ] {
        let resp = srv.post_auth("/api/fetch-image", serde_json::json!({ "url": url })).await;
        assert_eq!(resp.status(), 400, "{url} should be refused");
    }
}

#[tokio::test]
async fn fetch_image_refuses_a_host_outside_the_allowlist() {
    let srv = TestServer::start().await;
    let resp = srv
        .post_auth("/api/fetch-image", serde_json::json!({ "url": "https://example.com/a.jpg" }))
        .await;
    assert_eq!(resp.status(), 400);
    let body: serde_json::Value = resp.json().await.unwrap();
    assert!(
        body["error"].as_str().unwrap_or_default().contains("allowlist"),
        "the error should say why: {body}"
    );
}

#[tokio::test]
async fn fetch_image_refuses_non_http_schemes() {
    let srv = TestServer::start().await;
    for url in ["file:///etc/passwd", "gopher://example.com/", "ftp://example.com/a.jpg"] {
        let resp = srv.post_auth("/api/fetch-image", serde_json::json!({ "url": url })).await;
        assert_eq!(resp.status(), 400, "{url} should be refused");
    }
}

#[tokio::test]
async fn fetch_image_rejects_a_malformed_body_without_panicking() {
    let srv = TestServer::start().await;
    // Missing field, wrong type, and outright garbage in the url field.
    assert_eq!(
        srv.post_auth("/api/fetch-image", serde_json::json!({})).await.status(),
        422
    );
    assert_eq!(
        srv.post_auth("/api/fetch-image", serde_json::json!({ "url": 42 })).await.status(),
        422
    );
    assert_eq!(
        srv.post_auth("/api/fetch-image", serde_json::json!({ "url": "not a url" })).await.status(),
        400
    );
}

// ---------------------------------------------------------------------------
// Library routes against an empty catalog
// ---------------------------------------------------------------------------

#[tokio::test]
async fn an_empty_library_reports_zeroes_rather_than_erroring() {
    let srv = TestServer::start().await;
    let stats: serde_json::Value = srv.get_auth("/api/stats").await.json().await.unwrap();
    assert_eq!(stats["track_count"], 0);
    assert_eq!(stats["total_duration_secs"], 0);

    let count: serde_json::Value = srv.get_auth("/api/tracks/count").await.json().await.unwrap();
    assert_eq!(count["count"], 0);
}

#[tokio::test]
async fn asking_for_a_track_that_does_not_exist_is_a_404() {
    let srv = TestServer::start().await;
    for path in [
        "/api/tracks/999/metadata",
        "/api/tracks/999/cover",
        "/api/tracks/999/stream-token",
    ] {
        let status = srv.get_auth(path).await.status();
        assert_eq!(status, 404, "{path} returned {status}");
    }
}

#[tokio::test]
async fn a_playlist_survives_a_round_trip_through_the_api() {
    let srv = TestServer::start().await;
    let created: serde_json::Value = srv
        .post_auth("/api/playlists", serde_json::json!({ "name": "Test Mix" }))
        .await
        .json()
        .await
        .unwrap();
    assert_eq!(created["name"], "Test Mix");

    let listed: serde_json::Value = srv.get_auth("/api/playlists").await.json().await.unwrap();
    assert_eq!(listed.as_array().map(Vec::len), Some(1));
    assert_eq!(listed[0]["id"], created["id"]);
}

// ---------------------------------------------------------------------------
// Health: "Scanning" while the startup scan runs (Q14)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn health_reports_scanning_while_the_startup_scan_runs() {
    let srv = TestServer::start_with(|state| {
        state
            .scanning
            .store(true, std::sync::atomic::Ordering::SeqCst);
    })
    .await;
    let resp = srv.get("/api/health").await;
    assert_eq!(resp.status(), 200);
    assert_eq!(resp.text().await.unwrap(), "Scanning");
}

// ---------------------------------------------------------------------------
// Playlist pins (Q24)
// ---------------------------------------------------------------------------

/// Create a playlist through the API and return its id.
async fn create_playlist(srv: &TestServer, name: &str) -> i64 {
    let created: serde_json::Value = srv
        .post_auth("/api/playlists", serde_json::json!({ "name": name }))
        .await
        .json()
        .await
        .unwrap();
    created["id"].as_i64().unwrap()
}

/// The pinned playlist ids, in pin order.
async fn get_pins(srv: &TestServer) -> Vec<i64> {
    let body: serde_json::Value = srv
        .get_auth("/api/playlists/pins")
        .await
        .json()
        .await
        .unwrap();
    body["ids"]
        .as_array()
        .unwrap()
        .iter()
        .map(|v| v.as_i64().unwrap())
        .collect()
}

#[tokio::test]
async fn playlist_pins_round_trip_in_order() {
    let srv = TestServer::start().await;
    let a = create_playlist(&srv, "A").await;
    let b = create_playlist(&srv, "B").await;
    let c = create_playlist(&srv, "C").await;

    assert_eq!(get_pins(&srv).await, Vec::<i64>::new(), "no pins on a fresh server");

    // Pin out of creation order; the order given is the order stored.
    let resp = srv
        .put_auth("/api/playlists/pins", serde_json::json!({ "ids": [c, a] }))
        .await;
    assert_eq!(resp.status(), 200);
    assert_eq!(get_pins(&srv).await, vec![c, a]);

    // Replacing the set drops the unlisted pin.
    let resp = srv
        .put_auth("/api/playlists/pins", serde_json::json!({ "ids": [b] }))
        .await;
    assert_eq!(resp.status(), 200);
    assert_eq!(get_pins(&srv).await, vec![b]);
}

#[tokio::test]
async fn pinning_an_unknown_playlist_is_a_400() {
    let srv = TestServer::start().await;
    let resp = srv
        .put_auth("/api/playlists/pins", serde_json::json!({ "ids": [999] }))
        .await;
    assert_eq!(resp.status(), 400);
    // The refusal changes nothing.
    assert_eq!(get_pins(&srv).await, Vec::<i64>::new());
}

#[tokio::test]
async fn deleting_a_playlist_drops_its_pin() {
    let srv = TestServer::start().await;
    let a = create_playlist(&srv, "A").await;
    let b = create_playlist(&srv, "B").await;
    srv.put_auth("/api/playlists/pins", serde_json::json!({ "ids": [a, b] }))
        .await;

    let resp = srv.delete_auth(&format!("/api/playlists/{a}")).await;
    assert_eq!(resp.status(), 200);
    assert_eq!(
        get_pins(&srv).await,
        vec![b],
        "the pin cascades away with the playlist"
    );
}

// ---------------------------------------------------------------------------
// Favorites playlist protection (Q21)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn the_favorites_playlist_cannot_be_deleted_or_renamed() {
    let srv = TestServer::start().await;
    // `ensure_favorites_playlist` runs at startup in main; the test server
    // does not run main, so create it the same way the startup does.
    let favorites = create_playlist(&srv, "Favorites").await;
    let other = create_playlist(&srv, "Other").await;

    let resp = srv.delete_auth(&format!("/api/playlists/{favorites}")).await;
    assert_eq!(resp.status(), 403);
    let body: serde_json::Value = resp.json().await.unwrap();
    assert!(body["error"].as_str().unwrap().contains("Favorites"));

    let resp = srv
        .patch_auth(
            &format!("/api/playlists/{favorites}"),
            serde_json::json!({ "name": "Liked" }),
        )
        .await;
    assert_eq!(resp.status(), 403);

    // Icon changes stay allowed.
    let resp = srv
        .patch_auth(
            &format!("/api/playlists/{favorites}"),
            serde_json::json!({ "icon": "heart" }),
        )
        .await;
    assert_eq!(resp.status(), 200);

    // Other playlists are unaffected.
    let resp = srv
        .patch_auth(
            &format!("/api/playlists/{other}"),
            serde_json::json!({ "name": "Renamed" }),
        )
        .await;
    assert_eq!(resp.status(), 200);
    let resp = srv.delete_auth(&format!("/api/playlists/{other}")).await;
    assert_eq!(resp.status(), 200);

    // The Favorites playlist survived, still under its exact name.
    let listed: serde_json::Value = srv.get_auth("/api/playlists").await.json().await.unwrap();
    let names: Vec<&str> = listed
        .as_array()
        .unwrap()
        .iter()
        .map(|p| p["name"].as_str().unwrap())
        .collect();
    assert_eq!(names, vec!["Favorites"]);
}

// ---------------------------------------------------------------------------
// Rate limit: Retry-After on 429 (Q16)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn a_429_carries_retry_after() {
    let srv = TestServer::start_with(|state| {
        state.rate_limiter = muorg_server::ratelimit::RateLimiter::new(2, 60);
    })
    .await;
    // Two requests fit the budget. Bodies are read so the pooled connection
    // is reused — the limiter keys on the client's socket address.
    for _ in 0..2 {
        let resp = srv.get_auth("/api/tracks").await;
        assert_eq!(resp.status(), 200);
        resp.text().await.unwrap();
    }
    let resp = srv.get_auth("/api/tracks").await;
    assert_eq!(resp.status(), 429);
    let retry_after = resp
        .headers()
        .get("retry-after")
        .and_then(|v| v.to_str().ok())
        .map(str::to_owned);
    let secs: u64 = retry_after
        .expect("a 429 must carry Retry-After")
        .parse()
        .expect("Retry-After is a number of seconds");
    assert!((1..=60).contains(&secs), "got {secs} seconds");
}

// ---------------------------------------------------------------------------
// Play history: optional device name (Q19)
// ---------------------------------------------------------------------------

/// Insert a root and one track directly into the catalog, returning the track id.
fn seed_track(srv: &TestServer, path: &str) -> i64 {
    let conn = srv.catalog.db.lock().unwrap();
    conn.execute("INSERT OR IGNORE INTO roots (path, added_at) VALUES ('/music', 0)", [])
        .unwrap();
    let root_id: i64 = conn
        .query_row("SELECT id FROM roots WHERE path = '/music'", [], |r| r.get(0))
        .unwrap();
    conn.execute(
        "INSERT INTO tracks (root_id, path, format, mtime_secs) VALUES (?1, ?2, 'mp3', 0)",
        rusqlite::params![root_id, path],
    )
    .unwrap();
    conn.query_row("SELECT id FROM tracks WHERE path = ?1", [path], |r| r.get(0))
        .unwrap()
}

#[tokio::test]
async fn recording_a_play_stores_the_optional_device_name() {
    let srv = TestServer::start().await;
    let track_id = seed_track(&srv, "/music/song.mp3");

    // No body at all keeps working.
    let resp = srv
        .post_auth_empty(&format!("/api/tracks/{track_id}/play"))
        .await;
    assert_eq!(resp.status(), 200);
    // An empty body works too.
    let resp = srv
        .post_auth(&format!("/api/tracks/{track_id}/play"), serde_json::json!({}))
        .await;
    assert_eq!(resp.status(), 200);
    // A body with a device name records it.
    let resp = srv
        .post_auth(
            &format!("/api/tracks/{track_id}/play"),
            serde_json::json!({ "device_name": "Living room" }),
        )
        .await;
    assert_eq!(resp.status(), 200);

    let conn = srv.catalog.db.lock().unwrap();
    let device_names: Vec<Option<String>> = conn
        .prepare("SELECT device_name FROM play_history ORDER BY id")
        .unwrap()
        .query_map([], |r| r.get(0))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert_eq!(device_names, vec![None, None, Some("Living room".to_string())]);
}

// ---------------------------------------------------------------------------
// Cast: session id takeover signal (Q15)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn cast_play_returns_a_session_id_that_status_reports() {
    let srv = TestServer::start().await;
    let track_id = seed_track(&srv, "/music/song.mp3");

    let resp = srv
        .post_auth(
            "/api/cast/play",
            serde_json::json!({
                "device_address": "192.0.2.1",
                "device_port": 8009,
                "track_id": track_id,
            }),
        )
        .await;
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    let session_id = body["session_id"]
        .as_str()
        .expect("play returns a session id");
    assert!(!session_id.is_empty());

    let status: serde_json::Value = srv.get_auth("/api/cast/status").await.json().await.unwrap();
    assert_eq!(
        status["session_id"], session_id,
        "status reports the session the play started"
    );
}

// ---------------------------------------------------------------------------
// Cast: stream listener for loopback-bound servers (the desktop sidecar)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn a_loopback_bound_server_gets_a_stream_only_lan_listener() {
    // The desktop app's sidecar binds 127.0.0.1, so the Chromecast cannot
    // fetch /stream from the API port. The server must expose the
    // token-protected stream route on a second, all-interfaces listener —
    // and nothing else.
    let srv = TestServer::start().await;

    // A real file so the stream route can serve bytes.
    let dir = tempfile::tempdir().unwrap();
    let song = dir.path().join("song.mp3");
    std::fs::write(&song, b"fake mp3 bytes").unwrap();
    let track_id = seed_track(&srv, song.to_str().unwrap());

    let resp = srv
        .get_auth(&format!("/api/tracks/{track_id}/stream-token"))
        .await;
    assert_eq!(resp.status(), 200);
    let token: serde_json::Value = resp.json().await.unwrap();
    let token = token["token"].as_str().unwrap().to_string();

    // The main listener is loopback, so a stream listener is bound.
    let listener = muorg_server::bind_stream_listener_if_loopback(srv.addr)
        .await
        .expect("a loopback-bound server gets a stream listener");
    let stream_addr = listener.local_addr().unwrap();
    let app = muorg_server::build_stream_router(srv.state.clone());
    tokio::spawn(async move {
        axum::serve(listener, app).await.ok();
    });
    tokio::time::sleep(std::time::Duration::from_millis(100)).await;

    // The stream route answers on the second listener, token-protected.
    let resp = srv
        .client
        .get(format!("http://{stream_addr}/stream/{track_id}?token={token}"))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status(), 200);
    let body = resp.bytes().await.unwrap();
    assert_eq!(&body[..], b"fake mp3 bytes");

    // Without a token the stream route refuses.
    let resp = srv
        .client
        .get(format!("http://{stream_addr}/stream/{track_id}"))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status(), 401);

    // The API is not exposed on the stream listener.
    for path in ["/api/health", "/api/tracks", "/api/cast/status"] {
        let resp = srv
            .client
            .get(format!("http://{stream_addr}{path}"))
            .send()
            .await
            .unwrap();
        assert_eq!(
            resp.status(),
            404,
            "{path} must not be served on the stream listener"
        );
    }
}

#[tokio::test]
async fn a_lan_bound_server_needs_no_stream_listener() {
    for addr in ["0.0.0.0:7700", "192.168.1.10:7700"] {
        let addr: std::net::SocketAddr = addr.parse().unwrap();
        assert!(
            muorg_server::bind_stream_listener_if_loopback(addr)
                .await
                .is_none(),
            "{addr} is already LAN-reachable, so no second listener"
        );
    }
}

// ---------------------------------------------------------------------------
// Rename: destination collision check and overwrite (Q2 follow-up)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn rename_check_reports_existing_and_duplicate_destinations() {
    let srv = TestServer::start().await;
    let track_a = seed_track(&srv, "/music/a.mp3");
    let track_b = seed_track(&srv, "/music/b.mp3");
    let track_c = seed_track(&srv, "/music/c.mp3");
    let track_d = seed_track(&srv, "/music/d.mp3");

    // A file already sitting at one of the destinations.
    let dir = tempfile::tempdir().unwrap();
    let taken = dir.path().join("taken.mp3");
    std::fs::write(&taken, b"occupied").unwrap();
    let taken = taken.to_str().unwrap().to_string();

    let resp = srv
        .post_auth(
            "/api/tracks/rename/check",
            serde_json::json!({
                "items": [
                    { "id": track_a, "new_path": taken },
                    { "id": track_b, "new_path": "/music/free.mp3" },
                    { "id": track_c, "new_path": "/music/free.mp3" },
                    { "id": track_d, "new_path": "/music/also-free.mp3" },
                ]
            }),
        )
        .await;
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    let collisions = body["collisions"].as_array().expect("collisions array");

    let reason_for = |id: i64| -> Option<&str> {
        collisions
            .iter()
            .find(|c| c["id"].as_i64() == Some(id))
            .and_then(|c| c["reason"].as_str())
    };
    assert_eq!(reason_for(track_a), Some("exists"));
    assert_eq!(reason_for(track_b), Some("duplicate"));
    assert_eq!(reason_for(track_c), Some("duplicate"));
    assert_eq!(reason_for(track_d), None, "a free destination is not reported");
}

#[tokio::test]
async fn rename_refuses_to_clobber_and_overwrite_replaces() {
    let srv = TestServer::start().await;
    let dir = tempfile::tempdir().unwrap();
    let src = dir.path().join("src.mp3");
    let dst = dir.path().join("dst.mp3");
    std::fs::write(&src, b"new content").unwrap();
    std::fs::write(&dst, b"old content").unwrap();
    let track_id = seed_track(&srv, src.to_str().unwrap());
    let dst_str = dst.to_str().unwrap().to_string();

    // Without overwrite the existing destination is refused and both files survive.
    let resp = srv
        .post_auth(
            &format!("/api/tracks/{track_id}/rename"),
            serde_json::json!({ "new_path": dst_str }),
        )
        .await;
    assert_eq!(resp.status(), 400);
    assert_eq!(std::fs::read(&src).unwrap(), b"new content");
    assert_eq!(std::fs::read(&dst).unwrap(), b"old content");

    // With overwrite the destination is replaced.
    let resp = srv
        .post_auth(
            &format!("/api/tracks/{track_id}/rename"),
            serde_json::json!({ "new_path": dst_str, "overwrite": true }),
        )
        .await;
    assert_eq!(resp.status(), 200);
    assert!(!src.exists(), "the source file moved away");
    assert_eq!(std::fs::read(&dst).unwrap(), b"new content");

    // The catalog row follows the file.
    let conn = srv.catalog.db.lock().unwrap();
    let path: String = conn
        .query_row("SELECT path FROM tracks WHERE id = ?1", [track_id], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(path, dst_str);
}

#[tokio::test]
async fn rename_onto_another_tracks_path_is_refused_even_with_overwrite() {
    let srv = TestServer::start().await;
    let dir = tempfile::tempdir().unwrap();
    let src = dir.path().join("src.mp3");
    let dst = dir.path().join("dst.mp3");
    std::fs::write(&src, b"new content").unwrap();
    std::fs::write(&dst, b"old content").unwrap();
    let track_id = seed_track(&srv, src.to_str().unwrap());
    let other_id = seed_track(&srv, dst.to_str().unwrap());
    let dst_str = dst.to_str().unwrap().to_string();

    // The destination is another track's cataloged path: refused even with
    // overwrite, and neither the files nor the catalog change.
    let resp = srv
        .post_auth(
            &format!("/api/tracks/{track_id}/rename"),
            serde_json::json!({ "new_path": dst_str, "overwrite": true }),
        )
        .await;
    assert_eq!(resp.status(), 400);
    let body: serde_json::Value = resp.json().await.unwrap();
    assert!(
        body["error"].as_str().unwrap().contains(&other_id.to_string()),
        "the error names the track that holds the destination: {body}"
    );
    assert_eq!(std::fs::read(&src).unwrap(), b"new content");
    assert_eq!(std::fs::read(&dst).unwrap(), b"old content");

    let conn = srv.catalog.db.lock().unwrap();
    let paths: Vec<String> = conn
        .prepare("SELECT path FROM tracks ORDER BY id")
        .unwrap()
        .query_map([], |r| r.get(0))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert_eq!(
        paths,
        vec![src.to_str().unwrap().to_string(), dst_str],
        "both catalog rows keep their paths"
    );
}

// ---------------------------------------------------------------------------
// Admin: purge-deleted, and its deprecated alias clear-cache
// ---------------------------------------------------------------------------

/// Soft-delete a seeded track directly, the way `remove_folder` or a missed
/// rescan would, so a purge route has something real to remove.
fn soft_delete(srv: &TestServer, track_id: i64) {
    let conn = srv.catalog.db.lock().unwrap();
    conn.execute("UPDATE tracks SET deleted_at = 1 WHERE id = ?1", [track_id])
        .unwrap();
}

fn track_row_exists(srv: &TestServer, track_id: i64) -> bool {
    let conn = srv.catalog.db.lock().unwrap();
    let count: i64 = conn
        .query_row("SELECT COUNT(*) FROM tracks WHERE id = ?1", [track_id], |r| r.get(0))
        .unwrap();
    count > 0
}

#[tokio::test]
async fn purge_deleted_removes_soft_deleted_rows() {
    let srv = TestServer::start().await;
    let track_id = seed_track(&srv, "/music/gone.mp3");
    soft_delete(&srv, track_id);

    let resp = srv.post_auth_empty("/api/admin/purge-deleted").await;
    assert_eq!(resp.status(), 200);
    assert!(!track_row_exists(&srv, track_id), "the soft-deleted row should be gone");
}

#[tokio::test]
async fn clear_cache_is_a_working_deprecated_alias_of_purge_deleted() {
    let srv = TestServer::start().await;
    let track_id = seed_track(&srv, "/music/also-gone.mp3");
    soft_delete(&srv, track_id);

    let resp = srv.post_auth_empty("/api/admin/clear-cache").await;
    assert_eq!(resp.status(), 200);
    assert!(!track_row_exists(&srv, track_id), "the alias purges exactly like the real route");
}

#[tokio::test]
async fn purge_deleted_leaves_live_tracks_alone() {
    let srv = TestServer::start().await;
    let track_id = seed_track(&srv, "/music/still-here.mp3");

    let resp = srv.post_auth_empty("/api/admin/purge-deleted").await;
    assert_eq!(resp.status(), 200);
    assert!(track_row_exists(&srv, track_id), "a live track must survive a purge");
}

// ---------------------------------------------------------------------------
// Admin: browse (server-side folder picker for "Add folder")
// ---------------------------------------------------------------------------

use muorg_server::state::BrowseConfig;

#[tokio::test]
async fn browse_requires_auth() {
    let srv = TestServer::start().await;
    assert_eq!(srv.get("/api/admin/browse").await.status(), 401);
}

#[tokio::test]
async fn browse_with_no_configured_roots_permits_nothing() {
    let srv = TestServer::start().await;
    let resp = srv.get_auth("/api/admin/browse").await;
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    assert!(body["path"].is_null());
    assert_eq!(body["entries"].as_array().unwrap().len(), 0);
}

#[tokio::test]
async fn browse_with_no_path_lists_the_permitted_starting_points() {
    let dir = tempfile::tempdir().unwrap();
    let music = dir.path().join("music");
    std::fs::create_dir_all(&music).unwrap();
    let music_str = music.to_str().unwrap().to_string();

    let srv = TestServer::start_with(move |state| {
        state.browse = BrowseConfig { content_paths: vec![music_str.clone()], browse_roots: vec![] };
    })
    .await;

    let resp = srv.get_auth("/api/admin/browse").await;
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    assert!(body["path"].is_null());
    let entries = body["entries"].as_array().unwrap();
    let names: Vec<&str> = entries.iter().filter_map(|e| e["name"].as_str()).collect();
    // Default fallback: the content root's parent (dir) and the root itself.
    assert_eq!(names.len(), 2, "{names:?}");
    assert!(names.contains(&"music"), "{names:?}");
    let music_entry = entries.iter().find(|e| e["name"] == "music").unwrap();
    assert_eq!(music_entry["is_root"], true, "the content root itself should be flagged: {body}");
}

#[tokio::test]
async fn browse_explicit_roots_override_the_default_fallback() {
    let allowed = tempfile::tempdir().unwrap();
    let unrelated_content = tempfile::tempdir().unwrap();
    let allowed_str = allowed.path().to_str().unwrap().to_string();
    let unrelated_str = unrelated_content.path().to_str().unwrap().to_string();

    let srv = TestServer::start_with(move |state| {
        state.browse = BrowseConfig {
            content_paths: vec![format!("{unrelated_str}/music")],
            browse_roots: vec![allowed_str.clone()],
        };
    })
    .await;

    let resp = srv.get_auth("/api/admin/browse").await;
    let body: serde_json::Value = resp.json().await.unwrap();
    let entries = body["entries"].as_array().unwrap();
    assert_eq!(entries.len(), 1, "only the explicit browse_roots entry: {body}");
    assert_eq!(entries[0]["path"], allowed.path().canonicalize().unwrap().to_str().unwrap());
}

#[tokio::test]
async fn browse_lists_subdirectories_only_sorted_and_flags_a_content_root() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::create_dir_all(dir.path().join("rock")).unwrap();
    std::fs::create_dir_all(dir.path().join("jazz")).unwrap();
    std::fs::write(dir.path().join("not-a-dir.txt"), b"nope").unwrap();
    let root_str = dir.path().to_str().unwrap().to_string();
    let rock_str = dir.path().join("rock").to_str().unwrap().to_string();

    let browse_root_str = root_str.clone();
    let srv = TestServer::start_with(move |state| {
        state.browse = BrowseConfig {
            content_paths: vec![rock_str.clone()],
            browse_roots: vec![browse_root_str.clone()],
        };
    })
    .await;

    let resp = srv.get_auth(&format!("/api/admin/browse?path={root_str}")).await;
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    let entries = body["entries"].as_array().unwrap();
    let names: Vec<&str> = entries.iter().filter_map(|e| e["name"].as_str()).collect();
    assert_eq!(names, vec!["jazz", "rock"], "sorted, directories only, no file: {names:?}");
    assert_eq!(
        entries.iter().find(|e| e["name"] == "rock").unwrap()["is_root"],
        true,
        "rock is a configured content root: {body}"
    );
    assert_eq!(
        entries.iter().find(|e| e["name"] == "jazz").unwrap()["is_root"],
        false,
    );
    assert_eq!(body["truncated"], false);
}

#[tokio::test]
async fn browse_rejects_a_path_outside_the_allowed_roots() {
    let allowed = tempfile::tempdir().unwrap();
    let outside = tempfile::tempdir().unwrap();
    let allowed_str = allowed.path().to_str().unwrap().to_string();
    let outside_str = outside.path().to_str().unwrap().to_string();

    let srv = TestServer::start_with(move |state| {
        state.browse = BrowseConfig { content_paths: vec![], browse_roots: vec![allowed_str.clone()] };
    })
    .await;

    let resp = srv.get_auth(&format!("/api/admin/browse?path={outside_str}")).await;
    assert_eq!(resp.status(), 403);
}

#[tokio::test]
async fn browse_rejects_traversal_back_out_of_the_only_allowed_root() {
    let dir = tempfile::tempdir().unwrap();
    let allowed = dir.path().join("allowed");
    std::fs::create_dir_all(&allowed).unwrap();
    let allowed_str = allowed.to_str().unwrap().to_string();

    let browse_allowed_str = allowed_str.clone();
    let srv = TestServer::start_with(move |state| {
        state.browse = BrowseConfig { content_paths: vec![], browse_roots: vec![browse_allowed_str.clone()] };
    })
    .await;

    // `..` canonicalizes straight back out to `dir`, which is not allowed.
    let traversal = format!("{allowed_str}/..");
    let resp = srv.get_auth(&format!("/api/admin/browse?path={traversal}")).await;
    assert_eq!(resp.status(), 403);
}

#[tokio::test]
async fn browse_a_missing_path_is_404() {
    let allowed = tempfile::tempdir().unwrap();
    let allowed_str = allowed.path().to_str().unwrap().to_string();
    let missing = format!("{allowed_str}/does-not-exist");

    let srv = TestServer::start_with(move |state| {
        state.browse = BrowseConfig { content_paths: vec![], browse_roots: vec![allowed_str.clone()] };
    })
    .await;

    let resp = srv.get_auth(&format!("/api/admin/browse?path={missing}")).await;
    assert_eq!(resp.status(), 404);
}

#[tokio::test]
async fn browse_reports_truncation_past_the_entry_cap() {
    let dir = tempfile::tempdir().unwrap();
    for i in 0..505 {
        std::fs::create_dir_all(dir.path().join(format!("d{i:04}"))).unwrap();
    }
    let root_str = dir.path().to_str().unwrap().to_string();

    let srv = TestServer::start_with(move |state| {
        state.browse = BrowseConfig { content_paths: vec![], browse_roots: vec![root_str.clone()] };
    })
    .await;

    let resp = srv.get_auth(&format!("/api/admin/browse?path={}", dir.path().to_str().unwrap())).await;
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    assert_eq!(body["entries"].as_array().unwrap().len(), 500);
    assert_eq!(body["truncated"], true);
}

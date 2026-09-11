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

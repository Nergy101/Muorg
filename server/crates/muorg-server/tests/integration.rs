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

//! Hand-written OpenAPI 3.0 document describing the Muorg HTTP API.
//!
//! Rather than annotating every handler with `#[utoipa::path]` (heavy, and the
//! route layer's dynamic auth middleware complicates schema generation), the
//! spec is authored here as a `serde_json` value covering the public surface:
//! the core catalog, cover/lyrics/stream, and playlist endpoints. It is served
//! as JSON at `/api/openapi.json` and browsed through Swagger UI at
//! `/api/docs`. Keep it in sync when routes change.

use serde_json::{json, Value};

fn path(
    summary: &str,
    params: &[Value],
    responses: &[(&str, &str)], // (status, description)
    security: bool,
    request_body: Option<Value>,
) -> Value {
    let mut op = json!({
        "summary": summary,
        "parameters": params,
        "responses": {
            "200": {
                "description": "OK",
                "content": {"application/json": {"schema": {}}}
            },
            "400": {"description": "Bad request"},
            "401": {"description": "Unauthorized"},
            "404": {"description": "Not found"}
        }
    });
    if security {
        op["security"] = json!([{"BearerAuth": []}]);
    }
    if let Some(rb) = request_body {
        op["requestBody"] = json!({
            "required": true,
            "content": {"application/json": {"schema": rb}}
        });
    }
    for (status, desc) in responses {
        let key = format!("{}00", status);
        op["responses"][key] = json!({"description": desc});
    }
    json!(op)
}

/// Returns the OpenAPI document.
pub fn spec() -> Value {
    let id = json!({"name": "id", "in": "path", "required": true, "schema": {"type": "integer"}});
    let limit = json!({"name": "limit", "in": "query", "required": false, "schema": {"type": "integer"}});
    let offset = json!({"name": "offset", "in": "query", "required": false, "schema": {"type": "integer"}});
    let q = json!({"name": "q", "in": "query", "required": false, "schema": {"type": "string"}});
    let size = json!({"name": "size", "in": "query", "required": false, "schema": {"type": "integer", "minimum": 16, "description": "Max cover edge length in px (downscaled JPEG)"}});

    let mut paths = serde_json::Map::new();
    let mut put = |p: &str, v: Value| { paths.insert(p.to_string(), v); };

    // Public (no auth)
    put("/api/health", json!({
        "get": path("Server health check", &[], &[("2", "Healthy")], false, None)
    }));
    put("/stream/{id}", json!({
        "get": path("Stream a track by id (public stream URL)", &[id.clone()], &[("2", "Audio bytes")], false, None)
    }));

    // Catalog
    put("/api/tracks", json!({
        "get": path("List tracks (paginated)", &[offset.clone(), limit.clone()], &[("2", "Array of tracks")], true, None)
    }));
    put("/api/tracks/count", json!({
        "get": path("Total track count", &[], &[("2", "Count")], true, None)
    }));
    put("/api/tracks/recently-added", json!({
        "get": path("Recently added tracks", &[limit.clone()], &[("2", "Array of tracks")], true, None)
    }));
    put("/api/search", json!({
        "get": path("Search tracks", &[q.clone(), limit.clone()], &[("2", "Array of matching tracks")], true, None)
    }));
    put("/api/stats", json!({
        "get": path("Library statistics", &[], &[("2", "Stats object")], true, None)
    }));
    put("/api/play-history/recent", json!({
        "get": path("Recently played tracks", &[limit.clone()], &[("2", "Array of tracks")], true, None)
    }));
    put("/api/play-history/top", json!({
        "get": path("Most played tracks", &[limit.clone()], &[("2", "Array of tracks")], true, None)
    }));

    // Per-track resources
    put("/api/tracks/{id}/cover", json!({
        "get": path("Album cover art", &[id.clone(), size.clone()], &[("2", "Image bytes")], true, None)
    }));
    put("/api/tracks/{id}/lyrics", json!({
        "get": path("Embedded lyrics (sync_format: lrc|plain)", &[id.clone()], &[("2", "Lyrics object"), ("4", "No lyrics for this track")], true, None)
    }));
    put("/api/tracks/{id}/metadata", json!({
        "get": path("Track metadata", &[id.clone()], &[("2", "Metadata object")], true, None),
        "patch": path("Update track metadata", &[id.clone()], &[("2", "Updated metadata")], true, Some(json!({"type": "object"})))
    }));
    put("/api/tracks/{id}/rating", json!({
        "post": path("Set track rating", &[id.clone()], &[("2", "OK")], true, Some(json!({"type": "object"})))
    }));
    put("/api/tracks/{id}/play", json!({
        "post": path("Record a play", &[id.clone()], &[("2", "OK")], true, None)
    }));
    put("/api/tracks/{id}/stream-token", json!({
        "get": path("Issue a short-lived stream token", &[id.clone()], &[("2", "Token")], true, None)
    }));

    // Playlists
    put("/api/playlists", json!({
        "get": path("List playlists", &[], &[("2", "Array of playlists")], true, None),
        "post": path("Create a playlist", &[], &[("2", "Created playlist")], true, Some(json!({"type": "object"})))
    }));
    put("/api/playlists/{id}", json!({
        "patch": path("Rename a playlist", &[id.clone()], &[("2", "OK")], true, Some(json!({"type": "object"}))),
        "delete": path("Delete a playlist", &[id.clone()], &[("2", "OK")], true, None)
    }));
    put("/api/playlists/{id}/tracks", json!({
        "get": path("List playlist tracks", &[id.clone()], &[("2", "Array of tracks")], true, None),
        "post": path("Add tracks to playlist", &[id.clone()], &[("2", "OK")], true, Some(json!({"type": "array", "items": {"type": "integer"}}))),
        "delete": path("Remove tracks from playlist", &[id.clone()], &[("2", "OK")], true, Some(json!({"type": "array", "items": {"type": "integer"}})))
    }));
    put("/api/playlists/smart", json!({
        "post": path("Create a smart playlist", &[], &[("2", "Created playlist")], true, Some(json!({"type": "object"})))
    }));
    put("/api/playlists/smart/{id}/tracks", json!({
        "get": path("Resolve smart playlist tracks", &[id.clone()], &[("2", "Array of tracks")], true, None)
    }));

    json!({
        "openapi": "3.0.3",
        "info": {
            "title": "Muorg Server API",
            "version": env!("CARGO_PKG_VERSION"),
            "description": "The Muorg music server HTTP API. All endpoints except /api/health and /stream/{id} require an `Authorization: Bearer <api_key>` header."
        },
        "servers": [{"url": "/"}],
        "security": [{"BearerAuth": []}],
        "components": {
            "securitySchemes": {
                "BearerAuth": {"type": "http", "scheme": "bearer"}
            }
        },
        "paths": Value::Object(paths)
    })
}

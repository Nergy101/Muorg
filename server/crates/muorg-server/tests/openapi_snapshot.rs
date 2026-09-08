//! Keeps `server/openapi.json` honest.
//!
//! That file is the input to the generated TypeScript and Kotlin client models,
//! so it has to stay byte-identical to what the running server serves. This test
//! is the thing that notices when someone adds a route or renames a field and
//! forgets to regenerate.
//!
//! To update it after an intentional API change:
//!
//! ```sh
//! ./scripts/generate-api-clients.sh
//! ```
//!
//! or, for the spec alone: `UPDATE_OPENAPI=1 cargo test -p muorg-server --test openapi_snapshot`.

use std::path::PathBuf;

fn snapshot_path() -> PathBuf {
    // crates/muorg-server -> crates -> server
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../..")
        .join("openapi.json")
}

#[test]
fn spec_snapshot_is_current() {
    let generated = muorg_server::routes::openapi::spec_json();
    let path = snapshot_path();

    if std::env::var_os("UPDATE_OPENAPI").is_some() {
        std::fs::write(&path, &generated).expect("write openapi.json");
        return;
    }

    let checked_in = std::fs::read_to_string(&path).unwrap_or_else(|e| {
        panic!(
            "cannot read {}: {e}\nRun ./scripts/generate-api-clients.sh to create it.",
            path.display()
        )
    });

    assert_eq!(
        checked_in,
        generated,
        "\nserver/openapi.json is stale — the routes changed but the spec and the \
         generated clients did not.\nRun ./scripts/generate-api-clients.sh and commit \
         the result.\n"
    );
}

/// Cheap guard against the failure mode this whole contract exists to prevent:
/// a client calling `/api/tracks` once and believing it has the catalog.
#[test]
fn tracks_endpoint_documents_its_pagination() {
    let spec = muorg_server::routes::openapi::spec();
    let get = &spec["paths"]["/api/tracks"]["get"];

    let params = get["parameters"].as_array().expect("query parameters");
    let names: Vec<&str> = params.iter().filter_map(|p| p["name"].as_str()).collect();
    assert!(names.contains(&"offset"), "offset must be documented: {names:?}");
    assert!(names.contains(&"limit"), "limit must be documented: {names:?}");

    assert!(
        get["responses"]["200"]["headers"]
            .get("X-Total-Count")
            .is_some(),
        "X-Total-Count is how a client knows there are more pages; it must be documented"
    );
}

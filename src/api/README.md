# The shared API contract

Three clients talk to one server, and for a long time each of them described that
server in its own words. The same bugs kept showing up three times — most
memorably "only the first 500 tracks load", fixed separately in Android
(`b531618`), the desktop app (`5318938`) and correctly-by-accident in the web
app. Nothing connected the three copies, so nothing could tell you they had
diverged.

This directory is the connection.

## Where the types come from

```
server/crates/muorg-server/src/routes/*.rs      #[utoipa::path] on each handler
server/crates/muorg-core/src/**                 #[derive(ToSchema)] on each wire struct
                    |
                    |  cargo test -p muorg-server --test openapi_snapshot
                    v
server/openapi.json                             checked in; 53 operations, 39 schemas
                    |
        +-----------+------------------------------+
        |                                          |
        v                                          v
src/api/schema.d.ts                  android-client/.../api/schema/ApiSchema.kt
  (openapi-typescript)                  (scripts/generate-kotlin-models.mjs)
```

Regenerate everything with:

```sh
./scripts/generate-api-clients.sh
```

CI runs `--check` on the same script. If a route changes and the generated files
are not regenerated and committed, the build fails — which is the whole point.
The spec cannot describe an endpoint the server does not serve, because it is
built from the handlers themselves.

## What lives here

| File | Generated? | What it is |
|---|---|---|
| `schema.d.ts` | **yes** | Raw `paths` / `components` from the spec. Don't import directly. |
| `types.ts` | no | Readable aliases (`CatalogTrack`, `Playlist`, …) over `schema.d.ts`. |
| `transport.ts` | no | `fetch` plumbing: auth header, error unwrapping, `streamUrl`. |
| `endpoints.ts` | no | One typed function per endpoint, plus the pagination helpers. |

`transport.ts` takes the base URL and API key as callbacks, because the desktop
app switches between a bundled local server and a remote one at runtime while
the web app only ever has the one. That is the only thing the two apps actually
needed to do differently.

## Pagination

`GET /api/tracks` returns **500 rows by default**, and the full catalog size in
`X-Total-Count`. Do not call it directly. Use:

- `api.streamTracks()` — an async generator that yields each page as it lands,
  so the UI can render the first 500 tracks immediately. This is what all three
  clients should do.
- `api.fetchAllTracks()` — the whole thing in one array, for callers that
  genuinely cannot render incrementally.

## Adding an endpoint

1. Write the handler and put `#[utoipa::path(...)]` on it.
2. Add it to the `paths(...)` list in `routes/openapi.rs`.
3. Give it an explicit `operation_id` if the function name is not unique across
   the whole server — duplicate ids silently collapse two endpoints into one in
   every generated client.
4. Run `./scripts/generate-api-clients.sh`.
5. Add the typed wrapper to `endpoints.ts`.
6. Commit the generated files along with the change.

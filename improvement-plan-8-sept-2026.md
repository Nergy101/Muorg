# Muorg improvement plan — 8 September 2026

Findings from a review of the repo at `ba87239` (v2.42.1). The items that were
already acted on are listed at the bottom; everything above them is still open.

---

## Web App (`web-client/`)

### 1. No Cast support at all

The server exposes ten `/api/cast/*` routes. The desktop app and the Android app
both drive them; the web client uses none. This is the single largest feature gap
between the three clients.

The plumbing already exists — `api.castDevices()`, `castPlay()`, `castPause()`,
`castSeek()`, `castSetVolume()` and the rest are in the shared client
(`src/api/endpoints.ts`), typed and ready. What is missing is the UI: a device
picker and a "casting to X" state in `PlayerView.vue` / `MiniPlayer.vue`, plus a
store slice that polls `/api/cast/status` while a session is live.

Worth copying the desktop's shape rather than inventing one:
`client/src/components/playback/CastDevicePicker.vue` and `stores/cast.ts`.

**Size:** medium. **Value:** high — it is the reason people reach for the desktop
app on a machine where the web app would otherwise do.

### 2. No MusicBrainz auto-tagging

`POST /api/tracks/{id}/auto-tag-suggestions` is desktop-only. The web app can
edit metadata but cannot look candidates up. `api.autoTagSuggestions()` is in the
shared client already.

**Size:** small–medium. **Value:** medium.

### 3. Stream tokens travel in the query string

`streamUrl()` builds `/stream/{id}?token=…`. Query strings land in nginx and
reverse-proxy access logs, in browser history, and in `Referer` headers on any
outbound request from the page. The tokens are short-lived (8 h) but that is
still long enough to matter on a shared host.

Options, cheapest first:

- Shorten the TTL sharply for browser playback (minutes, re-issued on demand).
- Move the token into the path — `/stream/{token}/{id}` — so it at least stays
  out of `Referer`.
- Sign a URL with an expiry instead of handing out a bearer-equivalent.

Related: the API key itself sits in `localStorage`, readable by any XSS on the
origin. For a self-hosted single-user app that is a defensible trade, but it is
worth writing down as a deliberate choice rather than leaving it implicit.

**Size:** small. **Value:** medium.

### 4. No lint step

CI runs `pnpm build` and nothing else. There is no ESLint, Prettier or Biome
config anywhere in the repo, for either TypeScript app.

Suggested: one shared flat ESLint config at the repo root with
`typescript-eslint` + `eslint-plugin-vue`, extended by both apps, and a `lint`
script wired into the `client` and `web-client` CI jobs. `stores/player.ts`
(1,102 lines) is the file that would benefit most.

**Size:** small to set up, ongoing to clean up. **Value:** medium.

---

## Android App (`android-client/`)

### 5. Extend the Android unit tests

34 JVM tests now cover `PathPatternMatcher`, `WireMapping` and the
`LibraryRepository` paging loop, and CI runs `./gradlew testDebugUnitTest`.

Still untested and worth doing next:

- `data/local/LocalLibraryScanner.kt` — tag extraction and SAF path handling.
- `data/repository/OfflineDownloadManager.kt` — download, resume and eviction.
- `PathPatternMatcher.decodeLocalPath` — needs `android.net.Uri`, so it wants
  Robolectric or an instrumented test rather than a plain JVM one.
- The ViewModels, which would need a `MainDispatcherRule`.

**Size:** small per area. **Value:** medium — the highest-risk logic is covered.

### 6. Widen the domain model from `Int` to `Long` ids

Android now calls the generated `MuorgApi` and maps the wire types onto its own
models in `WireMapping.kt`, so the contract is load-bearing. One narrowing
remains, deliberately confined to that one file: the server's ids are `i64` and
the app's domain model, Room entities and Compose state are all `Int`, so
`toDomain()` calls `.toInt()`.

Truncation needs a library past 2.1 billion tracks, so this is not urgent. But
if it is ever worth removing, the change is `Int` → `Long` across roughly 50
call sites plus the Room entities, their DAOs and the hand-written migration in
`AppDatabase.kt` — and it wants the tests in §5 landed first.

**Size:** medium, mechanical. **Value:** low — correctness theatre at current
library sizes, but it is the one place the two models still disagree.

### 7. No metadata editing beyond the scan sheet

The generated `MuorgApi` now exposes every route, but the app only calls
`patchMetadata`. Auto-tag suggestions, backup/restore and rename are all sitting
there typed and unused — the UI is what is missing, not the plumbing.

**Size:** medium. **Value:** medium.

### 8. Large screen files

`SettingsScreen.kt` 737, `PlayerScreen.kt` 711, `NavGraph.kt` 711,
`PlaylistsScreen.kt` 703, `LibraryScreen.kt` 646. Worth splitting as each is next
touched, rather than as a dedicated refactor.

### 9. Offline downloads are Android-only

`OfflineDownloadManager` has no counterpart in the web app, which already has a
Workbox service worker and could cache audio the same way. Worth deciding
whether that is a deliberate platform difference or a gap.

---

## Desktop App (`client/`)

### 10. `LibrarySettingsModal.vue` is 2,949 lines

By a wide margin the largest file in the repo; `MetadataEditor.vue` (1,783) is
second. Both are doing settings-panel and form work that would split cleanly
along the tab boundaries already present in the markup.

**Size:** medium. **Value:** medium — mostly maintainability.

### 11. No Rust tests in `src-tauri`

`server/` has three `#[cfg(test)]` modules and CI runs `cargo test` for it. The
Tauri crate has none. Now that the catalog and metadata code has moved to
`muorg-core`, the surface left in `src-tauri` is the Tauri commands and the cast
module — the cast transcode path in particular is worth covering.

### 12. Cast code is still forked between the desktop app and the server

`client/src-tauri/src/cast/` and `server/crates/muorg-server/src/cast/` are
parallel implementations: `session.rs` is 340 lines against 298, with 208 lines
of diff. They are not straight copies — the desktop casts local files through its
own axum server, while the server casts from its own HTTP surface — but the
Chromecast protocol handling and the mDNS discovery in the middle are the same
code twice.

The move is to lift the protocol and discovery layers into `muorg-core` behind a
feature flag (they would pull in `rust_cast`, `mdns-sd` and `tokio`, which the
desktop app already links) and leave only the per-host media-source logic in each
crate. This was left out of the catalog/metadata deduplication because it is a
genuine refactor rather than a mechanical swap.

**Size:** medium. **Value:** medium — ~600 lines, and it is where the next
silent divergence will happen.

---

## Cross-cutting

### 13. Wire the smoke test into CI

`scripts/smoke-api.ts` runs the shared client against a live server and covers
what type-checking cannot — it is what caught the relative base URL, the silent
401 and the plain-text health probe. It is run by hand today
(`pnpm smoke:api`) because it needs a server.

Wiring it into CI means starting `muorg-server` against a fixture library in the
`api-contract` job and running it there. The integration harness for building
that fixture already exists in `server/crates/muorg-server/tests/helpers/`.
Extending it to assert every response validates against its schema in
`server/openapi.json` would close the loop completely.

**Size:** small to wire up, medium to make exhaustive. **Value:** high.

### 14. No component tests

Vitest now covers the shared logic — 144 tests over `lyrics.ts`, `api/`,
`useMixes` and the web client's library store, at 90% lines / 76% branches, run
in CI.

What is still untested is anything with a component tree: the shared
`FeatherIcon`, `MarqueeCell`, `EqualizerBars` and the stats charts, and every
`.vue` file in both apps. That needs `@vue/test-utils` and a jsdom/happy-dom
environment per app, which is a separate setup from the logic suite.

**Size:** medium. **Value:** medium — the logic underneath them is covered.

### 15. Repo has no root README pointer to the API contract

`src/api/README.md` documents the generation pipeline, but the top-level README's
repository-layout section still lists only `client/`, `server/`, `web-client/`
and `scripts/`. It should mention `src/` (shared frontend code, including the
generated API client) and `android-client/`.

**Size:** trivial.

---

## Done in this pass

For the record, so this list is not re-derived later:

- **Shared API contract.** The OpenAPI document is generated from the
  `muorg-server` handlers via `utoipa` (53 operations, 39 component schemas,
  replacing a hand-written spec with 24 paths and no schemas at all). Snapshotted
  to `server/openapi.json` by a `cargo test`, and the TypeScript and Kotlin
  client models are generated from it by `scripts/generate-api-clients.sh`. CI
  fails if any of the three is stale. Two duplicate `operationId`s were found and
  fixed in the process — `get_tracks` and `health` each described two endpoints,
  which would have collapsed them in every generated client.
- **Generated clients in all three apps.** The TypeScript apps call
  `openapi-fetch` typed on the generated `paths`, so a URL, method or body that
  the spec does not describe is a compile error; Android calls `MuorgApi`, a
  generated Retrofit interface with one method per operation, and maps to its
  own models in `WireMapping.kt`. The hand-written `MuorgApiService.kt` and the
  nine hand-copied wire interfaces across the two TS apps are gone.
- **`/api/tracks` pagination has one implementation** in `src/api/endpoints.ts`,
  and Android's loop is the only other copy.
- **Every operation has a meaningful `operationId`.** `list`, `create`, `play`,
  `stop` and friends became `list_playlists`, `create_playlist`, `cast_play`,
  `cast_stop` — they are the generated method names, and one namespace holds all
  53 of them.
- **A live smoke test** (`scripts/smoke-api.ts`, `pnpm smoke:api`) that caught
  three runtime bugs type-checking could not: a base URL that only resolves
  inside a browser, a bare 401 read as an empty library, and a plain-text health
  probe parsed as JSON.
- **178 automated tests where there were 16.** Vitest over the shared code and
  the web client's library store (144 tests, 90% lines / 76% branches on the
  targeted modules) and JVM unit tests on Android (34 tests over the path
  matcher, the wire mapping and the paging loop). Both run in CI, alongside the
  16 Rust tests that already existed.
- **Desktop: catalog streams in.** `loadTracks()` renders the first page
  immediately instead of awaiting all seven round-trips, with a progress badge
  for the rest.
- **Desktop: 2,032 lines of forked Rust deleted.** `src-tauri` now depends on
  `muorg-core` instead of carrying its own copy of `catalog/db.rs` and
  `metadata/read_write.rs`. As a side effect the desktop app picks up the WAL
  journal mode and the `play_history` table the server had and it did not —
  which matters, because both processes open the same SQLite file.
- **Desktop: Mixes, lyrics, now-playing bars.** All three shared with the web
  client rather than reimplemented (`@shared/composables/useMixes`,
  `@shared/lyrics`, `@shared/components/EqualizerBars.vue`).
- **Dependabot no longer proposes TypeScript 7** for either TS app, which had
  broken and been hand-reverted three times.
- **A pre-existing `clippy::while_let_loop` failure** in
  `server/.../transcode.rs` was fixed; it would have failed the server CI job on
  the next change to touch `server/`.

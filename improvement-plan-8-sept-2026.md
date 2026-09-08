# Muorg improvement plan — 8 September 2026

Findings from a review of the repo at `ba87239` (v2.42.1). The items that were
already acted on are listed at the bottom; everything above them is still open.

---

## Web App (`web-client/`)

### 1. ~~No Cast support~~ — done

The web client now drives the same `/api/cast/*` routes the desktop and Android
apps use: `stores/cast.ts` (session state, transport, volume), a
`CastDevicePicker` sheet, a cast button in `PlayerView`, and a "casting to X"
badge in `MiniPlayer`.

One difference from the desktop app worth knowing: it has Tauri events pushing
status, a browser does not, so this polls `/api/cast/status` every second while
a session is live and stops the moment it is not — on finish, on error, on the
user stopping it, and on the server going away mid-session.

Not carried over: cast volume has no UI control yet (the store exposes
`setVolume`), and the local `<audio>` element is detached rather than kept
running muted, so the desktop's trick of tracking position locally between
polls is not available. Position comes from the device.

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

### 5. The Android code that needs Robolectric or a device

53 JVM tests now cover `PathPatternMatcher`, `WireMapping`, the
`LibraryRepository` paging loop, `LocalTrack.toCatalogTrack` and
`ConnectViewModel` (with a `MainDispatcherRule` that other ViewModel tests can
reuse). CI runs `./gradlew testDebugUnitTest`.

What is left needs more than a JVM:

- `data/local/LocalLibraryScanner.kt` — built on `DocumentsContract`,
  `ContentResolver` and `MediaMetadataRetriever`. Testing it means Robolectric,
  an instrumented test, or extracting the pure parts behind an interface.
- `PathPatternMatcher.decodeLocalPath` — `android.net.Uri.parse` returns null
  under the stub `android.jar`, so a JVM test would only exercise the fallback.
- `OfflineDownloadManager` — needs a `Context`, a Room DAO and real file I/O;
  the parts worth testing (resume, eviction) are the ones that touch disk.

Robolectric was deliberately not added: it fetches an `android-all` jar at test
time, which makes CI slower and network-dependent, and only one small function
needs it today.

**Size:** medium. **Value:** medium.

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

### 11. `src-tauri`: the transcode path and mDNS discovery

26 tests now cover the local cast HTTP server (range parsing and the allowlist),
the cast session's serialized status, and the `commands.rs` helpers. `pnpm run
check` runs them, so the existing client CI job picks them up.

Two areas remain:

- `cast/transcode.rs` — the FLAC-to-MP3 path. The server has an equivalent test
  that generates a fixture with ffmpeg and skips when it is unavailable
  (`transcode_high_res_flac_preserves_duration`); the same approach would work
  here, and this is where the half-speed bug lived.
- `cast/discovery.rs` — mDNS, so it needs either a fake responder or an
  integration test on a real network.

**Size:** small for transcode, medium for discovery. **Value:** medium.

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

### 14. Component tests for the apps' own views

Every shared component is now tested with `@vue/test-utils` under happy-dom —
`FeatherIcon`, `MarqueeCell`, `EqualizerBars` and the three stats charts — and
the shared suite sits at 92% lines / 75% branches over 181 tests.

What is still untested is each app's own `.vue` files: the desktop's
`LibrarySettingsModal` and `MetadataEditor`, the web client's views and sheets.
Those need a Pinia store per test and a good deal of mocking, so they are worth
adding view by view as each is next touched rather than in one pass.

One setup note for whoever does: `vue` must NOT be aliased in
`vitest.config.mts`. `@vitejs/plugin-vue` compiles SFCs against the root's own
`@vue/compiler-sfc`, and pointing the runtime at another copy makes template
refs land on hoisted vnodes and prop updates stop re-rendering — with no error,
just a component that never updates.

**Size:** medium per view. **Value:** medium.

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
- **260 automated tests where there were 16**, all wired into CI:
  - 181 vitest tests over the shared code, the shared components and the web
    client's library store — 92% lines, 90% statements, 84% functions, 75%
    branches, thresholded at 60%.
  - 53 Android JVM tests over the path matcher, the wire mapping, the paging
    loop, the local-track conversion and the connect flow.
  - 26 `src-tauri` tests over the cast server's allowlist and range handling,
    the cast status contract, and the backup naming.
  - the 16 server Rust tests that already existed.
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

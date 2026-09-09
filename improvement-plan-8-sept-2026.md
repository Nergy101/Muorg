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

### 4. ~~No lint step~~ — done

One flat ESLint config at the repo root (`eslint.config.mjs`) covering the
shared `src/`, both apps and the codegen scripts, run as `pnpm lint` in CI at
`--max-warnings 0`.

Deliberately not type-aware: those rules need a program per app and roughly
triple the run time, and both apps already run `vue-tsc` in CI. `no-undef` is
off for the same reason — TypeScript resolves identifiers already, and leaving
it on means maintaining a globals list that duplicates tsconfig's `lib`.

It found three things worth having found:

- `PlaylistExportDialog.vue` imported Tauri's `open` alongside an `open` prop.
  In `<script setup>` the import shadows the prop in the template, so
  `v-if="open"` read an always-truthy function. Latent — the only caller passes
  `:open="true"` and guards with its own `v-if` — but a trap.
- `HomeView.vue` shipped a debug hook (`window.__muorg`) exposing store
  internals to every visitor. Now gated on `import.meta.env.DEV`.
- Dead assignments and `let` that should be `const` in both copies of
  `useDominantColor.ts`.

**Size:** done. **Value:** ongoing — the bar is zero warnings, so new noise
fails the build rather than accumulating.

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

### 7. ~~No metadata editing beyond the scan sheet~~ — done

The track sheet's edit level now carries the two tools that were reachable in
the API and unused:

- **Find matches** — MusicBrainz candidates with their confidence. Tapping one
  fills the form rather than writing straight through, so the user still sees
  what is about to be saved.
- **Undo last write** — restores the backup the server takes before each tag
  write. Only shown when one exists, which is what makes accepting a suggestion
  safe.

Both are hidden for on-device tracks (negative id, no server row).
`renameTrackFile` is on the repository and tested, but has no UI: renaming a
file is a desktop job, and the sheet has no good place to edit a path.

The work turned up a generator bug worth noting: Rust's `Option<T>` becomes
`oneOf: [null, T]` in the spec, which the Kotlin generators did not recognise —
`getBackup` was emitted as `Response<Unit>`, silently discarding the body. Both
generators now unwrap that pattern.

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

### 11. ~~`src-tauri`: the transcode path and mDNS discovery~~ — done

The FLAC-to-MP3 transcoder now has six tests, against real audio generated with
ffmpeg (skipped with a message when ffmpeg is absent, matching the server's
equivalent test): output decodes as MP3, a 96 kHz source keeps its duration —
the half-speed regression — a start offset actually skips, a missing or
non-audio file reports rather than panics, and the loop stops when the receiver
is dropped.

Discovery moved to `muorg-core` in §12, so its tests went there. The device-list
handling was lifted out of the mDNS thread into `upsert_device` /
`remove_device` to make it testable: a re-announcing Chromecast updates in place
instead of duplicating, a DHCP address change keeps its slot, and a goodbye for
an unknown device reports "nothing changed" so observers are not woken for a
no-op.

That last test caught a regression from §12: the server's `stop()` cleared the
device list and the desktop app's did not, and the shared module was built from
the desktop copy — so a stopped sweep left stale devices in
`GET /api/cast/devices`. Clearing is restored.

**Size:** done. **Value:** the transcoder is the piece with a history of
shipping wrong.

### 12. ~~Cast code forked between the desktop app and the server~~ — done

Discovery and the session protocol now live in `muorg-core::cast`, behind a
`cast` feature so a consumer that only wants the catalog does not pull in
`rust_cast` and `mdns-sd`.

The two copies differed in exactly one thing: what happens on a state change.
The desktop app pushes it to its webview as a Tauri event, the server stores it
for `GET /api/cast/status` to read. That is now a `CastObserver` /
`DiscoveryObserver` pair — the server passes `NoObserver`, the desktop app
passes a `TauriObserver`, and the ~600-line protocol loop exists once.

The bigger find was underneath: `rust_cast` itself was **vendored twice**, at
`client/src-tauri/vendor/` and `server/vendor/`, 4,675 lines each and differing
by a single `#![allow(deprecated)]`. The client now patches to the server's copy.

What stays in `client/src-tauri/src/cast/`: the local HTTP server that hands a
device a file off this machine, and the FLAC transcoder feeding it. Those are
genuinely per-host — the server streams from its own surface instead.

Also aligned by the move: the client was on `mdns-sd` 0.11 and the server on
0.21, which is the kind of drift that produces a bug on one platform only.

---

## Cross-cutting

### 13. ~~Wire the smoke test into CI~~ — done

`./scripts/smoke-api.sh` builds `muorg-server`, starts it on a temporary
database, runs `scripts/smoke-api.ts` against it and tears it down. CI runs it
in the `api-contract` job.

It no longer just checks that calls succeed: every JSON response is validated
against the schema the spec declares for that operation, using Ajv over
`server/openapi.json`. So the loop is closed — TypeScript proves the client
matches the spec, the call-surface tests prove each wrapper hits the route it
claims, and this proves the server matches the spec at runtime. (Verified by
deliberately mistyping `LibraryStats.track_count` in the spec and confirming
`getStats` failed.)

Still shallow in one respect: the fixture library is empty, so list responses
validate as empty arrays. Seeding a few real files — the audio fixtures in
`server/crates/muorg-core/tests/fixtures/` would do — would exercise
`CatalogTrack` and the cover and stream routes for real.

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

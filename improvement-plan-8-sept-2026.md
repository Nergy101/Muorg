# Muorg improvement plan — 8 September 2026

Findings from a review of the repo at `ba87239` (v2.42.1). The items that were
already acted on are listed at the bottom; everything above them is still open.

> **Updated 11 September 2026.** §2, §7, §8 and §15 closed in the intervening
> commits. This pass closed §3's neighbour (a worse hole the original review
> missed — see §16), §4's server-side equivalent (§17), §13's empty-fixture
> caveat, §9's sibling (reports parity, §18) and §10's remainder (§19).

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

### 2. ~~No MusicBrainz auto-tagging~~ — done

The web client looks candidates up through `api.autoTagSuggestions()`
(`69a587b`), via `useAutoTag` and `AutoTagPanel.vue`.

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

### 8. ~~Large screen files~~ — done

All five were split (`5aa3e17`, `115b37e`, `44f2694`, `9003324`): the settings
sections, the library and playlists screens, the player's backdrop and controls,
and the route table out of `NavGraph`.

### 9. Offline downloads are Android-only

`OfflineDownloadManager` has no counterpart in the web app, which already has a
Workbox service worker and could cache audio the same way. Worth deciding
whether that is a deliberate platform difference or a gap.

---

## Desktop App (`client/`)

### 10. ~~`LibrarySettingsModal.vue` is 2,949 lines~~ — done

Split along the tab boundaries already in the markup: eleven panels under
`components/modals/settings/`, and a 233-line shell that owns the tab rail, the
scroll container and open/close.

The panels reach for the stores themselves rather than taking props. That is
what made the original file so large — three dozen refs destructured at the top
and threaded through every tab — and the state is global anyway, so there is
nothing to thread.

Largest remaining: `TablePanel` 571, `ThemePanel` 538, `GeneralPanel` 556.
Those are mostly markup for option grids and live previews; splitting them
further would separate a control from the thing it controls.

~~`MetadataEditor.vue` (1,783) is untouched and is now the largest file in the
desktop app. It has no comparable seam — it is one form — so it wants a
different treatment.~~ Done, and the different treatment was to stop looking for
tab boundaries and pull out the things that were not the form — see §19.

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

~~Still shallow in one respect: the fixture library is empty, so list responses
validate as empty arrays.~~ Fixed: `scripts/smoke-api.sh` now copies the three
audio fixtures from `server/crates/muorg-core/tests/fixtures/` into a temporary
library (one at the root, two in a subdirectory, so the recursive walk is
covered too) and starts the server with `scan_on_startup`. The suite grew from
22 calls to 48, and the new ones are the ones that could not exist before:
`CatalogTrack` validated per row against its component schema rather than as an
empty array, real cover bytes with their MIME type, a 404 for a track with no
art, a stream that serves audio, a `Range` request answered with 206 and the
right length, a token refused for a different track's id, a tag write that then
turns up in search (which is also the only check that the FTS index is
maintained on update and not only on scan), a play that appears in both history
reports, a backup taken and restored, playlist membership and reordering with
real ids, and a smart playlist resolving against the library.

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

### 15. ~~Repo has no root README pointer to the API contract~~ — done

`f89b355`: the layout section now lists all four components plus `src/`, and
points at `src/api/README.md`.


---

## Added 11 September 2026

### 16. ~~`POST /api/fetch-image` was an open SSRF proxy~~ — done

Worse than §3 and missed by the original review. The route existed so the
clients could pull album art without tripping CORS: it took a URL from the
request body, fetched it server-side, and returned the body base64-encoded to
the caller. Nothing checked the host, the scheme, the redirect target or the
size. On a NAS that made `http://192.168.1.1/…`, `http://localhost:8080/…` and
a cloud metadata endpoint readable by anyone holding the API key — and because
the bytes came back in full, it was a read-SSRF rather than a blind one. Three
more on the same 25 lines: no timeout, so a host that accepted the connection
and stalled pinned a worker; `response.bytes()` buffered the whole body before
encoding it, which is a memory-exhaustion lever; and the declared "not an image"
error never happened, because an absent `Content-Type` defaulted to
`image/jpeg` and whatever came back was returned as an image.

Now in `urlguard.rs`, two layers because either alone has a hole:

- **A host allowlist**, the actual control. The route only ever needs the art
  sources the clients search, so the default list is Wikipedia/Wikimedia, the
  Cover Art Archive, `archive.org` and MusicBrainz, overridable via
  `[images] allowed_hosts`. An entry matches subdomains, because the Cover Art
  Archive redirects into `ia800207.us.archive.org` and there is no list of
  those to enumerate. Matching is on label boundaries: `evil-archive.org` and
  `archive.org.evil.com` are both refused, which a naive `ends_with` would not
  do.
- **An address check** on what the host resolves to, for an allowlisted name
  that points somewhere internal. Every resolved address is checked, not the
  first — a name resolving to one public and one loopback address is still a
  way in. `IpAddr::is_global` is unstable, so the ranges are spelled out,
  including the ones easy to forget: `169.254/16`, CGNAT `100.64/10`,
  `0.0.0.0/8`, and `::ffff:169.254.169.254`, which reaches the same metadata
  service as the bare v4 address.

Plus: every redirect hop is re-checked against the allowlist (the policy
closure is sync, so it cannot re-resolve DNS — the allowlist is the control
there), a 5s connect and 15s total timeout, a 10 MB cap enforced *while
streaming* because a `Content-Length` is a claim and not a promise, and a
response that must actually declare `image/*`.

17 unit tests over the guards and 4 integration tests through the live router,
and the smoke test now asserts a running server refuses both an internal
address and an unlisted host.

**Size:** done. **Value:** this was the one live vulnerability in the repo.

### 17. ~~The server's core was ~16 tests over ~4,000 lines~~ — done

§4 fixed the front end's floor; the server had none. `catalog/db.rs` was 1,655
lines with 2 tests, and `storage/scan.rs`, `musicbrainz.rs`, `backup.rs`,
`auth.rs` and `ratelimit.rs` had zero — in a repo advertising 260 tests. It is
also the component where one regression breaks the desktop app, the web app and
Android at once, since all three read the catalog through it.

Now 174 server-side tests (from 16):

- **63 in `muorg-core/tests/catalog.rs`** over roots, pagination, soft deletes,
  move detection, GC, FTS search, stats, play history, playlists, smart-playlist
  rules, metadata writes, backups and a real directory scan. Leaning towards
  what has actually broken: that a page walk covers the library exactly once
  (the `5318938` bug), that a soft-deleted track disappears from every read
  path including the FTS index, that a moved file keeps its rating, play count
  and playlist membership, and that the smart-rule compiler escapes `%` and `_`
  in `contains` and refuses a field outside its allowlist.
- **40 in the server lib** — the rate limiter's window and per-IP isolation,
  backup naming and retention, and the MusicBrainz parser.
- **24 integration tests** through the live router — every credential shape a
  wrong key can take, that every mutating route is behind the API key while
  health and `/stream` are not, stream-token refusal, and the fetch-image
  guards.

Three bugs fell out of writing them:

- **MusicBrainz responses never deserialized three of their fields.** The wire
  keys are `artist-credit`, `track-count` and `track-offset`; the structs had
  `artist_credit`, `track_count`, `track_offset` and no `rename_all`. Because
  every one is `Option` with `#[serde(default)]`, nothing failed — every
  auto-tag candidate simply came back with an empty artist, no album artist and
  no track number, on all three clients, and the artist half of the confidence
  score never contributed. The dead-code warnings on `joinphrase` and
  `track_count` had been the visible symptom the whole time.
- **`track-offset` is 0-based** and a track number in a tag is 1-based, so
  fixing the rename alone would have turned "always absent" into "always off by
  one".
- **Backup GC never ran on Linux.** It grouped files by `metadata().created()`
  and skipped any entry where that failed — which is `Unsupported` on several
  Linux filesystems, i.e. in the Docker image. Falls back to `modified()`.

Also: an empty MusicBrainz query now returns no candidates instead of spending
a rate-limit slot on a request MusicBrainz answers with a 400.

**Size:** done. **Value:** high — the shared backend now has a floor.

### 18. ~~Reports were desktop-only~~ — done

The sibling of §9, and a bigger gap: `LibraryReportsModal.vue` and
`SidebarReports.vue` existed nowhere else, and there is no `/api/reports` — the
desktop computed them client-side. So the web and Android apps could show a
library but not tell you what was wrong with it.

The logic is pure (tracks in, filtered tracks out), so it moved to
`src/reports.ts` and both TypeScript apps now share it. The desktop app had
*three* copies of it — the sidebar counts, the table's filter and the modal's
duplicate count — which had already drifted: the sidebar counted a duplicate
pair as 1 and the modal's badge recomputed it from the filtered list.

Android cannot import TypeScript, so `util/LibraryReports.kt` is a port, and
the two test suites assert the same cases deliberately: a change made to one
and not the other shows up as a case that passes on one side and fails on the
other. 24 tests in `src/reports.test.ts`, 23 in `LibraryReportsTest.kt`.

Two definitions worth recording, because they are the ones that could sensibly
go either way:

- A whitespace-only tag counts as **missing**. A file tagged `"   "` is not
  tagged, and treating it as present is exactly how those files stay invisible
  to the report that exists to find them.
- A numeric `0` counts as **present**. Track 0 is a real (if odd) tag, so
  sweeping it up as missing would flag files that are fine.
- The duplicate count is **copies beyond the first**, not tracks involved. Two
  copies of one song is one thing to fix; "2" beside a two-row list reads as
  two problems.
- Tracks with no artist, album *and* title are excluded from duplicates. They
  all collapse to one key, so including them reports every untagged file as a
  duplicate of every other and buries the real ones — that is the
  missing-metadata report's job.

Reached from Settings and the desktop rail on web, and from Settings on Android,
rather than the bottom nav: five tabs is a crowd on a phone, and reports are an
occasional errand. Duplicates render grouped on both new clients, because seeing
the copies of one recording together is the point of that report.

One thing deliberately not done: no `/api/reports` endpoint. The duplicate and
missing-art queries would be better in SQL, but all three clients already hold
the full catalog for search and the album grid, so the round trip would buy
nothing today. Worth revisiting if a client ever stops loading the whole
library.

### 19. ~~`MetadataEditor.vue` was 1,783 lines~~ — done

The remainder of §10, and the note there — "it is one form, so it wants a
different treatment" — was the right diagnosis and the wrong conclusion. The
treatment is not to split the form; it is to notice that most of the file was
not the form:

- `utils/imageData.ts` — the conversions between the shapes cover art arrives
  in (a `data:` URL from a file input, bytes from the Tauri FS plugin, base64
  from the server's proxy) and the bare base64 the tag writer takes. One of
  them had been copy-pasted into a second function rather than called: the PNG
  re-encode existed both as `pngDataUrlToJpegBase64` and inline inside
  `applyWikipediaImage`.
- `utils/wikipediaCover.ts` — the three MediaWiki calls and, more to the point,
  the heuristic that picks the cover out of a page's image list. An article's
  images are mostly furniture — rating stars, edit pencils, navigation arrows —
  so taking the first one puts a star icon in someone's album tag.
- `composables/useTooltipPopover.ts` + `components/shared/TooltipPopover.vue` —
  the body-teleported tooltip, which three components each had their own copy
  of, timeout dance and all.
- `GenreCombobox.vue` and `WikipediaCoverModal.vue` — a self-contained field
  and a self-contained dialog.

1,783 → 1,460 lines, and 44 desktop tests where there was 1: the image
conversions and the cover-art scoring are pure, so they are now tested
directly — including that `bytesToBase64` chunks (the reason it exists:
`String.fromCharCode(...bytes)` on a real cover exceeds the argument limit and
throws), that page furniture scores below zero, and that the search reads
`index` rather than the first key of MediaWiki's result object.

`LibraryTableBody.vue` (1,333) and `PlayerBar.vue` (1,195) are now the largest
in the desktop app, and `catalog.ts` (995) is the untested one that matters
most — see §14.

---

## Done in the 8 September pass

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

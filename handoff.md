# Muorg: handoff of audit decisions

## Purpose

A read-only audit of Muorg (server, desktop, web and Android clients) found 34 doubtful decisions. The owner answered each one. This document lists every decision so another agent can implement them. **No code has been changed yet.** Implement only the decisions below.

Items already decided in `improvement-plan-8-sept-2026.md` §1–§19 are out of scope. These include the stream token in the query string, Int vs Long IDs, Android-only offline downloads, no `/api/reports`, and client-side full-catalog load.

## Repo layout

- Repo root: `Muorg/`
- `server/` — Rust workspace: `crates/muorg-server` (HTTP, routes, startup, rate limit, cast state) and `crates/muorg-core` (catalog DB, metadata read/write, cast session)
- `client/` — desktop app: Vue frontend in `client/src`, Tauri backend in `client/src-tauri/src`
- `web-client/` — Vue web app: `src/stores`, `src/views`, `src/components`, `vite.config.ts` (Workbox)
- `android-client/` — Kotlin app: `app/src/main/java/nl/muorg/android/...`
- `docs-site/`, `README.md`, `server/docker-compose.yml`, `server/muorg-server.example.toml`

## Verification status

- Verified by grep or reading the code: Q1 defaults, Q2 `fs::rename` with no existence check, Q4 `LibraryHeader.vue` digit handling, Q6 `getLocalFilePath` has no callers, Q8, Q10, Q18 example config, Q19 device name, and Q34 mode split.
- Based on code reading by audit scouts; not run: the rest. Confirm each file:line before editing.
- No tests or builds were run during the audit.

## Modes (important for several items)

Each client has two modes:
- **Server / online mode**: data comes from the Muorg server.
- **Local / offline mode**: data lives on the device.

Several decisions depend on this split (Q21, Q24, Q32, Q34). Keep server data and local data separate. Do not merge them.

---

## Decisions

### Data safety

**Q1 — Tag writes have no backup by default and write in place.**
- Where: `server/crates/muorg-server/src/routes/tracks.rs` (~229, 264–289); `server/crates/muorg-core/src/metadata/read_write.rs` (~240–250, 347, 448–452); `client/src/stores/settings.ts:285` (`backupBeforeWrite: false`); `web-client/src/stores/player.ts:853–870` (hardcoded `false`).
- **Decision: warn on the first write and on bulk edits.** Keep the default as is. Show a one-time warning on the first tag write and on any bulk edit.

**Q2 — Smart Transform renames the whole library with no confirmation; rename can overwrite.**
- Where: `client/src/components/modals/settings/SmartTransformPanel.vue:62–83`; `client/src-tauri/src/commands.rs:573–587` (`rename_track_file`, `fs::rename` with no existence check).
- **Decision: confirmation dialog** showing the file count and a "cannot be undone" warning.
- Also consider refusing to overwrite an existing destination, but the decision was option 1 only. Do the confirmation. Adding the existence check is a judgement call; mention it in the PR.

**Q3 — Desktop "Clear cache" purges soft-deleted tracks immediately, with no confirmation.**
- Where: `client/src/components/modals/settings/GeneralPanel.vue:126–136, 283–297`; `client/src-tauri/src/commands.rs:89–95` (`gc_deleted_tracks(&conn, 0)`, bypasses the 30-day grace period in `lib.rs:72–76`).
- **Decision: split into two actions.** "Clear cache" does only the cache clear. A separate "Purge removed tracks" action does the hard delete, with its own confirmation.

**Q4 — Desktop digit keys 0–5 re-rate the whole selection with no undo.**
- Where: `client/src/components/library/LibraryHeader.vue:76–96`; `client/src/stores/catalog.ts:695–697, 731–740` (`setRating`, no `_pushUndo`).
- **Decision: both** of the following:
  - Push rating changes onto the undo stack.
  - Ask for confirmation when more than one track is selected.

**Q5 — Desktop Ctrl+Z only works while the Metadata editor is mounted.**
- Where: `client/src/components/metadata/MetadataEditor.vue:417–419, 721–733`; `client/src/App.vue:530–533`.
- **Decision: both** of the following:
  - Show an "Undo" toast after each write.
  - Change the key map text so it says undo works only in the editor, and fix the key map to match.
- Note: the owner chose options 2 and 3, not option 1. Do not move the handler to a global listener.

**Q6 — Move detection matches by file-path suffix and can carry ratings, play history and playlist membership to the wrong track.**
- Where: `server/crates/muorg-core/src/catalog/db.rs:657–673` (`by_suffix`), comment at ~607–611.
- **Decision: both** of the following:
  - Require the same file name and size before matching.
  - Backfill `content_hash` for existing rows so the fallback is not needed.

**Q7 — Android removes things with no confirmation.**
- Where: `android-client/.../ui/screen/settings/LocalFoldersSection.kt:65–69` (remove folder deletes local tracks and playlist entries); `android-client/.../ui/screen/queue/QueueScreen.kt:280–287` (swipe removes immediately).
- **Decision: long-press for both** actions. Remove the swipe-to-delete on the queue and the single-tap folder remove.

### Features and behaviour

**Q8 — Android offline downloads are never played.**
- Where: `android-client/.../data/repository/OfflineDownloadManager.kt:160–165` (`getLocalFilePath`, no callers); `android-client/.../player/PlayerController.kt:249–279` (`buildItems` checks only `CatalogTrack.localFilePath`).
- **Decision: "offline mode" plays only downloaded tracks.** Add an explicit offline mode that builds the queue only from downloaded files.

**Q9 — Android downloads have no progress or error UI, use `cacheDir` with no cap, are left behind when a playlist is deleted, and run on mobile data.**
- Where: `OfflineDownloadManager.kt:44–86` (`downloadProgress` is never read by UI; files in `cacheDir`; no network check); `PlaylistsViewModel.kt:364–380`.
- **Decision: both** of the following:
  - Show progress and failures; store files in `filesDir` with a size cap; remove files when a playlist is deleted.
  - Download only on Wi‑Fi by default, with a setting that allows mobile data.

**Q10 — Web Chromecast auto-advance stops when the Player view is unmounted.**
- Where: `web-client/src/views/PlayerView.vue:375–376` (subscribes to `cast.onTrackEnded`, unsubscribes on unmount).
- **Decision: move the "track ended" subscription into the cast or player store**, so it works from any screen.

**Q11 — Web offline banner says cached content is available, but nothing is cached.**
- Where: `web-client/src/components/OfflineBanner.vue:15`; `web-client/vite.config.ts:53–59` (`NetworkOnly` for `/api/` and `/stream/`).
- **Decision: offline caching with these requirements:**
  - A **playlist download button**: a user can download any playlist for offline use.
  - **Recent songs** are cached automatically, capped at about 100 tracks for now.
  - **Storage quota**: check available space with `navigator.storage.estimate()` before downloading, and cap the total size. Libraries can be tens of gigabytes, so never download everything.
  - **Implementation note**: the Cache API cannot reliably store partial (206) range responses. Fetch each track whole and store it as a complete file, in Cache Storage or OPFS.

**Q12 — Web API key is stored in `localStorage`, and the field has `autocomplete="current-password"`.**
- **Decision: keep as is.** No change.

**Q13 — Web logout keeps search history, sort order and theme.**
- Where: `web-client/src/views/SettingsView.vue:496–503`; `web-client/src/stores/settings.ts:18–41`.
- **Decision: keep the current behaviour** and say so in the logout confirmation.

**Q14 — Server runs the local library scan before the HTTP listener starts.**
- Where: `server/crates/muorg-server/src/main.rs:157–170` (local scan, synchronous); `199–216` (remote scan, spawned). Compose healthcheck `start_period: 10s`, `retries: 3`.
- **Decision: `/api/health` reports "scanning"** while the local scan runs.

**Q15 — One global Chromecast session per server; starting a cast on one device stops another.**
- Where: `server/crates/muorg-server/src/state.rs:118–119, 157–158`; `server/crates/muorg-core/src/cast/session.rs:97–117`.
- **Decision: notify the other clients** when their session is taken over.

**Q16 — Rate limit is 100 requests per 60 s per IP; the 429 response has no `Retry-After`.**
- Where: `server/crates/muorg-server/src/state.rs:159`; `server/crates/muorg-server/src/ratelimit.rs:35–62`.
- **Decision: make the limit configurable and add `Retry-After`** to 429 responses.

**Q17 — Removed-track clean-up (30 days) and play-history prune (90 days) run only at startup.**
- Where: `server/crates/muorg-server/src/main.rs:143–155`.
- **Decision: run clean-up daily in the background.**

**Q18 — Example config ships `allowed_origins = ["*"]`, and the README tells users to copy it.**
- Where: `server/muorg-server.example.toml:54–57`; `README.md:128`.
- **Decision: both** of the following:
  - The owner's VPS config lists the known origins explicitly, including `nergy.space`. Do not use `*` there.
  - The example config and README get a warning that `*` allows any website to call the server.
- The example file should ship with a safe default. Use known origins as the default and document the warning.

**Q19 — Play history and play counts are global to the server.**
- Where: `server/crates/muorg-core/src/catalog/db.rs:1335–1358`; smart rules `db.rs:1564–1589`.
- **Decision: add an optional per-device "device name" field.**
  - Name it **"device name"**, not "device ID".
  - Each device fills it with its default device name. The field is required in settings, and the user may change it.
  - Play history records the device name.

**Q20 — A "play" is counted at different moments on each app.**
- Where: desktop `client/src/components/playback/PlayerBar.vue:263–267` (30 s); web `web-client/src/stores/player.ts:297–298` (instant); Android `android-client/.../player/PlayerController.kt:223–241` (instant, skipped for offline).
- **Decision: count a play after 30 seconds of listening on every app.**

**Q21 — Favorites differ across apps.**
- Where: `web-client/src/stores/playlists.ts:20–21, 124–129, 247–249` (server playlist matched by name "Favorites"); `web-client/src/stores/player.ts:813–832`; `android-client/.../AppPreferences.kt:38, 103–116` (local only); no favorites in `client/src`.
- **Decision: store favorites on the server, with the same UI on all three apps.**
  - The server "Favorites" playlist cannot be deleted or renamed.
  - Local favorites stay as a separate local playlist. The server list and local list do not merge.
  - Desktop gets favorites too.

**Q22 — Search semantics differ across apps.**
- **Decision: keep as is.** No change.

**Q23 — "Shuffle all" exists on web and Android but not desktop.**
- Where: `web-client/src/stores/player.ts:104, 109–121`; `android-client/.../PlayerViewModel.kt:44–48`; desktop `client/src/stores/settings.ts:230, 481–484`.
- **Decision: add "Shuffle all" to desktop.**

**Q24 — Pinned playlists are stored per device, and desktop has no pinning.**
- Where: `web-client/src/stores/playlists.ts:56–96`; `android-client/.../AppPreferences.kt:42, 94–101`; desktop `SidebarPlaylists.vue` has no pinning.
- **Decision: both** of the following:
  - Pins are stored on the server in server mode and on the device in local mode.
  - Add pinning to desktop, in both modes.

**Q25 — Settings do not sync through the server.**
- Where: desktop YAML (`client/src/stores/settings.ts:429–433`); web `localStorage` (`web-client/src/stores/settings.ts:19–21`); Android DataStore.
- **Decision: keep settings per device**, and add a note in onboarding saying so.

**Q26 — Web reloads the whole catalog after saving one track's metadata.**
- Where: `web-client/src/stores/player.ts:864–866` → `loadLibrary()` (`web-client/src/stores/library.ts:360–395`).
- **Decision: both** of the following:
  - Update that one track in place from the save response.
  - Batch and delay the reload when several edits come in a row.

**Q27 — Android checks playback state every 500 ms, even when paused or in the background.**
- Where: `android-client/.../player/PlayerController.kt:57, 199–207`.
- **Decision: check only while something is playing.**

**Q28 — Android API key is stored unencrypted in DataStore.**
- Where: `android-client/.../data/preferences/AppPreferences.kt:33, 53–54, 136–141`.
- **Decision: keep as is.** No change.

**Q29 — Android FLAC seek fetches a new stream token and restarts the stream on every seek.**
- Where: `android-client/.../player/PlayerController.kt:317–358`.
- **Decision: show buffering on the seek bar** while a seek loads.

**Q30 — Desktop Settings → Keyboard lists shortcuts that don't exist and contradict the Key Map modal.**
- Where: `client/src/components/modals/settings/KeyboardPanel.vue:25–118`; `client/src/components/modals/LibraryKeyMapModal.vue:15–36`; actual bindings in `client/src/App.vue:364–376` and `client/src/components/library/LibraryHeader.vue:76–166`.
- **Decision: generate both lists from one source**, so they cannot drift.

**Q31 — Desktop "Previous" while shuffling goes to the table neighbour, not the track played before.**
- Where: `client/src/components/playback/PlayerBar.vue:45–66` (next, random), `69–81` (previous, table order).
- **Decision: keep a history of played tracks**, so Previous goes back through what was played.

**Q32 — Desktop Local ↔ Online switch keeps the queue and selection, whose track IDs belong to the other catalog.**
- Where: `client/src/components/modals/settings/ConnectionPanel.vue:29–46`; `client/src/stores/catalog.ts:128, 276–279`.
- **Decision: clear the queue and selection when switching modes.**

**Q33 — Android logout keeps playback running and keeps the previous server's catalog cache for up to 10 minutes.**
- Where: `android-client/.../ui/screen/settings/SettingsViewModel.kt:226–231`; `android-client/.../data/repository/LibraryRepository.kt:34–72` (10-minute TTL on a singleton); `player/PlayerController.kt`.
- **Decision: stop playback and clear the cache on logout.**

**Q34 — Favorites and pins: one-time sync from local to server?**
- **Decision: no.** Local and server lists stay separate, each belonging to its mode (offline/local or server/online). There is no one-time sync action.

---

## Implementation order (suggested)

1. Data safety: Q1, Q2, Q3, Q4, Q5, Q6, Q7.
2. Server: Q14, Q15, Q16, Q17, Q18, Q19, Q20.
3. Shared features: Q21, Q24, Q23, Q30, Q31, Q32, Q25.
4. Web: Q10, Q11, Q26, Q13.
5. Android: Q8, Q9, Q27, Q29, Q33.

Each item should be verified with a smoke run of the changed path. Do not write tests that only check text or wiring.

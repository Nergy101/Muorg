---
sidebar_position: 8
---

# Release Notes

Muorg ships as tagged GitHub releases (`v3.x.y`). This page summarizes the notable changes per release, per component. Full details are on the [GitHub Releases page](https://github.com/Nergy101/Muorg/releases).

## v3.3.0

- **Server**: WAV support — scanning, tag reading and tag editing now cover `.wav` files. Tags are written to the WAV's ID3 chunk (what DJ software and taggers use), with RIFF LIST/INFO read as a fallback and copied into the new ID3 tag on the first edit so no fields disappear; every other chunk (audio, cue points, smpl, bext, iXML) is left byte-for-byte as it was. Streaming and casting send WAV through the existing MP3 transcode path, like FLAC. Also fixes editing "featuring" on ID3 files: it removed every TXXX frame (catalogue numbers, Discogs IDs, …) and now removes only TXXX:FEATURING.
- **Desktop App**: WAV support end to end — `.wav` files show up in the library and play, the metadata editor reads and writes their tags, and Chromecast sessions transcode them like FLAC.
- **Web App**: playlists can be sorted by title, artist or added date (ascending/descending) from the playlist header, and the Home header has a **Shuffle all** button. WAV files play and stream like FLAC. Fixed iOS playback stalling on the lock screen — auto-advance now keeps working when the screen is locked.
- **Android**: no new Android release in this cycle — see the [Android release notes](#android-32x--33x) (android-v3.3.8 through android-v3.4.0 shipped since v3.2.4). WAV support lands in the next Android release.

## v3.2.4

- **Desktop App**: the app now checks the bundled server (sidecar) on every launch and replaces an outdated one. A sidecar left running by a previous version (after an auto-update, say) used to keep port 7700, so the new app silently talked to the old server — which is why Chromecast kept failing after updating to v3.2.3. Only a process named `muorg-server` is ever stopped; anything else on the port is left alone. The sidebar's tab bar no longer squeezes or wraps its tabs when the sidebar is narrow: it scrolls horizontally (mouse wheel works too) and keeps the active tab in view.
- **Server**: `GET /api/health` now sends an `X-Muorg-Version` header with the server's version, which the desktop app uses to detect an outdated sidecar. The response body is unchanged.

## v3.2.3

- **Desktop App**: fixed connecting to Chromecast devices — devices showed up in the cast menu but clicking them did nothing (see Server below). Fixed favoriting: clicking the heart before the playlists finished loading created a duplicate "Favorites" playlist and the heart reverted on the next refresh, so favorites appeared broken in both local and online mode; the playlist list is now loaded first, so a favorite always lands in the one real Favorites playlist. Mouse-drag text selection is now disabled app-wide (form fields stay selectable and editable). The Connection settings tab no longer renders underneath the other tabs — four panels had fragment template roots (Teleport siblings and stray comments), which `v-show` cannot hide. Exporting a playlist to M3U now writes the file into the chosen output folder through the Tauri fs plugin; the previous blob-URL download never fired in the desktop webview and ignored the picked folder.
- **Server**: when the API listener is loopback-only (the desktop app's sidecar binds `127.0.0.1`), the server now also binds a second, all-interfaces listener that serves only the token-protected `/stream/{id}` route, so Chromecast devices on the LAN can fetch the audio a cast session points them at. `/api/*` stays loopback-only behind the API key. This is what made Chromecast devices appear in the desktop app but do nothing when clicked.

## v3.2.2

- **Web App**: added an **Offline mode** toggle in Settings → Playback (mirroring the Android app) — when on, the player makes no network calls and only plays downloaded tracks, with a "Not downloaded" message for anything else. Settings gained an **Offline** section showing cached tracks, storage used and downloaded playlists, with a clear-cache action. The background auto-download of recent plays no longer toasts on failure (a track whose file is missing on the server used to nag "Offline download failed (404)" on every page load), and an explicit download now says the server could not find the track's file instead of a bare 404. Downloaded tracks now actually play offline: the service worker's `/stream/` route never matched cross-origin server URLs (workbox only matches a cross-origin URL when the regex matches at index 0), so cached tracks silently fell through to the network, and the server's `Vary: Accept-Encoding` header defeated Cache API matching — both fixed. A cold deep-link to a playlist no longer shows "No tracks" when the playlists list was still loading.
- **CI**: the VPS deploy no longer races the release build — it now runs after the Release workflow completes, so a `v*` tag deploys the image it just built instead of the previous one.

## v3.2.1

- **Web App**: fixed adding songs to favorites and playlists — the API transport now rebuilds requests with an explicit body instead of cloning the Request object, which some browsers silently dropped on cross-origin POST/PATCH/DELETE calls. Add-to-playlist actions now show an error toast and revert the optimistic UI on failure instead of failing silently. Favorites toggle shows a confirmation toast again. The Playlists bottom-nav icon is now a library-music icon.
- **Android**: consistent add-to-playlist conflict checking across all screens (library, album, playlist, player); rename-file action in the track menu with a pre-flight collision check; Playlists bottom-nav icon is now a library-music icon.

## v3.2.0

- **All components**: coordinated release implementing the handoff-audit decisions across the server, desktop, web app and Android client.

## Android 3.2.x – 3.3.x

The Android app ships on its own `android-v*` tags, published separately from the server and desktop.

- **android-v3.4.0** — UI/UX pass: every screen now slides up over the previous one as a solid page, and phones are locked to portrait while tablets get multi-column grids, a centred bottom bar and a side-by-side Player. Failures now show a snackbar instead of failing silently, removals from playlists and the queue offer Undo, and deleting a playlist or removing a local folder asks first. Long album titles no longer show two copies at once, touch targets are 48dp, Library scroll-to-current works in track view, search history only keeps real searches, Home shelves refresh independently and show placeholders while loading, a failed re-connect no longer wipes working server credentials, and Settings has a "Check for updates" row. The home-screen widget now follows playback and its buttons work.
- **android-v3.3.14** — once the app is exempt from battery optimization, the "Playback stops automatically?" row and its button are hidden under Settings > Playback instead of showing a disabled "Already exempt" button.
- **android-v3.3.13** — fixed the "Update available" button never appearing: the update check now finds the newest Android release by comparing versions instead of trusting the order of GitHub's releases list.
- **android-v3.3.12** — in local folders mode the "Offline mode" setting is hidden under Settings > Playback, since local folders are already on the device and the toggle has no effect there.
- **android-v3.3.11** — when an update is available, an "Update available" button appears next to the app version in Settings and opens that release's page for an easy download. The update check now looks at the newest Android release specifically, instead of whichever release GitHub happens to list as latest.
- **android-v3.3.10** — fixed playback stopping at the end of a song with the screen off: a wake lock now keeps local playback advancing to the next track, and a foreground service keeps the app alive while casting to a Chromecast so the next track still loads. Added an "Exempt from battery optimization" button under Settings > Playback, and removed the screen-shrinking animation on the back gesture.
- **android-v3.3.9** — in local mode the Home page now scans the selected folder automatically after setup: a full-screen loader with the scanner bar shows while the first scan runs and hides itself once the library is filled. Scans are serialized app-wide, so Home and Library can no longer scan at the same time.
- **android-v3.3.8** — fixed the favorites toast saying "Already in favorites" when adding a song that wasn't favorited; playlists can be sorted by added date, title or artist, with an ascending/descending switch under the Play/Shuffle buttons; the player's favorite button shows a filled heart when the song is favorited.
- **android-v3.3.7** — in local mode the playlist download button is hidden and playlist cards show mosaic covers.
- **android-v3.3.6** — consistent add-to-playlist conflict checking across library, album, playlist and player screens; rename-file action in the track menu with a pre-flight collision check; Playlists bottom-nav icon is now a library-music icon.
- **android-v3.3.5** — downloaded playlists keep up with the server: when songs are added to one, they are downloaded automatically in the background, right after you add them in the app and within about 15 minutes for changes made elsewhere.
- **android-v3.3.4** — fixed the visual glitch on Android 16 when swiping back from Settings: the screen no longer shrinks into the home screen with its contents showing through. Settings, Player and Queue now slide down with the back gesture, and Settings and Library have an opaque background.
- **android-v3.3.3** — fixed the seek bar: the thumb can be grabbed and dragged again and tapping the bar seeks. The home header logo is back in its real colours.
- **android-v3.3.2** — fixed the queue and the playing track disagreeing (one song's audio with another song's artwork); queue edits are now applied one at a time and re-checked against the player.
- **android-v3.3.1** — larger seek touch area; the wave stops as soon as you touch the bar.
- **android-v3.3.0** — home header with the Muorg brand and a **Shuffle all** button.
- **android-v3.2.9** — the player's add-to-playlist button opens the playlist picker.
- **android-v3.2.8 / v3.2.7** — wave-animated seek line: it ripples behind the thumb while playing and straightens when paused or dragged.
- **android-v3.2.6 / v3.2.5** — the loading bar no longer flickers during playback and now animates while buffering.
- **android-v3.2.4** — a loading bar while a track streams in.
- **android-v3.2.3** — mini player artwork inset so it no longer breaks the island lines.
- **android-v3.2.2** — swipe down to dismiss the full player; tapping a second song no longer restarts the one already playing; dragging a track to the top of an empty queue starts your queue; offline mode shows only downloaded albums and tracks, with a placeholder on Home; 3-dots track menus on playlist and mix rows.
- **android-v3.2.1** — dragging a track across the **Your queue** / **Up next** divider moves it to the other section; offline mode is a hard, self-contained mode.

## v3.1.1

- **Server**: deterministic track ordering when pagination sort keys are tied.
- **Desktop**: distinguish same-titled albums by album artist.
- **Web App**: improve connection-form labels, API-key visibility accessibility, and connection-error announcements.
- **Android**: retain previously loaded report results and show a refresh warning if reports cannot be refreshed. APK is published separately as `android-v3.1.1`.

## v2.33.1

- **Web App**: Development section in Settings with a link to the GitHub repo

## v2.33.0

- **Server**: S3-compatible **cloud storage** — index and stream music from Hetzner Object Storage, Cloudflare R2, Backblaze B2, Wasabi, MinIO, or AWS S3 (`[[library.remotes]]`)
- **Server**: remote library scanner, cover cache for cloud tracks, range-passthrough streaming
- **Server**: metadata write, backup, restore, and rename for cloud tracks

## v2.32.x

- **v2.32.7** — Web App: disable double-tap-to-zoom in the PWA shell
- **v2.32.6** — Web App: dashboard nav icon, square playlist grid, pin playlists to top
- **v2.32.5** — Web App: favorite heart bounce animation, queue button moved to quick actions
- **v2.32.4** — Web App: fix missing list-view icon
- **v2.32.3** — Web App: switch to Mage Icons, player background stretched over top inset
- **v2.32.2** — Web App: disc icon for the library tab
- **v2.32.1** — Web App: new queue icon, keep library scroll position when navigating back
- **v2.32.0** — Web App: swipe between tabs, queue nav item dropped from the bottom bar

## v2.31.x

- **v2.31.1** — Web App: full-width layout on desktop, capped single-column content
- **v2.31.0** — Web App: layout grows out on tablet and desktop

## v2.30.x

- **v2.30.7** — Web App: larger artwork and quick actions on the player screen
- **v2.30.6** — Web App: back chevron on the queue and player screens
- **v2.30.5** — Web App: freeze now-playing bars while playback is paused
- **v2.30.4** — Web App: bottom nav padding tweaks
- **v2.30.3** — Web App: lift the bottom nav clear of the home indicator
- **v2.30.2 / v2.30.1** — Web App: bottom nav flush against the screen edge
- **v2.30.0** — Web App: icon-only bottom nav with safe-area offsets; tighter player/queue for mobile; per-component release pipeline

## v2.29.0

- **Web App**: mirror the Android client as a mobile-first **PWA**; VPS deploy workflow

## v2.28.0

- **Web + Server**: mobile-first revival (NER-128–134)

## v2.27.0 and earlier

See the [GitHub Releases page](https://github.com/Nergy101/Muorg/releases) for older releases.

---

### Versioning policy

Docs are versioned per release — see [Versioned docs](/docs/development#versioned-docs) for when a new docs snapshot is cut.

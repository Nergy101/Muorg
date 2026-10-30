---
title: Web App
description: The Muorg browser client — a mobile-first, installable PWA that connects to your muorg-server.
---

The Muorg web app is a browser UI (Vue 3 + Vite) that connects to a [muorg-server](/docs/server/). It is mobile-first, installable as a PWA, and mirrors the Android app's experience without an app store.

![Web app library grid](/img/screenshots/web-app-library.jpg)

## Connecting to a server

On first load the app asks for:

- **Server URL** — e.g. `http://192.168.1.50:7700`
- **API key** — the `api_key` from your `muorg-server.toml`

Both are saved in your browser's local storage and can be cleared again with **Settings → Log out**.

:::caution The server URL is not configured at deploy time
There is no `VITE_API_URL` or any other environment variable that points the web app at a server. The image is a plain static build; the connection is always entered by the user in the browser.
:::

:::caution CORS
The server's default `allowed_origins` only permits the desktop app. Before a browser can talk to your server, set `allowed_origins` in `[cors]` to include the web app's exact origin (e.g. `http://<host>:7800`). Avoid `["*"]` on public deployments. See [Configuration](/docs/server/configuration#cors).
:::

## Deploying

### Docker Compose with the server (recommended)

The repository's [docker-compose.yml](https://github.com/Nergy101/Muorg/blob/main/docker-compose.yml) runs the server on `7700` and the web app on `7800`:

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/docker-compose.yml
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml
# edit muorg-server.toml — api_key, content_paths, allowed_origins
docker compose up -d
```

Open `http://<host>:7800`.

### Docker on its own

```bash
docker run -d -p 7800:80 ghcr.io/nergy101/muorg-web:latest
```

The container serves the static build with nginx on port `80`; map it wherever you like.

### Static hosting

The build output is a plain static site — host it on any web server, S3 bucket or CDN:

```bash
cd web-client
pnpm install
pnpm build
# serve dist/
```

The app uses hash-based routing, so no server-side rewrite rules are required.

## Feature tour

![Web app album detail](/img/screenshots/web-app-album.png)

The full-screen player shows album art with a dominant-colour backdrop, seek bar and transport controls, and can toggle to synced lyrics when a track has them.

![Web app player](/img/screenshots/web-app-player.jpg)

The bottom bar has four tabs — **Home**, **Library**, **Playlists** and **Settings**. The queue and full player are reached from the mini player.

### Library

- Album **grid**, album **list** and flat **track list** layouts, cycled from the toolbar; long lists are virtualized.
- On screens ≥ 1024 px the track list becomes a **desktop-style table**: sortable columns (title, artist, album, year, duration, rating, format), resizable and hideable columns, three row densities, grouping by artist or album, a minimum-rating filter, inline star ratings, keyboard navigation and a right-click menu. Configure it in Settings → Tracks table.
- Search across albums and artists, with recent searches kept as chips you can re-run or clear.
- Sort by album, artist or year, ascending or descending, plus a **genre filter**.
- **Shuffle all** plays everything currently filtered.
- Tap an album for its tracks; add a whole album to a playlist in one action.
- **Multi-select** — long-press a row on touch, or click / Ctrl-click / Shift-click on desktop (Ctrl/⌘+A selects everything in the list). A selection bar offers add to playlist, add to queue, play next, edit metadata, rate and delete for all selected tracks. Rating or deleting more than one track asks for confirmation; rating changes can be undone from the toast.

### Editing metadata

**Edit metadata** (from a track's menu, the selection bar, or `E`) opens an editor — a full-height sheet on phones, a docked side panel on desktop:

- Title, artist, album, album artist, featuring, year, genre (with autocomplete), track and disc number; ReplayGain tags are shown read-only. The star rating saves immediately.
- **Cover art** from a file (picker or drag-and-drop), a Wikipedia search, or a URL on the server's image allowlist; clear it, or apply it to the whole album.
- **Bulk edit** — with several tracks selected, only the fields you touch are written.
- **Apply from path** fills fields from the folder and file names using the patterns in Settings → Metadata writing; **Look up on MusicBrainz** fetches suggestions to apply to the form or save straight away.
- **Restore from backup**, **rename file** (with a collision check) and **delete track**.
- Tags are written in place. Enable **Back up before writing** in Settings to keep a copy of the original first; the first write and every bulk edit show a warning while it is off. Every write shows an **Undo** toast.

### Playlists

- Create playlists with a name and an emoji icon; pin the ones you use most to the top.
- **Smart playlists** — create *and* edit rules right in the browser. Rules cover `rating`, `play count`, `genre`, `year`, `artist`, `album`, `title`, `last played` and `has cover`.
- Regular playlists support drag-to-reorder with an explicit save; smart playlists are ordered by their rules instead.
- Adding tracks that are already in a playlist asks whether to add anyway or skip the duplicates.
- On desktop, drag tracks from the table onto a playlist in the sidebar or on the Playlists page.
- **Export to M3U** with either stream URLs (which expire after 8 hours) or paths relative to a music root folder you set in Settings → Export.

### Reports, Smart Transform and statistics

- **Reports** (missing metadata, duplicates, missing cover, recently played, most played). The fields that count as "missing" are configurable. From a report you can save the result as a playlist, apply "from path" to every track, or **auto-tag all** (MusicBrainz matches of 90 % confidence or better fill only empty fields; the rest are listed for review).
- **Smart Transform** (Settings → Reports) bulk-renames files by rewriting a path pattern. It checks for collisions, never overwrites without an explicit choice, and asks for confirmation with the file count — renames cannot be undone.
- **Statistics** shows totals, metadata health, genre, year and rating distributions and top artists.

### Player and queue

- Full-screen player with large artwork, a background glow derived from the cover, draggable seek bar, shuffle, repeat (including repeat-one), a favourite toggle, and **volume and mute** (hidden on iOS, where the system controls volume).
- **ReplayGain** (Settings → Playback): track or album mode, preamp and clipping prevention. Gain is applied through the element volume, so it can attenuate but not boost above full volume.
- **Sleep timer** with presets from 5 to 90 minutes.
- Queue view with drag-to-reorder, a shuffle toggle, clear-all and **Save queue as playlist**. The queue and position are restored (paused) when you reopen the app.
- **Previous** restarts the track, and pressing it again steps back through what you played.
- **Lock-screen playback** — the next track is pre-buffered while the current one plays, so auto-advance keeps working when the screen is locked (the same handoff as gapless playback). On browsers that support it (Chrome/Edge on Android and desktop), a screen wake lock is held while playing; iOS Safari does not support wake locks, where the pre-buffered handoff carries the advance. If a track ever appears stuck after unlocking the phone, opening the app re-syncs it automatically.

### Keyboard shortcuts

Press `?` (or Ctrl/⌘+K) for the full list. Highlights: `Space`/`K` play-pause, `←`/`→` or `J`/`L` seek, `Ctrl+←`/`→` previous/next, `Shift+↑`/`↓` volume, `M` mute, `S` shuffle, `R` repeat, `F` full-screen player, `/` search, `G` then `H`/`L`/`P`/`Q`/`R`/`S` to jump between screens, `E` edit metadata, `Ctrl/⌘+A` select all. Browsers reserve Ctrl/⌘+T, +S and +1–9, so the desktop app's panel shortcuts are single keys here.

### Settings

Version and update status, install-as-app prompt, playback behaviour (continuous playback, what tapping the mini player does, offline mode, ReplayGain), default library sort and layout, table options, metadata-writing options, export options, theme (dark / light / follow system, plus a true-black OLED mode), offline downloads (cached tracks, storage used, downloaded playlists, clear cache), library statistics, server URL, refresh library, and log out.

### Folders

Settings → Library Folders lists every configured root with its track count, and lets you rescan one folder or all of them. **Add folder** opens a picker that browses the *server's* disk (not your device) via `GET /api/admin/browse`, confined to directories the operator allowed in `[library] browse_roots`; a typed-path field is there as a fallback when nothing is browsable. Removing a folder soft-deletes its tracks — a grace period runs before the catalog purges them — and leaves the files on disk untouched.

### Maintenance

Settings → Maintenance has one destructive action, **Purge removed tracks**: it permanently drops catalog rows for tracks whose folder was removed, bypassing the normal 30-day grace period. It asks for confirmation first and cannot be undone.

## PWA and offline behaviour

- **Installable** — from the browser's install prompt on Android and desktop Chrome, or **Share → Add to Home Screen** on iOS. Settings shows an *Install app* row when installation is possible.
- **Offline downloads** — a playlist's download button stores every track whole in Cache Storage (recent plays are cached automatically, up to 100 tracks and a 2 GiB cap). Settings → Offline shows what is cached and can clear it.
- **Offline mode** — the Settings → Playback toggle makes the player use no network calls at all: only downloaded tracks play, anything else shows "Not downloaded". With the server unreachable you also get an offline banner, and cached tracks keep playing.
- **Updates** — a new deployment does not reload you mid-listen. A banner appears saying a new version is available with a **Refresh now** button (also in Settings); the app checks again every 30 minutes while open.
- **Gestures** — swipe horizontally between the tabs; when installed as a PWA, swipe from the left edge to go back.

:::info Client ↔ server compatibility
See [Version Compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

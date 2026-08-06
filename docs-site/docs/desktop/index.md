---
sidebar_position: 1
---

# Desktop App

The Muorg desktop client is built with **Tauri 2 + Vue 3** and runs on macOS, Windows, and Linux. It works fully offline with local music folders, and can also connect to a [muorg-server](/docs/server/) for remote libraries.

:::note Screenshots
Desktop screenshots are still being captured — the [screenshots script](https://github.com/Nergy101/Muorg/blob/main/docs/screenshots/screenshots.sh) captures them; they land in `docs-site/static/img/screenshots/` and are referenced here.
:::

## Install

Download the installer for your platform from the [Releases page](https://github.com/Nergy101/Muorg/releases/latest):

| Platform | File |
|----------|------|
| macOS (Apple Silicon) | `Muorg_*_aarch64.dmg` |
| macOS (Intel) | `Muorg_*_x86_64.dmg` |
| Windows | `Muorg_*_x64-setup.exe` |
| Linux (Debian/Ubuntu) | `muorg_*_amd64.deb` |
| Linux (AppImage) | `Muorg_*_amd64.AppImage` |

On macOS, drag the app into your Applications folder. On Windows, run the setup wizard. On Linux, install the `.deb` (`sudo apt install ./muorg_*.deb`) or make the AppImage executable and run it.

## First Launch

When you start Muorg for the first time you are asked where your music lives. You can:

- **Add a local folder** — pick a directory on this machine. Muorg scans it recursively for music files.
- **Connect to a server** — point Muorg at a [muorg-server](/docs/server/) URL with your API key to browse a remote library instead.

You can add or remove folders later from **Settings → Music folders**.

## The Library

The library is the heart of the app. Two layouts are available from the top bar:

- **Table view** (default) — every track with album art, title, artist, album, year, duration, and path. Full-text search; group by album or artist; multi-select for bulk actions.
- **Album view** — browse your collection as a grid of album covers. Click an album to see its tracks.

### Search

Type in the search box to filter the current view. Search matches titles, artists, albums, and file names as you type.

### Multi-select

Select one or more tracks (hold `Shift` or `Cmd/Ctrl` while clicking, or use the checkbox column) to run bulk actions: edit metadata, add to a playlist, or remove from the library.

## Editing Metadata

Select any track to open the bottom panel, then open the **Metadata** tab:

- Edit title, artist, album, year, genre, track number, and more
- Embed or replace album art
- **Bulk edit** — select multiple tracks and apply a change to all of them at once
- **Auto-tag** — fetch tag suggestions from MusicBrainz and apply them

## Playlists

- Create, rename, delete, and reorder playlists
- **Smart playlists** — dynamic rules (e.g. "all tracks by artist X rated 4+") that update automatically as your library changes
- Export any playlist to **M3U**

## Reports

The Reports section finds problem tracks so you can clean them up:

- Tracks with **missing metadata** (e.g. no artist or album)
- **Duplicates** (same track hashed in multiple places)
- Tracks with **missing album art**

## Playback

Press play on any track to start playback. The player bar at the bottom offers play/pause, previous/next, seek, volume, mute, and shuffle. Click the maximize button to switch to a full player view with large album art.

## Sidecar server mode

When you add local folders, the desktop app can run a built-in **sidecar** muorg-server process so your other devices (web client, Android) can reach the same library. This is managed automatically — no configuration needed. See [Server](/docs/server/) if you want to run a standalone server instead.

:::info Client ↔ server compatibility
See [Version compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

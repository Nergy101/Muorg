---
title: Desktop App
description: The Muorg desktop client for macOS, Windows and Linux — library views, metadata editing, smart playlists, reports and playback.
---

The Muorg desktop client is built with **Tauri 2 + Vue 3** and runs on macOS, Windows and Linux. It is the most complete Muorg client: everything the metadata editor, reports and playlist tooling can do, it does here.

## Install

Download the installer for your platform from the [latest release](https://github.com/Nergy101/Muorg/releases/latest):

| Platform | File |
|----------|------|
| macOS (Apple Silicon) | `muorg-<version>-macos.dmg` |
| Windows (x64) | `muorg-<version>-windows.msi` |
| Linux (x64) | `muorg-<version>-linux.AppImage` |
| Linux (Debian/Ubuntu) | `muorg_*_amd64.deb`, inside `muorg-<version>-extras.zip` |

See [Install & Update](/docs/installation) for the full asset list and how the built-in updater works.

## How it connects

The desktop app is an HTTP client to a muorg-server — it just usually talks to its own.

- **Local mode (default)** — the app bundles a `muorg-server` binary and starts it automatically on launch, bound to `127.0.0.1:7700`. There is nothing to configure.
- **Online mode** — in **Settings → Connection**, switch to a remote server and enter its URL and API key to browse a shared library instead.

:::caution The bundled server is local-only
The sidecar binds to `127.0.0.1`, so other devices on your network **cannot** reach it. To share one library across desktop, browser and phone, run a standalone [muorg-server](/docs/server/) and point every client at that.
:::

## First launch

You are asked where your music lives:

- **Add a local folder** — pick a directory; Muorg scans it recursively for MP3 and FLAC files.
- **Connect to a server** — enter a [muorg-server](/docs/server/) URL and API key.

Folders can be added and removed later from the **Folders** tab in the sidebar. You can also drag files or folders onto the window to add them.

## The library

Two layouts, switchable in the toolbar and remembered in Settings:

- **Table view** (default) — one row per track with cover, title, artist, album, year, duration and path. Columns and row density are configurable.
- **Album grid** — your collection as album covers; click through to an album's tracks.

Search filters the current view as you type across titles, artists, albums and file names. Tracks can be grouped by album or artist, and sorted by any visible column.

**Multi-select** with `Shift`-click, `Cmd/Ctrl`-click or `Cmd/Ctrl+A`, then apply a bulk action: edit metadata, add to a playlist, add to the queue, or remove from the library.

## Editing metadata

Select a track to open the bottom panel and switch to the **Metadata** tab.

- Edit title, artist, album, album artist, featuring, year, genre (with autocomplete over genres already in your library), track number and disc number.
- Embed or replace **cover art**, including fetching one from the web.
- **Bulk edit** — with several tracks selected, only the fields you actually touch are written to all of them.
- **Apply from path** — pull artist/album/title out of the folder and file names using a configurable pattern.
- **Auto-tag** — fetch tag suggestions from [MusicBrainz](https://musicbrainz.org/) and apply them, per track or across a whole report.
- **Undo/redo** with `Cmd/Ctrl+Z` and `Cmd/Ctrl+Shift+Z` before you save.

### Backups

With **backup before write** enabled (Settings → Library), Muorg copies the original file before writing tags, and the editor offers a one-click **restore from backup**. If a write fails because the file or folder is read-only, the app offers to retry without the backup step.

## Playlists

- Create, rename, reorder and delete playlists; drag tracks to reorder within one.
- **Smart playlists** — rule-driven and always current. Rules combine `rating`, `play count`, `genre`, `year`, `artist`, `album`, `title`, `last played` and `has cover` with type-appropriate operators (numeric comparisons, text `is` / `contains` / `is empty`, and so on). Rules can be edited after creation.
- **Export to M3U** — from a playlist's context menu. Paths are written relative to the music root folder configured in **Settings → Exports**.

## Reports

The **Reports** tab in the sidebar shows live counts and opens a results table you can act on in bulk:

| Report | What it finds |
|--------|---------------|
| Missing metadata | Tracks missing any of the fields you selected in Settings |
| Duplicates | Tracks sharing the same artist + album + title |
| Missing album cover | Tracks with no embedded artwork |
| Recently played | Your recent listening |
| Most played | Your top tracks by play count |

From the results table you can **save the report as a playlist** for auditing, **apply from path** in bulk, or **auto-tag all**.

## Playback

The player bar offers play/pause, previous/next, seek, volume and mute, shuffle, a repeat cycle (off → all → one), a star rating for the current track, and a Chromecast button. Continuous playback carries on through the rest of the current list when the queue empties.

Press `Cmd/Ctrl+M` for the maximized player — large artwork with a colour glow derived from the cover — and `Escape` to go back.

**Chromecast**: the desktop app discovers and controls Cast devices through the server's cast API, so casting works against whichever server the app is currently connected to.

## Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Cmd/Ctrl+M` | Maximize the player |
| `Escape` | Leave the maximized player |
| `Cmd/Ctrl+S` | Show/hide the sidebar |
| `Cmd/Ctrl+A` | Select all tracks in the current view |
| `Cmd/Ctrl+Z` / `Cmd/Ctrl+Shift+Z` | Undo / redo in the metadata editor |

## Settings

**Settings** covers connection (local vs. remote server), theme, library layout (table/grid, density, visible columns), playback (autoplay, continuous playback, cover glow), ReplayGain mode, backup-before-write, the *apply from path* patterns, which fields count as "missing metadata", the M3U export root, the default sidebar tab, and the update check.

:::info Client ↔ server compatibility
See [Version Compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

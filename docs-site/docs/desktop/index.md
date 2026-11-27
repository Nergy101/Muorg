---
title: Desktop App
description: The Muorg desktop client for macOS, Windows and Linux — home, library, albums, artists, playlists, mixes, queue, reports, statistics, metadata editing and playback with a dynamic cover-derived accent.
---

The Muorg desktop client is built with **Tauri 2 + Vue 3** and runs on macOS, Windows and Linux. Since **v5.0.0** it shares the web client's design language: an app shell with a collapsible sidebar, a Home page, album/artist/playlist views, a glass mini-player and a maximized player — all tinted by a dynamic accent color derived from the playing album's cover.

## Install

Download the installer for your platform from the newest [`desktop-v*` release](https://github.com/Nergy101/Muorg/releases?q=desktop) (desktop releases ship on their own tags, separate from the server/web `v*` releases):

| Platform | File |
| ---------- | ------ |
| macOS (Apple Silicon) | `muorg-<version>-macos.dmg` |
| Windows (x64) | `muorg-<version>-windows.msi` |
| Linux (x64) | `muorg-<version>-linux.AppImage` |
| Linux (Debian/Ubuntu) | `muorg_*_amd64.deb`, inside `muorg-<version>-extras.zip` |

See [Install & Update](/docs/installation) for the full asset list and how the built-in updater works.

## How it connects

The desktop app is an HTTP client to a muorg-server — it just usually talks to its own.

- **Local mode (default)** — the app bundles a `muorg-server` binary and starts it automatically on launch, bound to `127.0.0.1:7700`. There is nothing to configure.
- **Online mode** — in **Settings → Server**, switch to a remote server and enter its URL and API key to browse a shared library instead.

:::caution The bundled server is local-only
The sidecar binds to `127.0.0.1`, so other devices on your network **cannot** reach it. To share one library across desktop, browser and phone, run a standalone [muorg-server](/docs/server/) and point every client at that.
:::

## First launch

You are asked where your music lives:

- **Add a local folder** — pick a directory; Muorg scans it recursively for MP3, FLAC and WAV files.
- **Connect to a server** — enter a [muorg-server](/docs/server/) URL and API key.

Folders can be added and removed later from the **Folders** section in the sidebar. You can also drag files or folders onto the window to add them.

## The app shell

- **Sidebar** — collapsible to an icon rail (click the Muorg logo to expand again). Home, Search, Library (albums / artists / tracks), Playlists, Mixes, Queue, Reports, Statistics, Settings.
- **Top bar** — back/forward navigation, a full-width search bar, and the layout switch on the Library views.
- **Mini-player** — a floating glass bar at the bottom with cover, marquee title/artist/album, transport controls, seek, volume, favorite and rating. Click it to open the **maximized player**.

## Home

**Recommended** albums and genre-based **mixes** (4-cover mosaics with a colored icon and a marquee of the artists in the mix) fill the first rows. Hover a card for play/shuffle; right-click for the context menu.

## The library

Three views, switchable from the top bar:

- **Albums** — your collection as album cards. Sort by year, album or artist; filter by genre. Hover a card for play/shuffle actions; right-click for the context menu (Play, Shuffle, Add to queue, View artist).
- **Artists** — artist cards with a mosaic of their first and last albums. Open one for their albums and a full track list.
- **Tracks** — compact track rows: index, cover, title (artist · album), format, rating, duration, and a 3-dots menu. Sort and genre controls work here too.

Search (top bar) filters across titles, artists, albums and file names, with recent searches and results as cards or track rows. Right-click a recent result to remove it or jump to its album/artist.

**Multi-select** with `Shift`-click, `Cmd/Ctrl`-click or `Cmd/Ctrl+A`, then apply a bulk action: edit metadata, add to a playlist, add to the queue, or remove from the library.

## Albums and artists

The **album view** shows the cover, artist (clickable), year, genre and track rows with format, rating and a 3-dots menu, plus Play / Shuffle / Add to queue / 3-dots actions in the album's own accent color. The **artist view** lists their albums as cards (hover for play/shuffle, right-click for the context menu) and all their tracks as rows.

## Editing metadata

Open a track and switch to the metadata editor.

- Edit title, artist, album, album artist, featuring, year, genre (with autocomplete over genres already in your library), track number and disc number.
- Embed or replace **cover art**, including fetching one from the web.
- **Bulk edit** — with several tracks selected, only the fields you actually touch are written to all of them.
- **Apply from path** — pull artist/album/title out of the folder and file names using a configurable pattern.
- **Auto-tag** — fetch tag suggestions from [MusicBrainz](https://musicbrainz.org/) and apply them, per track or across a whole report.
- **Undo/redo** with `Cmd/Ctrl+Z` and `Cmd/Ctrl+Shift+Z` before you save.

### Backups

With **backup before write** enabled (Settings → Library), Muorg copies the original file before writing tags, and the editor offers a one-click **restore from backup**. If a write fails because the file or folder is read-only, the app offers to retry without the backup step.

## Playlists and mixes

- Create, rename, pin, reorder and delete playlists; drag tracks to reorder within one. **Favorites** is always pinned on top with a gold star icon.
- **Smart playlists** — rule-driven and always current. Rules combine `rating`, `play count`, `genre`, `year`, `artist`, `album`, `title`, `last played` and `has cover` with type-appropriate operators (numeric comparisons, text `is` / `contains` / `is empty`, and so on). Rules can be edited after creation.
- **Mixes** — genre-based mixes shown on Home; open one to see it as a playlist view.
- **Export to M3U** — from a playlist's context menu. Paths are written relative to the music root folder configured in **Settings → Export & Rockbox**.

## Queue

Add tracks from any context menu (**Add to queue**). The **Queue** view lists them with index, cover, title, artist, album, format, rating, duration and a 3-dots menu; reorder by drag-and-drop. The maximized player has a collapsible queue side panel next to the controls.

## Reports and statistics

The **Reports** section shows live counts and opens a results list you can act on in bulk:

| Report | What it finds |
| -------- | --------------- |
| Missing metadata | Tracks missing any of the fields you selected in Settings |
| Duplicates | Tracks sharing the same artist + album + title |
| Missing album cover | Tracks with no embedded artwork |
| Recently played | Your recent listening |
| Most played | Your top tracks by play count |

From the results you can **save the report as a playlist** for auditing, **apply from path** in bulk, or **auto-tag all**.

**Statistics** shows your listening for the last 7 / 30 / 90 days: top tracks, artists and albums, genre distribution, tracks per release year, and format distribution.

## Playback

The **mini-player** offers play/pause, previous/next, seek, volume, shuffle, a repeat cycle (off → all → one), favorite and a star rating. The **maximized player** (click the mini-player) shows large album art over a blurred cover backdrop, a glass controls panel in the album's accent color, and a collapsible queue side panel. Press `Escape` to go back.

The **accent color** is derived from the playing album's cover (most common color, with a saturation/lightness clamp) and tints the mini-player, the maximized player, the progress and volume bars, and action buttons. You can pin a fixed accent in **Settings → Appearance** instead.

**Chromecast**: the desktop app discovers and controls Cast devices through the server's cast API, so casting works against whichever server the app is currently connected to.

## Keyboard shortcuts

| Shortcut | Action |
| ---------- | -------- |
| `Space` | Play / pause |
| `Escape` | Leave the maximized player |
| `Cmd/Ctrl+A` | Select all tracks in the current view |
| `Cmd/Ctrl+Z` / `Cmd/Ctrl+Shift+Z` | Undo / redo in the metadata editor |

## Settings

**Settings** is organized in collapsible sections: **Muorg Info** (version, update check), **Appearance** (theme, accent color, card layout — glass overlay or text below the card — and album art corners), **Playback** (queue behavior, mini player, audio), **Library** (sorting and result display), **Export & Rockbox**, **Smart Transform & Metadata Editing**, **Offline**, **Server** (connection, folders, device and library stats), and **Development**.

:::info Client ↔ server compatibility
See [Version Compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

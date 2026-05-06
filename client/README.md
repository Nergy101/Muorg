# Muorg — The Music Organizer from Hell

<div align="center">
  <img src="./public/favicon.svg" alt="Muorg logo" width="64" />
  <br /><br />
  <em>Pronounced “Mu-Ork” — think of a Musical Ork who organizes your music.</em>
</div>

A cross-platform desktop app that organizes your music library with a dense, library-style UI. Add folders of MP3 and FLAC files, browse and search your catalog, and edit embedded metadata—title, artist, album, year, album art, and more—so your collection stays consistent and findable.


**Platforms:** macOS, Windows, Linux

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting started](#getting-started)
- [Running Muorg](#running-muorg)
  - [Mac](#mac)
  - [Linux](#linux)
  - [Windows](#windows)
- [Project structure](#project-structure)
- [Creating a release (GitHub)](#creating-a-release-github)
- [Documentation](#documentation)
- [License](#license)

---

## ✨ Features

- 📁 **Library** — Add folders (or drag-and-drop); Muorg scans for `.mp3` and `.flac` and builds a persistent catalog in SQLite. Rescan or remove folders from the sidebar. **Hide/show folders** in the table (per-root toggle); expand or collapse all groups.
- 📋 **Library view** — Table with album art, title, artist, album, year, duration, format, and path. Full-text search across title, artist, and album. Group by album or artist (collapsible; album art in group header when all tracks share the same art). **Multi-select** (Ctrl+A) for bulk actions.
- 📂 **Playlists** — Sidebar has **Folders** and **Playlists** tabs. Create, rename, and delete playlists; add tracks via context menu or drag-and-drop (with duplicate handling). Click a playlist to filter the library to that playlist’s tracks. **Export playlists** to M3U (relative paths; Music Root Folder in Settings → Exports).
- 📋 **Queue** — Right-click tracks or groups and choose **Add to queue**. Bottom bar has a **Queue** tab (Ctrl+Q) with a reorderable list, play-from-queue, and clear. Next/previous follow the queue when it has items (unless shuffle is on). Queue panel width and bottom panel height are resizable and saved in Settings.
- ✏️ **Metadata editor** — Edit tags (title, artist, album, album artist, year, genre, track/disc number) and embed or clear album art. **Apply from path** uses the path-format template (Settings → Smart Suggestions) to suggest title, artist, album, track number, etc. **From Wikipedia**: fetch album art from Wikipedia (can be hidden in Smart Suggestions). Save to files; catalog updates automatically. **Bulk edit:** select multiple tracks and change only the fields you edit—other fields stay per-track.
- ▶️ **Playback** — Player bar: play/pause, previous, next, restart, seek, volume, mute. **Shuffle** and **continuous playback** (Settings → Playback). When the queue is filled and shuffle is off, next/previous use the queue. **Maximized player** (Ctrl+S): full-screen view with large album art, gradient, and glow from artwork. Bottom panel tabs: **Library**, **Metadata**, **Play**, **Queue** (default tab and resizable layout in Settings).
- 📊 **Reports** — Sidebar: **Missing metadata**, **Duplicates** (same artist+album+title), and **Missing album cover**. Click to open a modal and jump to a track. Configurable “missing” fields and optional album art in Missing metadata report (Settings → Reports). Option to hide the reports section (Settings → Layout).
- 🎨 **Theming** — Auto (follow system), Dark, Light, Orkish (green tints), and DOOM. **Settings** (sidebar): General (e.g. check for updates), Theme, Playback, Keyboard, Layout (density, columns, default bottom panel, sidebar/reports visibility, column widths, queue/panel size), Reports, Exports, Smart Suggestions. **Key map** (Ctrl+K): shortcuts for search, refresh, panels, queue, select all, navigation, and play.

See [plan.md](./plan.md) for the roadmap and priorities.

---

## 🛠️ Tech stack

| Layer      | Choice              |
|-----------|----------------------|
| Desktop   | **Tauri 2** (Rust + web frontend) |
| Frontend  | **Vue 3** + **TypeScript** |
| Styling   | **Tailwind CSS**     |
| State     | **Pinia**            |
| Catalog   | **SQLite**           |
| Metadata  | Rust: `id3` (MP3), FLAC crates (FLAC) |

---

## 📋 Prerequisites

- [Node.js](https://nodejs.org/) (LTS) and [pnpm](https://pnpm.io/)
- [Rust](https://www.rust-lang.org/) (latest stable)
- [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/) for your OS (e.g. system deps for Linux, WebView2 on Windows)

---

## 🚀 Getting started

```bash
# Install dependencies (from project root)
pnpm install

# Run in development
pnpm tauri dev

# Build for production (installer for current OS)
pnpm tauri build
```

---

## ▶️ Running Muorg

### 🍎 Mac

If macOS says **“Muorg.app is damaged and can’t be opened”** (Gatekeeper quarantine on unsigned builds), allow it via System Settings: open **System Settings → Privacy & Security**, scroll down to the security message about Muorg, then click **Open Muorg** (or **Open Anyway**). After that, open Muorg from Finder or Spotlight as usual.

### 🐧 Linux

Install or run the build artifact for your distro (e.g. **AppImage** or **.deb** from [releases](https://github.com/your-repo/Muorg/releases)). For an AppImage, make it executable and run it:

```bash
chmod +x Muorg-*.AppImage
./Muorg-*.AppImage
```

For a .deb package, install with your package manager and start Muorg from your application menu.

### Mock mode (Tauri with fake data)

To run the full Tauri app with a fixed fake catalog (no real library scan):

```bash
pnpm run dev:mock
```

This opens the desktop app with the album **A Kiss for the Whole World** (12 tracks) as the only catalog. The UI is viewable; playback and other features may be limited with mock data.

### 🪟 Windows

Download the **MSI** or **EXE** installer from [releases](https://github.com/your-repo/Muorg/releases), run the installer, then launch Muorg from the Start menu or desktop shortcut.

---

## 📁 Project structure

```
muorg/
├── src-tauri/          # Tauri (Rust) backend
│   ├── src/
│   │   ├── metadata/   # MP3/FLAC read & write
│   │   ├── catalog/    # Scan, index, SQLite
│   │   └── commands.rs # Tauri commands (scan, get_tracks, write_metadata, …)
│   └── ...
├── src/                # Vue frontend
│   ├── components/     # LibraryTable, MetadataEditor, AlbumCover, …
│   ├── composables/    # useCatalog, useSelection, …
│   ├── stores/         # Pinia stores
│   └── App.vue
├── plan.md             # Vision, features, technical direction
├── agent.md            # Contributor & AI agent guide
└── README.md
```

---

## 🏷️ Creating a release (GitHub)

Releases are built and published via GitHub Actions.

### 🔄 Alpha builds (automatic)

- **Push to `main`** or run the workflow manually (**Actions → Build and Release → Run workflow**).
- The workflow builds the app for macOS (Apple Silicon + Intel), Windows, and Linux.
- Version is set to `0.1.0-alpha.<run_number>` (e.g. `0.1.0-alpha.42`). The run number increments with each workflow run.
- A **prerelease** is created with that version; installers (DMG, MSI/EXE, AppImage/Deb) are attached.

### 🏷️ Stable releases (tagged)

Use the release script in `scripts/` to bump the version in `package.json`, `src-tauri/tauri.conf.json`, and `src-tauri/Cargo.toml`, commit, create a `v*` tag, and push:

```bash
./scripts/release.sh 0.2.0
```

The **Build and Release** workflow runs on the new tag, builds all platforms, and creates a **full release** (not prerelease) with that version and the installers attached.

### 📊 Pipeline summary

| Trigger              | Version format           | Release type |
|----------------------|--------------------------|--------------|
| Push to `main`       | `0.1.0-alpha.<run_number>` | Prerelease   |
| Manual run           | same as above            | Prerelease   |
| Push tag `v*` (e.g. `v0.2.0`) | From tag (e.g. `0.2.0`) | Full release |

### 🔄 Auto-updates

The app can check for updates from **Settings → General → Check for updates**. It uses the [Tauri updater plugin](https://v2.tauri.app/plugin/updater/) and the endpoints configured in `src-tauri/tauri.conf.json` (e.g. a `latest.json` on GitHub Releases).

To **build signed updater artifacts** (required for the in-app updater to install them), set the signing key in the environment before building (do **not** use `.env` files; Tauri will not read them):

```bash
export TAURI_SIGNING_PRIVATE_KEY="$(cat /path/to/your/private-key.pem)"
# Optional: inject public key so you don't commit it
export TAURI_SIGNING_PUBLIC_KEY="$(cat /path/to/your/public-key.pub)"
pnpm tauri build
```

Generate keys once with: `pnpm tauri signer generate -w ~/.tauri/muorg.key`. Put the **public key** content in `tauri.conf.json` under `plugins.updater.pubkey` (or use `TAURI_SIGNING_PUBLIC_KEY` when running the script). Each release that should be installable via “Check for updates” must include a `latest.json` (or equivalent) in the format expected by the Tauri updater (see the plugin docs).

### ⚠️ macOS: “App is damaged” when opening

Releases are not signed or notarized with an Apple Developer certificate. After downloading the `.app` (from a DMG or the release assets), macOS may say **“Muorg.app is damaged and can’t be opened”**. This is Gatekeeper quarantining the app.

**Fix:** Open **System Settings → Privacy & Security**, scroll down to the security message about Muorg, then click **Open Muorg** (or **Open Anyway**). After that, open Muorg from Finder or Spotlight as normal.

### ✅ Pre-merge checks (Build and Lint)

The **Build and Lint** workflow (`.github/workflows/build.yml`) runs on every push to `main` and on every pull request targeting `main`. It runs in parallel:

- **Frontend:** `pnpm install --frozen-lockfile`, then `pnpm build` (TypeScript check + Vite build).
- **Rust:** `cargo check` and `cargo clippy` (with `-D warnings`).

Use it to validate changes before merging to `main`. You can run the same checks locally:

```bash
pnpm run check          # frontend + Rust (TypeScript build, cargo check, clippy)
pnpm run check:frontend # TypeScript check + Vite build only
pnpm run check:rust     # cargo check + clippy (requires Rust) only
```

---

## 📚 Documentation

- **[plan.md](./plan.md)** — Vision, goals, feature list (P1/P2/P3), UI concept, risks, success criteria.
- **[agent.md](./agent.md)** — Tech stack, repo layout, conventions, run/build, and guidance for contributors and AI agents.

---

## 📄 License

See repository for license information.

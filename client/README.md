# Muorg — The Music Organizer from Hell

<div align="center">
  <img src="./public/favicon.svg" alt="Muorg logo" width="64" />
  <br /><br />
  <em>Pronounced “Mu-Ork” — think of a Musical Ork who organizes your music.</em>
</div>

A cross-platform desktop app that organizes your music library with a dense, library-style UI. Add folders of MP3, FLAC and WAV files, browse and search your catalog, and edit embedded metadata—title, artist, album, year, album art, and more—so your collection stays consistent and findable.


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

## ✨ Features (v5 redesign)

- 🏠 **Home** — Recommended albums and genre-based mixes (4-cover mosaics with colored icons) to jump straight back in.
- 📁 **Library** — Add folders (or drag-and-drop); Muorg scans for `.mp3`, `.flac` and `.wav` and builds a persistent catalog in SQLite. Rescan or remove folders from the sidebar.
- 💿 **Albums / Artists / Tracks views** — Album cards and artist cards (with an album mosaic), or compact track rows. Sort by year, album or artist; filter by genre; full-text search with recent results. Card layout (glass overlay text vs. text below the card) and rounded/square album art are configurable in Settings → Appearance.
- 📂 **Playlists & mixes** — Create, rename, pin and delete playlists; **Favorites** always on top with a gold star. Smart playlists stay current by rule. Mixes open as a playlist view. **Export playlists** to M3U (relative paths; Music Root Folder in Settings → Exports).
- 📋 **Queue** — Add tracks from any context menu (**Add to queue**). Dedicated queue view and a collapsible queue side panel in the maximized player; reorderable, with per-track format, rating and actions. Next/previous follow the queue when it has items (unless shuffle is on).
- ✏️ **Metadata editor** — Edit tags (title, artist, album, album artist, year, genre, track/disc number) and embed or clear album art. **Apply from path** uses the path-format template (Settings → Smart Transform) to suggest title, artist, album, track number, etc. Save to files; catalog updates automatically. **Bulk edit:** select multiple tracks and change only the fields you edit—other fields stay per-track.
- ▶️ **Playback** — Glass **mini-player** with seek, volume, marquee title/artist and favorite/rating actions; a **maximized player** with large album art, a collapsible queue side panel, and Chromecast. The **accent color is derived from the playing album's cover** and tints the player, progress bar and controls. Shuffle and continuous playback in Settings → Playback.
- 📊 **Reports & Statistics** — Missing metadata, duplicates, missing album cover, recently/most played. **Statistics**: top tracks/artists/albums per time range, genre and format distribution, tracks per release year.
- 🎨 **Theming** — Auto (follow system), Dark, Light, Orkish, and DOOM; accent-color presets or fully dynamic cover-derived accents. **Settings** is organized in collapsible sections: Muorg Info, Appearance (theme, accent, card layout, album art corners), Playback, Library, Export & Rockbox, Smart Transform & Metadata Editing, Offline, Server, Development.

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

Install or run the build artifact for your distro (e.g. **AppImage** or **.deb** from [releases](https://github.com/Nergy101/Muorg/releases)). For an AppImage, make it executable and run it:

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

Download the **MSI** or **EXE** installer from [releases](https://github.com/Nergy101/Muorg/releases), run the installer, then launch Muorg from the Start menu or desktop shortcut.

---

## 📁 Project structure

```
client/
├── src-tauri/          # Tauri (Rust) backend + bundled muorg-server sidecar
│   ├── src/            # sidecar process management, updater, Chromecast
│   └── binaries/       # muorg-server-<target> sidecar binaries (per platform)
├── src/                # Vue 3 frontend
│   ├── views/          # HomeView, AlbumsView, ArtistsView, ArtistDetailView,
│   │                   # PlaylistsView, PlaylistDetailView, MixDetailView,
│   │                   # QueueView, ReportsView, StatsView, SettingsView, …
│   ├── components/     # layout/ (Sidebar, TopBar), library/, playback/ (PlayerBar,
│   │                   # PlayScreenPlayBar), shared/, metadata/, modals/, stats/
│   ├── state/          # navigation (view state + history), toast
│   ├── stores/         # Pinia stores (settings, playlists, playback, …)
│   ├── composables/    # useDynamicAccent, useAlbums, useMarquee, …
│   └── App.vue         # app shell: sidebar + topbar + view + mini-player
├── plan.md             # Vision, features, technical direction
├── agent.md            # Contributor & AI agent guide
└── README.md
```

---

## 🏷️ Creating a release (GitHub)

Desktop releases are built and published via GitHub Actions in their own
workflow, `.github/workflows/release-desktop.yml`, so the ~25-minute Tauri
matrix never rides along on a server- or web-only `v*` release.

### 🏷️ Desktop releases (tagged `desktop-v*`)

Use the desktop release script to bump the version in `client/package.json`,
`client/src-tauri/tauri.conf.json`, and `client/src-tauri/Cargo.toml`, commit,
create a `desktop-v*` tag, and push:

```bash
../scripts/release-desktop.sh 5.0.0
```

The **Release Desktop** workflow runs on the new tag, builds all platforms
(macOS Apple Silicon, Windows x64, Linux x64 — each with its own bundled
`muorg-server` sidecar), and creates a **full release** (not the repo's
"latest") with the installers attached: `muorg-<version>-macos.dmg`,
`muorg-<version>-windows.msi`, `muorg-<version>-linux.AppImage`, the macOS
updater bundle, and an `extras.zip` with signatures and the `.deb`.

You can also trigger the workflow manually (**Actions → Release Desktop → Run
workflow**) and enter a version — no tag push needed.

### 📊 Pipeline summary

| Trigger | Workflow | Release |
|---------|----------|---------|
| Push tag `desktop-v*` (e.g. `desktop-v5.0.0`) | `release-desktop.yml` | Full release `desktop-v5.0.0` with desktop installers |
| Manual run of `release-desktop.yml` | `release-desktop.yml` | Full release for the entered version |
| Push tag `v*` | `release.yml` | Server / web / docs Docker images only |
| Push tag `android-v*` | `release-android.yml` | Android APK |

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

### ✅ Pre-merge checks (Build)

The **Build** workflow (`.github/workflows/build.yml`) runs on every push to `main` and on every pull request targeting `main`. It runs in parallel:

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

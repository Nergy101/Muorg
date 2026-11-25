<div align="center">
  <img src="web-client/public/favicon.svg" alt="Muorg logo" width="80" />
  <h1>Muorg — The Music Organizer from Hell</h1>
  <em>Pronounced "Mu-Ork" — think of a Musical Ork who organizes your music.</em>
  <br /><br />

  [![Build](https://github.com/Nergy101/Muorg/actions/workflows/build.yml/badge.svg)](https://github.com/Nergy101/Muorg/actions/workflows/build.yml)
  [![Latest Release](https://img.shields.io/github/v/release/Nergy101/Muorg)](https://github.com/Nergy101/Muorg/releases/latest)
  [![Docker Server](https://img.shields.io/github/v/release/Nergy101/Muorg?label=docker%20server&color=0db7ed)](https://github.com/Nergy101/Muorg/pkgs/container/muorg-server)
  [![Docker Web](https://img.shields.io/github/v/release/Nergy101/Muorg?label=docker%20web&color=0db7ed)](https://github.com/Nergy101/Muorg/pkgs/container/muorg-web)
  [![Docs](https://img.shields.io/badge/docs-docs.muorg.nergy.space-blue)](https://docs.muorg.nergy.space)
  ![Platforms](https://img.shields.io/badge/platform-Web%20%7C%20Android%20%7C%20macOS%20%7C%20Windows%20%7C%20Linux-lightgrey)
</div>

---

A music library organizer with a dense, library-style UI — a cross-platform desktop app (macOS, Windows, Linux), a web app and an Android app that connect to the same self-hosted server. Add folders of MP3, FLAC and WAV files, browse and search your catalog, and edit embedded metadata—title, artist, album, year, album art, and more—so your collection stays consistent and findable.

> **Desktop v5.0.0 is here** — a full redesign of the desktop app: a new app shell with collapsible sidebar, Home with recommended albums and mixes, album/artist/playlist views, a glass mini-player and maximized player with a queue side panel, dynamic accent colors derived from album art, statistics, and a rebuilt Settings. Desktop releases now ship on their own `desktop-v*` tags (see [Releases](https://github.com/Nergy101/Muorg/releases)).

<div align="center">
  <img src="assets/orc/orc-empty.svg" alt="Muorg Orc shrugging while holding an empty crate" width="110" />
  <img src="assets/orc/orc-not-found.svg" alt="Muorg Orc scratching its head and holding an empty record sleeve" width="110" />
  <img src="assets/orc/orc-forbidden.svg" alt="Muorg Orc bouncer in sunglasses with crossed arms" width="110" />
  <img src="assets/orc/orc-server-error.svg" alt="Muorg Orc panicking next to a guitar with a snapped string" width="110" />
  <img src="assets/orc/orc-maintenance.svg" alt="Muorg Orc in a hard hat holding a tuning fork and a wrench" width="110" />
  <br />
  <em>Meet the Muorg Orc — he shows up when something's missing, forbidden, or broken.</em>
</div>

Muorg works in two modes:
- **Local** — music files live on your own machine; no remote server needed.
- **Online** — connect to a remote **muorg-server** (e.g. on a NAS) where your music files are stored, and access your full library from any device.

---

## Table of contents

- [Documentation](#-documentation)
- [Download & install](#-download--install)
- [Features](#-features)
- [Repository layout](#-repository-layout)
- [Self-hosting with Docker](#-self-hosting-with-docker)
- [Building from source](#-building-from-source)
- [Contributing](#-contributing)
- [License](#-license)

---

## 📚 Documentation

Full user and developer documentation — quick start, installation, configuration, API reference, and per-platform guides — lives at **[docs.muorg.nergy.space](https://docs.muorg.nergy.space)**.

---

## ⬇️ Download & install

Grab the latest build for your platform from the [Releases page](https://github.com/Nergy101/Muorg/releases/latest):

| Platform | File | Release |
|----------|------|---------|
| macOS (Apple Silicon) | `muorg-*-macos.dmg` | newest [`desktop-v*` release](https://github.com/Nergy101/Muorg/releases?q=desktop-v) |
| Windows | `muorg-*-windows.msi` | newest [`desktop-v*` release](https://github.com/Nergy101/Muorg/releases?q=desktop-v) |
| Linux (universal) | `muorg-*-linux.AppImage` | newest [`desktop-v*` release](https://github.com/Nergy101/Muorg/releases?q=desktop-v) |

The server, web app and docs ship on the main [`v*` releases](https://github.com/Nergy101/Muorg/releases?q=v), and the Android app on its own [`android-v*` releases](https://github.com/Nergy101/Muorg/releases?q=android-v) — take `muorg-*.apk` from the newest one.

> **macOS note:** Releases are not signed with an Apple Developer certificate. If macOS says *"Muorg.app is damaged and can't be opened"*, open **System Settings → Privacy & Security**, scroll to the Muorg entry, and click **Open Anyway**.

> **Linux AppImage:** `chmod +x muorg-*.AppImage && ./muorg-*.AppImage`

> **Intel Macs:** not built. The macOS job targets `aarch64-apple-darwin` only —
> build from source if you need an x86_64 binary.

---

## ✨ Features

- **🏠 Home** — Recommended albums and genre-based mixes to jump straight back in.
- **📁 Library** — Add folders (or drag-and-drop); Muorg scans for `.mp3`, `.flac` and `.wav` and builds a persistent SQLite catalog. Rescan or remove folders at any time.
- **💿 Albums, artists & tracks** — Browse the collection as album cards or artist cards (with a mosaic of their albums), or as compact track rows. Sort by year, album or artist; filter by genre. Full-text search with recent results.
- **📂 Playlists & mixes** — Create, rename, pin and delete playlists (Favorites always on top, with a gold star). Smart playlists stay current by rule. Export to M3U.
- **📋 Queue** — Add tracks from any context menu; reorderable queue view with per-track actions. Next/previous follow the queue.
- **✏️ Metadata editor** — Edit tags (title, artist, album, album artist, year, genre, track/disc number) and embed or clear album art. Bulk-edit multiple tracks at once. **Smart Suggestions**: apply tags from the file path using a configurable template.
- **▶️ Playback** — Glass mini-player with seek, volume and marquee titles; a maximized player with large album art, a collapsible queue side panel, and Chromecast. Dynamic accent color derived from the playing album's cover tints the whole player.
- **📊 Reports & statistics** — Missing metadata, duplicate tracks, and missing album art — click to jump directly to the offending track. Statistics with top tracks/artists/albums, genre and format charts, and tracks per release year.
- **🎨 Theming** — Auto (system), Dark, Light, Orkish, and DOOM, plus accent-color presets or fully dynamic cover-derived accents. Card layout (glass overlay or text below), rounded or square album art, and more in Settings.

---

## 📁 Repository layout

This is a monorepo holding one server and three clients, plus the code they
share:

```
Muorg/
├── client/          # Desktop app (Tauri 2 + Vue 3 + Rust)
│   └── README.md    # Developer setup, build, and release docs
├── web-client/      # Browser-based UI (Vue 3 + Vite, served via nginx)
├── android-client/  # Android app (Kotlin + Jetpack Compose + Media3)
├── server/          # Standalone REST API (Rust + Axum)
│   └── README.md    # Docker quick-start and configuration reference
├── docs-site/       # Documentation site (Docusaurus)
├── src/             # Frontend code shared by the desktop and web apps
│   └── api/         # Generated API client — see api/README.md
└── scripts/
    ├── release.sh              # Bump version, tag v*, push server/web/docs release
    ├── release-desktop.sh      # Desktop-only release (desktop-v* tag, all platforms)
    ├── release-android.sh      # Android-only release (android-v* tag)
    └── generate-api-clients.sh # Regenerate the OpenAPI spec and every client
```

| Component | What it does | When you need it |
|-----------|-------------|-----------------|
| `client/` | Cross-platform desktop app (macOS, Windows, Linux) | On your desktop — local library or connected to a server |
| `web-client/` | Browser UI connecting to muorg-server | When you want web access alongside or instead of the desktop app |
| `android-client/` | Android app connecting to muorg-server | On a phone or tablet |
| `server/` | HTTP backend for remote/NAS use | For home-server / multi-device setups |
| `src/` | Shared frontend code and the generated API client | When changing anything both the desktop and web apps use |

### The API contract

The server describes itself in OpenAPI, and every client's API layer is
generated from that description rather than hand-written — the TypeScript types
the desktop and web apps share, and the Kotlin models and Retrofit interface the
Android app uses. `scripts/generate-api-clients.sh` regenerates all of it, and
CI fails if the checked-in output has drifted from the server's routes.

**[src/api/README.md](src/api/README.md)** documents that pipeline: what is
generated, from where, and what to do when a route changes.

For full developer and configuration details see **[client/README.md](client/README.md)** and **[server/README.md](server/README.md)**.

---

## 🐳 Self-hosting with Docker

### Server only (desktop app in Online mode)

Run muorg-server on a NAS or home server and connect to it from the desktop app in **Online** mode.

```bash
# 1. Grab the compose file
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml

# 2. Edit muorg-server.toml — set api_key and content_paths

# 3. Start
docker compose up -d
```

The API listens on port **7700**. In the desktop app, go to **Settings → Connection**, switch to **Online**, and enter your server URL and API key.

> **Warning — CORS:** in `muorg-server.toml`, never set `cors.allowed_origins` to `["*"]` on a public deployment. `*` allows **any** website to call the server — any page you visit could read your library and change your playlists using your API key. List the origins you actually use explicitly: the desktop app (`tauri://localhost`, `http://tauri.localhost`), the web UI dev server (`http://localhost:7800`), and your web UI's production URL.

### Server + Web UI (browser access)

Run both muorg-server and the muorg-web browser interface together. Drop this `docker-compose.yml` next to your `muorg-server.toml`:

```yaml
services:
  muorg-server:
    image: ghcr.io/nergy101/muorg-server:latest
    ports:
      - "7700:7700"
    volumes:
      - ./muorg-server.toml:/app/muorg-server.toml:ro
      - muorg-data:/data
      - /path/to/your/music:/music:ro
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "sh", "-c", "curl -sf http://localhost:7700/api/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 10s

  muorg-web:
    image: ghcr.io/nergy101/muorg-web:latest
    ports:
      - "7800:80"
    restart: unless-stopped
    depends_on:
      muorg-server:
        condition: service_healthy

volumes:
  muorg-data:
```

Then start with:

```bash
docker compose up -d
```

- **muorg-server** → `http://<your-host>:7700`
- **muorg-web** → `http://<your-host>:7800` — open this in any browser and enter the server URL and API key to connect.

See the [server README](server/README.md) for the full configuration reference and security notes.

---

## 🛠️ Building from source

### Prerequisites

- [Node.js](https://nodejs.org/) (LTS) + [pnpm](https://pnpm.io/)
- [Rust](https://www.rust-lang.org/) (latest stable)
- [Tauri v2 system dependencies](https://v2.tauri.app/start/prerequisites/) for your OS
- For the Android app only: JDK 17 and the Android SDK

### Desktop app

```bash
cd client
pnpm install

# Development (hot-reload)
pnpm run tauri dev

# Production build (installer for current OS)
pnpm run tauri build

# Cut a desktop release for all platforms (macOS/Windows/Linux) on a
# desktop-v* tag — runs .github/workflows/release-desktop.yml
../scripts/release-desktop.sh 5.0.0
```

### Web app

```bash
cd web-client
pnpm install
pnpm dev        # or: pnpm build
```

### Android app

Needs a JDK 17 and the Android SDK; `ANDROID_HOME` must point at it.

```bash
cd android-client
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### Server (standalone binary)

```bash
cd server
cp muorg-server.example.toml muorg-server.toml
# edit muorg-server.toml
cargo run --release --bin muorg-server
```

### After changing a server route

The clients' API layers are generated from the server's OpenAPI description, and
CI fails if the checked-in output has drifted:

```bash
./scripts/generate-api-clients.sh          # regenerate, then commit the result
./scripts/generate-api-clients.sh --check  # what CI runs
```

See [src/api/README.md](src/api/README.md) for what that covers.

Full developer documentation lives in [client/README.md](client/README.md) and [server/README.md](server/README.md).

---

## 🤝 Contributing

1. Fork the repo and create a feature branch.
2. Make your changes inside `client/`, `server/`, or `web-client/` (whichever applies).
3. Run the pre-merge checks locally:
   ```bash
   cd client
   pnpm run check   # TypeScript build + cargo check + clippy
   ```
4. Open a pull request against `main`. The CI pipeline will build all platforms and run linting automatically.

Please open an issue first for anything non-trivial so we can agree on the approach.

---

## 📄 License

See [LICENSE](LICENSE) for details.

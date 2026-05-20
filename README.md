<div align="center">
  <img src="client/public/favicon.svg" alt="Muorg logo" width="80" />
  <h1>Muorg — The Music Organizer from Hell</h1>
  <em>Pronounced "Mu-Ork" — think of a Musical Ork who organizes your music.</em>
  <br /><br />

  [![Build](https://github.com/Nergy101/Muorg/actions/workflows/build.yml/badge.svg)](https://github.com/Nergy101/Muorg/actions/workflows/build.yml)
  [![Latest Release](https://img.shields.io/github/v/release/Nergy101/Muorg)](https://github.com/Nergy101/Muorg/releases/latest)
  [![Docker Server](https://img.shields.io/docker/v/nergy101/muorg-server?label=docker%20server&color=0db7ed)](https://hub.docker.com/r/nergy101/muorg-server)
  [![Docker Web](https://img.shields.io/docker/v/nergy101/muorg-web?label=docker%20web&color=0db7ed)](https://hub.docker.com/r/nergy101/muorg-web)
  ![Platforms](https://img.shields.io/badge/platform-macOS%20%7C%20Windows%20%7C%20Linux-lightgrey)
</div>

---

A cross-platform desktop app that organizes your music library with a dense, library-style UI. Add folders of MP3 and FLAC files, browse and search your catalog, and edit embedded metadata—title, artist, album, year, album art, and more—so your collection stays consistent and findable.

Muorg works in two modes:
- **Local** — music files live on your own machine; no remote server needed.
- **Online** — connect to a remote **muorg-server** (e.g. on a NAS) where your music files are stored, and access your full library from any device.

---

## Table of contents

- [Download & install](#-download--install)
- [Features](#-features)
- [Repository layout](#-repository-layout)
- [Self-hosting with Docker](#-self-hosting-with-docker)
- [Building from source](#-building-from-source)
- [Contributing](#-contributing)
- [License](#-license)

---

## ⬇️ Download & install

Grab the latest installer for your OS from the [Releases page](https://github.com/Nergy101/Muorg/releases/latest):

| Platform | File |
|----------|------|
| macOS (Apple Silicon) | `Muorg_*_aarch64.dmg` |
| macOS (Intel) | `Muorg_*_x86_64.dmg` |
| Windows | `Muorg_*_x64-setup.exe` or `Muorg_*_x64_en-US.msi` |
| Linux (Debian / Ubuntu) | `muorg_*_amd64.deb` |
| Linux (universal) | `Muorg_*_amd64.AppImage` |

> **macOS note:** Releases are not signed with an Apple Developer certificate. If macOS says *"Muorg.app is damaged and can't be opened"*, open **System Settings → Privacy & Security**, scroll to the Muorg entry, and click **Open Anyway**.

> **Linux AppImage:** `chmod +x Muorg-*.AppImage && ./Muorg-*.AppImage`

---

## ✨ Features

- **📁 Library** — Add folders (or drag-and-drop); Muorg scans for `.mp3` and `.flac` and builds a persistent SQLite catalog. Rescan or remove folders at any time.
- **📋 Library view** — Table with album art, title, artist, album, year, duration, and path. Full-text search; group by album or artist; multi-select for bulk actions.
- **💿 Album view** — Browse your collection as an album grid with cover art. Click an album to see its tracks.
- **📂 Playlists** — Create, rename, and delete playlists. Add tracks via context menu or drag-and-drop. Export to M3U.
- **📋 Queue** — Right-click tracks to add to queue. Reorderable list, play-from-queue, and clear. Next/previous follow the queue.
- **✏️ Metadata editor** — Edit tags (title, artist, album, album artist, year, genre, track/disc number) and embed or clear album art. Bulk-edit multiple tracks at once. **Smart Suggestions**: apply tags from the file path using a configurable template.
- **▶️ Playback** — Play/pause, previous, next, seek, volume, mute, shuffle, and continuous playback. Maximized player view with large album art and gradient tinted from the cover.
- **📊 Reports** — Missing metadata, duplicate tracks, and missing album art — click to jump directly to the offending track.
- **🎨 Theming** — Auto (system), Dark, Light, Orkish, and DOOM. Configurable layout density, column widths, keyboard shortcuts, and more in Settings.

---

## 📁 Repository layout

This is a monorepo with two independently deployable components:

```
Muorg/
├── client/          # Desktop app (Tauri 2 + Vue 3 + Rust)
│   └── README.md    # Developer setup, build, and release docs
├── server/          # Standalone REST API (Rust + Axum)
│   └── README.md    # Docker quick-start and configuration reference
├── web-client/      # Browser-based UI (Vue 3 + Vite, served via nginx)
└── scripts/
    └── release.sh   # Bump version, tag, and push a new release
```

| Component | What it does | When you need it |
|-----------|-------------|-----------------|
| `client/` | Cross-platform desktop app | Always — this is the main app |
| `server/` | HTTP backend for remote/NAS use | Only for home-server / multi-device setups |
| `web-client/` | Browser UI connecting to muorg-server | When you want web access alongside or instead of the desktop app |

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

### Server + Web UI (browser access)

Run both muorg-server and the muorg-web browser interface together. Drop this `docker-compose.yml` next to your `muorg-server.toml`:

```yaml
services:
  muorg-server:
    image: nergy101/muorg-server:latest
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
    image: nergy101/muorg-web:latest
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

### Desktop app

```bash
cd client
pnpm install

# Development (hot-reload)
pnpm run tauri dev

# Production build (installer for current OS)
pnpm run tauri build
```

### Server (standalone binary)

```bash
cd server
cp muorg-server.example.toml muorg-server.toml
# edit muorg-server.toml
cargo run --release --bin muorg-server
```

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

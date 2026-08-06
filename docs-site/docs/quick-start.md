---
sidebar_position: 3
---

# Quick Start

Muorg works two ways — pick the one that fits:

- **Run locally** — music files live on your own machine; the desktop app manages everything offline. On Android, the app can also manage an offline library (and sync playlists from your server when you have one).
- **Self-host** — run a **muorg-server** (e.g. on a NAS or VPS) so every device — desktop, web, Android — streams from the same library.

## Run locally

1. **Download** the app for your platform from the [Releases page](https://github.com/Nergy101/Muorg/releases) (see [Installation](/docs/installation) for the exact file names):
   - **Desktop** — macOS, Windows, or Linux installer
   - **Android** — the *"Muorg Android"* release APK
2. **Launch** Muorg and add your music folders (or the app's music folder on Android).
3. **Browse** your library with the album grid or track table.
4. **Edit** metadata by clicking a track and opening the metadata editor — every write is backed up first, so mistakes are recoverable.

## Self-host

1. **Get a server** — any machine that's always on: a NAS, a mini PC, or a small VPS.
2. **Deploy muorg-server** with Docker:

   ```bash
   curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
   curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
   cp muorg-server.example.toml muorg-server.toml
   # Edit muorg-server.toml — set api_key and content_paths
   docker compose up -d
   ```

3. **Point your clients at it** — in the desktop app, web app, or Android app, enter your server's address (e.g. `http://192.168.1.50:7700`) and the `api_key` from your config.

That's it — your library is now available on every device.

See [Desktop](/docs/desktop/), [Server](/docs/server/), or [Web Client](/docs/web-client/) docs for more details.

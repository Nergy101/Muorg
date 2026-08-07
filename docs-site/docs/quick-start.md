---
title: Quick Start
description: Get Muorg running in a few minutes — either as a local desktop library or as a self-hosted server with web and Android clients.
---

Pick the path that matches what you want:

- **[Run locally](#run-locally)** — music files stay on this machine. Nothing to configure, no network.
- **[Self-host](#self-host)** — run muorg-server once, then reach the same library from desktop, browser and phone.

---

## Run locally

1. **Download the desktop app** for your platform from the [Releases page](https://github.com/Nergy101/Muorg/releases/latest):

   | Platform | File |
   |----------|------|
   | macOS (Apple Silicon) | `muorg-<version>-macos.dmg` |
   | Windows (x64) | `muorg-<version>-windows.msi` |
   | Linux (x64) | `muorg-<version>-linux.AppImage` |

   Full details, including the `.deb` and the Android APK, are on the [Install & Update](/docs/installation) page.

2. **Launch Muorg** and add a music folder when prompted. Muorg scans it recursively for MP3 and FLAC files.

3. **Browse** the library as a track table (default) or an album grid.

4. **Clean up** — select a track to open the metadata editor in the bottom panel. Turn on *backup before write* in Settings and every tag write keeps a copy of the original first, so mistakes are recoverable.

:::tip Android without a server
The Android app can also run fully offline: pick **Local Library** on the welcome screen and point it at folders on the device. See [Android App](/docs/android/).
:::

---

## Self-host

1. **Pick a machine that stays on** — a NAS, a mini PC, or a small VPS with Docker installed.

2. **Grab the compose file and an example config:**

   ```bash
   curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/docker-compose.yml
   curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
   cp muorg-server.example.toml muorg-server.toml
   ```

   This compose file starts **both** muorg-server (port `7700`) and the web app (port `7800`).

3. **Edit `muorg-server.toml`.** At minimum:

   ```toml
   [server]
   host = "0.0.0.0"                      # required in Docker
   api_key = "a-long-random-secret"      # change this

   [library]
   content_paths = ["/music"]            # the path *inside* the container

   [cors]
   allowed_origins = ["*"]               # required for the browser web app
   ```

   Then edit `docker-compose.yml` so the `/path/to/your/music` volume points at your actual music folder.

4. **Start it:**

   ```bash
   docker compose up -d
   curl http://localhost:7700/api/health
   # → Healthy
   ```

5. **Connect a client.** Open `http://<host>:7800` in a browser, or launch the desktop or Android app, and enter:

   - **Server URL** — e.g. `http://192.168.1.50:7700`
   - **API key** — the `api_key` from your config

That's it — the same library, playlists, ratings and play counts on every device.

---

## Next steps

- [Server configuration reference](/docs/server/configuration) — every option, including S3-compatible cloud storage.
- [Docker deployment](/docs/server/docker) — volumes, ports, upgrades and reverse proxies.
- [Desktop App](/docs/desktop/) · [Web App](/docs/web-client/) · [Android App](/docs/android/)
- [FAQ & Troubleshooting](/docs/faq) if something did not go to plan.

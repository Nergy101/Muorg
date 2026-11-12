---
title: Quick Start
description: Get Muorg running in a few minutes — self-host muorg-server and use the web or Android client, or run the legacy desktop app locally.
---

Pick the path that matches what you want:

- **[Self-host](#self-host)** — run muorg-server once, then reach the same library from the browser and phone. **Recommended.**
- **[Run locally](#run-locally)** — music files stay on your machine via the legacy desktop app, or on your phone via the Android app's local folders. Nothing to configure, no network.

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
   allowed_origins = ["tauri://localhost", "http://tauri.localhost", "http://<host>:7800"]  # add the web app's origin for browser use
   ```

   Then edit `docker-compose.yml` so the `/path/to/your/music` volume points at your actual music folder.

4. **Start it:**

   ```bash
   docker compose up -d
   curl http://localhost:7700/api/health
   # → Healthy
   ```

5. **Connect a client.**

   - **Web app, running locally** — open `http://localhost:7800` in a browser on the same machine and enter:
     - **Server URL** — `http://localhost:7700`
     - **API key** — the `api_key` from your config
   - **Android app** — launch it and enter the server's address directly, e.g. `http://<nas-or-vps-ip>:7700` (use your configured port if you changed it from `7700`), plus the **API key** from your config. When the phone is on the same network as the server, the LAN IP works; from outside, use your VPS/domain and make sure the port is reachable.

That's it — the same library, playlists, ratings and play counts on every device.

---

## Run locally

**Android app, local folders** — no server needed: install the APK, pick **Local Library** on the welcome screen, and point it at the folders on your device that hold your music. The app scans them into an on-device catalogue and plays them fully offline. See [Android App](/docs/android/).

:::tip Android without a server
The Android app can also run fully offline: pick **Local Library** on the welcome screen and point it at folders on the device. See [Android App](/docs/android/).
:::

:::warning Legacy
The desktop app is **legacy** — it is no longer the focus of development. For new setups, [self-host](#self-host) and use the web or Android client instead.
:::

1. **Download the desktop app** for your platform from the [Releases page](https://github.com/Nergy101/Muorg/releases/latest):

   | Platform | File |
   |----------|------|
   | macOS (Apple Silicon) | `muorg-<version>-macos.dmg` |
   | Windows (x64) | `muorg-<version>-windows.msi` |
   | Linux (x64) | `muorg-<version>-linux.AppImage` |

   Full details, including the `.deb` and the Android APK, are on the [Install & Update](/docs/installation) page.

2. **Launch Muorg** and add a music folder when prompted. Muorg scans it recursively for MP3, FLAC and WAV files.

3. **Browse** the library as a track table (default) or an album grid.

4. **Clean up** — select a track to open the metadata editor in the bottom panel. Turn on *backup before write* in Settings and every tag write keeps a copy of the original first, so mistakes are recoverable.

:::tip Android without a server
The Android app can also run fully offline: pick **Local Library** on the welcome screen and point it at folders on the device. See [Android App](/docs/android/).
:::

---

## Next steps

- [Server configuration reference](/docs/server/configuration) — every option, including S3-compatible cloud storage.
- [Docker deployment](/docs/server/docker) — volumes, ports, upgrades and reverse proxies.
- [Web App](/docs/web-client/) · [Android App](/docs/android/) · [Desktop App (legacy)](/docs/desktop/)
- [FAQ & Troubleshooting](/docs/faq) if something did not go to plan.

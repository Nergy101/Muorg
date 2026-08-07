---
title: Install & Update
description: Download names, install steps and update behaviour for the Muorg desktop app, Android APK, server and web app.
---

Every Muorg component is published from the same GitHub repository. This page is the complete download and update reference; for the fastest path to a working setup see the [Quick Start](/docs/quick-start).

## Desktop app

Download from the [latest release](https://github.com/Nergy101/Muorg/releases/latest). Assets are named after the version, e.g. `muorg-2.34.11-macos.dmg`.

| Platform | File | Install |
|----------|------|---------|
| macOS (Apple Silicon) | `muorg-<version>-macos.dmg` | Open the disk image, drag **Muorg** into Applications. |
| Windows (x64) | `muorg-<version>-windows.msi` | Run the installer. |
| Linux (x64) | `muorg-<version>-linux.AppImage` | `chmod +x muorg-*.AppImage && ./muorg-*.AppImage` |
| Linux (Debian/Ubuntu) | inside `muorg-<version>-extras.zip` | Unzip, then `sudo apt install ./muorg_*_amd64.deb` |

Two more assets exist and are **not** meant to be downloaded by hand:

- `muorg-<version>-macos.app.tar.gz` and `latest.json` — used by the built-in updater.
- `muorg-<version>-extras.zip` — the Debian package, the alternative Windows NSIS `.exe` installer, and the updater signatures.

:::note macOS and Windows builds
Only an Apple Silicon (`aarch64`) macOS build is published — there is no Intel `.dmg`. On Windows the MSI is the primary installer; the NSIS `.exe` is only inside the extras zip.
:::

### Updating

The desktop app checks GitHub Releases for a newer version and can install it in place. You can also trigger the check from **Settings → Muorg info**. Installing a newer build over an older one keeps your library database and settings.

## Android app

The Android app is released **separately** from the desktop and server, under its own `android-v<version>` tag. It is not on Google Play.

1. Open the [Releases page](https://github.com/Nergy101/Muorg/releases) and find the release tagged `android-v<version>`.
2. Download `muorg-<version>.apk`.
3. Allow **Install unknown apps** for your browser or file manager when prompted.
4. Open the downloaded file to install.

Requires **Android 8.0 (API 26)** or newer. The app checks GitHub Releases for updates and links you to the new APK; installing the new APK over the old one keeps your data.

:::info Why a separate release
Android has its own build pipeline, so an Android-only fix ships without rebuilding desktop installers and Docker images. This means the newest Android release can lag the newest `v<version>` release — see [Version Compatibility](/docs/compatibility).
:::

## Server

Container images are published to the **GitHub Container Registry**:

```bash
docker pull ghcr.io/nergy101/muorg-server:latest
```

The recommended setup uses the repository's compose file, which brings up the server and the web app together:

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/docker-compose.yml
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml
# Edit muorg-server.toml — set api_key, content_paths and allowed_origins
# Edit docker-compose.yml — point the /music volume at your library
docker compose up -d
```

See [Docker deployment](/docs/server/docker) for volumes, ports and upgrades, and [Configuration](/docs/server/configuration) for every option.

### Without Docker

```bash
git clone https://github.com/Nergy101/Muorg.git
cd Muorg/server
cargo build --release --bin muorg-server
./target/release/muorg-server --config muorg-server.toml
```

## Web app

```bash
docker pull ghcr.io/nergy101/muorg-web:latest
docker run -d -p 7800:80 ghcr.io/nergy101/muorg-web:latest
```

The image is a static site served by nginx on port `80`. It has **no build-time or run-time server URL** — you enter the server URL and API key in the app itself on first load. See [Web App](/docs/web-client/).

## Image and version summary

| Artifact | Name | Tags |
|----------|------|------|
| Server image | `ghcr.io/nergy101/muorg-server` | `<version>`, `latest` |
| Web image | `ghcr.io/nergy101/muorg-web` | `<version>`, `latest` |
| Docs image | `ghcr.io/nergy101/muorg-docs` | `<version>`, `latest` |
| Desktop installers | `muorg-<version>-<platform>.<ext>` | GitHub release `v<version>` |
| Android APK | `muorg-<version>.apk` | GitHub release `android-v<version>` |

Keep clients and server on matching versions — see [Version Compatibility](/docs/compatibility).

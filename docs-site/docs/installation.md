---
sidebar_position: 2
---

# Installation

Muorg works in two modes:

- **Local** — music files live on your own machine; no remote server needed.
- **Online** — connect to a remote **muorg-server** (e.g. on a NAS) where your music files are stored.

## Desktop App

Download the latest installer from the [Releases page](https://github.com/Nergy101/Muorg/releases/latest):

| Platform | File |
|----------|------|
| macOS (Apple Silicon) | `Muorg_*_aarch64.dmg` |
| macOS (Intel) | `Muorg_*_x86_64.dmg` |
| Windows | `Muorg_*_x64-setup.exe` |
| Linux (Debian/Ubuntu) | `muorg_*_amd64.deb` |
| Linux (AppImage) | `Muorg_*_amd64.AppImage` |

## Server (Docker)

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml
# Edit muorg-server.toml — set api_key and content_paths
docker compose up -d
```

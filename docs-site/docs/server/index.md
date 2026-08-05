---
sidebar_position: 1
---

# Server

The Muorg server is a standalone REST API (Rust + Axum) that serves your music library to the desktop app, web client, and Android app. It scans folders (or S3-compatible cloud buckets) into a SQLite database and streams audio with optional transcoding.

## Quick Start with Docker

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml
# 1. Edit muorg-server.toml — set api_key and content_paths (see below)
# 2. Make sure your music is mounted at the path you configured
docker compose up -d
```

Check that it is running:

```bash
curl http://localhost:7700/api/health
# → Healthy
```

:::tip
`/api/health` is public — everything else requires the API key as a `Authorization: Bearer <api_key>` header.
:::

## Config walkthrough (`muorg-server.toml`)

The server is configured with a single TOML file. A minimal working config:

```toml
[server]
host = "0.0.0.0"            # 0.0.0.0 in Docker, 127.0.0.1 for local-only
port = 7700
api_key = "change-me-to-something-secret"   # REQUIRED — pick a long random string

[library]
content_paths = ["/music"]  # directories to scan, recursively

[storage]
db_path = "/data/muorg.db"
backup_dir = "/data/backups"
```

### `[server]`

| Key | Default | Description |
|-----|---------|-------------|
| `host` | `"127.0.0.1"` | Bind address. Use `0.0.0.0` when running in Docker so clients can reach it. |
| `port` | `7700` | HTTP port |
| `api_key` | `"change-me"` | API key for `Authorization: Bearer` auth. **Always change this.** |
| `shutdown_timeout_secs` | `30` | Seconds to wait for in-flight requests before force-exiting |

### `[library]`

| Key | Default | Description |
|-----|---------|-------------|
| `content_paths` | `[]` | Directories to scan for music (sub-folders are recursive) |
| `scan_on_startup` | `true` | Rescan on startup, or only when triggered via the admin API |
| `remote_scan_concurrency` | `8` | Parallel tag reads during a scan of cloud remotes |

### `[storage]`

| Key | Default | Description |
|-----|---------|-------------|
| `db_path` | `muorg.db` | SQLite database path (keep in the `/data` volume in Docker) |
| `backup_dir` | `backups` | Where database backups are written |
| `backup_retention_count` | `5` | How many backups to keep |
| `cover_cache_dir` | — | Optional cache for extracted cloud-track album art |
| `cover_cache_max_bytes` | `536870912` | Cover cache size cap (512 MiB default) |

### `[cors]`

| Key | Default | Description |
|-----|---------|-------------|
| `allowed_origins` | `["*"]` | Origins allowed to call the API. Restrict to your domain for public deployments. |

### `[transcoding]`

| Key | Default | Description |
|-----|---------|-------------|
| `bitrate` | `128` | Output bitrate in kbps (128/160/192/256/320) |
| `format` | `"mp3"` | Output format (currently only mp3) |
| `sample_rate` | `44100` | Output sample rate in Hz |

### Cloud storage (S3-compatible)

Instead of (or alongside) local `content_paths`, the server can index music straight out of an S3-compatible bucket — Hetzner Object Storage, Cloudflare R2, Backblaze B2, Wasabi, MinIO, AWS S3. Add a `[[library.remotes]]` block:

```toml
[[library.remotes]]
name = "cloud"
bucket = "muorg"
endpoint = "https://nbg1.your-objectstorage.com"
region = "nbg1"
virtual_hosted_style = true
access_key_id = "..."
secret_access_key = "..."
```

See the [example config](https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml) for the full set of remote options. Keys can also be supplied via `MUORG_REMOTE_<NAME>_ACCESS_KEY_ID` / `MUORG_REMOTE_<NAME>_SECRET_ACCESS_KEY` environment variables.

## Running without Docker

```bash
cargo build --release --bin muorg-server
./target/release/muorg-server --config muorg-server.toml
```

The server looks for `muorg-server.toml` in the working directory by default.

## API overview

The full HTTP API is documented in [Server API](/docs/server/api). Highlights:

- `GET /api/health` — liveness check (public)
- `GET /api/tracks`, `GET /api/search`, `GET /api/stats` — browse and search the library
- `GET /stream/{id}` — stream audio (public, URL contains a signed token)
- `GET/PUT /api/tracks/{id}/metadata`, `.../rating`, `.../cover` — edit metadata
- `GET/POST /api/playlists`, `/api/playlists/smart` — playlists and smart playlists
- `POST /api/admin/rescan` — trigger a library rescan

## Deployment

See [Docker deployment](/docs/server/docker) for Docker Compose examples, volume layout, and running the server together with the web client.

:::info Client ↔ server compatibility
See [Version compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

---
sidebar_position: 2
---

# Server Configuration

The server is configured via a TOML file. A minimal configuration looks like:

```toml
[server]
host = "0.0.0.0"
port = 7700
api_key = "your-secret-key"

[library]
content_paths = ["/music"]

[storage]
db_path = "/data/muorg.db"
backup_dir = "/data/backups"
backup_retention_count = 5

[cors]
allowed_origins = ["tauri://localhost", "http://tauri.localhost", "http://localhost:7800"]

[transcoding]
bitrate = 320
format = "mp3"
sample_rate = 48000
```

A fully-commented example ships at [`server/muorg-server.example.toml`](https://github.com/Nergy101/Muorg/blob/main/server/muorg-server.example.toml).

## Configuration Reference

### `[server]`

| Key | Default | Description |
|-----|---------|-------------|
| `host` | `"127.0.0.1"` | Bind address. Use `"0.0.0.0"` in Docker. When loopback, a second all-interfaces listener serves only the token-protected `/stream/{token}/{id}` route for Chromecast devices on the LAN; `/api/*` stays loopback-only. |
| `port` | `7700` | HTTP port |
| `api_key` | `"change-me"` | API key for Bearer auth — change before exposing to the internet |
| `shutdown_timeout_secs` | `30` | Graceful shutdown: seconds to wait for in-flight requests before force-exiting |
| `rate_limit_requests` | `100` | Requests one IP may make within the window before the server answers 429 |
| `rate_limit_window_secs` | `60` | Length of the rate-limit window in seconds |

### `[library]`

| Key | Default | Description |
|-----|---------|-------------|
| `content_paths` | `[]` | Directories to scan for music (recursive) |
| `scan_on_startup` | `true` | Auto-scan on server start |
| `browse_roots` | `[]` | Directories `GET /api/admin/browse` may start from or descend into. Empty (default) falls back to each `content_paths` entry's parent directory plus the entry itself — never the server user's home directory unless listed here explicitly. |
| `remote_scan_concurrency` | `8` | How many objects to read tags from in parallel during a remote (S3) scan |
| `remotes` | `[]` | S3-compatible buckets indexed alongside `content_paths`. See below. |

#### `[[library.remotes]]` — cloud storage (S3-compatible)

Index music straight out of an S3-compatible bucket (Hetzner Object Storage, Cloudflare R2, Backblaze B2, Wasabi, MinIO, AWS S3, …). Tracks are stored as `remote://<name>/<object-key>`.

| Key | Default | Description |
|-----|---------|-------------|
| `name` | — | Letters, digits, `-`, `_` only; used internally as `remote://<name>/<key>` |
| `bucket` | — | Bucket name |
| `endpoint` | AWS S3 | Provider endpoint, e.g. `https://nbg1.your-objectstorage.com` |
| `region` | `"auto"` | Region code |
| `access_key_id` | — | Or set `MUORG_REMOTE_<NAME>_ACCESS_KEY_ID` env var |
| `secret_access_key` | — | Or set `MUORG_REMOTE_<NAME>_SECRET_ACCESS_KEY` env var |
| `prefix` | — | Optional sub-folder inside the bucket |
| `virtual_hosted_style` | `false` | `true` for providers requiring `<bucket>.<host>` addressing (Hetzner) |
| `unsigned_payload` | `false` | Send `UNSIGNED-PAYLOAD` instead of signing request bodies (some Ceph gateways) |

### `[storage]`

| Key | Default | Description |
|-----|---------|-------------|
| `db_path` | — | SQLite database path |
| `backup_dir` | — | Directory for database backups |
| `backup_retention_count` | `5` | How many backups to keep |
| `cover_cache_dir` | `covers/` next to the DB | Where extracted cover art for remote (cloud) tracks is cached |
| `cover_cache_max_bytes` | `536870912` (512 MiB) | Max size of the cover cache |

### `[cors]`

| Key | Default | Description |
|-----|---------|-------------|
| `allowed_origins` | `["tauri://localhost", "http://tauri.localhost"]` | Origins allowed to call the API. The default only permits the desktop app. |

List every origin that needs access: the desktop app (`tauri://localhost`, `http://tauri.localhost`), the web UI dev server (`http://localhost:7800`) and your web app's production URL.

:::warning
Never set `allowed_origins = ["*"]` on a public deployment. `*` lets **any** website you visit call your server, read your library and change your playlists using your API key.
:::

### `[transcoding]`

| Key | Default | Description |
|-----|---------|-------------|
| `bitrate` | `128` | Output bitrate in kbps (128/160/192/256/320) |
| `format` | `"mp3"` | Output format (currently only mp3) |
| `sample_rate` | `44100` | Output sample rate in Hz |

### `[images]`

Limits on `POST /api/fetch-image`, the one route that fetches a caller-supplied URL (used by clients for album art).

| Key | Default | Description |
|-----|---------|-------------|
| `allowed_hosts` | see `urlguard::DEFAULT_ALLOWED_HOSTS` | Hosts the route may fetch from. An entry also matches its subdomains. Replacing this list replaces the default — it is not merged. |
| `max_bytes` | `10485760` (10 MiB) | Hard cap on the response body, enforced while streaming |
| `timeout_secs` | `15` | Whole-request budget so a stalling host cannot hold a worker indefinitely |

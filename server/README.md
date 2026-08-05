# muorg-server

The HTTP backend for [Muorg](https://github.com/Nergy101/Muorg) — a music library manager. Exposes a REST API for tracks, playlists, ratings, metadata editing, and Chromecast streaming.

The server is normally bundled as a sidecar inside the Muorg desktop app and started automatically. This README covers running it **standalone via Docker** for home-server or NAS setups, allowing you to connect from the desktop app in **Online** mode.

---

## Quick start (Docker)

### 1. Copy and edit the config

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml
```

Open `muorg-server.toml` and at minimum change:

| Key | What to set |
|-----|-------------|
| `server.api_key` | A long random string — this is your password |
| `library.content_paths` | Paths **inside the container** where your music is mounted |

### 2. Copy the compose file

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
```

Edit the music volume path in `docker-compose.yml` to point at your music folder on the host:

```yaml
volumes:
  - /your/actual/music/path:/music:ro   # ← change this line
```

### 3. Start

```bash
docker compose up -d
```

The API is now available at `http://<your-server-ip>:7700`.

### 4. Connect the desktop app

Open Muorg → Settings → Connection → switch to **Online** mode. Enter:
- **Server URL**: `http://<your-server-ip>:7700`
- **API Key**: the value you set for `api_key`

---

## docker-compose.yml reference

```yaml
services:
  muorg-server:
    image: nergy101/muorg-server:latest
    ports:
      - "7700:7700"          # host:container — change left side to use a different host port
    volumes:
      - ./muorg-server.toml:/app/muorg-server.toml:ro   # config (read-only)
      - muorg-data:/data                                 # database + backups (persistent)
      - /path/to/your/music:/music:ro                    # your music library (read-only)
    restart: unless-stopped

volumes:
  muorg-data:
```

Multiple music directories are supported — just add more volume mounts and list each path under `content_paths` in the config.

---

## Cloud storage (S3-compatible)

A library that outgrows the server's disk can live in an S3-compatible bucket
instead. Cloud tracks behave exactly like local ones — search, playback with
seeking, FLAC transcoding, cover art, Chromecast, metadata editing and backups
all work — and local `content_paths` and buckets can coexist in one library.

Reads are ranged HTTP GETs, so nothing is mirrored to disk: seeking to the
middle of a track fetches only that slice.

### 1. Create a bucket and S3 credentials

Using Hetzner Object Storage as the example (cheapest per-GB EU option, and the
setup this was verified against): Hetzner Console → Object Storage → create a
bucket, then **Generate S3 credentials**. The secret is shown only once.

### 2. Upload your library

No S3 CLI needs to be installed — Docker is enough:

```bash
export AK=<access key> SK=<secret key>
docker run --rm -v "$HOME/Music:/music:ro" -e AK -e SK --entrypoint /bin/sh minio/mc -c '
  mc alias set hz https://nbg1.your-objectstorage.com "$AK" "$SK" --api s3v4 --path off &&
  mc mirror --overwrite /music hz/muorg'
```

`--path off` selects virtual-hosted addressing, which is what Hetzner expects.

### 3. Point the server at it

In `muorg-server.toml`:

```toml
[[library.remotes]]
name = "cloud"                  # letters/digits/-/_ only
bucket = "muorg"
endpoint = "https://nbg1.your-objectstorage.com"
region = "nbg1"
virtual_hosted_style = true     # required for Hetzner
# prefix = "albums"             # optional sub-folder inside the bucket
```

Credentials come from the environment so they stay out of the config file:
`MUORG_REMOTE_CLOUD_ACCESS_KEY_ID` and `MUORG_REMOTE_CLOUD_SECRET_ACCESS_KEY`
(the `CLOUD` part is the remote's `name`, uppercased). They can also be set as
`access_key_id` / `secret_access_key` in the TOML, but the environment wins.

### Provider settings

Only three values differ between providers:

| Provider | `endpoint` | `region` | `virtual_hosted_style` |
|---|---|---|---|
| Hetzner Object Storage | `https://<loc>.your-objectstorage.com` (`loc` = `nbg1`/`fsn1`/`hel1`) | same location code | `true` |
| Cloudflare R2 | `https://<account-id>.r2.cloudflarestorage.com` | `auto` | `false` |
| Backblaze B2 | `https://s3.<region>.backblazeb2.com` | e.g. `us-west-004` | `false` |
| Wasabi | `https://s3.<region>.wasabisys.com` | e.g. `eu-central-1` | `false` |
| MinIO (self-hosted) | `http://host:9000` | `us-east-1` | `false` |

### Notes

- The first scan of a large library (~60 k tracks) takes roughly 20 minutes at
  the default concurrency and runs in the **background** — the API and clients
  are usable while it runs, with the track list filling in as it goes.
- Later scans issue one bucket listing and no per-object reads: only objects
  whose `last_modified` changed are re-read.
- Cover art for cloud tracks is cached on disk (`storage.cover_cache_dir`,
  512 MiB by default) so browsing a library does not hit the bucket per row.
- Editing tags on a cloud track downloads the object, rewrites it and uploads it
  again. It is a user-initiated action, so the extra round trip is deliberate.
- If an upload fails with a payload or checksum error, set
  `unsigned_payload = true` on that remote.

---

## Configuration reference (`muorg-server.toml`)

```toml
[server]
host = "0.0.0.0"           # bind address — use 0.0.0.0 in Docker
port = 7700
api_key = "secret"         # Bearer token required on all /api/* requests

[library]
content_paths = ["/music"] # music directories to index (inside the container)
scan_on_startup = true     # re-scan on every container start
remote_scan_concurrency = 8       # parallel tag reads during a cloud scan

# Optional, repeatable — see "Cloud storage (S3-compatible)" above.
# [[library.remotes]]
# name = "cloud"
# bucket = "muorg"
# endpoint = "https://nbg1.your-objectstorage.com"
# region = "nbg1"
# virtual_hosted_style = true

[storage]
db_path = "/data/muorg.db"        # SQLite database location
backup_dir = "/data/backups"      # metadata backup directory
cover_cache_dir = "/data/covers"  # cover cache for cloud tracks (default: <db dir>/covers)
cover_cache_max_bytes = 536870912 # 512 MiB

[cors]
allowed_origins = ["*"]    # restrict to your domain for public deployments
```

---

## Updating

```bash
docker compose pull
docker compose up -d
```

The database lives in the `muorg-data` volume and is preserved across updates.

---

## Running without Docker

### Prerequisites

- Rust 1.75+
- `libmp3lame-dev` (Debian/Ubuntu) or `lame` (Homebrew)

### Build and run

```bash
git clone https://github.com/Nergy101/Muorg.git
cd Muorg/server
cp muorg-server.example.toml muorg-server.toml
# edit muorg-server.toml
cargo run --release --bin muorg-server
```

Or with a custom config path:

```bash
cargo run --release --bin muorg-server -- --config /path/to/muorg-server.toml
```

---

## API overview

All endpoints (except `/api/health` and `/stream/:id`) require:
```
Authorization: Bearer <api_key>
```

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Health check (no auth) |
| `GET` | `/api/roots` | List indexed root folders |
| `GET` | `/api/tracks` | All tracks |
| `GET` | `/api/search?q=` | Full-text search |
| `GET` | `/api/stats` | Library statistics |
| `GET` | `/api/tracks/:id/cover` | Album art |
| `PATCH` | `/api/tracks/:id/metadata` | Edit metadata |
| `POST` | `/api/tracks/:id/rating` | Set star rating |
| `GET` | `/api/playlists` | List playlists |
| `POST` | `/api/playlists` | Create playlist |
| `POST` | `/api/admin/rescan` | Trigger a library rescan |
| `GET` | `/stream/:id` | Audio stream (token auth) |

---

## Security notes

- **Always change `api_key`** before exposing the server outside your LAN.
- Run behind a reverse proxy (nginx, Caddy) with HTTPS for remote access.
- Set `cors.allowed_origins` to your actual domain instead of `["*"]` for public deployments.
- The `/data` volume contains your full library database — back it up regularly.

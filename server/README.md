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

## Configuration reference (`muorg-server.toml`)

```toml
[server]
host = "0.0.0.0"           # bind address — use 0.0.0.0 in Docker
port = 7700
api_key = "secret"         # Bearer token required on all /api/* requests

[library]
content_paths = ["/music"] # music directories to index (inside the container)
scan_on_startup = true     # re-scan on every container start

[storage]
db_path = "/data/muorg.db"        # SQLite database location
backup_dir = "/data/backups"      # metadata backup directory

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

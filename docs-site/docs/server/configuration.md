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
backup_dir = "/data/muorg-backups"
backup_retention_count = 5

[cors]
allowed_origins = ["tauri://localhost", "http://tauri.localhost", "http://localhost:7800"]

[transcoding]
bitrate = 320
format = "mp3"
sample_rate = 48000
```

## Configuration Reference

### `[server]`

| Key | Default | Description |
|-----|---------|-------------|
| `host` | `"127.0.0.1"` | Bind address |
| `port` | `7700` | HTTP port |
| `api_key` | `"change-me"` | API key for Bearer auth |

### `[library]`

| Key | Default | Description |
|-----|---------|-------------|
| `content_paths` | `[]` | Directories to scan for music |
| `scan_on_startup` | `true` | Auto-scan on server start |

### `[transcoding]`

| Key | Default | Description |
|-----|---------|-------------|
| `bitrate` | `128` | Output bitrate in kbps (128/160/192/256/320) |
| `format` | `"mp3"` | Output format (currently only mp3) |
| `sample_rate` | `44100` | Output sample rate in Hz |

### `[cors]`

| Key | Default | Description |
|-----|---------|-------------|
| `allowed_origins` | `["tauri://localhost", "http://tauri.localhost"]` | Origins allowed to call the API. The default only permits the desktop app. |

List every origin that needs access: the desktop app (`tauri://localhost`, `http://tauri.localhost`), the web UI dev server (`http://localhost:7800`) and your web app's production URL.

:::warning
Never set `allowed_origins = ["*"]` on a public deployment. `*` lets **any** website you visit call your server, read your library and change your playlists using your API key.
:::

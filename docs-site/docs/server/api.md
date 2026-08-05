---
sidebar_position: 3
---

# Server API

The muorg-server exposes a JSON REST API. All endpoints except the ones marked *public* require the API key:

```
Authorization: Bearer <your-api-key>
```

Base URL: `http://<host>:<port>` (default port `7700`).

## Public endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Liveness check — returns `Healthy` |
| `GET` | `/stream/{id}` | Stream a track's audio. The `id` is the stream token issued by the tracks API. |

## Library & search

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/roots` | List configured library roots / folders |
| `GET` | `/api/tracks` | List tracks (paged, filterable) |
| `GET` | `/api/tracks/count` | Total track count |
| `GET` | `/api/tracks/recently-added` | Recently added tracks |
| `GET` | `/api/search` | Full-text search across the library |
| `GET` | `/api/stats` | Library statistics |
| `GET` | `/api/play-history/recent` | Recently played tracks |
| `GET` | `/api/play-history/top` | Top played tracks |

## Tracks

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/tracks/{id}/cover` | Album art for a track |
| `GET` | `/api/tracks/{id}/metadata` | Full metadata |
| `PUT` | `/api/tracks/{id}/metadata` | Update metadata |
| `GET/PUT` | `/api/tracks/{id}/rating` | Read / set rating |
| `POST` | `/api/tracks/{id}/play` | Record a play |
| `GET` | `/api/tracks/{id}/backup` | Backup original file |
| `POST` | `/api/tracks/{id}/restore` | Restore from backup |
| `POST` | `/api/tracks/{id}/rename` | Rename the file on disk |
| `GET` | `/api/tracks/{id}/auto-tag-suggestions` | MusicBrainz tag suggestions |
| `GET` | `/api/tracks/{id}/stream-token` | Issue a streaming token for `/stream/{id}` |
| `POST` | `/api/tracks/metadata/batch` | Batch metadata update |

## Playlists

| Method | Path | Description |
|--------|------|-------------|
| `GET/POST` | `/api/playlists` | List / create playlists |
| `GET/PUT/DELETE` | `/api/playlists/{id}` | Read / update / delete a playlist |
| `POST` | `/api/playlists/order` | Reorder playlists |
| `GET/POST` | `/api/playlists/smart` | List / create smart playlists |
| `GET/PUT` | `/api/playlists/smart/{id}/rules` | Read / update smart playlist rules |
| `GET` | `/api/playlists/smart/{id}/tracks` | Tracks matching a smart playlist |
| `GET/POST` | `/api/playlists/{id}/tracks` | Tracks in a playlist / add tracks |
| `POST` | `/api/playlists/{id}/tracks/order` | Reorder tracks in a playlist |
| `DELETE` | `/api/playlists/{id}/entries/{entry_id}` | Remove a track from a playlist |

## Admin

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/admin/rescan` | Trigger a library rescan |
| `POST` | `/api/admin/remove-folder` | Remove a library folder |
| `POST` | `/api/admin/backup-directory` | Run a database backup |
| `POST` | `/api/admin/clear-cache` | Clear caches (e.g. cover cache) |
| `GET` | `/api/admin/health` | Health incl. dependency checks |
| `GET` | `/api/admin/metrics` | Metrics (Prometheus format) |

## Cast

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/cast/devices` | Discovered Chromecast devices |
| `POST` | `/api/cast/discovery/start` · `/stop` | Start / stop device discovery |
| `POST` | `/api/cast/play` · `/pause` · `/resume` · `/stop` · `/seek` | Playback control |
| `GET/POST` | `/api/cast/status` | Cast session status |
| `POST` | `/api/cast/volume` | Set cast volume |

## Misc

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/fetch-image` | Fetch / cache a remote image (used by clients for album art) |

## Streaming

Audio is streamed via `GET /stream/{id}`. Clients first request a stream token (`GET /api/tracks/{id}/stream-token`) and use the returned URL — this keeps the actual stream endpoint public while the token gates access. Streaming supports HTTP range requests, and transcodes on the fly when the client requests a different bitrate/format (see `[transcoding]`).

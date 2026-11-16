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
| `GET` | `/stream/{token}/{id}` | Stream a track's audio. The `token` is the stream token issued by the tracks API. |

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
| `PATCH` | `/api/tracks/{id}/metadata` | Update metadata |
| `GET` | `/api/tracks/{id}/rating` | Read rating |
| `POST` | `/api/tracks/{id}/rating` | Set rating |
| `POST` | `/api/tracks/{id}/play` | Record a play |
| `GET` | `/api/tracks/{id}/backup` | Backup original file |
| `POST` | `/api/tracks/{id}/restore` | Restore from backup |
| `POST` | `/api/tracks/{id}/rename` | Rename the file on disk |
| `POST` | `/api/tracks/rename/check` | Check whether a rename target is valid / free |
| `DELETE` | `/api/tracks/{id}` | Delete a track |
| `GET` | `/api/tracks/{id}/lyrics` | Lyrics for a track |
| `POST` | `/api/tracks/{id}/auto-tag-suggestions` | MusicBrainz tag suggestions |
| `GET` | `/api/tracks/{id}/stream-token` | Issue a streaming token for `/stream/{token}/{id}` |
| `POST` | `/api/tracks/metadata/batch` | Batch metadata update |

## Playlists

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/playlists` | List playlists |
| `POST` | `/api/playlists` | Create a playlist |
| `GET` | `/api/playlists/{id}` | Read a playlist |
| `PATCH` | `/api/playlists/{id}` | Update a playlist |
| `DELETE` | `/api/playlists/{id}` | Delete a playlist |
| `PUT` | `/api/playlists/order` | Reorder playlists |
| `GET` | `/api/playlists/pins` | Pinned playlists |
| `PUT` | `/api/playlists/pins` | Set pinned playlists |
| `GET` | `/api/playlists/smart` | List smart playlists |
| `POST` | `/api/playlists/smart` | Create a smart playlist |
| `GET` | `/api/playlists/smart/{id}/tracks` | Tracks matching a smart playlist |
| `GET` | `/api/playlists/smart/{id}/rules` | Read smart playlist rules |
| `PATCH` | `/api/playlists/smart/{id}/rules` | Update smart playlist rules |
| `GET` | `/api/playlists/{id}/tracks` | Tracks in a playlist |
| `POST` | `/api/playlists/{id}/tracks` | Add tracks to a playlist |
| `DELETE` | `/api/playlists/{id}/tracks` | Remove tracks from a playlist |
| `PUT` | `/api/playlists/{id}/tracks/order` | Reorder tracks in a playlist |
| `GET` | `/api/playlists/{id}/entries` | Playlist entries |
| `DELETE` | `/api/playlists/{id}/entries/{entry_id}` | Remove an entry from a playlist |

## Admin

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/admin/rescan` | Trigger a library rescan |
| `GET` | `/api/admin/browse` | Browse server-side directories (for "Add folder" pickers); confined to `[library] browse_roots` |
| `POST` | `/api/admin/remove-folder` | Remove a library folder |
| `GET` | `/api/admin/backup-directory` | Run a database backup |
| `POST` | `/api/admin/purge-deleted` | Permanently purge soft-deleted catalog rows, bypassing the grace period |
| `POST` | `/api/admin/clear-cache` | **Deprecated**, use `purge-deleted` instead — same behaviour, kept for one release |
| `POST` | `/api/admin/update` | Check for / apply a server update |
| `GET` | `/api/admin/health` | Health incl. dependency checks |
| `GET` | `/api/admin/metrics` | Metrics (Prometheus format) |

## Cast

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/cast/devices` | Discovered Chromecast devices |
| `POST` | `/api/cast/discovery/start` · `/stop` | Start / stop device discovery |
| `POST` | `/api/cast/play` · `/pause` · `/resume` · `/stop` · `/seek` | Playback control |
| `GET` | `/api/cast/status` | Cast session status |
| `POST` | `/api/cast/volume` | Set cast volume |

## Misc

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/fetch-image` | Fetch / cache a remote image (used by clients for album art); see `[images]` in [Server Configuration](./configuration.md) |

## Streaming

Audio is streamed via `GET /stream/{token}/{id}`. Clients first request a stream token (`GET /api/tracks/{id}/stream-token`) and use the returned URL — this keeps the actual stream endpoint public while the token gates access. The token travels in the path, not the query string, so it stays out of proxy access logs, browser history and `Referer` headers. Streaming supports HTTP range requests, and transcodes on the fly when the client requests a different bitrate/format (see `[transcoding]`).

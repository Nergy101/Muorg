---
sidebar_position: 9
---

# FAQ & Troubleshooting

## Docker Compose says "no configuration file provided"

You are running `docker compose up` from the wrong directory, or the compose file is not named `docker-compose.yml`/`compose.yml`.

```bash
# Run from the directory that contains docker-compose.yml:
cd /path/to/where/you/downloaded-it
docker compose up -d
```

If you renamed the file, point compose at it explicitly:

```bash
docker compose -f my-compose.yml up -d
```

## The server won't start: "missing api_key" or "missing content_paths"

The server refuses to start without these two settings. Open your `muorg-server.toml` and make sure both exist:

```toml
[server]
api_key = "a-long-random-secret"

[library]
content_paths = ["/music"]
```

If you mounted the config with `:ro` in Docker, edit the file on the host (outside the container) and restart: `docker compose restart`.

## I can't connect a client to the server

Check, in order:

1. **Server is up** — `curl http://<host>:7700/api/health` returns `Healthy`. If not, check the server logs.
2. **Reachable** — from the client device, the URL must be reachable. On the same machine `localhost`/`127.0.0.1` works; for other devices use the LAN IP (`ipconfig getifaddr en0` on macOS, `ip addr` on Linux) or a domain. In Docker, make sure the port is published (`ports: ["7700:7700"]`).
3. **API key matches** — the client must use the exact `api_key` from the server config. Changing the key on the server invalidates all existing client connections.
4. **CORS** — for browser clients, `allowed_origins` must include your web client's origin. `["*"]` works for LAN/home use.

## Music files are not appearing after I added a folder

- The scan may still be running — large libraries take a while. Trigger one explicitly: `POST /api/admin/rescan` (requires the API key).
- The folder must contain supported audio formats (MP3, FLAC, etc.).
- In Docker, `content_paths` must match the **container** path of the mounted volume, e.g. a host folder mounted at `/music` must be configured as `/music`, not `/home/you/Music`.
- Check the server logs for scan errors.

## Metadata edits are not saved / permission denied

The server (or desktop app) needs **write access** to the music directory to edit tags and rename files.

- In Docker: the `/music` volume must be writable by the container user; if the host folder is read-only for that user, tags can't be written.
- On Linux: check directory permissions (`ls -ld /path/to/music`). The user running the server needs write permission.
- Some files may be flagged read-only themselves — make them writable and retry.

## The PWA / web client behaves oddly offline

The web client is a PWA: the app shell is cached and works offline, but **streaming** requires the server. If the server is unreachable, the library view may be stale and playback will fail — reconnect to the server for full functionality.

If the cached shell looks outdated after a new release, hard-refresh (`Cmd/Ctrl+Shift+R`) or clear site data once.

## Where is the desktop app's library stored?

Local libraries are scanned into the app's data directory; playlists, metadata edits, and the database live there too. Removing a folder from the app does **not** delete your files.

## How do I back up my library?

- **Server**: the server keeps rolling database backups in `backup_dir` (`backup_retention_count` to control how many). Back up `muorg.db` and the music folders themselves.
- **Desktop**: keep your music folders safe; playlists can be exported to M3U from the app.

## Does Muorg need an internet connection?

No. Everything runs on your own machines — desktop app, server, web client, and Android app are all self-hosted. The only internet-dependent feature is **MusicBrainz auto-tagging** (optional, used when you explicitly request tag suggestions).

## Android widget shows no artwork / stale info

Widgets refresh when the app updates playback state. If the widget is stuck, remove and re-add it, or restart the app. The widget needs the app to be connected to a server to show current track info.

## More help

- [GitHub Issues](https://github.com/Nergy101/Muorg/issues) — report bugs and ask questions
- [Version compatibility](/docs/compatibility) — which client and server versions work together
- [Release Notes](/docs/releases) — what changed in each version

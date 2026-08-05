---
sidebar_position: 1
---

# Web Client

The Muorg web client is a browser-based UI (Vue 3 + Vite) that connects to a [muorg-server](/docs/server/) instance. It is mobile-first, installable as a PWA, and mirrors the Android client's experience — no app store required.

## Deploying the web client

### Option 1 — Docker (recommended)

The web client ships as a static site in an nginx container:

```bash
docker pull nergy101/muorg-web:latest
docker run -d -p 8080:80 \
  -e VITE_API_URL=http://localhost:7700 \
  nergy101/muorg-web
```

`VITE_API_URL` tells the client where your muorg-server lives. If the server runs on the same host, `http://localhost:7700` works; use your machine's LAN IP or domain for other devices.

### Option 2 — Docker Compose (with the server)

The [docker-compose.yml](https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml) in the repo starts both the server and the web client:

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml
# edit muorg-server.toml — set api_key and content_paths
docker compose up -d
```

The web client is then available at `http://<host>:8080`.

### Option 3 — Static hosting

The web client build is a plain static site. Build it yourself and host it anywhere:

```bash
cd web-client
pnpm install
VITE_API_URL=https://your-server.example pnpm build
# serve the dist/ directory
```

## Connecting to a server

On first launch you are asked for:

- **Server URL** — e.g. `http://192.168.1.50:7700`
- **API key** — the `api_key` from your `muorg-server.toml`

The client stores the connection locally. If the connection is lost, the connect screen reappears. Your library, playlists, and queue are then fetched from the server.

## Feature tour

- **Library** — album grid and track table views, with search and filter
- **Album view** — tap an album to see its tracks and play them
- **Playlists** — regular and smart playlists, with a playlist editor
- **Queue** — now playing queue with reordering
- **Player** — full player screen with artwork, quick actions, and favorites
- **Playback** — streaming from the server with seek
- **Multi-select** — batch actions on multiple tracks
- **PWA / offline** — installable from the browser; cached shell works offline
- **Settings** — server connection, appearance, and a link to the GitHub repo

:::info Client ↔ server compatibility
See [Version compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

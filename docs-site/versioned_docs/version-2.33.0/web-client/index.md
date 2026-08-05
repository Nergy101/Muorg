---
sidebar_position: 1
---

# Web Client

The Muorg web client is a browser-based UI (Vue 3 + Vite) that connects to a muorg-server instance.

## Setup

Run the web client alongside the server with Docker:

```bash
docker pull nergy101/muorg-web:latest
docker run -d -p 8080:80 nergy101/muorg-web
```

Or use the full Docker Compose setup:

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
docker compose up -d
```

## Features

- Album grid and track table views
- Search and filter your library
- Track playback with streaming
- Multi-select and batch actions
- Smart playlist editor
- PWA offline support

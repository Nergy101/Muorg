---
sidebar_position: 4
---

# Server Docker Deployment

Prebuilt images are published to the GitHub Container Registry:

- `ghcr.io/nergy101/muorg-server:latest`
- `ghcr.io/nergy101/muorg-web:latest`

## Using Docker Compose

The repo ships a ready-to-use [`docker-compose.yml`](https://github.com/Nergy101/Muorg/blob/main/docker-compose.yml) with both the server and the web client. Copy it next to your config, edit the music path, and run:

```bash
docker compose up -d
```

```yaml
services:
  muorg-server:
    image: ghcr.io/nergy101/muorg-server:latest
    container_name: muorg-server
    ports:
      - "7700:7700"
    volumes:
      - ./muorg-server.toml:/app/muorg-server.toml:ro
      - /path/to/music:/music:ro
      - muorg-data:/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "sh", "-c", "curl -sf http://localhost:7700/api/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 10s

  muorg-web:
    image: ghcr.io/nergy101/muorg-web:latest
    ports:
      - "7800:80"
    restart: unless-stopped
    depends_on:
      muorg-server:
        condition: service_healthy

volumes:
  muorg-data:
```

## Configuration

The server reads `./muorg-server.toml` from its working directory (`/app` in the container). Mount your config there:

```yaml
volumes:
  - ./muorg-server.toml:/app/muorg-server.toml:ro
```

Start from [`muorg-server.example.toml`](https://github.com/Nergy101/Muorg/blob/main/server/muorg-server.example.toml) and see [Server Configuration](./configuration.md) for every option. Remember to add the web client's origin (`http://localhost:7800`) to `[cors] allowed_origins`.

## Ports

| Service | Container port | Host port | Purpose |
|---------|---------------|-----------|---------|
| muorg-server | 7700 | 7700 | REST API + streaming |
| muorg-web | 80 | 7800 | Web client (static files) |

## Notes

- Mount your music read-only (`:ro`) — the server only reads audio files; renames go through the API and are applied by the server itself.
- The web client takes the server URL in the browser at runtime — there is no build-time `VITE_API_URL` or other environment variable to set.
- Persistent data (SQLite database, backups, cover cache) lives in the `muorg-data` volume mounted at `/data`.

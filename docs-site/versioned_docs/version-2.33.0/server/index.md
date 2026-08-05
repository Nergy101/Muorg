---
sidebar_position: 1
---

# Server

The Muorg server is a standalone REST API (Rust + Axum) that serves your music library.

## Quick Start with Docker

```bash
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/docker-compose.yml
curl -O https://raw.githubusercontent.com/Nergy101/Muorg/main/server/muorg-server.example.toml
cp muorg-server.example.toml muorg-server.toml
docker compose up -d
```

## Config Reference

See [Configuration](/docs/server/configuration) for all settings.

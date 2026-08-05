---
sidebar_position: 4
---

# Server Docker Deployment

## Using Docker Compose

```yaml
services:
  muorg-server:
    image: nergy101/muorg-server:latest
    container_name: muorg-server
    ports:
      - "7700:7700"
    volumes:
      - ./muorg-server.toml:/app/muorg-server.toml:ro
      - /path/to/music:/music
      - muorg-data:/data
    restart: unless-stopped

volumes:
  muorg-data:
```

## Environment Variables

The server looks for a config file in the working directory. Mount your config at `/app/muorg-server.toml`.

## With Web Client

```yaml
services:
  muorg-server:
    image: nergy101/muorg-server:latest
    ports: ["7700:7700"]
    volumes:
      - ./muorg-server.toml:/app/muorg-server.toml:ro
      - /path/to/music:/music
      - muorg-data:/data

  muorg-web:
    image: nergy101/muorg-web:latest
    ports: ["8080:80"]
    environment:
      - VITE_API_URL=http://localhost:7700

volumes:
  muorg-data:
```

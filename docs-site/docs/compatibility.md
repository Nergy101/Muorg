---
sidebar_position: 10
---

# Version Compatibility

Muorg is a multi-component product: the desktop app, web client, and Android app all talk to the muorg-server over its REST API.

## General rule

- **Clients and server are released in lockstep.** A `v2.x.y` client is designed to work with a `v2.x.y` server, and all components in the same **minor** series (e.g. `v2.33.*`) are compatible with each other.
- **Keep the server at least as new as your clients.** New client features sometimes rely on new server endpoints. Running an old server with a new client can cause missing features or errors; running a new server with an old client is generally safe.
- **Within a minor series you can mix freely** — e.g. a `v2.33.0` desktop app works with a `v2.33.1` server.

## Practical guidance

| Scenario | Recommendation |
|----------|----------------|
| Upgrading clients only | Upgrade the server in the same maintenance window to stay safe |
| Upgrading the server | Older clients keep working; new client features may not be available until clients are updated |
| Self-hosted all-in-one (desktop sidecar) | Everything ships together — just update the app |
| Docker server + web client | Use the same tagged version for both images (`nergy101/muorg-server:v2.33.1` + `nergy101/muorg-web:v2.33.1`), or `latest` for both |

## API key changes

The API key is the only authentication. Changing `api_key` on the server immediately invalidates every connected client — reconnect them with the new key.

## Checking versions

- **Server**: `GET /api/health` and the release tag of your image/install
- **Desktop / Web / Android**: the version is shown in Settings (and on the Releases page for downloads)

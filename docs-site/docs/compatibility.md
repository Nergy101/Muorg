---
sidebar_position: 10
---

# Version Compatibility

Muorg is a multi-component product: the desktop app, web app, and Android app all talk to the muorg-server over its REST API.

## General rule

- **Server, desktop and web app are released in lockstep.** A `v3.x.y` client is designed to work with a `v3.x.y` server, and all components in the same **minor** series (e.g. `v3.2.*`) are compatible with each other.
- **The Android app is versioned on its own.** It ships on `android-v*` tags (e.g. `android-v3.3.3`) and can be newer than the server. See the [Android release notes](./releases.md#android-32x--33x) for what each build changed.
- **Keep the server at least as new as your clients.** New client features sometimes rely on new server endpoints. Running an old server with a new client can cause missing features or errors; running a new server with an old client is generally safe.
- **Within a minor series you can mix freely** — e.g. a `v3.2.0` desktop app works with a `v3.2.1` server.

## Practical guidance

| Scenario | Recommendation |
|----------|----------------|
| Upgrading clients only | Upgrade the server in the same maintenance window to stay safe |
| Upgrading the server | Older clients keep working; new client features may not be available until clients are updated |
| Self-hosted all-in-one (desktop sidecar) | Everything ships together — just update the app |
| Docker server + web app | Use the same tagged version for both images (`ghcr.io/nergy101/muorg-server:3.2.0` + `ghcr.io/nergy101/muorg-web:3.2.0`), or `latest` for both |

## API key changes

The API key is the only authentication. Changing `api_key` on the server immediately invalidates every connected client — reconnect them with the new key.

## Checking versions

- **Server**: `GET /api/health` and the release tag of your image/install
- **Desktop / Web / Android**: the version is shown in Settings (and on the Releases page for downloads)

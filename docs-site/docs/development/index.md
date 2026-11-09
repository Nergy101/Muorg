---
sidebar_position: 1
---

# Development

## Repository Layout

```
Muorg/
├── client/          # Desktop app (Tauri 2 + Vue 3)
├── server/          # REST API (Rust + Axum)
├── web-client/      # Browser UI (Vue 3 + Vite)
├── android-client/  # Android app (Kotlin + Jetpack Compose)
├── docs-site/       # Documentation site (Docusaurus)
└── scripts/         # Utility scripts
```

## Building from Source

### Desktop Client

```bash
cd client
pnpm install
pnpm tauri dev
```

A mock-data mode is available for development and documentation screenshots:

```bash
pnpm dev:mock
```

The capture helper lives at `docs/screenshots/screenshots.sh` in the repo root (not inside `docs-site/`) — it starts the app with mock data and walks you through taking screenshots for the docs.

### Server

```bash
cd server
cargo build --release --bin muorg-server
```

### Web App

```bash
cd web-client
pnpm install
pnpm dev
```

### Android

Open `android-client/` in Android Studio and run.

## Docs site

The docs live in `docs-site/` (Docusaurus 3):

```bash
cd docs-site
pnpm install
pnpm start       # dev server with live reload
pnpm build       # static build → build/
```

### Docs versioning

The docs are **unversioned**: `docs/` always describes the latest release. Because every component ships in lockstep from one repo, frozen per-release snapshots rot quickly — a snapshot of v2.33.0 was dropped for exactly that reason. `docusaurus.config.js` therefore sets no `lastVersion`/`versions` config, and `onBrokenLinks: "throw"` keeps the links honest at build time. If a release ever ships a breaking change that users on older versions still need documented, reintroduce versioning deliberately rather than by default.

### Search

Local client-side search is provided by `@easyops-cn/docusaurus-search-local` (configured in `docusaurus.config.js`). It indexes all doc pages, works fully offline, and supports the `/` keyboard shortcut. No external service is involved.

### Screenshots

Product screenshots live in `docs-site/static/img/screenshots/`. They are referenced from the component docs pages. Keep them compressed (aim for < 200 KB each) — the site is served statically and screenshots can bloat the bundle. Re-capture with the [screenshots script](https://github.com/Nergy101/Muorg/blob/main/docs/screenshots/screenshots.sh) when the UI changes.

## Release process

Releases are tagged `v3.x.y` and built by GitHub Actions. See [Release Notes](/docs/releases) for the changelog and [Version Compatibility](/docs/compatibility) for cross-component version rules.

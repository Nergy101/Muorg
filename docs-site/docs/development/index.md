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

### Versioned docs

The docs are versioned per release with Docusaurus's built-in versioning.

- `docs/` holds the **current** docs (tracking the latest release)
- `versioned_docs/version-2.33.0/` is the snapshot for the v2.33.0 release
- `docusaurus.config.js` sets `lastVersion` and per-version labels — the sidebar shows a version picker automatically once more than one version exists

**When to cut a new docs version:** run `pnpm docusaurus docs:version <version>` whenever a release ships **notable docs changes or breaking changes** — do not version on every patch release. Snapshot the docs *before* rewriting them for the next release, so the snapshot matches what users of that release actually saw. Keep the current docs describing the latest release.

### Search

Local client-side search is provided by `@easyops-cn/docusaurus-search-local` (configured in `docusaurus.config.js`). It indexes all doc pages, works fully offline, and supports the `/` keyboard shortcut. No external service is involved.

### Screenshots

Product screenshots live in `docs-site/static/img/screenshots/`. They are referenced from the component docs pages. Keep them compressed (aim for < 200 KB each) — the site is served statically and screenshots can bloat the bundle. Re-capture with the [screenshots script](https://github.com/Nergy101/Muorg/blob/main/docs/screenshots/screenshots.sh) when the UI changes.

## Release process

Releases are tagged `v2.x.y` and built by GitHub Actions. See [Release Notes](/docs/releases) for the changelog and [Version Compatibility](/docs/compatibility) for cross-component version rules.

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

### Server

```bash
cd server
cargo build --release --bin muorg-server
```

### Web Client

```bash
cd web-client
pnpm install
pnpm dev
```

### Android

Open `android-client/` in Android Studio and run.

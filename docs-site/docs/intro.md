---
title: Welcome to Muorg
description: Muorg is a cross-platform music organizer for people who care about their metadata — desktop, web, Android and a self-hosted server.
---

**Muorg** (pronounced "Mu-Ork") is a cross-platform music organizer — *"The Music Organizer from Hell"* — for people who care about their metadata. Organize, clean up, and play your library, either straight off your own disk or from a server you run yourself.

Everything is self-hosted. There is no Muorg account, no cloud service, and nothing phones home.

## Two ways to run it

| Mode | What it means | Best for |
|------|---------------|----------|
| **Local** | Music lives on the machine you are using. The desktop app manages it directly; the Android app can do the same with on-device folders. | One machine, one library, no networking. |
| **Self-hosted** | You run **muorg-server** on a NAS, mini PC or VPS. Desktop, web and Android all stream from the same library. | Multiple devices, listening away from home. |

You are not locked into either: the desktop app can switch between its own local library and a remote server at any time.

## Components

| Component | Stack | Needs a server? | Docs |
|-----------|-------|-----------------|------|
| **Desktop app** | Tauri 2 + Vue 3 (macOS, Windows, Linux) | No — it bundles its own | [Desktop App](/docs/desktop/) |
| **Web app** | Vue 3 + Vite, installable PWA | Yes | [Web App](/docs/web-client/) |
| **Android app** | Kotlin + Jetpack Compose | No — server *or* on-device folders | [Android App](/docs/android/) |
| **Server** | Rust + Axum + SQLite | — | [Server](/docs/server/) |

## What Muorg does

- **Organize** — track table and album grid views, search, sorting and genre filters, smart playlists driven by rules, and reports that surface tracks with missing metadata, missing artwork or likely duplicates.
- **Clean up** — a metadata editor with bulk editing, embedded cover art, filename-pattern extraction and MusicBrainz auto-tagging. Every write can back up the original file first.
- **Play** — a full player with queue, shuffle, repeat, ratings and favourites; a sleep timer on mobile and web; Chromecast from Android and from the desktop app.
- **Share one library** — point every client at the same muorg-server and playlists, ratings and play counts follow you around.

Supported audio formats are **MP3** and **FLAC**.

## Where to go next

- **[Quick Start](/docs/quick-start)** — the shortest path from nothing to playing music.
- **[Install & Update](/docs/installation)** — exact download names for every platform, plus how updates work.
- **[Server](/docs/server/)** — configure and run muorg-server.
- **[FAQ & Troubleshooting](/docs/faq)** — the problems people actually hit.
- **[Release Notes](/docs/releases)** — what changed, per version.

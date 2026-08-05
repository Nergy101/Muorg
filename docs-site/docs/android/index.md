---
sidebar_position: 1
---

# Android App

The Muorg Android client (Kotlin + Jetpack Compose) connects to a [muorg-server](/docs/server/) for streaming, playback, and library management.

## Install

The Android app is distributed as a debug/release APK from the [Releases page](https://github.com/Nergy101/Muorg/releases/latest) (it is not on Google Play).

1. Download the APK (`muorg-android-*.apk`)
2. On your phone, allow **Install unknown apps** for your browser/file manager
3. Open the APK and install

## Connecting to a server

On first launch you are asked for:

- **Server URL** — e.g. `http://192.168.1.50:7700` (your phone must be able to reach the server — same Wi-Fi/LAN, or a publicly reachable address)
- **API key** — the `api_key` from your `muorg-server.toml`

If the connection fails, check that the server is up (`/api/health`), the URL is reachable from the phone, and the API key matches.

## Browsing and playback

- **Library** — album grid and track list, search, and filter
- **Album view** — tap an album to browse its tracks
- **Player** — now playing screen with artwork, seek, queue, and sleep timer
- **Playlists** — regular and smart playlists
- **Metadata** — edit track metadata; the metadata scan sheet finds tracks that need tags
- **Chromecast** — cast playback to Chromecast devices on your network

## Offline playlist sync

Mark a playlist for offline sync to download its tracks to the device:

1. Open a playlist
2. Toggle **Offline sync**
3. The tracks download in the background and play back without a network connection

This is great for commuting or anywhere without reliable network access.

## Home screen widget

Muorg ships a **Now Playing** widget:

1. Long-press your home screen
2. Add the **Muorg** widget
3. It shows the current track artwork and playback controls (play/pause, next/previous)

## Sleep timer

Start playback, then set the sleep timer from the player screen to stop playback after a chosen duration — handy for falling asleep to music.

:::info Client ↔ server compatibility
See [Version compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

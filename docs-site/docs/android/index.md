---
title: Android App
description: The Muorg Android client — stream from your server or play an on-device library, with Chromecast, a home-screen widget and a sleep timer.
---

The Muorg Android client (Kotlin + Jetpack Compose, Media3/ExoPlayer) works in two modes: streaming from a [muorg-server](/docs/server/), or playing music stored on the device itself.

## Install

The app is distributed as an APK from GitHub Releases — it is not on Google Play. It has its **own release tag**, separate from the desktop and server releases.

1. Open the [Releases page](https://github.com/Nergy101/Muorg/releases) and find the release tagged `android-v<version>`.
2. Download `muorg-<version>.apk`.
3. Allow **Install unknown apps** for your browser or file manager when prompted.
4. Open the file to install.

Requires **Android 8.0 (API 26)** or newer.

## Choosing a music source

The welcome screen offers two paths, and you can switch later in **Settings → Music source**:

| Mode | What it does |
|------|--------------|
| **Muorg Server** | Streams from a muorg-server. Enter the server URL (e.g. `http://192.168.1.50:7700`) and the `api_key` from `muorg-server.toml`. |
| **Local Library** | Pick folders on the device; the app scans them into an on-device catalogue. No server, no network. |

:::caution Switching modes clears the cache
Changing the music source wipes the currently cached library and rescans from the new source. Server-side playlists, ratings and play counts are unaffected — they live on the server.
:::

If a server connection fails, check that the server is up (`/api/health`), that the phone can actually reach the URL (same Wi-Fi/LAN or a publicly reachable address), and that the API key matches.

## Browsing and playback

- **Home** — shelves for Recommended, Mixes, Most Played and Recently Played, plus a **Shuffle all** button in the header that shuffles your whole library and keeps topping the queue up as it plays.
- **Library** — album grid, album list or flat track list, with live search and recent-search chips.
- **Album view** — tap an album to browse and play its tracks.
- **Player** — artwork, seek bar, shuffle, repeat and queue. Swipe down to close it.
- **Playlists** — create and manage regular playlists. Smart playlists defined on the server are shown and playable, but their rules are edited on [desktop](/docs/desktop/) or in the [web app](/docs/web-client/).
- **Metadata** — edit a single track's title, artist, album, album artist, genre and year from its actions sheet. The **metadata scan** tool under Settings → Library tools finds tracks that still need tags.

### Seek bar

The line under the artwork is a seek bar. While a track plays, the elapsed part ripples as a wave; paused, it is a straight line. Touch the bar anywhere to jump there, or put your finger on it and drag to scrub — the wave straightens the moment you touch it so the thumb is easy to follow, and playback seeks when you lift. The touch area is taller than the drawn line, so you do not need to hit the thin line exactly.

While a track is still streaming in, the bar turns into a loading bar until it is ready to play.

## Queue

Open the queue from the mini player or the player screen. It is one ordered list — what you see is what will play — split into two sections:

| Section | What it holds |
|---------|---------------|
| **Your queue** | Tracks you added yourself (**Add to queue** from a track's actions sheet). They play first, in the order you put them. **Clear** empties just this section. |
| **Up next** | The rest of the album, playlist or mix you started. It picks up after your queue. |

- **Reorder** — drag a row by its handle. Dropping a track across the divider moves it to the other section, and dropping at the very top starts your queue.
- **Remove** — long-press a row and choose **Remove from queue**.
- **Jump** — tap a row to play it now.
- **Shuffle** — shuffles what is still to come in *Up next*; your queue and the song playing are left alone, and turning it off restores the original order.
- **Clear all** — drops everything after the current track.

## Sleep timer

From the player screen, tap the sleep-timer icon and pick a duration. Playback stops when it elapses — including cast playback. Tapping the icon again while a timer is running lets you cancel it, and the remaining time is shown in the mini player.

## Chromecast

Cast from the app to any Chromecast device on your network using the Cast button. Server-backed tracks are cast straight from the server's stream URL; tracks stored on the device are served to the Cast receiver by a small HTTP server that runs inside the app while casting.

## Home-screen widget

A **Now Playing** widget shows the current track and playback controls:

1. Long-press your home screen and open the widget picker.
2. Add the **Muorg** widget.

The widget updates when the app updates playback state. If it looks stuck, remove and re-add it, or reopen the app.

## Settings

Version and update check, playback behaviour (continuous playback, what tapping the mini player does, **offline mode**), downloads (playlist downloads wait for Wi-Fi unless you allow mobile data), default library sort and layout, theme (dark / light / follow system, true black, Material You), the music-source switch with per-mode options (server URL, statistics, refresh and log out; or add-folder and rescan), and the metadata scan tool.

:::note Offline mode
Offline mode makes no network calls: only downloaded albums, playlists and tracks are shown and played, and Home shows a placeholder instead of the shelves.
:::

:::note Downloaded playlists stay in step
When songs are added to a playlist you have downloaded, the app downloads them in the background — right after you add them in the app, and within about 15 minutes for changes made elsewhere (the web app, another device, or a smart playlist that grew). It follows the same rules as any download: Wi-Fi only unless you allow mobile data, the storage limit, and nothing while offline mode is on.
:::

:::info Client ↔ server compatibility
See [Version Compatibility](/docs/compatibility) for which client versions work with which server versions.
:::

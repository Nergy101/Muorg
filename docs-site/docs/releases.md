---
sidebar_position: 8
---

# Release Notes

Muorg ships as tagged GitHub releases (`v2.x.y`). This page summarizes the notable changes per release, per component. Full details are on the [GitHub Releases page](https://github.com/Nergy101/Muorg/releases).

## v2.33.1

- **Web Client**: Development section in Settings with a link to the GitHub repo

## v2.33.0

- **Server**: S3-compatible **cloud storage** — index and stream music from Hetzner Object Storage, Cloudflare R2, Backblaze B2, Wasabi, MinIO, or AWS S3 (`[[library.remotes]]`)
- **Server**: remote library scanner, cover cache for cloud tracks, range-passthrough streaming
- **Server**: metadata write, backup, restore, and rename for cloud tracks

## v2.32.x

- **v2.32.7** — Web Client: disable double-tap-to-zoom in the PWA shell
- **v2.32.6** — Web Client: dashboard nav icon, square playlist grid, pin playlists to top
- **v2.32.5** — Web Client: favorite heart bounce animation, queue button moved to quick actions
- **v2.32.4** — Web Client: fix missing list-view icon
- **v2.32.3** — Web Client: switch to Mage Icons, player background stretched over top inset
- **v2.32.2** — Web Client: disc icon for the library tab
- **v2.32.1** — Web Client: new queue icon, keep library scroll position when navigating back
- **v2.32.0** — Web Client: swipe between tabs, queue nav item dropped from the bottom bar

## v2.31.x

- **v2.31.1** — Web Client: full-width layout on desktop, capped single-column content
- **v2.31.0** — Web Client: layout grows out on tablet and desktop

## v2.30.x

- **v2.30.7** — Web Client: larger artwork and quick actions on the player screen
- **v2.30.6** — Web Client: back chevron on the queue and player screens
- **v2.30.5** — Web Client: freeze now-playing bars while playback is paused
- **v2.30.4** — Web Client: bottom nav padding tweaks
- **v2.30.3** — Web Client: lift the bottom nav clear of the home indicator
- **v2.30.2 / v2.30.1** — Web Client: bottom nav flush against the screen edge
- **v2.30.0** — Web Client: icon-only bottom nav with safe-area offsets; tighter player/queue for mobile; per-component release pipeline

## v2.29.0

- **Web Client**: mirror the Android client as a mobile-first **PWA**; VPS deploy workflow

## v2.28.0

- **Web + Server**: mobile-first revival (NER-128–134)

## v2.27.0 and earlier

See the [GitHub Releases page](https://github.com/Nergy101/Muorg/releases) for older releases.

---

### Versioning policy

Docs are versioned per release — see [Versioned docs](/docs/development#versioned-docs) for when a new docs snapshot is cut.

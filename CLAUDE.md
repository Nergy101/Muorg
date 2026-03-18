# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Muorg** — a cross-platform desktop music organizer (macOS/Windows/Linux) for MP3 and FLAC files. Dense, keyboard-friendly library UI; not a minimal player. See `agent.md` for full context on architecture, conventions, and feature priorities. See `plan.md` for the product roadmap.

## Commands

**Package manager:** `pnpm`

| Command | What it does |
|---------|-------------|
| `pnpm tauri dev` | Run the full Tauri app with hot reload (use this for normal development) |
| `pnpm run dev:mock` | Run with mock catalog data (no real files needed) |
| `pnpm build` | TypeScript check + Vite build (frontend only) |
| `pnpm run check` | Full validation: `pnpm build && cargo check && cargo clippy` |
| `pnpm run check:frontend` | TypeScript check + Vite build |
| `pnpm run check:rust` | `cargo check` + `cargo clippy -D warnings` |
| `pnpm tauri build` | Build platform-specific installers |
| `cargo test` | Run Rust tests (run from `src-tauri/`) |

## Architecture

Three-layer stack:

1. **Rust backend (`src-tauri/src/`)** — Tauri 2 app. File I/O, metadata read/write (ID3 for MP3 via `id3` crate, Vorbis for FLAC via `lofty`), SQLite catalog (`rusqlite`), 21 registered Tauri commands. Entry: `lib.rs` → `commands.rs` + `catalog/` + `metadata/`.

2. **Vue 3 frontend (`src/`)** — TypeScript SPA in the Tauri WebView. State lives in Pinia stores (`catalog`, `settings`, `playlists`). Components communicate with Rust via `invoke()`. Entry: `main.ts` → `App.vue`.

3. **SQLite database** — Persists library roots, track catalog, and playlists. Managed entirely in Rust (`catalog/db.rs`).

### Frontend structure

```
src/
├── stores/          # catalog.ts, settings.ts, playlists.ts
├── components/
│   ├── layout/      # Sidebar (folders, playlists, reports)
│   ├── library/     # Virtualized track table, header, reports modal, key map modal
│   ├── playback/    # Player bar, maximized play screen, queue list, volume
│   ├── metadata/    # Metadata editor (tags, art, Wikipedia)
│   ├── modals/      # Settings modal (all tabs), reports, key map
│   └── shared/      # TrackAlbumArt, FeatherIcon, playlist export/duplicate dialogs
├── composables/     # useDominantColor, useOverlayScrollbars, usePlaylistAdd
├── types.ts         # Shared TS interfaces mirroring Rust types
└── utils/           # pathFormat.ts (Smart Suggestions path template parsing)
```

### Key data flow

- **Catalog**: folders added via Tauri dialog → `add_folder` command → Rust scans and inserts into SQLite → `get_tracks` returns full catalog to Vue → `catalog` store holds tracks, selection, queue, active playlist.
- **Metadata write**: user edits in MetadataEditor → `write_track_metadata` command → Rust writes tag block only (no full file rewrite) → store updated.
- **Settings**: stored in `settings.ts` Pinia store, persisted to AppConfig `settings.yml` via Tauri.
- **Playback**: handled in the frontend via the Web Audio API; Rust provides file path via `read_audio_file`.

## Git commits

- **Never commit automatically** — only run `git commit` when the user explicitly asks
- Always use [Gitmoji](https://gitmoji.dev/) notation at the start of the commit message (e.g. `🐛`, `✨`, `♻️`)
- Keep the summary to 1–2 sentences max — no bullet lists or long bodies

### Adding features

- **New backend capability** → new Tauri command in `commands.rs`, expose via `invoke()` in frontend.
- **New UI** → new Vue component in the matching `components/` subfolder; consume Pinia stores and Tauri commands.
- **New metadata field** → update Rust metadata layer + `MetadataEditor`; keep field names aligned with ID3/Vorbis standards.
- **New setting** → add key to `stores/settings.ts` + appropriate tab in `LibrarySettingsModal`.
- **New shortcut** → register in `LibraryHeader` key handler + document in Key map modal.

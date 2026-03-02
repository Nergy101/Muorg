# Agent instructions for Muorg

This file gives AI agents (and humans) consistent context for working on **Muorg** — The Music Organizer from Hell.

## What Muorg is

- **Desktop app** (macOS, Windows, and Linux) for organizing music files.
- **Library-style UI**: tables, filters, search, metadata forms — not a minimal player.
- **Formats**: MP3 and FLAC only (for now).
- **Main job**: Load folders of music → show catalog → edit metadata (title, artist, album, year, album cover, etc.) → write tags back to files.

See **plan.md** for full vision, features, and technical direction.

---

## Tech stack

| Layer | Choice | Notes |
|-------|--------|--------|
| Desktop shell | **Tauri 2** | Rust backend, WebView frontend. |
| Frontend | **Vue** + **TypeScript** | SPA inside Tauri. |
| Styling | **Tailwind CSS** | Prefer “library/archivist” look: dense, readable. |
| Metadata (Rust) | `id3` (MP3), `lofty` or FLAC crates (FLAC) | Read/write tags + embedded pictures. |
| Catalog | **SQLite** (e.g. `tauri-plugin-sql` or Rust `rusqlite`) | Persist selected directories and all tracks; load on startup; rescan on demand. |
| State (Vue) | **Pinia** | Stores for catalog, selection, UI state. |

---

## Repo layout

- **`src-tauri/`** — Tauri app (Rust): commands, metadata I/O, catalog/scan logic.
- **`src/`** — Frontend: Vue app (components, composables, Pinia stores).
- **`plan.md`** — Product/technical plan.
- **`agent.md`** — This file; agent and contributor guide.

When adding features, prefer:

- **New Tauri commands** in `src-tauri/src/` for: scan folder, get tracks, write metadata, load/save catalog.
- **New Vue components** in `src/components/` for UI (tables, editor, album art).
- **Shared types**: define in Rust and mirror in TypeScript (or generate from Rust) for payloads between backend and frontend.

---

## Conventions

1. **Paths**
   - Use Tauri’s path/dialog APIs where possible so sandbox and permissions stay correct.
   - Never trust path input from the frontend without validation in Rust (allowlist extensions: `.mp3`, `.flac`; resolve to real paths).

2. **Metadata**
   - Prefer standard tag names: Title, Artist, Album, Album Artist, Year, Genre, Track number, Disc number.
   - Album cover: store as embedded picture (e.g. ID3 APIC, FLAC picture block). Support at least JPEG/PNG.

3. **Errors**
   - Rust: return `Result<>` from commands; map to clear error messages (e.g. “Failed to write ID3: …”).
   - Frontend: show per-file or per-operation errors (e.g. toast or inline), don’t fail the whole batch silently.

4. **Performance**
   - For large libraries: scan in background; stream or page results; consider virtualized table.
   - Avoid loading entire file contents into memory when only tags are needed.

5. **Safety**
   - Metadata write = tag block only; no rewriting entire file if the crate allows tag-only updates.
   - Optional: backup tag or file before first write (configurable or behind a “safe mode”).

---

## How to run / build

(Adjust after project is bootstrapped.)

- **Dev**: `pnpm tauri dev`
- **Build**: `pnpm tauri build` — produces platform-specific installers (macOS, Windows, Linux).
- **Tests**: Rust: `cargo test` in `src-tauri/`. Frontend: add `vitest` or Jest and run from repo root.

---

## Feature priorities (for agents)

When implementing, follow this order unless the user says otherwise:

1. **P1**: Add folder(s) → scan MP3/FLAC → build catalog in SQLite → library table → metadata editor (all main fields + album cover) → save to file. Persistence: load catalog from SQLite on startup; rescan action to refresh.
2. **P2**: Search, album/artist grouping, bulk edit, drag-and-drop add.
3. **P3**: Playback, export/reports, theming.

Stick to **plan.md** for scope; don’t add streaming, cloud, or mobile unless the user asks.

---

## Common tasks

| Task | Where | Hint |
|------|--------|------|
| New backend capability (e.g. “get artwork”) | `src-tauri/src/` | New Tauri command; expose via `invoke()`. |
| New UI (e.g. “Album view”) | `src/components/` | Consume existing or new Tauri commands. |
| New metadata field | Rust metadata layer + frontend form | Keep field names aligned with ID3/Vorbis. |
| Persist library | Rust + **SQLite** | Store directory roots and track records (paths, mtime, tag snapshot); load on startup; rescan action to refresh. |

---

## Branding

- **Name**: Muorg — The Music Organizer from Hell.
- **Tone**: Functional, slightly dark/archivist; “library from hell” = dense, no-nonsense, powerful. No cute or playful copy unless the user requests it.
- **Theming**: P3 includes an easter egg **DOOM** themed-theme (classic game style: reds, ambers, dark grays, terminal/green-monitor vibes).

---

When in doubt: prefer safety (no silent overwrites), clarity (obvious errors and success states), and alignment with **plan.md** and this **agent.md**.

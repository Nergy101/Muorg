# Muorg — The Music Organizer from Hell

## Vision

**Muorg** is a cross-platform desktop app that organizes your music library with a library/archivist aesthetic. It loads MP3 and FLAC files, displays them in a library-like UI, and lets you edit embedded metadata (title, artist, album, release year, album art, etc.) so your collection stays consistent and findable.

## Goals

- **Cross-platform**: Runs on **macOS**, **Windows**, and **Linux** with a single codebase.
- **Library-style UI**: Dense, structured, catalog-like interface (tables, filters, search) rather than a minimal player UI.
- **Format support**: Load and edit **MP3** and **FLAC** files.
- **Metadata editing**: Edit and save common tags: title, artist, album, release year, album cover (embedded artwork), and other standard fields (genre, track number, disc number, etc.).

## Non-Goals (for initial scope)

- Streaming or cloud sync.
- Audio playback (focus on organization and metadata; playback can be added later).
- Mobile or web versions.
- Ripping CDs or downloading from services.

---

## Technical direction

### Stack

- **UI framework**: **Tauri 2** (Rust backend + web frontend) for:
  - Single codebase for macOS, Windows, and Linux.
  - Small binary size and good performance.
  - Safe, fast file and metadata handling in Rust.
- **Frontend**: **Vue** + **TypeScript** for the library UI (tables, filters, forms, dialogs).
- **Styling**: **Tailwind CSS** with a “library/archivist” theme (readable typography, clear hierarchy, high information density).

### Why Tauri

- Native desktop on Mac, Windows, and Linux without Electron’s weight.
- Rust backend for file I/O and metadata libraries (e.g. `id3`, `lofty`, `metaflac` or equivalent) with no Node bridge.
- Frontend stays in TypeScript/Vue for rapid UI iteration.

### Metadata & file handling (Rust)

- **MP3**: `id3` crate (ID3v2) for read/write of tags and embedded pictures.
- **FLAC**: `lofty` (or dedicated FLAC crate) for Vorbis comments and picture blocks.
- **Paths**: Use `tauri::api::dialog` and `tauri::api::fs` (or Rust `std::fs`) for open/save; respect sandbox where applicable.
- **Safety**: Validate paths, avoid overwriting non-music files; optional backup-before-write for metadata.

---

## Features (prioritized)

### P1 — Core

1. **Add folder(s)**  
   User picks one or more directories; app recursively scans for `.mp3` and `.flac`, then builds the catalog in **SQLite** (selected directories and all items therein).

2. **Library view**  
   Table/grid of tracks with columns: Title, Artist, Album, Year, Duration, Format, Path. Sort and basic filter (e.g. by artist, album).

3. **Metadata editor**  
   Select a track (or multiple); open a side panel or modal to edit:
   - Title, Artist, Album, Album Artist  
   - Release year, Genre  
   - Track number, Disc number  
   - **Album cover**: load image from file (or paste?), embed in file, clear existing.

4. **Save metadata**  
   Write changes back to the files (ID3 for MP3, Vorbis/picture for FLAC). Show success/error feedback.

5. **Persistence**  
   Store the catalog in **SQLite**: selected directory roots and all tracks therein. Remember last added folders so reopening the app loads the existing catalog without a full rescan. Provide a **rescan** action to refresh directories (e.g. new files, changed metadata, or removed paths).

### P2 — Polish

6. **Search**  
   Full-text or column filter across title, artist, album.

7. **Album / artist grouping**  
   Group by album or artist in the UI (e.g. expandable rows or separate “Album” view).

8. **Bulk edit**  
   Select multiple tracks and set common fields (e.g. same album, same year, same cover).

9. **Drag-and-drop**  
   Add folders or files by dropping onto the window.

### P3 — Later

10. **Playback**  
    Optional in-app preview (e.g. via Tauri command + system/default player or embedded audio).

11. **Export / reports**  
    Export catalog to CSV/JSON; simple “missing metadata” or “duplicate” reports.

12. **Theming**  
    “Library from Hell” dark/light theme (e.g. dim, high-contrast, slightly oppressive aesthetic). Easter egg: **DOOM** themed-theme (inspired by the classic game: reds, ambers, dark grays, terminal/green-monitor vibes).

---

## UI concept (library-like)

- **Sidebar**: “Library” (list of added roots or saved sessions), “Albums”, “Artists”, “Search”.
- **Main area**: Table of tracks (or albums) with sortable columns; multi-select for bulk edit.
- **Detail panel**: When a track (or album) is selected, show metadata form + current artwork; “Save” writes to file(s).
- **Visual tone**: Dense tables, clear typography, limited decoration; optional “archivist” color palette (dark greens/grays, or sepia-like).

---

## Project structure (suggested)

```text
muorg/
├── src-tauri/          # Tauri (Rust) backend
│   ├── src/
│   │   ├── main.rs
│   │   ├── lib.rs
│   │   ├── metadata/   # MP3/FLAC read/write
│   │   ├── catalog/    # Scan, index, store
│   │   └── commands.rs # Tauri commands (scan, get_tracks, write_metadata, …)
│   ├── Cargo.toml
│   └── tauri.conf.json
├── src/                # Frontend (Vue + TS)
│   ├── components/     # LibraryTable, MetadataEditor, AlbumCover, …
│   ├── composables/    # useCatalog, useSelection, …
│   ├── stores/         # Catalog state (Pinia)
│   └── App.vue
├── plan.md
├── agent.md
└── README.md
```

---

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| FLAC/ID3 edge cases (corrupt or non-standard tags) | Use well-tested crates; catch errors per-file; show “failed” list instead of crashing. |
| Large libraries (100k+ files) | Scan in chunks; store catalog in SQLite; lazy-load table rows / virtualize list. |
| Overwriting user data | Only write tag blocks; optional “backup before write”; confirm on bulk save. |

---

## Success criteria

- User can add folders, see all MP3/FLAC in a table, edit metadata (including album art), and save back to files on **macOS**, **Windows**, and **Linux**.
- UI feels like a “library” (structured, catalog-first) and supports at least basic search/filter and bulk edit.
- No data loss: metadata writes are bounded to tag updates with clear feedback.

---

## Out of scope (for this plan)

- Streaming, sync, or cloud.
- Mobile or browser-only version.
- Legal/DRM or format conversion (only read/write existing MP3/FLAC tags).

---

*Next step: set up Tauri 2 + Vue + TypeScript + Tailwind, implement folder scan and catalog in Rust, then build the library table and metadata editor in the frontend.*

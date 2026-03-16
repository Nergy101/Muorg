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
- Mobile or web versions.
- Ripping CDs or downloading from services.

---

## Current state

The app is **shipped** with: add folders, catalog in SQLite, library table with search/grouping and virtual scroll; metadata editor with bulk edit, album art, Apply from path, and Wikipedia cover search; **playlists** (create/rename/delete, add by context menu or drag, filter library by playlist, export to M3U); **queue** (add to queue, queue panel with reorder/clear/play); playback with shuffle, continuous playback, previous/next, restart, and maximized player; **reports** (missing metadata, duplicates, missing album cover) with configurable fields; theming, key map, Smart Suggestions; in-app update check; **hidden roots** (hide folders from table); bottom panel tabs (Library, Metadata, Play, Queue) with resizable layout. **Next** is Phase 4 (catalog export CSV/JSON, backup before write, undo, auto-tagging, workspaces, custom views, ReplayGain).

---

## Technical direction

### Stack

- **UI framework**: **Tauri 2** (Rust backend + web frontend) for a single codebase, small binaries, and fast file/metadata handling in Rust.
- **Frontend**: **Vue 3** + **TypeScript** for the library UI (tables, filters, forms, dialogs).
- **Styling**: **Tailwind CSS** with themeable “library/archivist” look (readable typography, clear hierarchy, high information density).

### Metadata & file handling (Rust)

- **MP3**: ID3v2 for read/write of tags and embedded pictures.
- **FLAC**: Vorbis comments and picture blocks.
- **Paths**: Tauri dialog/fs for open/save; respect sandbox where applicable.
- **Safety**: Validate paths, avoid overwriting non-music files; optional backup-before-write for metadata.

---

## Features — Next (Phase 4)

Prioritized work still to do.

1. **Export catalog**  
   Export full catalog or current view to **CSV** or **JSON** (e.g. for backup, analysis, or use in other tools).

2. **Backup before write**  
   Optional “backup before modifying metadata” (e.g. copy file or save tag backup) so users can revert bad writes.

3. **Undo / redo**  
   Undo (and redo) for metadata edits in the current session (in-memory or limited history).

4. **Auto-tagging (MusicBrainz / acoustic fingerprint)**  
   Optional integration with MusicBrainz or acoustic fingerprinting to suggest or apply metadata from an online database.

5. **Multiple libraries / workspaces**  
   Support more than one catalog (e.g. “Home”, “External drive”) and switch or merge views.

6. **Custom columns / saved views**  
   Let users choose which columns to show and save table layout (sort, column order, visibility) per session or as a preference.

7. **ReplayGain / loudness**  
   Read and display ReplayGain or loudness metadata where present; optional write support later.

---

## UI concept (library-like)

- **Sidebar**: Library roots, add/remove folders, rescan, reports (missing metadata, duplicates), collapse/expand.
- **Main area**: Table of tracks (or grouped by album/artist) with sortable columns, multi-select, search, grouping controls.
- **Detail panel**: When a track (or selection) is chosen, metadata form + album art; Save writes to file(s). Optional player bar for single-track playback.
- **Visual tone**: Dense tables, clear typography, themeable (Dark, Light, Orkish, DOOM, Auto).

---

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| FLAC/ID3 edge cases (corrupt or non-standard tags) | Use well-tested crates; catch errors per-file; show “failed” list instead of crashing. |
| Large libraries (100k+ files) | Scan in chunks; store catalog in SQLite; virtual-scroll table for 100k+ rows. |
| Overwriting user data | Only write tag blocks; optional “backup before write”; confirm on bulk save. |

---

## Success criteria

- User can add folders, see all MP3/FLAC in a table, edit metadata (including album art), and save back to files on **macOS**, **Windows**, and **Linux**.
- UI feels like a “library” (structured, catalog-first) and supports search/filter, grouping, bulk edit, reports, and theming.
- No data loss: metadata writes are bounded to tag updates with clear feedback.

---

## Out of scope (for this plan)

- Streaming, sync, or cloud.
- Mobile or browser-only version.
- Legal/DRM or format conversion (only read/write existing MP3/FLAC tags).

---

## Archived — Completed features

Items below are implemented; kept for reference.

### P1 — Core

- [x] **Add folder(s)** — User picks directories; app recursively scans for `.mp3` and `.flac`, builds catalog in **SQLite** (roots + all tracks).
- [x] **Library view** — Table of tracks with columns: Title, Artist, Album, Year, Duration, Format, Path; optional album art; sort and filter.
- [x] **Metadata editor** — Select one or more tracks; side panel to edit title, artist, album, album artist, year, genre, track/disc number, and **album cover** (load from file, embed, clear).
- [x] **Save metadata** — Write changes to files (ID3 for MP3, Vorbis/picture for FLAC); success/error feedback; catalog refresh after save.
- [x] **Persistence** — Catalog in **SQLite**; remember roots; **rescan** per folder to refresh.

### P2 — Polish

- [x] **Search** — Full-text filter across title, artist, album.
- [x] **Album / artist grouping** — Group by album or artist in the UI; expandable/collapsible rows; album art in group header when shared.
- [x] **Bulk edit** — Select multiple tracks; set common fields only (other fields stay per-track).
- [x] **Drag-and-drop** — Add folders or files by dropping onto the window.

### P3 — Later

- [x] **Playback** — In-app player bar (play/pause, seek, volume, mute); Enter to start/pause; optional auto-play on select.
- [x] **Reports** — “Missing metadata” and “duplicate” reports (configurable fields); open in modal; jump to track / expand group.
- [x] **Theming** — Dark, Light, Orkish, DOOM, and Auto (follow system); settings and key map in UI.

### Additional (implemented)

- [x] **Settings** — General (e.g. check for updates), Theme, Playback (autoplay, continuous, shuffle, volume, marquee, player glow), Keyboard (nav wrap, focus follows mouse), Layout (density, columns, default bottom panel, sidebar/reports visibility, column widths, queue panel width, bottom panel height), Reports (missing fields, group album art), Exports (Music Root Folder for playlist paths), Smart Suggestions (path format, hide Wikipedia cover). Persisted to AppConfig `settings.yml`.
- [x] **Key map** — Modal (Ctrl+K) listing shortcuts: search, refresh, metadata/library/player/queue panels, maximized player, select all, navigation, play.
- [x] **Collapsible sidebar** — Collapse library panel; logo/title/keymap/settings in main toolbar; optional “Start with sidebar closed.”
- [x] **Playing / selection highlight** — Clear row styling for “now playing” and selection across themes.
- [x] **Refresh reports** — Button to reload tracks (and thus report counts) from the sidebar.
- [x] **Virtualization / large libraries** — Virtual-scroll table rows so 100k+ tracks stay responsive (scroll, search, sort without loading everything into the DOM).
- [x] **Tag-from-filename** — Path format template in Settings (Smart Suggestions); “Apply from path” in metadata editor to parse track path and fill/suggest title, artist, album, track number, etc.
- [x] **Auto-updating** — Check for new releases (Settings → General → Check for updates); Tauri updater with download/install from GitHub Releases.
- [x] **Playlists** — Create, rename, delete playlists; add tracks via context menu or drag onto playlist; filter library by active playlist; duplicate handling when adding. Stored in SQLite.
- [x] **Playlist export** — Export playlist to M3U with relative paths; Music Root Folder in Settings → Exports.
- [x] **Queue** — Add to queue (context menu); queue panel (Ctrl+Q) with reorder (drag), play, clear; next/previous use queue when non-empty (unless shuffle); resizable queue panel and bottom panel height.
- [x] **Playback extras** — Shuffle, continuous playback, previous/next, restart from beginning; maximized player (Ctrl+S) with large art and glow.
- [x] **Reports (three)** — Missing metadata (configurable fields), Duplicates, Missing album cover; optional hide reports section; optional album art in Missing metadata groups.
- [x] **Hidden roots** — Per-folder “hide from table” so tracks from that root are excluded from the library view; show/hide all.
- [x] **Bottom panel tabs** — Library, Metadata, Play, Queue; default tab and sizes in Settings.
- [x] **Wikipedia album art** — “From Wikipedia” in metadata editor and on album group headers (optional hide in Smart Suggestions).

---

*Next: tackle remaining Phase 4 items (catalog export CSV/JSON, backup before write, undo, auto-tagging, workspaces, custom views, ReplayGain) as needed.*

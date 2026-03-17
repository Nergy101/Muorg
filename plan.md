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

Phase 4 focuses on **safety**, **export interoperability**, and **workflow power** for larger libraries. The aim is to make changes reversible, data portable, and common “library cleanup” tasks faster.

### Phase 4 epics (prioritized)

#### P4-E1 — Catalog export (CSV / JSON)

**User value**: “Let me get my library out of Muorg for backup, analysis, or migration.”

**Scope**
- Export **entire catalog** or **current filtered/sorted view**.
- Formats:
  - **CSV**: human-friendly, spreadsheet-ready.
  - **JSON**: tooling-friendly, preserves types and nested fields.
- Include both:
  - **Track-level fields** (title, artist, album, year, etc.)
  - **File-level fields** (path, size, modified time, format, duration)
- Optional “include artwork”:
  - Default: **no embedded binary art in exports**
  - If enabled: export **artwork as file references** (e.g. extracted thumbnails or “artwork hash”) rather than raw bytes.

**UX / UI**
- Export action available from:
  - Library toolbar (Export…)
  - Context menu (Export current view…)
- Dialog options:
  - Format selector (CSV / JSON)
  - Scope selector (All tracks / Current view / Selected tracks)
  - Column/field picker (reuse the existing column configuration where possible)
  - File naming template (default: `muorg-catalog-YYYY-MM-DD`)

**Acceptance criteria**
- Exported file is written successfully for large libraries (100k+ rows) without freezing UI (progress + cancel).
- “Current view” export matches visible filters, search, grouping, and sort order.
- CSV escapes correctly (commas, quotes, newlines, UTF-8).
- JSON schema is stable and versioned (`schemaVersion`) so it can evolve without breaking tools.

**Dependencies / notes**
- Reuse catalog query layer used for library table and reports.
- Consider streaming writes for CSV/JSON to avoid memory spikes.

---

#### P4-E2 — Playlist export upgrades (Rockbox / portable paths)

**User value**: “Export playlists that work on my devices (Rockbox, DAPs, etc.).”

**Scope**
- Export:
  - Existing playlist entities (current behavior)
  - “Current view” as an ad-hoc playlist export
- Add target presets:
  - **Standard M3U / M3U8**
  - **Rockbox-friendly** (path rules aligned with Rockbox expectations)
- Path strategies:
  - Relative to **Music Root Folder** (existing setting)
  - Absolute paths
  - Optional “normalize separators” (Windows `\` vs `/`) depending on target
- Encoding:
  - `.m3u8` always UTF-8
  - `.m3u` optionally local/legacy encoding (default UTF-8 unless strong reason otherwise)

**UX / UI**
- Export dialog adds “Target device preset” and previews a few lines of output.
- Clear errors when a track path cannot be made relative to the chosen root.

**Acceptance criteria**
- Rockbox preset produces playlists that load on Rockbox for common folder layouts.
- Export is deterministic and stable (same input -> same output).
- User can export either a named playlist or the current filtered view.

**Dependencies / notes**
- Builds on P4-E1 export dialog plumbing (shared “scope selection” patterns).

---

#### P4-E3 — Backup-before-write (metadata safety net)

**User value**: “I can experiment with edits without fear of destroying my files.”

**Scope**
- Optional setting: **Backup before modifying metadata**
- Backup modes (choose one, or phase in):
  - **Copy file backup** (simplest/most reliable; largest disk cost)
  - **Tag-only backup** (smaller but more complex; store original tag blocks)
- Backup location:
  - Default under app-managed directory (per-workspace or global)
  - Optionally “next to file” (disabled by default to avoid clutter/perms issues)
- Retention:
  - Keep last \(N\) backups per file or time-based retention (configurable)

**UX / UI**
- Before Save: summary shows “Backups will be created: Yes/No”.
- Provide “Reveal backup folder” action and a per-file “Restore from backup…” entry (even if restore is a later epic, design the backup format to support it).

**Acceptance criteria**
- When enabled, any successful tag write results in a restorable backup artifact.
- Backup failures are surfaced clearly and do not silently proceed (user chooses: continue without backup / cancel).
- Works across macOS/Windows/Linux paths and permissions.

**Dependencies / notes**
- Impacts write pipeline; should integrate with Undo (P4-E4) even if undo is session-only.

---

#### P4-E4 — Undo / redo (session-level edits)

**User value**: “I can revert a bad bulk edit immediately.”

**Scope**
- Undo/redo for metadata edits made in the current app session.
- Bound the history:
  - Limit by count (e.g. 50 operations) and/or memory footprint.
- Operation granularity:
  - One “Save” action = one undo step (preferred; matches user mental model)
  - Bulk edit = one step with per-file diffs stored

**UX / UI**
- Standard shortcuts (Cmd/Ctrl+Z, Cmd/Ctrl+Shift+Z).
- Undo stack shown in a lightweight history popover (optional; can be later).

**Acceptance criteria**
- Undo reverts both:
  - In-app catalog view state (SQLite + UI)
  - File tags on disk (when a save has occurred), or clearly defines limitations if not implemented yet.

**Dependencies / notes**
- If “undo on disk” is too risky for first pass, do:
  - Immediate session undo for **unsaved** changes first
  - Then integrate with backups (P4-E3) to support “undo saved changes” by restore.

---

#### P4-E5 — Smart-tagging at album scope (path-based automation)

**User value**: “I can normalize a whole album quickly using my folder conventions.”

**Scope**
- Extend “Apply from path” to operate on:
  - Entire album group
  - Entire selection
- Support templates and rules already in Smart Suggestions:
  - Artist/Album/Track parsing
  - Optional inference of disc/track numbers
  - “Do not overwrite existing fields” toggles

**UX / UI**
- On album group header: “Apply from path to album…”
- Preview changes (diff) before applying to many files.

**Acceptance criteria**
- For a grouped album, applying uses the correct base path and produces consistent results.
- User can review a change list and deselect outliers before applying.

**Dependencies / notes**
- Reuses bulk edit + diff/preview component patterns.

---

#### P4-E6 — Auto-tagging (MusicBrainz / fingerprint-assisted suggestions)

**User value**: “Help me fix unknown tracks and fill in missing metadata reliably.”

**Scope (incremental)**
- Start with **suggest-only** (never auto-write without review).
- Data sources:
  - MusicBrainz lookup by metadata (artist/title/album)
  - Optional acoustic fingerprinting (Chromaprint / AcoustID) for hard cases
- Matching workflow:
  - Candidate matches list with confidence signals
  - Apply selected fields (title/artist/album/year/track#) with preview

**UX / UI**
- “Find matches…” action in metadata panel and/or report rows (missing metadata).
- Side-by-side comparison: current tags vs suggested tags.

**Acceptance criteria**
- User can run lookup for a selection and apply chosen match safely.
- Clear rate limits and caching to avoid hammering services.

**Dependencies / notes**
- Requires careful UX for trust and safety; tie into backups (P4-E3).

---

#### P4-E7 — Workspaces (separate libraries + settings profiles)

**User value**: “Keep different collections (e.g. DAP sync, DJ crates, archive) separated.”

**Scope**
- Create/switch/delete workspaces.
- Each workspace owns:
  - Library roots
  - SQLite catalog
  - Playlists
  - Layout preferences that affect catalog UX (columns, grouping defaults)
- Global settings remain global (theme, keybindings) unless a strong reason to scope them.

**UX / UI**
- Workspace switcher in sidebar/header.
- First-run flow: create default workspace automatically.

**Acceptance criteria**
- Switching workspaces cleanly swaps catalog and playlists without cross-contamination.
- Export/backup paths include workspace name to avoid confusion.

---

#### P4-E8 — Custom views (saved filters + column presets)

**User value**: “I can save ‘reports-as-views’ like a librarian’s index cards.”

**Scope**
- Save a “View” consisting of:
  - Search query
  - Filters
  - Grouping mode
  - Visible columns + widths + sort order
  - Optional playlist filter
- Views appear in sidebar under a “Views” section.

**UX / UI**
- “Save current view…” action and “Update saved view” when active.
- Manage views (rename, reorder, delete).

**Acceptance criteria**
- Activating a view reproduces the exact library presentation reliably.
- Views survive restarts and are workspace-scoped.

---

#### P4-E9 — ReplayGain (analysis + playback normalization)

**User value**: “Consistent loudness across tracks/albums without manual volume riding.”

**Scope (phased)**
- Detect and display ReplayGain tags (track/album gain + peak).
- Optional analysis pass to compute ReplayGain for selected tracks/albums.
- Playback option:
  - Off / Track / Album modes
  - Preamp controls and clipping prevention

**UX / UI**
- Display gain fields in metadata panel (read-only initially).
- Settings toggle for normalization behavior.

**Acceptance criteria**
- If tags exist, Muorg uses them during playback when enabled.
- Analysis (if implemented) is cancellable and doesn’t block the UI.

**Dependencies / notes**
- Requires DSP decisions and careful cross-platform audio behavior; okay to ship read-only support first.

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
| External metadata trust (auto-tagging mismatches) | Suggest-only first; require preview/explicit apply; show confidence + provenance; integrate with backups/restore. |
| Cross-platform path/encoding weirdness (exports/playlists) | UTF-8 by default; test Windows path separators; preview output; validate “Music Root Folder” relativity. |

---

## Success criteria

- User can add folders, see all MP3/FLAC in a table, edit metadata (including album art), and save back to files on **macOS**, **Windows**, and **Linux**.
- UI feels like a “library” (structured, catalog-first) and supports search/filter, grouping, bulk edit, reports, and theming.
- No data loss: metadata writes are bounded to tag updates with clear feedback.
- Phase 4 improvements make Muorg safer and more interoperable: exports are reliable, playlist exports work on target devices, and edits are reversible (undo and/or restore via backups).

---

## Phase 4 definition of done (backlog helper)

For any Phase 4 item to be considered “done” (for a release), it should meet:

- **Clear scope boundary**: The feature explicitly states what it does *not* do yet (e.g. ReplayGain read-only vs analysis).
- **Progress + cancel**: Long-running tasks (scan/export/analysis/lookup) show progress and can be cancelled.
- **No UI freeze**: Library remains responsive during background work.
- **Per-file error reporting**: Failures are isolated and presented in a usable list (with copyable details).
- **Safe defaults**: Any operation that touches files favors safety (preview-first, no silent overwrite, backups when enabled).
- **Workspace-aware** (if workspaces exist): Data is stored and retrieved within the active workspace boundaries.

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

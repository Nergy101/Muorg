import { defineStore } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import type { CatalogTrack } from "../types";
import { useSettingsStore } from "./settings";
import type { MissingMetadataField, SortableColumn } from "./settings";
import { MOCK_COVER_SOURCE_PATH, MOCK_ROOTS, MOCK_TRACKS } from "../mockTracks";

const isMock = () => import.meta.env.VITE_MOCK === "1" || import.meta.env.VITE_MOCK === "true";

// ── Cover fetch concurrency control (module-level, not reactive) ──────────────
// Limits concurrent Tauri IPC cover reads so disk I/O doesn't get overwhelmed.
// Paths are de-duplicated so the same album is never fetched twice simultaneously.
const _coverInFlight = new Set<string>();
const _coverQueue: string[] = [];
let _coverActive = 0;
const COVER_CONCURRENCY = 8;

/** Cover art from backend: base64 data, MIME type (e.g. image/jpeg, image/png), and size in bytes. */
export interface CoverInfo {
  base64: string;
  mime: string;
  size_bytes: number;
}

// ── FTS search debounce (module-level, not reactive) ─────────────────────────
let _searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;

const DEFAULT_GROUP_BY_KEY = "muorg-default-group-by";
const HIDDEN_ROOTS_KEY = "muorg-hidden-roots";

function loadStoredDefaultGroupBy(): "none" | "artist" | "album" {
  if (typeof window === "undefined") return "album";
  const stored = window.localStorage.getItem(DEFAULT_GROUP_BY_KEY);
  if (stored === "none" || stored === "artist" || stored === "album") return stored;
  return "album";
}

function loadStoredHiddenRoots(): string[] {
  if (typeof window === "undefined") return [];
  try {
    const stored = window.localStorage.getItem(HIDDEN_ROOTS_KEY);
    if (!stored) return [];
    const parsed = JSON.parse(stored) as unknown;
    return Array.isArray(parsed) && parsed.every((x) => typeof x === "string") ? parsed : [];
  } catch {
    return [];
  }
}

export const useCatalogStore = defineStore("catalog", {
  state: () => ({
    roots: [] as string[],
    tracks: [] as CatalogTrack[],
    selectedTrackIds: [] as number[],
    currentPlayingTrackId: null as number | null,
    reportFilter: null as null | "missing_metadata" | "duplicates" | "missing_album_cover" | "recently_played" | "most_played",
    reportSingleField: null as MissingMetadataField | null,
    /** FTS5 search results from the backend; null when the JS in-memory filter should be used. */
    searchResults: null as CatalogTrack[] | null,
    loading: false,
    error: null as string | null,
    searchQuery: "",
    /** Minimum rating filter (1–5). Null = no filter. Tracks below this rating are hidden. */
    filterMinRating: null as number | null,
    /** Genre filter. Null = no filter. Only tracks with this exact genre are shown. */
    filterGenre: null as string | null,
    groupBy: loadStoredDefaultGroupBy(),
    /** Cache of track path -> album art (base64 + mime + size) or null if no art. */
    coverCache: {} as Record<string, CoverInfo | null>,
    /** Album key -> cover; updated when any track in that album gets its cover fetched. Used for group headers. */
    albumCoverCache: {} as Record<string, CoverInfo | null>,
    /** When true, MetadataEditor opens the Wikipedia cover modal (e.g. from group header "From Wikipedia"). */
    openWikipediaModal: false,
    /** When true, clicking rows adds to selection (multi-select); header shows "Edit metadata (N selected)". */
    multiSelectMode: false,
    /** Root paths that are hidden from the table (tracks from these folders are filtered out). Persisted to localStorage. */
    hiddenRoots: loadStoredHiddenRoots(),
    /** Ordered list of track IDs in the play queue (filled via "Add to queue" context menu). */
    queueTrackIds: [] as number[],
    /** When set, the player should start playback once this track is loaded (used by queue "play" button). Cleared after play(). */
    playRequestTrackId: null as number | null,
    /** True while the user is dragging a queue item (internal DnD). Used to avoid showing the "drop folders" overlay. */
    isInternalQueueDrag: false,
    /** The ID of the currently active playlist filter (null = no filter / show all). */
    activePlaylistId: null as number | null,
    /** The playlist that current playback was started from (independent of the active filter). */
    playingFromPlaylistId: null as number | null,
    /** Track IDs belonging to the active playlist, in playlist order (null = no filter). */
    activePlaylistTrackIds: null as number[] | null,
    /** `playlist_tracks.id` values parallel to activePlaylistTrackIds — used to delete exactly
     *  one entry when a track appears multiple times in the same playlist. */
    activePlaylistEntryIds: null as number[] | null,
    /** When set, MetadataEditor should load this image file path as the pending cover image. */
    pendingCoverImagePath: null as string | null,
    /** When set, LibraryTable will switch to library tab and scroll to this track. Cleared after scroll. */
    revealTrackId: null as number | null,
    /** When set, shows a full-screen progress overlay for bulk operations like "Apply all from path". */
    bulkProgress: null as { current: number; total: number } | null,
  }),
  getters: {
    selectedTracks(state): CatalogTrack[] {
      const set = new Set(state.selectedTrackIds);
      return state.tracks.filter((t) => set.has(t.id));
    },
    filteredTracks(state): CatalogTrack[] {
      // When a playlist is active, show only its tracks in playlist order (ignoring hidden roots).
      if (state.activePlaylistTrackIds !== null) {
        const idToTrack = new Map(state.tracks.map((t) => [t.id, t]));
        let list = state.activePlaylistTrackIds
          .map((id) => idToTrack.get(id))
          .filter((t): t is CatalogTrack => t != null);
        const q = state.searchQuery.trim().toLowerCase();
        if (q) {
          list = list.filter((t) => {
            const title = (t.title ?? "").toLowerCase();
            const artist = (t.artist ?? "").toLowerCase();
            const album = (t.album ?? "").toLowerCase();
            return title.includes(q) || artist.includes(q) || album.includes(q);
          });
        }
        if (state.filterMinRating !== null) {
          list = list.filter((t) => (t.rating ?? 0) >= state.filterMinRating!);
        }
        if (state.filterGenre !== null) {
          list = list.filter((t) => t.genre === state.filterGenre);
        }
        return list;
      }
      // When FTS results are ready, use them (apply rating/genre on top).
      if (state.searchResults !== null) {
        let list = state.searchResults;
        if (state.filterMinRating !== null) {
          list = list.filter((t) => (t.rating ?? 0) >= state.filterMinRating!);
        }
        if (state.filterGenre !== null) {
          list = list.filter((t) => t.genre === state.filterGenre);
        }
        return list;
      }
      const norm = (p: string) => p.replace(/\\/g, "/").replace(/\/+$/, "") || "/";
      const hiddenSet = new Set(state.hiddenRoots.map(norm));
      let list = state.tracks;
      if (hiddenSet.size > 0) {
        list = list.filter((t) => {
          const tNorm = norm(t.path);
          for (const r of state.roots) {
            const rNorm = norm(r);
            if (tNorm === rNorm || tNorm.startsWith(rNorm + "/")) {
              return !hiddenSet.has(rNorm);
            }
          }
          return true;
        });
      }
      const q = state.searchQuery.trim().toLowerCase();
      if (q) {
        list = list.filter((t) => {
          const title = (t.title ?? "").toLowerCase();
          const artist = (t.artist ?? "").toLowerCase();
          const album = (t.album ?? "").toLowerCase();
          return title.includes(q) || artist.includes(q) || album.includes(q);
        });
      }
      if (state.filterMinRating !== null) {
        list = list.filter((t) => (t.rating ?? 0) >= state.filterMinRating!);
      }
      if (state.filterGenre !== null) {
        list = list.filter((t) => t.genre === state.filterGenre);
      }
      return list;
    },
    /**
     * Tracks in the same order as the table view (groupBy, then group sort).
     * Use this for play next/previous so playback order matches what the user sees.
     */
    tableOrderedTracks(): CatalogTrack[] {
      const base = this.filteredTracks;
      const by = this.groupBy;
      if (by === "none" || !base.length) return base;
      type Group = { key: string; label: string; artist?: string; tracks: CatalogTrack[] };
      const map = new Map<string, Group>();
      for (const t of base) {
        if (by === "artist") {
          const artist = t.artist ?? "—";
          let group = map.get(artist);
          if (!group) {
            group = { key: artist, label: artist, tracks: [] };
            map.set(artist, group);
          }
          group.tracks.push(t);
        } else if (by === "album") {
          const album = t.album ?? "—";
          const artist = t.artist ?? "—";
          const key = `${album}|||${artist}`;
          let group = map.get(key);
          if (!group) {
            group = { key, label: album, artist, tracks: [] };
            map.set(key, group);
          }
          group.tracks.push(t);
        }
      }
      const groups = [...map.values()];
      // Mirror the sort applied in LibraryTableBody so playback order matches display order.
      const settingsStore = useSettingsStore();
      const col: SortableColumn | null = settingsStore.tableSortColumn;
      const dir = settingsStore.tableSortDirection === "desc" ? -1 : 1;
      groups.sort((a, b) => {
        if (col === "year") {
          const aYear = a.tracks.find((t) => t.year != null)?.year ?? 0;
          const bYear = b.tracks.find((t) => t.year != null)?.year ?? 0;
          const diff = aYear - bYear;
          if (diff !== 0) return dir * diff;
        } else if (col === "duration") {
          const aDur = a.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
          const bDur = b.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
          const diff = aDur - bDur;
          if (diff !== 0) return dir * diff;
        } else if (col === "artist") {
          const cmp = (a.artist ?? a.label).localeCompare(b.artist ?? b.label, undefined, { sensitivity: "base" });
          if (cmp !== 0) return dir * cmp;
        } else if (col === "album") {
          const cmp = a.label.localeCompare(b.label, undefined, { sensitivity: "base" });
          if (cmp !== 0) return dir * cmp;
        }
        const byLabel = a.label.localeCompare(b.label, undefined, { sensitivity: "base" });
        if (byLabel !== 0) return byLabel;
        return (a.artist ?? "").localeCompare(b.artist ?? "", undefined, { sensitivity: "base" });
      });
      return groups.flatMap((g) => g.tracks);
    },
    /** Tracks in the queue, in order (resolved from queueTrackIds). */
    queueTracks(state): CatalogTrack[] {
      const idToTrack = new Map(state.tracks.map((t) => [t.id, t]));
      return state.queueTrackIds
        .map((id) => idToTrack.get(id))
        .filter((t): t is CatalogTrack => t != null);
    },
  },
  actions: {
    setCurrentPlaying(id: number | null) {
      this.currentPlayingTrackId = id;
      if (id == null) this.playingFromPlaylistId = null;
      if (id != null) {
        const idx = this.queueTrackIds.indexOf(id);
        if (idx >= 0) {
          this.queueTrackIds = this.queueTrackIds.filter((_, i) => i !== idx);
        }
      }
    },
    setPlayingFromPlaylistId(id: number | null) {
      this.playingFromPlaylistId = id;
    },
    setOpenWikipediaModal(value: boolean) {
      this.openWikipediaModal = value;
    },
    setPendingCoverImagePath(path: string | null) {
      this.pendingCoverImagePath = path;
    },
    setReportFilter(kind: null | "missing_metadata" | "duplicates" | "missing_album_cover" | "recently_played" | "most_played") {
      this.reportFilter = kind;
      if (kind === null) this.reportSingleField = null;
    },
    setReportSingleField(field: MissingMetadataField) {
      this.reportSingleField = field;
      this.reportFilter = "missing_metadata";
    },
    toggleRootVisibility(rootPath: string) {
      const norm = (p: string) => p.replace(/\\/g, "/").replace(/\/+$/, "") || "/";
      const key = norm(rootPath);
      const current = this.hiddenRoots.map(norm);
      const idx = current.indexOf(key);
      if (idx >= 0) {
        this.hiddenRoots = this.hiddenRoots.filter((_, i) => i !== idx);
      } else {
        this.hiddenRoots = [...this.hiddenRoots, rootPath];
      }
      this.persistHiddenRoots();
    },
    isRootHidden(rootPath: string): boolean {
      const norm = (p: string) => p.replace(/\\/g, "/").replace(/\/+$/, "") || "/";
      const key = norm(rootPath);
      return this.hiddenRoots.some((r) => norm(r) === key);
    },
    /** Hide all roots from the library table (all folders get the "hidden" state). */
    hideAllRoots() {
      const rootsList = this.roots;
      if (!rootsList.length) return;
      this.hiddenRoots = rootsList.slice();
      this.persistHiddenRoots();
    },
    /** Show all roots in the table (clear hidden state so every folder is visible). */
    showAllRoots() {
      if (this.hiddenRoots.length === 0) return;
      this.hiddenRoots = [];
      this.persistHiddenRoots();
    },
    persistHiddenRoots() {
      if (typeof window === "undefined") return;
      try {
        window.localStorage.setItem(HIDDEN_ROOTS_KEY, JSON.stringify(this.hiddenRoots));
      } catch {
        // ignore
      }
    },
    async loadRoots() {
      if (isMock()) {
        this.roots = MOCK_ROOTS;
        return;
      }
      this.loading = true;
      this.error = null;
      try {
        this.roots = await invoke<string[]>("get_roots");
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      } finally {
        this.loading = false;
      }
    },
    async loadTracks() {
      if (isMock()) {
        this.tracks = MOCK_TRACKS;
        return;
      }
      this.loading = true;
      this.error = null;
      try {
        this.tracks = await invoke<CatalogTrack[]>("get_tracks");
        // Rebuild album cover cache from existing coverCache so group headers keep showing after refresh (e.g. remove folder).
        // Preserve explicit "no cover" (null) entries so album headers stop showing a spinner once checked.
        const next: Record<string, CoverInfo | null> = {};
        for (const t of this.tracks) {
          const cover = this.coverCache[t.path];
          if (cover) {
            const key = t.album ?? "—";
            if (!(key in next)) next[key] = cover;
          }
        }
        // Carry over any existing nulls (or other values) for albums that were already checked.
        for (const [albumKey, cover] of Object.entries(this.albumCoverCache)) {
          if (!(albumKey in next)) {
            next[albumKey] = cover;
          }
        }
        this.albumCoverCache = next;
        // Eagerly prefetch one cover per unique album in the background so both the
        // grid and the track table are cover-ready before the user scrolls to them.
        this._prefetchAllCovers();
        // On startup (or whenever we have tracks and nothing selected), select the first track in table order.
        if (this.tracks.length > 0 && this.selectedTrackIds.length === 0) {
          const first = this.tableOrderedTracks[0];
          if (first) this.selectedTrackIds = [first.id];
        }
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      } finally {
        this.loading = false;
      }
    },
    async addFolder(path: string) {
      this.loading = true;
      this.error = null;
      try {
        const result = await invoke<{ roots: string[]; tracks_added: number }>(
          "add_folder",
          { path }
        );
        this.roots = result.roots;
        await this.loadTracks();
        return result.tracks_added;
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    /** Add multiple folders in one go (single loading state, one refresh at the end). */
    async addFolders(paths: string[]) {
      if (!paths.length) return;
      this.loading = true;
      this.error = null;
      try {
        for (const path of paths) {
          await invoke<{ roots: string[] }>("add_folder", { path });
        }
        this.roots = await invoke<string[]>("get_roots");
        this.tracks = await invoke<CatalogTrack[]>("get_tracks");
        if (this.tracks.length > 0 && this.selectedTrackIds.length === 0) {
          const first = this.tableOrderedTracks[0];
          if (first) this.selectedTrackIds = [first.id];
        }
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    async rescan(rootPath: string) {
      this.loading = true;
      this.error = null;
      try {
        await invoke<number>("rescan", { rootPath });
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    /** Rescan all roots and reload tracks (e.g. for Ctrl/Cmd+R refresh). */
    async refreshAll() {
      const roots = [...this.roots];
      if (!roots.length) return;
      this.loading = true;
      this.error = null;
      try {
        for (const rootPath of roots) {
          await invoke<number>("rescan", { rootPath });
        }
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    async removeFolder(rootPath: string) {
      this.loading = true;
      this.error = null;
      try {
        await invoke("remove_folder", { rootPath });
        await this.loadRoots();
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    /** Remove all folders from the library (files on disk are not deleted). */
    async removeAllFolders() {
      const list = [...this.roots];
      if (!list.length) return;
      this.loading = true;
      this.error = null;
      try {
        for (const rootPath of list) {
          await invoke("remove_folder", { rootPath });
        }
        this.roots = [];
        this.hiddenRoots = [];
        this.persistHiddenRoots();
        await this.loadRoots();
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    async writeMetadata(path: string, update: import("../types").MetadataUpdate) {
      const settingsStore = useSettingsStore();
      await invoke("write_track_metadata", {
        path,
        update,
        backupBeforeWrite: settingsStore.backupBeforeWrite,
      });
      // Invalidate cached cover so subsequent fetches reflect newly written artwork.
      const nextCoverCache = { ...this.coverCache };
      if (path in nextCoverCache) {
        delete nextCoverCache[path];
      }
      this.coverCache = nextCoverCache;
      await this.loadTracks();
    },
    /** Write the same metadata to multiple tracks, then reload catalog once. Shows global loader until done to avoid flicker. */
    async writeMetadataBulk(paths: string[], update: import("../types").MetadataUpdate) {
      if (paths.length === 0) return;
      this.loading = true;
      this.error = null;
      const settingsStore = useSettingsStore();
      try {
        for (const path of paths) {
          await invoke("write_track_metadata", {
            path,
            update,
            backupBeforeWrite: settingsStore.backupBeforeWrite,
          });
          const nextCoverCache = { ...this.coverCache };
          if (path in nextCoverCache) {
            delete nextCoverCache[path];
          }
          this.coverCache = nextCoverCache;
        }
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    /** Set rating for all currently selected tracks. Keyboard shortcut (1–5, 0 to clear). */
    async setRatingForSelection(rating: number | null) {
      const paths = this.selectedTracks.map((t) => t.path);
      if (paths.length) await this.setRating(paths, rating);
    },
    /** Return all tracks sharing the same album + album_artist as any of the given tracks. */
    getTracksForAlbum(album: string, albumArtist: string): CatalogTrack[] {
      const normAlbum = album.trim().toLowerCase();
      const normArtist = albumArtist.trim().toLowerCase();
      return this.tracks.filter((t) => {
        const tAlbum = (t.album ?? "").trim().toLowerCase();
        const tArtist = (t.album_artist ?? "").trim().toLowerCase();
        return tAlbum === normAlbum && tArtist === normArtist;
      });
    },
    /** Restore a saved session: populate the queue from stored track IDs. */
    restoreSession(queueTrackIds: number[]) {
      const existingIds = new Set(this.tracks.map((t) => t.id));
      const valid = queueTrackIds.filter((id) => existingIds.has(id));
      if (valid.length > 0) {
        this.queueTrackIds = valid;
      }
    },
    /** Record that the given track was played. Optimistically increments play_count in the store. */
    async recordPlay(path: string) {
      if (isMock()) return;
      const now = Math.floor(Date.now() / 1000);
      this.tracks = this.tracks.map((t) =>
        t.path === path
          ? { ...t, play_count: (t.play_count ?? 0) + 1, last_played_at: now }
          : t,
      );
      try {
        await invoke("record_play", { path });
      } catch {
        // Non-critical; ignore errors silently.
      }
    },
    /** Set a star rating (1–5) or clear it (null) for one or more tracks. Optimistic update + backend persist. */
    async setRating(paths: string[], rating: number | null) {
      // Optimistic update in the store
      this.tracks = this.tracks.map((t) =>
        paths.includes(t.path) ? { ...t, rating } : t,
      );
      for (const path of paths) {
        await invoke("set_track_rating", { path, rating });
      }
    },
    toggleSelection(id: number) {
      const i = this.selectedTrackIds.indexOf(id);
      if (i >= 0) {
        this.selectedTrackIds = this.selectedTrackIds.filter((x) => x !== id);
      } else {
        this.selectedTrackIds = [...this.selectedTrackIds, id];
      }
    },
    clearSelection() {
      this.selectedTrackIds = [];
    },
    setSelection(ids: number[]) {
      this.selectedTrackIds = [...ids];
    },
    setMultiSelectMode(value: boolean) {
      this.multiSelectMode = value;
    },
    setSearchQuery(q: string) {
      this.searchQuery = q;
      // Always clear FTS results immediately so the JS filter shows results right away.
      this.searchResults = null;
      if (_searchDebounceTimer) {
        clearTimeout(_searchDebounceTimer);
        _searchDebounceTimer = null;
      }
      if (q.trim().length < 2) return;
      _searchDebounceTimer = setTimeout(async () => {
        _searchDebounceTimer = null;
        if (isMock()) return;
        try {
          const results = await invoke<CatalogTrack[]>("search_tracks", { query: q });
          // Only replace JS results with FTS results if:
          // 1. The query hasn't changed while we were waiting, AND
          // 2. FTS actually returned something (avoids emptying out when index is stale)
          if (this.searchQuery === q && results.length > 0) {
            this.searchResults = results;
          }
        } catch {
          // Leave searchResults null → JS filter continues showing results
        }
      }, 300);
    },
    setFilterMinRating(rating: number | null) {
      this.filterMinRating = rating;
    },
    setFilterGenre(genre: string | null) {
      this.filterGenre = genre;
    },
    setRevealTrackId(id: number | null) {
      this.revealTrackId = id;
    },
    setBulkProgress(progress: { current: number; total: number } | null) {
      this.bulkProgress = progress;
    },
    setGroupBy(mode: "none" | "artist" | "album") {
      this.groupBy = mode;
    },
    /** Append one or more tracks to the play queue. */
    addToQueue(trackIds: number[]) {
      if (!trackIds.length) return;
      const existing = new Set(this.queueTrackIds);
      const toAdd = trackIds.filter((id) => !existing.has(id));
      for (const id of toAdd) existing.add(id);
      this.queueTrackIds = [...this.queueTrackIds, ...toAdd];
    },
    /** Append all tracks from an array (e.g. album/group) to the queue. */
    addTracksToQueue(tracks: CatalogTrack[]) {
      this.addToQueue(tracks.map((t) => t.id));
    },
    /** Remove a single item from the queue by index. */
    removeFromQueueAtIndex(index: number) {
      if (index < 0 || index >= this.queueTrackIds.length) return;
      this.queueTrackIds = this.queueTrackIds.filter((_, i) => i !== index);
    },
    /** Move a queue item so it takes the index of the drop target (dragged item takes that position, others shift). */
    reorderQueue(fromIndex: number, toIndex: number) {
      if (fromIndex === toIndex) return;
      if (fromIndex < 0 || fromIndex >= this.queueTrackIds.length) return;
      if (toIndex < 0 || toIndex >= this.queueTrackIds.length) return;
      const id = this.queueTrackIds[fromIndex];
      const next = this.queueTrackIds.filter((_, i) => i !== fromIndex);
      // toIndex is the final index the item should occupy; insert there in the shortened array.
      next.splice(toIndex, 0, id);
      this.queueTrackIds = next;
    },
    /** Clear the entire queue. */
    clearQueue() {
      this.queueTrackIds = [];
    },
    /** Request that the player start playback once the given track is loaded. Cleared by the player after play(). */
    setPlayRequestTrackId(id: number | null) {
      this.playRequestTrackId = id;
    },
    setInternalQueueDrag(value: boolean) {
      this.isInternalQueueDrag = value;
    },
    setActivePlaylist(id: number, entries: { entryId: number; trackId: number }[]) {
      this.activePlaylistId = id;
      this.activePlaylistTrackIds = entries.map((e) => e.trackId);
      this.activePlaylistEntryIds = entries.map((e) => e.entryId);
      // Don't change selection (and thus don't interrupt playback) if something is currently playing.
      if (this.currentPlayingTrackId === null) {
        this.clearSelection();
        if (entries.length > 0) {
          this.selectedTrackIds = [entries[0].trackId];
        }
      }
    },
    clearActivePlaylist() {
      this.activePlaylistId = null;
      this.activePlaylistTrackIds = null;
      this.activePlaylistEntryIds = null;
      // Don't interrupt playback when clearing the playlist filter.
      if (this.currentPlayingTrackId === null) {
        this.clearSelection();
      }
    },
    getCover(path: string): CoverInfo | null | undefined {
      return this.coverCache[path];
    },
    /** Data URL for embedding cover in img src. Use this so PNG/other types display correctly. */
    getCoverDataUrl(path: string): string | null {
      const c = this.coverCache[path];
      if (!c) return null;
      return `data:${c.mime};base64,${c.base64}`;
    },
    /** Drain the cover fetch queue up to COVER_CONCURRENCY active fetches. */
    _drainCoverQueue() {
      while (_coverActive < COVER_CONCURRENCY && _coverQueue.length > 0) {
        const path = _coverQueue.shift()!;
        if (path in this.coverCache) {
          _coverInFlight.delete(path);
          continue;
        }
        _coverActive++;
        const run = async () => {
          try {
            let cover: CoverInfo | null;
            if (isMock()) {
              const existing = this.tracks.find((t) => t.path in this.coverCache);
              const cached = existing ? this.coverCache[existing.path] : undefined;
              if (cached !== undefined) {
                cover = cached;
              } else {
                const result = await invoke<CoverInfo | null>("get_track_cover", { path: MOCK_COVER_SOURCE_PATH });
                cover = result ?? null;
              }
            } else {
              const result = await invoke<CoverInfo | null>("get_track_cover", { path });
              cover = result ?? null;
            }
            this.coverCache = { ...this.coverCache, [path]: cover };
            const track = this.tracks.find((t) => t.path === path);
            if (track) {
              const albumKey = track.album ?? "—";
              if (!this.albumCoverCache[albumKey]) {
                this.albumCoverCache = { ...this.albumCoverCache, [albumKey]: cover };
              }
            }
          } catch {
            this.coverCache = { ...this.coverCache, [path]: null };
          } finally {
            _coverInFlight.delete(path);
            _coverActive--;
            this._drainCoverQueue();
          }
        };
        run();
      }
    },
    /** Enqueue a cover fetch. No-op if already cached or already in-flight. */
    fetchCover(path: string) {
      if (path in this.coverCache) return;
      if (_coverInFlight.has(path)) return;
      _coverInFlight.add(path);
      _coverQueue.push(path);
      this._drainCoverQueue();
    },
    /** Move a path to the front of the fetch queue so it loads before off-screen albums. */
    boostCoverPriority(path: string) {
      if (path in this.coverCache || !_coverInFlight.has(path)) return;
      const idx = _coverQueue.indexOf(path);
      if (idx > 0) {
        _coverQueue.splice(idx, 1);
        _coverQueue.unshift(path);
      }
    },
    /** Fire-and-forget: enqueue one cover fetch per unique album for the current track list. */
    _prefetchAllCovers() {
      const seen = new Set<string>();
      for (const track of this.tracks) {
        if (!track.has_cover) continue;
        const albumKey = track.album ?? "—";
        if (seen.has(albumKey)) continue;
        seen.add(albumKey);
        this.fetchCover(track.path);
      }
    },
  },
});

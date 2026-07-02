import { defineStore } from "pinia";
import type { CatalogTrack } from "../types";
import { useSettingsStore } from "./settings";
import type { MissingMetadataField, SortableColumn } from "./settings";
import { MOCK_ROOTS, MOCK_TRACKS } from "../mockTracks";
import * as api from "../api/catalog";

export type { CoverInfo } from "../api/catalog";
import type { CoverInfo } from "../api/catalog";
import type { UndoEntry, UndoSnapshot } from "../types";

const isMock = () => import.meta.env.VITE_MOCK === "1" || import.meta.env.VITE_MOCK === "true";

// ── Cover fetch concurrency control (module-level, not reactive) ──────────────
const _coverInFlight = new Set<string>();
const _coverQueue: string[] = [];
let _coverActive = 0;
const COVER_CONCURRENCY = 8;

// ── FTS search debounce (module-level, not reactive) ─────────────────────────
let _searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;

const DEFAULT_GROUP_BY_KEY = "muorg-default-group-by";
const HIDDEN_ROOTS_KEY = "muorg-hidden-roots";
const FILTER_STATE_KEY = "muorg-filter-state";

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

function loadStoredFilterState(): { searchQuery: string; filterMinRating: number | null; filterGenre: string | null } {
  if (typeof window === "undefined") return { searchQuery: "", filterMinRating: null, filterGenre: null };
  try {
    const stored = window.sessionStorage.getItem(FILTER_STATE_KEY);
    if (!stored) return { searchQuery: "", filterMinRating: null, filterGenre: null };
    const parsed = JSON.parse(stored) as unknown;
    if (parsed && typeof parsed === "object") {
      const obj = parsed as Record<string, unknown>;
      return {
        searchQuery: typeof obj.searchQuery === "string" ? obj.searchQuery : "",
        filterMinRating: typeof obj.filterMinRating === "number" ? obj.filterMinRating : null,
        filterGenre: typeof obj.filterGenre === "string" ? obj.filterGenre : null,
      };
    }
    return { searchQuery: "", filterMinRating: null, filterGenre: null };
  } catch {
    return { searchQuery: "", filterMinRating: null, filterGenre: null };
  }
}

function persistFilterState(searchQuery: string, filterMinRating: number | null, filterGenre: string | null) {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(FILTER_STATE_KEY, JSON.stringify({ searchQuery, filterMinRating, filterGenre }));
  } catch {
    // ignore
  }
}

export const useCatalogStore = defineStore("catalog", {
  state: () => {
    const initialFilter = loadStoredFilterState();
    return {
    roots: [] as string[],
    tracks: [] as CatalogTrack[],
    selectedTrackIds: [] as number[],
    currentPlayingTrackId: null as number | null,
    reportFilter: null as null | "missing_metadata" | "duplicates" | "missing_album_cover" | "recently_played" | "most_played",
    reportSingleField: null as MissingMetadataField | null,
    searchResults: null as CatalogTrack[] | null,
    loading: false,
    error: null as string | null,
    searchQuery: initialFilter.searchQuery,
    filterMinRating: initialFilter.filterMinRating,
    filterGenre: initialFilter.filterGenre,
    groupBy: loadStoredDefaultGroupBy(),
    coverCache: {} as Record<string, CoverInfo | null>,
    _coverCacheOrder: [] as string[],
    albumCoverCache: {} as Record<string, CoverInfo | null>,
    openWikipediaModal: false,
    multiSelectMode: false,
    hiddenRoots: loadStoredHiddenRoots(),
    queueTrackIds: [] as number[],
    playRequestTrackId: null as number | null,
    isInternalQueueDrag: false,
    pendingDragTrackIds: null as number[] | null,
    activePlaylistId: null as number | null,
    playingFromPlaylistId: null as number | null,
    activePlaylistTrackIds: null as number[] | null,
    activePlaylistEntryIds: null as number[] | null,
    pendingCoverImagePath: null as string | null,
    revealTrackId: null as number | null,
    bulkProgress: null as { current: number; total: number } | null,
    bulkCancelled: false,
    sessionBackupCount: 0,
    undoStack: [] as UndoEntry[],
    redoStack: [] as UndoEntry[],
    };
  },
  getters: {
    selectedTracks(state): CatalogTrack[] {
      const set = new Set(state.selectedTrackIds);
      return state.tracks.filter((t) => set.has(t.id));
    },
    filteredTracks(state): CatalogTrack[] {
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
    queueTracks(state): CatalogTrack[] {
      const idToTrack = new Map(state.tracks.map((t) => [t.id, t]));
      return state.queueTrackIds
        .map((id) => idToTrack.get(id))
        .filter((t): t is CatalogTrack => t != null);
    },
    canUndo(): boolean {
      return this.undoStack.length > 0;
    },
    canRedo(): boolean {
      return this.redoStack.length > 0;
    },
  },
  actions: {
    /** Look up a track ID from its file path. Returns undefined if not found. */
    _trackIdByPath(path: string): number | undefined {
      return this.tracks.find((t) => t.path === path)?.id;
    },
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
    hideAllRoots() {
      const rootsList = this.roots;
      if (!rootsList.length) return;
      this.hiddenRoots = rootsList.slice();
      this.persistHiddenRoots();
    },
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
        this.roots = await api.getRoots();
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
        this.tracks = await api.getTracks();
        const next: Record<string, CoverInfo | null> = {};
        for (const t of this.tracks) {
          const cover = this.coverCache[t.path];
          if (cover) {
            const key = t.album ?? "—";
            if (!(key in next)) next[key] = cover;
          }
        }
        for (const [albumKey, cover] of Object.entries(this.albumCoverCache)) {
          if (!(albumKey in next)) {
            next[albumKey] = cover;
          }
        }
        this.albumCoverCache = next;
        this._prefetchAllCovers();
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
        const result = await api.addFolder(path);
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
    async addFolders(paths: string[]) {
      if (!paths.length) return;
      this.loading = true;
      this.error = null;
      try {
        for (const path of paths) {
          await api.rescan(path);
        }
        this.roots = await api.getRoots();
        this.tracks = await api.getTracks();
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
        await api.rescan(rootPath);
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    async refreshAll() {
      const roots = [...this.roots];
      if (!roots.length) return;
      this.loading = true;
      this.error = null;
      try {
        for (const rootPath of roots) {
          await api.rescan(rootPath);
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
        await api.removeFolder(rootPath);
        await this.loadRoots();
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
      }
    },
    async removeAllFolders() {
      const list = [...this.roots];
      if (!list.length) return;
      this.loading = true;
      this.error = null;
      try {
        for (const rootPath of list) {
          await api.removeFolder(rootPath);
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
    /** Build undo snapshots for the given paths using current store track state. */
    _buildSnapshots(paths: string[]): UndoSnapshot[] {
      return paths
        .map((path): UndoSnapshot | null => {
          const track = this.tracks.find((t) => t.path === path);
          if (!track) return null;
          const cover = this.coverCache[path];
          return {
            trackId: track.id,
            path,
            metadata: {
              title: track.title ?? null,
              artist: track.artist ?? null,
              album: track.album ?? null,
              album_artist: track.album_artist ?? null,
              featuring: track.featuring ?? null,
              year: track.year ?? null,
              genre: track.genre ?? null,
              track_number: track.track_number ?? null,
              disc_number: track.disc_number ?? null,
              picture_base64: cover?.base64 ?? undefined,
            },
          };
        })
        .filter((s): s is UndoSnapshot => s != null);
    },

    /** Push an undo entry and clear the redo stack. */
    _pushUndo(paths: string[], description: string) {
      const snapshots = this._buildSnapshots(paths);
      if (snapshots.length === 0) return;
      const MAX_UNDO = 50;
      this.undoStack = [...this.undoStack.slice(-(MAX_UNDO - 1)), { description, snapshots }];
      this.redoStack = [];
    },

    /** Restore an undo/redo entry's snapshots back to disk + DB. */
    async _applySnapshots(entry: UndoEntry) {
      let hadBackupTrack = false;
      for (const snap of entry.snapshots) {
        await api.patchMetadata(snap.trackId, snap.metadata, false);
        if (!hadBackupTrack) {
          const backup = await api.getLatestBackup(snap.trackId).catch(() => null);
          if (backup) hadBackupTrack = true;
        }
        if ("picture_base64" in snap.metadata && snap.metadata.picture_base64 !== undefined) {
          const next = { ...this.coverCache };
          if (snap.path in next) {
            delete next[snap.path];
          }
          this.coverCache = next;
        }
      }
      if (hadBackupTrack) this.sessionBackupCount += 1;
      await this.loadTracks();
    },

    async writeMetadata(path: string, update: import("../types").MetadataUpdate, description?: string) {
      // Snapshot current state for undo before applying the change
      const trackTitle = this.tracks.find((t) => t.path === path)?.title ?? path;
      this._pushUndo([path], description ?? `Edit "${trackTitle}"`);
      const settingsStore = useSettingsStore();
      const id = this._trackIdByPath(path);
      if (id == null) throw new Error(`Track not found: ${path}`);
      await api.patchMetadata(id, update, settingsStore.backupBeforeWrite);
      const nextCoverCache = { ...this.coverCache };
      if (path in nextCoverCache) {
        delete nextCoverCache[path];
      }
      this.coverCache = nextCoverCache;
      await this.loadTracks();
    },
    async writeMetadataBulk(paths: string[], update: import("../types").MetadataUpdate) {
      if (paths.length === 0) return;
      // Snapshot current state for undo before applying the change
      this._pushUndo(paths, `Edit ${paths.length} tracks`);
      this.loading = true;
      this.error = null;
      this.bulkCancelled = false;
      this.bulkProgress = { current: 0, total: paths.length };
      try {
        // Use batch endpoint when possible, fall back to per-track otherwise
        const items = paths
          .map((path) => {
            const id = this._trackIdByPath(path);
            return id != null ? { id, update } as const : null;
          })
          .filter((item): item is { id: number; update: import("../types").MetadataUpdate } => item != null);
        if (items.length > 0) {
          await api.patchMetadataBatch(items);
        }
        const nextCoverCache = { ...this.coverCache };
        for (const path of paths) {
          if (this.bulkCancelled) break;
          if (path in nextCoverCache) {
            delete nextCoverCache[path];
          }
        }
        this.coverCache = nextCoverCache;
        this.bulkProgress = { current: paths.length, total: paths.length };
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
        this.bulkProgress = null;
      }
    },
    async undo() {
      const entry = this.undoStack.pop();
      if (!entry) return;
      // Capture current state of affected tracks so redo can restore it
      const currentPaths = entry.snapshots.map((s) => s.path);
      const redoSnapshots = this._buildSnapshots(currentPaths);
      if (redoSnapshots.length > 0) {
        this.redoStack = [
          ...this.redoStack.slice(-(50 - 1)),
          { description: entry.description, snapshots: redoSnapshots },
        ];
      }
      await this._applySnapshots(entry);
    },
    async redo() {
      const entry = this.redoStack.pop();
      if (!entry) return;
      // Capture current state of affected tracks so undo can restore it
      const currentPaths = entry.snapshots.map((s) => s.path);
      const undoSnapshots = this._buildSnapshots(currentPaths);
      if (undoSnapshots.length > 0) {
        this.undoStack = [
          ...this.undoStack.slice(-(50 - 1)),
          { description: entry.description, snapshots: undoSnapshots },
        ];
      }
      await this._applySnapshots(entry);
    },
    /** Bulk write with per-track custom updates. Pushes a single undo entry. */
    async writeMetadataCustomBulk(
      updates: { path: string; update: import("../types").MetadataUpdate }[],
    ) {
      if (updates.length === 0) return;
      const paths = updates.map((u) => u.path);
      this._pushUndo(paths, `Edit ${paths.length} tracks`);
      this.loading = true;
      this.error = null;
      this.bulkCancelled = false;
      this.bulkProgress = { current: 0, total: updates.length };
      const settingsStore = useSettingsStore();
      try {
        for (let i = 0; i < updates.length; i++) {
          if (this.bulkCancelled) break;
          const { path, update } = updates[i];
          const id = this._trackIdByPath(path);
          if (id == null) continue;
          await api.patchMetadata(id, update, settingsStore.backupBeforeWrite);
          const next = { ...this.coverCache };
          if (path in next) delete next[path];
          this.coverCache = next;
          this.bulkProgress = { current: i + 1, total: updates.length };
        }
        await this.loadTracks();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        throw e;
      } finally {
        this.loading = false;
        this.bulkProgress = null;
      }
    },
    async setRatingForSelection(rating: number | null) {
      const paths = this.selectedTracks.map((t) => t.path);
      if (paths.length) await this.setRating(paths, rating);
    },
    getTracksForAlbum(album: string, albumArtist: string): CatalogTrack[] {
      const normAlbum = album.trim().toLowerCase();
      const normArtist = albumArtist.trim().toLowerCase();
      return this.tracks.filter((t) => {
        const tAlbum = (t.album ?? "").trim().toLowerCase();
        const tArtist = (t.album_artist ?? "").trim().toLowerCase();
        return tAlbum === normAlbum && tArtist === normArtist;
      });
    },
    restoreSession(queueTrackIds: number[]) {
      const existingIds = new Set(this.tracks.map((t) => t.id));
      const valid = queueTrackIds.filter((id) => existingIds.has(id));
      if (valid.length > 0) {
        this.queueTrackIds = valid;
      }
    },
    async recordPlay(path: string) {
      if (isMock()) return;
      const id = this._trackIdByPath(path);
      if (id == null) return;
      const now = Math.floor(Date.now() / 1000);
      this.tracks = this.tracks.map((t) =>
        t.path === path
          ? { ...t, play_count: (t.play_count ?? 0) + 1, last_played_at: now }
          : t,
      );
      try {
        await api.recordPlay(id);
      } catch {
        // Non-critical; ignore errors silently.
      }
    },
    async setRating(paths: string[], rating: number | null) {
      this.tracks = this.tracks.map((t) =>
        paths.includes(t.path) ? { ...t, rating } : t,
      );
      for (const path of paths) {
        const id = this._trackIdByPath(path);
        if (id == null) continue;
        await api.setRating(id, rating);
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
      this.searchResults = null;
      persistFilterState(q, this.filterMinRating, this.filterGenre);
      if (_searchDebounceTimer) {
        clearTimeout(_searchDebounceTimer);
        _searchDebounceTimer = null;
      }
      if (q.trim().length < 2) return;
      _searchDebounceTimer = setTimeout(async () => {
        _searchDebounceTimer = null;
        if (isMock()) return;
        try {
          const results = await api.searchTracks(q);
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
      persistFilterState(this.searchQuery, rating, this.filterGenre);
    },
    setFilterGenre(genre: string | null) {
      this.filterGenre = genre;
      persistFilterState(this.searchQuery, this.filterMinRating, genre);
    },
    setRevealTrackId(id: number | null) {
      this.revealTrackId = id;
    },
    setBulkProgress(progress: { current: number; total: number } | null) {
      this.bulkProgress = progress;
      if (progress === null) this.bulkCancelled = false;
    },
    setBulkCancelled(value: boolean) {
      this.bulkCancelled = value;
    },
    setGroupBy(mode: "none" | "artist" | "album") {
      this.groupBy = mode;
    },
    addToQueue(trackIds: number[]) {
      if (!trackIds.length) return;
      const existing = new Set(this.queueTrackIds);
      const toAdd = trackIds.filter((id) => !existing.has(id));
      for (const id of toAdd) existing.add(id);
      this.queueTrackIds = [...this.queueTrackIds, ...toAdd];
    },
    addTracksToQueue(tracks: CatalogTrack[]) {
      this.addToQueue(tracks.map((t) => t.id));
    },
    removeFromQueueAtIndex(index: number) {
      if (index < 0 || index >= this.queueTrackIds.length) return;
      this.queueTrackIds = this.queueTrackIds.filter((_, i) => i !== index);
    },
    reorderQueue(fromIndex: number, toIndex: number) {
      if (fromIndex === toIndex) return;
      if (fromIndex < 0 || fromIndex >= this.queueTrackIds.length) return;
      if (toIndex < 0 || toIndex >= this.queueTrackIds.length) return;
      const id = this.queueTrackIds[fromIndex];
      const next = this.queueTrackIds.filter((_, i) => i !== fromIndex);
      next.splice(toIndex, 0, id);
      this.queueTrackIds = next;
    },
    clearQueue() {
      this.queueTrackIds = [];
    },
    setPlayRequestTrackId(id: number | null) {
      this.playRequestTrackId = id;
    },
    setInternalQueueDrag(value: boolean, trackIds?: number[]) {
      this.isInternalQueueDrag = value;
      this.pendingDragTrackIds = value ? (trackIds ?? null) : null;
    },
    setActivePlaylist(id: number, entries: { entryId: number; trackId: number }[]) {
      this.activePlaylistId = id;
      this.activePlaylistTrackIds = entries.map((e) => e.trackId);
      this.activePlaylistEntryIds = entries.map((e) => e.entryId);
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
      if (this.currentPlayingTrackId === null) {
        this.clearSelection();
      }
    },
    getCover(path: string): CoverInfo | null | undefined {
      // Touch on access to keep LRU order accurate
      if (path in this.coverCache) {
        this._touchCover(path);
      }
      return this.coverCache[path];
    },
    getCoverDataUrl(path: string): string | null {
      const c = this.coverCache[path];
      if (!c) return null;
      this._touchCover(path);
      return `data:${c.mime};base64,${c.base64}`;
    },
    /** Touch a path in the LRU order (moves it to most-recently-used). */
    _touchCover(path: string) {
      const idx = this._coverCacheOrder.indexOf(path);
      if (idx > 0) {
        this._coverCacheOrder.splice(idx, 1);
        this._coverCacheOrder.push(path);
      } else if (idx < 0) {
        this._coverCacheOrder.push(path);
      }
    },
    /** Evict oldest entries if cache exceeds MAX_COVERS (500). */
    _pruneCoverCache() {
      const MAX_COVERS = 500;
      if (this._coverCacheOrder.length <= MAX_COVERS) return;
      const evict = this._coverCacheOrder.length - MAX_COVERS;
      const evicted = new Set(this._coverCacheOrder.slice(0, evict));
      this._coverCacheOrder = this._coverCacheOrder.slice(evict);
      const next = { ...this.coverCache };
      for (const path of evicted) {
        delete next[path];
      }
      this.coverCache = next;
    },
    /** Set a cover entry in the cache and update LRU order. */
    _setCover(path: string, cover: CoverInfo | null | undefined) {
      this._touchCover(path);
      this.coverCache = { ...this.coverCache, [path]: cover ?? null };
      this._pruneCoverCache();
    },
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
              cover = null;
            } else {
              const id = this._trackIdByPath(path);
              cover = id != null ? await api.getCover(id) : null;
            }
            this._setCover(path, cover);
            const track = this.tracks.find((t) => t.path === path);
            if (track) {
              const albumKey = track.album ?? "—";
              if (!this.albumCoverCache[albumKey]) {
                this.albumCoverCache = { ...this.albumCoverCache, [albumKey]: cover };
              }
            }
          } catch {
            this._setCover(path, null);
          } finally {
            _coverInFlight.delete(path);
            _coverActive--;
            this._drainCoverQueue();
          }
        };
        run();
      }
    },
    fetchCover(path: string) {
      if (path in this.coverCache) return;
      if (_coverInFlight.has(path)) return;
      _coverInFlight.add(path);
      _coverQueue.push(path);
      this._drainCoverQueue();
    },
    boostCoverPriority(path: string) {
      if (path in this.coverCache || !_coverInFlight.has(path)) return;
      const idx = _coverQueue.indexOf(path);
      if (idx > 0) {
        _coverQueue.splice(idx, 1);
        _coverQueue.unshift(path);
      }
    },
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

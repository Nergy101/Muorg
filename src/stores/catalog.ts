import { defineStore } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import type { CatalogTrack } from "../types";
import { MOCK_COVER_SOURCE_PATH, MOCK_ROOTS, MOCK_TRACKS } from "../mockTracks";

const isMock = () => import.meta.env.VITE_MOCK === "1" || import.meta.env.VITE_MOCK === "true";

/** Cover art from backend: base64 data, MIME type (e.g. image/jpeg, image/png), and size in bytes. */
export interface CoverInfo {
  base64: string;
  mime: string;
  size_bytes: number;
}

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
    reportFilter: null as null | "missing_metadata" | "duplicates" | "missing_album_cover",
    loading: false,
    error: null as string | null,
    searchQuery: "",
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
  }),
  getters: {
    selectedTracks(state): CatalogTrack[] {
      const set = new Set(state.selectedTrackIds);
      return state.tracks.filter((t) => set.has(t.id));
    },
    filteredTracks(state): CatalogTrack[] {
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
      if (!q) return list;
      return list.filter((t) => {
        const title = (t.title ?? "").toLowerCase();
        const artist = (t.artist ?? "").toLowerCase();
        const album = (t.album ?? "").toLowerCase();
        return title.includes(q) || artist.includes(q) || album.includes(q);
      });
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
      groups.sort((a, b) => {
        const byLabel = a.label.localeCompare(b.label, undefined, { sensitivity: "base" });
        if (byLabel !== 0) return byLabel;
        const aArtist = (a.artist ?? "").toLowerCase();
        const bArtist = (b.artist ?? "").toLowerCase();
        return aArtist.localeCompare(bArtist);
      });
      return groups.flatMap((g) => g.tracks);
    },
  },
  actions: {
    setCurrentPlaying(id: number | null) {
      this.currentPlayingTrackId = id;
    },
    setOpenWikipediaModal(value: boolean) {
      this.openWikipediaModal = value;
    },
    setReportFilter(kind: null | "missing_metadata" | "duplicates" | "missing_album_cover") {
      this.reportFilter = kind;
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
      await invoke("write_track_metadata", { path, update });
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
      try {
        for (const path of paths) {
          await invoke("write_track_metadata", { path, update });
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
    },
    setGroupBy(mode: "none" | "artist" | "album") {
      this.groupBy = mode;
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
    async fetchCover(path: string) {
      if (path in this.coverCache) return;
      if (isMock()) {
        const existing = this.tracks.find((t) => t.path in this.coverCache);
        const cached = existing ? this.coverCache[existing.path] : undefined;
        if (cached !== undefined) {
          this.coverCache = { ...this.coverCache, [path]: cached };
          const track = this.tracks.find((t) => t.path === path);
          if (track) {
            const albumKey = track.album ?? "—";
            this.albumCoverCache = { ...this.albumCoverCache, [albumKey]: cached };
          }
          return;
        }
        try {
          const result = await invoke<CoverInfo | null>("get_track_cover", {
            path: MOCK_COVER_SOURCE_PATH,
          });
          const cover = result ?? null;
          const nextCover = { ...this.coverCache };
          const nextAlbum = { ...this.albumCoverCache };
          for (const t of this.tracks) {
            nextCover[t.path] = cover;
          }
          const albumKey = this.tracks[0]?.album ?? "—";
          nextAlbum[albumKey] = cover;
          this.coverCache = nextCover;
          this.albumCoverCache = nextAlbum;
        } catch {
          this.coverCache = { ...this.coverCache, [path]: null };
        }
        return;
      }
      try {
        const result = await invoke<CoverInfo | null>("get_track_cover", { path });
        const cover = result ?? null;
        this.coverCache = { ...this.coverCache, [path]: cover };
        const track = this.tracks.find((t) => t.path === path);
        if (track) {
          const albumKey = track.album ?? "—";
          // Store even null so album headers know we've checked and can stop showing a spinner.
          this.albumCoverCache = { ...this.albumCoverCache, [albumKey]: cover };
        }
      } catch {
        this.coverCache = { ...this.coverCache, [path]: null };
      }
    },
  },
});

import { defineStore } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import type { CatalogTrack } from "../types";

/** Cover art from backend: base64 data, MIME type (e.g. image/jpeg, image/png), and size in bytes. */
export interface CoverInfo {
  base64: string;
  mime: string;
  size_bytes: number;
}

const DEFAULT_GROUP_BY_KEY = "muorg-default-group-by";

function loadStoredDefaultGroupBy(): "none" | "artist" | "album" {
  if (typeof window === "undefined") return "album";
  const stored = window.localStorage.getItem(DEFAULT_GROUP_BY_KEY);
  if (stored === "none" || stored === "artist" || stored === "album") return stored;
  return "album";
}

export const useCatalogStore = defineStore("catalog", {
  state: () => ({
    roots: [] as string[],
    tracks: [] as CatalogTrack[],
    selectedTrackIds: [] as number[],
    currentPlayingTrackId: null as number | null,
    reportFilter: null as null | "missing_metadata" | "duplicates",
    loading: false,
    error: null as string | null,
    searchQuery: "",
    groupBy: loadStoredDefaultGroupBy(),
    /** Cache of track path -> album art (base64 + mime + size) or null if no art. */
    coverCache: {} as Record<string, CoverInfo | null>,
    /** Album key -> cover; updated when any track in that album gets its cover fetched. Used for group headers. */
    albumCoverCache: {} as Record<string, CoverInfo | null>,
  }),
  getters: {
    selectedTracks(state): CatalogTrack[] {
      const set = new Set(state.selectedTrackIds);
      return state.tracks.filter((t) => set.has(t.id));
    },
    filteredTracks(state): CatalogTrack[] {
      const q = state.searchQuery.trim().toLowerCase();
      if (!q) return state.tracks;
      return state.tracks.filter((t) => {
        const title = (t.title ?? "").toLowerCase();
        const artist = (t.artist ?? "").toLowerCase();
        const album = (t.album ?? "").toLowerCase();
        return title.includes(q) || artist.includes(q) || album.includes(q);
      });
    },
  },
  actions: {
    setCurrentPlaying(id: number | null) {
      this.currentPlayingTrackId = id;
    },
    setReportFilter(kind: null | "missing_metadata" | "duplicates") {
      this.reportFilter = kind;
    },
    async loadRoots() {
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
      this.loading = true;
      this.error = null;
      try {
        this.tracks = await invoke<CatalogTrack[]>("get_tracks");
        // Rebuild album cover cache from existing coverCache so group headers keep showing after refresh (e.g. remove folder)
        const next: Record<string, CoverInfo | null> = {};
        for (const t of this.tracks) {
          const cover = this.coverCache[t.path];
          if (cover) {
            const key = t.album ?? "—";
            if (!(key in next)) next[key] = cover;
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
    async writeMetadata(path: string, update: import("../types").MetadataUpdate) {
      await invoke("write_track_metadata", { path, update });
      await this.loadTracks();
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
      try {
        const result = await invoke<CoverInfo | null>("get_track_cover", { path });
        const cover = result ?? null;
        this.coverCache = { ...this.coverCache, [path]: cover };
        const track = this.tracks.find((t) => t.path === path);
        if (track && cover) {
          const albumKey = track.album ?? "—";
          this.albumCoverCache = { ...this.albumCoverCache, [albumKey]: cover };
        }
      } catch {
        this.coverCache = { ...this.coverCache, [path]: null };
      }
    },
  },
});

import { defineStore } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import type { CatalogTrack } from "../types";

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
    loading: false,
    error: null as string | null,
    searchQuery: "",
    groupBy: loadStoredDefaultGroupBy(),
    /** Cache of track path -> album art base64 (or null if no art). */
    coverCache: {} as Record<string, string | null>,
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
    getCover(path: string): string | null | undefined {
      return this.coverCache[path];
    },
    async fetchCover(path: string) {
      if (path in this.coverCache) return;
      try {
        const result = await invoke<null | string>("get_track_cover", { path });
        this.coverCache = { ...this.coverCache, [path]: result ?? null };
      } catch {
        this.coverCache = { ...this.coverCache, [path]: null };
      }
    },
  },
});

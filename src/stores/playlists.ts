import { defineStore } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import type { Playlist } from "../types";

export interface PlaylistEntry {
  entryId: number;
  trackId: number;
}

const isMock = () => import.meta.env.VITE_MOCK === "1" || import.meta.env.VITE_MOCK === "true";

export const usePlaylistStore = defineStore("playlists", {
  state: () => ({
    playlists: [] as Playlist[],
    loading: false,
    error: null as string | null,
  }),
  actions: {
    async loadPlaylists() {
      if (isMock()) return;
      this.loading = true;
      this.error = null;
      try {
        this.playlists = await invoke<Playlist[]>("get_playlists");
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      } finally {
        this.loading = false;
      }
    },

    async createPlaylist(name: string): Promise<Playlist | null> {
      if (isMock()) return null;
      this.error = null;
      try {
        const playlist = await invoke<Playlist>("create_playlist", { name });
        this.playlists = [...this.playlists, playlist];
        return playlist;
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return null;
      }
    },

    async renamePlaylist(id: number, name: string) {
      if (isMock()) return;
      this.error = null;
      try {
        await invoke("rename_playlist", { id, name });
        this.playlists = this.playlists.map((p) =>
          p.id === id ? { ...p, name } : p
        );
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async setPlaylistIcon(id: number, icon: string | null) {
      if (isMock()) return;
      this.error = null;
      try {
        await invoke("set_playlist_icon", { id, icon });
        this.playlists = this.playlists.map((p) =>
          p.id === id ? { ...p, icon } : p
        );
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async deletePlaylist(id: number) {
      if (isMock()) return;
      this.error = null;
      try {
        await invoke("delete_playlist", { id });
        this.playlists = this.playlists.filter((p) => p.id !== id);
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async getPlaylistTracks(playlistId: number): Promise<number[]> {
      if (isMock()) return [];
      try {
        return await invoke<number[]>("get_playlist_tracks", { playlistId });
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return [];
      }
    },

    async getPlaylistEntries(playlistId: number): Promise<PlaylistEntry[]> {
      if (isMock()) return [];
      try {
        const raw = await invoke<{ entry_id: number; track_id: number }[]>(
          "get_playlist_entries",
          { playlistId }
        );
        return raw.map((r) => ({ entryId: r.entry_id, trackId: r.track_id }));
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return [];
      }
    },

    async removePlaylistEntry(entryId: number) {
      if (isMock()) return;
      this.error = null;
      try {
        await invoke("remove_playlist_entry", { entryId });
        // Decrement track_count for the playlist that contained this entry.
        // We don't know which playlist without an extra query, so reload all playlists.
        await this.loadPlaylists();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async addTracksToPlaylist(playlistId: number, trackIds: number[]) {
      if (isMock() || !trackIds.length) return;
      this.error = null;
      try {
        await invoke("add_tracks_to_playlist", { playlistId, trackIds });
        await this.loadPlaylists();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async createPlaylistFromTracks(name: string, trackIds: number[]): Promise<Playlist | null> {
      if (isMock() || !trackIds.length) return null;
      const playlist = await this.createPlaylist(name);
      if (!playlist) return null;
      await this.addTracksToPlaylist(playlist.id, trackIds);
      return playlist;
    },

    async removeTracksFromPlaylist(playlistId: number, trackIds: number[]) {
      if (isMock() || !trackIds.length) return;
      this.error = null;
      try {
        await invoke("remove_tracks_from_playlist", { playlistId, trackIds });
        await this.loadPlaylists();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async createSmartPlaylist(name: string, rulesJson: string): Promise<import("../types").Playlist | null> {
      if (isMock()) return null;
      this.error = null;
      try {
        const playlist = await invoke<import("../types").Playlist>("create_smart_playlist", { name, rulesJson });
        this.playlists = [...this.playlists, playlist];
        return playlist;
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return null;
      }
    },

    async updateSmartPlaylistRules(id: number, rulesJson: string) {
      if (isMock()) return;
      this.error = null;
      try {
        await invoke("update_smart_playlist_rules", { id, rulesJson });
        const ids = await invoke<number[]>("get_smart_playlist_track_ids", { playlistId: id }).catch(() => null);
        this.playlists = this.playlists.map((p) =>
          p.id === id ? { ...p, smart_rules: rulesJson, track_count: ids?.length ?? p.track_count } : p,
        );
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async getSmartPlaylistTrackIds(playlistId: number): Promise<number[]> {
      if (isMock()) return [];
      try {
        return await invoke<number[]>("get_smart_playlist_track_ids", { playlistId });
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return [];
      }
    },

    async reorderPlaylists(ids: number[]) {
      // Optimistically reorder in-place, then persist.
      const idToPlaylist = new Map(this.playlists.map((p) => [p.id, p]));
      this.playlists = ids.map((id) => idToPlaylist.get(id)!).filter(Boolean);
      if (isMock()) return;
      try {
        await invoke("reorder_playlists", { ids });
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },
  },
});

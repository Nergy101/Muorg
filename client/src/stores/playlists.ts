import { defineStore } from "pinia";
import type { Playlist } from "../types";
import * as api from "../api/playlists";

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
        this.playlists = await api.getPlaylists();
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
        const playlist = await api.createPlaylist(name);
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
        await api.renamePlaylist(id, name);
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
        await api.setPlaylistIcon(id, icon);
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
        await api.deletePlaylist(id);
        this.playlists = this.playlists.filter((p) => p.id !== id);
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async getPlaylistTracks(playlistId: number): Promise<number[]> {
      if (isMock()) return [];
      try {
        return await api.getPlaylistTracks(playlistId);
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return [];
      }
    },

    async getPlaylistsForTrack(trackId: number): Promise<number[]> {
      if (isMock()) return [];
      try {
        return await api.getPlaylistsForTrack(trackId);
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return [];
      }
    },

    async getPlaylistEntries(playlistId: number): Promise<PlaylistEntry[]> {
      if (isMock()) return [];
      try {
        const raw = await api.getPlaylistEntries(playlistId);
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
        await api.removePlaylistEntry(entryId);
        await this.loadPlaylists();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async addTracksToPlaylist(playlistId: number, trackIds: number[]) {
      if (isMock() || !trackIds.length) return;
      this.error = null;
      try {
        await api.addTracksToPlaylist(playlistId, trackIds);
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
        await api.removeTracksFromPlaylist(playlistId, trackIds);
        await this.loadPlaylists();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    async createSmartPlaylist(name: string, rulesJson: string): Promise<import("../types").Playlist | null> {
      if (isMock()) return null;
      this.error = null;
      try {
        const playlist = await api.createSmartPlaylist(name, rulesJson);
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
        await api.updateSmartPlaylistRules(id, rulesJson);
        const ids = await api.getSmartPlaylistTrackIds(id).catch(() => null);
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
        return await api.getSmartPlaylistTrackIds(playlistId);
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
        return [];
      }
    },

    async reorderPlaylists(ids: number[]) {
      const idToPlaylist = new Map(this.playlists.map((p) => [p.id, p]));
      this.playlists = ids.map((id) => idToPlaylist.get(id)!).filter(Boolean);
      if (isMock()) return;
      try {
        await api.reorderPlaylists(ids);
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },
  },
});

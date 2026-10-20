import { defineStore } from "pinia";
import type { Playlist } from "../types";
import * as api from "../api/playlists";
import { getBackendMode } from "../api/client";
import { useSettingsStore } from "./settings";

export interface PlaylistEntry {
  entryId: number;
  trackId: number;
}

const isMock = () => import.meta.env.VITE_MOCK === "1" || import.meta.env.VITE_MOCK === "true";

/** Shared with the web and Android clients so a common library stays consistent. */
export const FAVORITES_PLAYLIST_NAME = "Favorites";
const FAVORITES_PLAYLIST_ICON = "⭐";

export const usePlaylistStore = defineStore("playlists", {
  state: () => ({
    playlists: [] as Playlist[],
    loading: false,
    error: null as string | null,
    /**
     * Q24: pinned playlist ids in pin order (earliest pin first). The pin set
     * lives on the server in online mode and on the device in local mode.
     */
    pinnedIds: [] as number[],
    /**
     * Q21: track ids in the Favorites playlist of the active backend. Seeded
     * from that backend's Favorites playlist on every load — the online
     * server's and the local sidecar's favorites are never merged.
     */
    favoriteTrackIds: [] as number[],
  }),
  getters: {
    isPinned: (state) => (playlistId: number) => state.pinnedIds.includes(playlistId),
    isFavorite: (state) => (trackId: number) => state.favoriteTrackIds.includes(trackId),
    /** The Favorites playlist of the active backend, matched by exact name. */
    favoritesPlaylist(state): Playlist | null {
      return state.playlists.find((p) => p.name === FAVORITES_PLAYLIST_NAME) ?? null;
    },
    /** The server refuses to delete or rename the Favorites playlist (403), so the UI hides those actions. */
    isFavoritesPlaylist: () => (p: Playlist) => p.name === FAVORITES_PLAYLIST_NAME,
    /** Pinned playlists first (in pin order), then the rest in server order. */
    sortedPlaylists(state): Playlist[] {
      const pinned = state.pinnedIds
        .map((id) => state.playlists.find((p) => p.id === id))
        .filter((p): p is Playlist => p != null);
      const pinnedSet = new Set(state.pinnedIds);
      const rest = state.playlists.filter((p) => !pinnedSet.has(p.id));
      return [...pinned, ...rest];
    },
  },
  actions: {
    async loadPlaylists() {
      if (isMock()) return;
      this.loading = true;
      this.error = null;
      try {
        this.playlists = await api.getPlaylists();
        await this.loadPins();
        await this.hydrateFavorites();
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      } finally {
        this.loading = false;
      }
    },

    /** Q24: load the pin set — from the server in online mode, from device settings in local mode. */
    async loadPins() {
      if (isMock()) return;
      try {
        if (getBackendMode() === "online") {
          this.pinnedIds = await api.getPlaylistPins();
        } else {
          this.pinnedIds = [...useSettingsStore().pinnedPlaylistIds];
        }
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    /**
     * Q24: pin/unpin a playlist; pin order = order in which pins happened.
     * Applied optimistically and reverted when the write fails.
     */
    async togglePin(playlistId: number) {
      if (isMock()) return;
      const previous = this.pinnedIds;
      const next = previous.includes(playlistId)
        ? previous.filter((id) => id !== playlistId)
        : [...previous, playlistId];
      this.pinnedIds = next;
      try {
        if (getBackendMode() === "online") {
          await api.setPlaylistPins(next);
        } else {
          useSettingsStore().setPinnedPlaylistIds(next);
        }
      } catch (e) {
        this.pinnedIds = previous;
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    /** Q21: seed the favorite ids from the active backend's Favorites playlist. */
    async hydrateFavorites() {
      if (isMock()) return;
      const fav = this.favoritesPlaylist;
      if (!fav) {
        this.favoriteTrackIds = [];
        return;
      }
      try {
        this.favoriteTrackIds = await api.getPlaylistTracks(fav.id);
      } catch (e) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    },

    /** Q21: the Favorites playlist, matched by exact name; created (with the star icon) when missing. */
    async ensureFavoritesPlaylist(): Promise<Playlist | null> {
      // If playlists haven't loaded yet, load them first — otherwise we'd
      // create a duplicate "Favorites" playlist (the server has no uniqueness
      // constraint on names) and split the user's favorites across two, so
      // the heart would revert on the next hydrate. Same fix as the web
      // client (bbe81cc); without the loading guard so a click during the
      // first in-flight load waits for it too.
      if (this.playlists.length === 0) await this.loadPlaylists();
      const existing = this.favoritesPlaylist;
      if (existing) return existing;
      const created = await this.createPlaylist(FAVORITES_PLAYLIST_NAME);
      if (!created) return null;
      await this.setPlaylistIcon(created.id, FAVORITES_PLAYLIST_ICON);
      return { ...created, icon: FAVORITES_PLAYLIST_ICON };
    },

    /**
     * Q21: toggle a track's favorite state. Optimistic; the write goes to the
     * Favorites playlist of whichever backend is active (the server in online
     * mode, the local sidecar in local mode) — the two are never merged.
     */
    async toggleFavorite(trackId: number) {
      if (isMock()) return;
      const wasFavorite = this.favoriteTrackIds.includes(trackId);
      const previous = this.favoriteTrackIds;
      this.favoriteTrackIds = wasFavorite
        ? previous.filter((id) => id !== trackId)
        : [...previous, trackId];
      try {
        const fav = await this.ensureFavoritesPlaylist();
        if (!fav) throw new Error("Could not create the Favorites playlist");
        // Call the API directly (not the store actions, which swallow errors)
        // so a failed write reverts the optimistic state below.
        if (wasFavorite) await api.removeTracksFromPlaylist(fav.id, [trackId]);
        else await api.addTracksToPlaylist(fav.id, [trackId]);
        // The reload re-hydrates favoriteTrackIds from the backend, so the
        // optimistic state is replaced by server truth.
        await this.loadPlaylists();
      } catch (e) {
        this.favoriteTrackIds = previous;
        this.error = e instanceof Error ? e.message : String(e);
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

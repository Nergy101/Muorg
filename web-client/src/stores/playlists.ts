import { defineStore } from "pinia";
import { ref } from "vue";
import {
  getPlaylists,
  createPlaylist as apiCreate,
  renamePlaylist as apiRename,
  deletePlaylist as apiDelete,
  addTracksToPlaylist as apiAdd,
  removeTracksFromPlaylist as apiRemove,
  reorderPlaylistTracks as apiReorder,
  getTracksForPlaylist,
} from "../api/playlists";
import type { Playlist } from "../types";
import { usePlayerStore } from "./player";

/** Shared with the Android client so a common library stays consistent. */
const FAVORITES_NAME = "Favorites";
const FAVORITES_ICON = "⭐";

export const usePlaylistStore = defineStore("playlists", () => {
  const playlists = ref<Playlist[]>([]);
  const loading = ref(false);

  /** Cache of track-ID sets per playlist, populated lazily. */
  const trackIdSets = ref<Map<number, Set<number>>>(new Map());

  async function loadPlaylists(): Promise<void> {
    loading.value = true;
    try {
      playlists.value = await getPlaylists();
    } finally {
      loading.value = false;
    }
    await hydrateFavorites();
  }

  /** Seeds the player's favourite ids from the server-side Favorites playlist. */
  async function hydrateFavorites(): Promise<void> {
    const fav = playlists.value.find((p) => p.name === FAVORITES_NAME);
    if (!fav) return;
    try {
      const ids = await getTracksForPlaylist(fav);
      trackIdSets.value = new Map(trackIdSets.value).set(fav.id, new Set(ids));
      usePlayerStore().favorites = new Set(ids);
    } catch {
      /* favourites are cosmetic; a failed read must not break boot */
    }
  }

  async function createPlaylist(name: string, icon?: string | null): Promise<Playlist> {
    const p = await apiCreate(name, icon);
    playlists.value = [...playlists.value, p];
    return p;
  }

  async function renamePlaylist(id: number, name: string, icon?: string | null): Promise<void> {
    await apiRename(id, name, icon);
    playlists.value = playlists.value.map((p) =>
      p.id === id ? { ...p, name, ...(icon !== undefined ? { icon } : {}) } : p,
    );
  }

  async function deletePlaylist(id: number): Promise<void> {
    await apiDelete(id);
    playlists.value = playlists.value.filter((p) => p.id !== id);
    trackIdSets.value.delete(id);
  }

  async function loadTrackIdsForPlaylist(playlistId: number): Promise<Set<number>> {
    const cached = trackIdSets.value.get(playlistId);
    if (cached) return cached;
    const playlist = playlists.value.find((p) => p.id === playlistId);
    if (!playlist) return new Set();
    const ids = await getTracksForPlaylist(playlist);
    const s = new Set(ids);
    trackIdSets.value = new Map(trackIdSets.value).set(playlistId, s);
    return s;
  }

  /** Ordered track ids — smart playlists resolve through the smart endpoint. */
  async function loadTrackOrderForPlaylist(playlistId: number): Promise<number[]> {
    const playlist = playlists.value.find((p) => p.id === playlistId);
    if (!playlist) return [];
    const ids = await getTracksForPlaylist(playlist);
    trackIdSets.value = new Map(trackIdSets.value).set(playlistId, new Set(ids));
    return ids;
  }

  async function loadAllTrackIds(): Promise<void> {
    await Promise.all(playlists.value.map((p) => loadTrackIdsForPlaylist(p.id)));
  }

  async function getPlaylistsContainingTrack(trackId: number): Promise<Set<number>> {
    await loadAllTrackIds();
    const result = new Set<number>();
    for (const [pid, set] of trackIdSets.value) {
      if (set.has(trackId)) result.add(pid);
    }
    return result;
  }

  async function addTracks(playlistId: number, trackIds: number[]): Promise<void> {
    await apiAdd(playlistId, trackIds);
    playlists.value = playlists.value.map((p) =>
      p.id === playlistId ? { ...p, track_count: p.track_count + trackIds.length } : p,
    );
    const cached = trackIdSets.value.get(playlistId);
    if (cached) {
      const updated = new Set(cached);
      for (const id of trackIds) updated.add(id);
      trackIdSets.value = new Map(trackIdSets.value).set(playlistId, updated);
    }
  }

  async function removeTracks(playlistId: number, trackIds: number[]): Promise<void> {
    await apiRemove(playlistId, trackIds);
    playlists.value = playlists.value.map((p) =>
      p.id === playlistId
        ? { ...p, track_count: Math.max(0, p.track_count - trackIds.length) }
        : p,
    );
    const cached = trackIdSets.value.get(playlistId);
    if (cached) {
      const updated = new Set(cached);
      for (const id of trackIds) updated.delete(id);
      trackIdSets.value = new Map(trackIdSets.value).set(playlistId, updated);
    }
  }

  async function reorderTracks(playlistId: number, trackIds: number[]): Promise<void> {
    await apiReorder(playlistId, trackIds);
  }

  async function ensureFavoritesPlaylist(): Promise<Playlist> {
    const existing = playlists.value.find((p) => p.name === FAVORITES_NAME);
    if (existing) return existing;
    return createPlaylist(FAVORITES_NAME, FAVORITES_ICON);
  }

  function reset(): void {
    playlists.value = [];
    loading.value = false;
    trackIdSets.value = new Map();
  }

  return {
    playlists,
    loading,
    trackIdSets,
    loadPlaylists,
    createPlaylist,
    renamePlaylist,
    deletePlaylist,
    loadTrackIdsForPlaylist,
    loadTrackOrderForPlaylist,
    loadAllTrackIds,
    getPlaylistsContainingTrack,
    addTracks,
    removeTracks,
    reorderTracks,
    ensureFavoritesPlaylist,
    reset,
  };
});

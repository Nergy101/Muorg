import { defineStore } from "pinia";
import { ref } from "vue";
import {
  getPlaylists,
  createPlaylist as apiCreate,
  createSmartPlaylist as apiCreateSmart,
  updateSmartRules as apiUpdateSmartRules,
  renamePlaylist as apiRename,
  deletePlaylist as apiDelete,
  getPlaylistTracks,
  addTracksToPlaylist as apiAdd,
  removeTracksFromPlaylist as apiRemove,
  reorderPlaylistTracks as apiReorder,
} from "../api/playlists";
import type { Playlist, SmartRule } from "../types";
import { useLibraryStore } from "./library";

export const usePlaylistStore = defineStore("playlists", () => {
  const playlists = ref<Playlist[]>([]);
  const activePlaylistId = ref<number | null>(null);
  const loading = ref(false);

  // Cache of track-ID sets per playlist, populated lazily
  const trackIdSets = ref<Map<number, Set<number>>>(new Map());

  async function loadPlaylists(): Promise<void> {
    loading.value = true;
    try {
      playlists.value = await getPlaylists();
    } finally {
      loading.value = false;
    }
  }

  async function createPlaylist(name: string, icon?: string | null): Promise<void> {
    const p = await apiCreate(name, icon);
    playlists.value = [...playlists.value, p];
  }

  async function createSmartPlaylist(name: string, icon: string, rules: SmartRule[]): Promise<void> {
    const rulesJson = JSON.stringify(rules);
    const p = await apiCreateSmart(name, rulesJson);
    // Set icon after creation
    if (icon) {
      await apiRename(p.id, name, icon);
      p.icon = icon;
    }
    playlists.value = [...playlists.value, p];
  }

  async function updateSmartRules(playlistId: number, rules: SmartRule[]): Promise<void> {
    const rulesJson = JSON.stringify(rules);
    await apiUpdateSmartRules(playlistId, rulesJson);
    playlists.value = playlists.value.map((p) =>
      p.id === playlistId ? { ...p, smart_rules: rulesJson } : p,
    );
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
    if (activePlaylistId.value === id) {
      await selectPlaylist(null);
    }
  }

  async function selectPlaylist(id: number | null): Promise<void> {
    activePlaylistId.value = id;
    const lib = useLibraryStore();
    if (id === null) {
      lib.playlistTrackIds = null;
    } else {
      lib.playlistTrackIds = await getPlaylistTracks(id);
    }
  }

  async function loadTrackIdsForPlaylist(playlistId: number): Promise<Set<number>> {
    const cached = trackIdSets.value.get(playlistId);
    if (cached) return cached;
    const ids = await getPlaylistTracks(playlistId);
    const s = new Set(ids);
    trackIdSets.value = new Map(trackIdSets.value).set(playlistId, s);
    return s;
  }

  async function getPlaylistsContainingTrack(trackId: number): Promise<Set<number>> {
    await Promise.all(playlists.value.map((p) => loadTrackIdsForPlaylist(p.id)));
    const result = new Set<number>();
    for (const [pid, set] of trackIdSets.value) {
      if (set.has(trackId)) result.add(pid);
    }
    return result;
  }

  async function addTracks(playlistId: number, trackIds: number[]): Promise<void> {
    await apiAdd(playlistId, trackIds);
    playlists.value = playlists.value.map((p) =>
      p.id === playlistId
        ? { ...p, track_count: p.track_count + trackIds.length }
        : p,
    );
    // Update cache if present
    const cached = trackIdSets.value.get(playlistId);
    if (cached) {
      const updated = new Set(cached);
      for (const id of trackIds) updated.add(id);
      trackIdSets.value = new Map(trackIdSets.value).set(playlistId, updated);
    }
    if (activePlaylistId.value === playlistId) {
      const lib = useLibraryStore();
      lib.playlistTrackIds = await getPlaylistTracks(playlistId);
    }
  }

  async function removeTracks(playlistId: number, trackIds: number[]): Promise<void> {
    await apiRemove(playlistId, trackIds);
    playlists.value = playlists.value.map((p) =>
      p.id === playlistId
        ? { ...p, track_count: Math.max(0, p.track_count - trackIds.length) }
        : p,
    );
    // Update cache if present
    const cached = trackIdSets.value.get(playlistId);
    if (cached) {
      const updated = new Set(cached);
      for (const id of trackIds) updated.delete(id);
      trackIdSets.value = new Map(trackIdSets.value).set(playlistId, updated);
    }
    if (activePlaylistId.value === playlistId) {
      const lib = useLibraryStore();
      lib.playlistTrackIds = await getPlaylistTracks(playlistId);
    }
  }

  /**
   * Reorder a playlist's tracks with an optimistic local update.
   * Reverts the local order if the API call fails.
   */
  async function reorderTracks(playlistId: number, orderedIds: number[]): Promise<void> {
    const lib = useLibraryStore();
    const previous = lib.playlistTrackIds;
    lib.playlistTrackIds = orderedIds;
    try {
      await apiReorder(playlistId, orderedIds);
    } catch (e) {
      lib.playlistTrackIds = previous;
      throw e;
    }
  }

  return {
    playlists,
    activePlaylistId,
    loading,
    loadPlaylists,
    createPlaylist,
    createSmartPlaylist,
    updateSmartRules,
    renamePlaylist,
    deletePlaylist,
    selectPlaylist,
    getPlaylistsContainingTrack,
    addTracks,
    removeTracks,
    reorderTracks,
  };
});

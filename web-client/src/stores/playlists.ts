import { defineStore } from "pinia";
import { ref } from "vue";
import {
  getPlaylists,
  createPlaylist as apiCreate,
  renamePlaylist as apiRename,
  deletePlaylist as apiDelete,
  getPlaylistTracks,
  addTracksToPlaylist as apiAdd,
  removeTracksFromPlaylist as apiRemove,
} from "../api/playlists";
import type { Playlist } from "../types";
import { useLibraryStore } from "./library";

export const usePlaylistStore = defineStore("playlists", () => {
  const playlists = ref<Playlist[]>([]);
  const activePlaylistId = ref<number | null>(null);
  const loading = ref(false);

  async function loadPlaylists(): Promise<void> {
    loading.value = true;
    try {
      playlists.value = await getPlaylists();
    } finally {
      loading.value = false;
    }
  }

  async function createPlaylist(name: string): Promise<void> {
    const p = await apiCreate(name);
    playlists.value = [...playlists.value, p];
  }

  async function renamePlaylist(id: number, name: string): Promise<void> {
    await apiRename(id, name);
    playlists.value = playlists.value.map((p) =>
      p.id === id ? { ...p, name } : p,
    );
  }

  async function deletePlaylist(id: number): Promise<void> {
    await apiDelete(id);
    playlists.value = playlists.value.filter((p) => p.id !== id);
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

  async function addTracks(playlistId: number, trackIds: number[]): Promise<void> {
    await apiAdd(playlistId, trackIds);
    playlists.value = playlists.value.map((p) =>
      p.id === playlistId
        ? { ...p, track_count: p.track_count + trackIds.length }
        : p,
    );
    // Refresh if viewing this playlist
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
    if (activePlaylistId.value === playlistId) {
      const lib = useLibraryStore();
      lib.playlistTrackIds = await getPlaylistTracks(playlistId);
    }
  }

  return {
    playlists,
    activePlaylistId,
    loading,
    loadPlaylists,
    createPlaylist,
    renamePlaylist,
    deletePlaylist,
    selectPlaylist,
    addTracks,
    removeTracks,
  };
});

import { ref } from "vue";
import { usePlaylistStore } from "../stores/playlists";
import { useCatalogStore } from "../stores/catalog";
import { showToast } from "../state/toast";

export interface PendingPlaylistAdd {
  playlistId: number;
  playlistName: string;
  /** All track IDs the user originally wanted to add. */
  allIds: number[];
  /** Subset that are already in the playlist. */
  dupeIds: number[];
  /** Subset that are not yet in the playlist. */
  newIds: number[];
}

export function usePlaylistAdd() {
  const playlistStore = usePlaylistStore();
  const catalogStore = useCatalogStore();

  const pendingAdd = ref<PendingPlaylistAdd | null>(null);

  async function tryAddToPlaylist(
    playlistId: number,
    trackIds: number[],
    playlistName: string
  ) {
    if (!trackIds.length) return;

    const existing = await playlistStore.getPlaylistTracks(playlistId);
    const existingSet = new Set(existing);
    const dupeIds = trackIds.filter((id) => existingSet.has(id));
    const newIds = trackIds.filter((id) => !existingSet.has(id));

    if (!dupeIds.length) {
      await _commit(playlistId, trackIds, playlistName);
      return;
    }

    pendingAdd.value = { playlistId, playlistName, allIds: trackIds, dupeIds, newIds };
  }

  async function _commit(playlistId: number, trackIds: number[], playlistName?: string) {
    if (!trackIds.length) return;
    await playlistStore.addTracksToPlaylist(playlistId, trackIds);
    if (catalogStore.activePlaylistId === playlistId) {
      const entries = await playlistStore.getPlaylistEntries(playlistId);
      catalogStore.setActivePlaylist(playlistId, entries);
    }
    if (playlistName) {
      showToast(`Added ${trackIds.length} track${trackIds.length === 1 ? "" : "s"} to “${playlistName}”`);
    }
  }

  async function confirmAddAll() {
    if (!pendingAdd.value) return;
    const { playlistId, playlistName, allIds } = pendingAdd.value;
    pendingAdd.value = null;
    await _commit(playlistId, allIds, playlistName);
  }

  async function confirmAddDeduped() {
    if (!pendingAdd.value) return;
    const { playlistId, playlistName, newIds } = pendingAdd.value;
    pendingAdd.value = null;
    await _commit(playlistId, newIds, playlistName);
  }

  function cancelPendingAdd() {
    pendingAdd.value = null;
  }

  return {
    pendingAdd,
    tryAddToPlaylist,
    confirmAddAll,
    confirmAddDeduped,
    cancelPendingAdd,
  };
}

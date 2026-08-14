import { computed, ref, watch } from "vue";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import type { Playlist } from "../types";

/**
 * Drives background preloading of every playlist's cover collage and reports
 * when all of them have settled (each candidate cover loaded or definitively
 * failed) — the same behaviour as `useMixCoverReady` for the Home Mixes grid.
 *
 * PlaylistsView renders the whole grid only once `allReady` is true, so it
 * appears as one complete set of cards instead of popping in one by one. A
 * playlist that can't reach 4 distinct covers (or is empty) still counts as
 * ready once its track order resolves, so the grid is never stuck waiting.
 */
export function usePlaylistCoverReady(playlists: () => Playlist[]) {
  const lib = useLibraryStore();
  const playlistStore = usePlaylistStore();

  const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

  /** How many distinct covers to scan per playlist so failed ones can fall
   *  through to later albums (matches PlaylistCard's MAX_DISTINCT_COVERS). */
  const MAX_DISTINCT_COVERS = 16;

  /** The first MAX_DISTINCT_COVERS distinct album-cover track ids in order. */
  function distinctCovers(orderedIds: number[]): number[] {
    const seen = new Set<string>();
    const out: number[] = [];
    for (const id of orderedIds) {
      const t = trackById.value.get(id);
      if (!t) continue;
      const key = albumKeyFor(t);
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(t.id);
      if (out.length === MAX_DISTINCT_COVERS) break;
    }
    return out;
  }

  /** Resolved cover track ids per playlist (empty until its order loads). */
  const coversByPlaylist = ref<Map<number, number[]>>(new Map());

  /** Load every playlist's track order, compute its covers, and request them. */
  async function preload(): Promise<void> {
    const list = playlists();
    const map = new Map<number, number[]>();
    await Promise.all(
      list.map(async (p) => {
        try {
          const ids = await playlistStore.loadTrackOrderForPlaylist(p.id);
          map.set(p.id, distinctCovers(ids));
        } catch {
          map.set(p.id, []);
        }
      }),
    );
    coversByPlaylist.value = new Map(map);
    for (const ids of map.values()) for (const id of ids) lib.requestCover(id);
  }

  // Re-preload whenever the set of playlists changes (add/remove).
  watch(
    () => playlists().map((p) => p.id).join(","),
    () => {
      void preload();
    },
    { immediate: true },
  );

  /** True once every playlist's covers have settled (loaded or failed). */
  const allReady = computed(() => {
    const list = playlists();
    if (list.length === 0) return true;
    for (const p of list) {
      const ids = coversByPlaylist.value.get(p.id);
      if (!ids) return false; // this playlist's track order hasn't loaded yet
      for (const id of ids) {
        if (!lib.coverCache.has(id) && !lib.coverFailed.has(id)) return false;
      }
    }
    return true;
  });

  return { allReady };
}

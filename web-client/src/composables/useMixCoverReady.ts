import { computed, watch } from "vue";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import type { Mix } from "./useMixes";

/**
 * Drives background preloading of every mix's cover collage and reports when
 * all of them have settled (each candidate cover loaded or definitively failed).
 *
 * HomeView renders the whole Mixes grid only once `allReady` is true, so the
 * section appears as one complete set of cards instead of popping in one by one
 * — and a mix that can't reach 4 distinct covers still counts as ready once its
 * candidates resolve, so the grid is never stuck waiting forever.
 */
export function useMixCoverReady(getMixes: () => Mix[]) {
  const lib = useLibraryStore();

  const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

  /** Distinct cover candidates per mix, capped at MAX_DISTINCT_COVERS. */
  const candidatesByMix = computed<number[][]>(() =>
    getMixes().map((mix) => {
      const seen = new Set<string>();
      const out: number[] = [];
      for (const id of mix.trackIds) {
        const t = trackById.value.get(id);
        if (!t) continue;
        const key = albumKeyFor(t);
        if (seen.has(key)) continue;
        seen.add(key);
        out.push(t.id);
        if (out.length === 16) break;
      }
      return out;
    }),
  );

  // Preload all candidates in the background so the grid can appear whole.
  // requestCover dedupes (pending/cached/failed), so re-running is a no-op.
  watch(
    candidatesByMix,
    (byMix) => {
      for (const ids of byMix) for (const id of ids) lib.requestCover(id);
    },
    { immediate: true },
  );

  /** True once every candidate cover across all mixes has settled. */
  const allReady = computed(() =>
    candidatesByMix.value.every((ids) =>
      ids.every((id) => lib.coverCache.has(id) || lib.coverFailed.has(id)),
    ),
  );

  return { allReady };
}

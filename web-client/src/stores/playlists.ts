import { defineStore } from "pinia";
import { computed, ref } from "vue";
import {
  getPlaylists,
  createPlaylist as apiCreate,
  renamePlaylist as apiRename,
  deletePlaylist as apiDelete,
  addTracksToPlaylist as apiAdd,
  removeTracksFromPlaylist as apiRemove,
  reorderPlaylistTracks as apiReorder,
  reorderPlaylists as apiReorderPlaylists,
  getTracksForPlaylist,
  createSmartPlaylist as apiCreateSmart,
  updateSmartPlaylistRules as apiUpdateSmartRules,
  getSmartTracks,
  getPlaylistPins,
  setPlaylistPins,
} from "../api/playlists";
import { showToast } from "../composables/useToast";
import type { Playlist, SmartRule } from "../types";
import { usePlayerStore } from "./player";

/**
 * A pending "add tracks to a playlist" operation waiting on the user's
 * duplicate-track decision (desktop parity — see PlaylistDuplicateDialog).
 */
export interface PendingPlaylistAdd {
  playlistId: number;
  playlistName: string;
  /** All track IDs the caller originally wanted to add. */
  allIds: number[];
  /** Subset already in the playlist. */
  dupeIds: number[];
  /** Subset not yet in the playlist. */
  newIds: number[];
}

/** Shared with the Android client so a common library stays consistent. */
const FAVORITES_NAME = "Favorites";
const FAVORITES_ICON = "⭐";

/** Fields whose rule values must be sent as JSON numbers, not strings. */
const SMART_NUMERIC_FIELDS = new Set(["rating", "play_count", "year", "last_played_at", "has_cover"]);

/** Serialize editor rules to the server's rules_json format. */
export function rulesToSmartJson(rules: SmartRule[]): string {
  return JSON.stringify(
    rules.map((r) => ({
      field: r.field,
      op: r.op,
      value:
        r.op === "is_null" || r.op === "is_not_null"
          ? undefined
          : SMART_NUMERIC_FIELDS.has(r.field) && r.value.trim() !== ""
            ? Number(r.value)
            : r.value,
    })),
  );
}

/** Parse a stored rules_json string back into editor rows. */
export function parseSmartRules(json: string | null): SmartRule[] {
  try {
    const raw = JSON.parse(json ?? "[]") as { field: string; op: string; value?: unknown }[];
    return raw.map((r) => ({
      field: r.field,
      op: r.op,
      value: r.value == null ? "" : String(r.value),
    }));
  } catch {
    return [];
  }
}

export const usePlaylistStore = defineStore("playlists", () => {
  const playlists = ref<Playlist[]>([]);
  const loading = ref(false);

  /** Cache of track-ID sets per playlist, populated lazily. */
  const trackIdSets = ref<Map<number, Set<number>>>(new Map());

  /** Cache of ordered track-id arrays per playlist (used for cover collages). */
  const trackOrders = ref<Map<number, number[]>>(new Map());

  /** Pinned playlist ids in pin order (earliest pin first). The pin set lives
   *  on the server (Q24) — the web client is always in server mode — so it is
   *  fetched with the playlists and written back on every change. */
  const pinnedIds = ref<number[]>([]);

  /** The duplicate-track confirmation waiting on the user, or null. One at a
   *  time — PlaylistDuplicateDialog is mounted globally in App.vue. */
  const pendingAdd = ref<PendingPlaylistAdd | null>(null);

  function isPinned(playlistId: number): boolean {
    return pinnedIds.value.includes(playlistId);
  }

  /** Pin/unpin a playlist; pin order = order in which pins happened. The
   *  change is applied optimistically and reverted when the server rejects it. */
  async function togglePin(playlistId: number): Promise<void> {
    const previous = pinnedIds.value;
    const next = isPinned(playlistId)
      ? previous.filter((id) => id !== playlistId)
      : [...previous, playlistId];
    pinnedIds.value = next;
    try {
      await setPlaylistPins(next);
    } catch (e) {
      pinnedIds.value = previous;
      showToast(`Failed to update pins: ${(e as Error).message}`);
    }
  }

  /** Pinned playlists first (in pin order), then the rest in server order. */
  const sortedPlaylists = computed<Playlist[]>(() => {
    const byId = new Map<number, Playlist>();
    const rest: Playlist[] = [];
    for (const p of playlists.value) {
      if (isPinned(p.id)) byId.set(p.id, p);
      else rest.push(p);
    }
    const pinned = pinnedIds.value
      .map((id) => byId.get(id))
      .filter((p): p is Playlist => p !== undefined);
    return [...pinned, ...rest];
  });

  /** In-flight loadPlaylists() call, so concurrent callers (the router's
   *  fire-and-forget loadData() plus a deep-linked playlist view) share one
   *  fetch instead of racing two. Cleared when the flight ends, so a later
   *  call still refetches. */
  let loadPlaylistsFlight: Promise<void> | null = null;

  async function loadPlaylists(): Promise<void> {
    if (loadPlaylistsFlight) return loadPlaylistsFlight;
    loadPlaylistsFlight = (async () => {
      loading.value = true;
      try {
        playlists.value = await getPlaylists();
        // Pins live on the server (Q24); a failed read must not break boot.
        try {
          pinnedIds.value = await getPlaylistPins();
        } catch {
          /* keep whatever pin state we already had */
        }
      } finally {
        loading.value = false;
      }
      await hydrateFavorites();
    })();
    try {
      await loadPlaylistsFlight;
    } finally {
      loadPlaylistsFlight = null;
    }
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

  async function createSmartPlaylist(name: string, rulesJson: string): Promise<Playlist> {
    const p = await apiCreateSmart(name, rulesJson);
    playlists.value = [...playlists.value, p];
    return p;
  }

  async function updateSmartPlaylistRules(id: number, rulesJson: string): Promise<void> {
    await apiUpdateSmartRules(id, rulesJson);
    const ids = await getSmartTracks(id).catch(() => null);
    playlists.value = playlists.value.map((p) =>
      p.id === id ? { ...p, smart_rules: rulesJson, track_count: ids?.length ?? p.track_count } : p,
    );
    if (ids) {
      trackIdSets.value = new Map(trackIdSets.value).set(id, new Set(ids));
    }
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
    trackOrders.value.delete(id);
    if (isPinned(id)) {
      const next = pinnedIds.value.filter((x) => x !== id);
      pinnedIds.value = next;
      // The server drops the pin with the playlist; write the filtered set
      // back in case it keeps stale ids around.
      await setPlaylistPins(next).catch(() => undefined);
    }
  }

  async function loadTrackIdsForPlaylist(playlistId: number): Promise<Set<number>> {
    const cached = trackIdSets.value.get(playlistId);
    if (cached) return cached;
    // The router boots loadData() without blocking navigation, so a cold
    // deep-link can reach this before the playlist list has landed — wait for
    // it instead of concluding the playlist does not exist.
    if (playlists.value.length === 0) await loadPlaylists();
    const playlist = playlists.value.find((p) => p.id === playlistId);
    if (!playlist) return new Set();
    const ids = await getTracksForPlaylist(playlist);
    const s = new Set(ids);
    trackIdSets.value = new Map(trackIdSets.value).set(playlistId, s);
    return s;
  }

  /** Ordered track ids — smart playlists resolve through the smart endpoint.
   *  Cached so PlaylistCard and the cover-preload composable don't refetch. */
  async function loadTrackOrderForPlaylist(playlistId: number, force = false): Promise<number[]> {
    if (!force) {
      const cached = trackOrders.value.get(playlistId);
      if (cached) return cached;
    }
    // See loadTrackIdsForPlaylist: a cold deep-link can arrive before the
    // playlist list has landed.
    if (playlists.value.length === 0) await loadPlaylists();
    const playlist = playlists.value.find((p) => p.id === playlistId);
    if (!playlist) return [];
    const ids = await getTracksForPlaylist(playlist);
    trackOrders.value = new Map(trackOrders.value).set(playlistId, ids);
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

  // ── Duplicate-checked add (desktop parity) ────────────────────────────
  // Concurrent callers (a picker confirm racing a drag-drop, say) share one
  // pendingAdd slot — chain them so a second check never clobbers the first
  // while its dialog is still open.
  let addCheckChain: Promise<unknown> = Promise.resolve();
  let resolvePendingAdd: ((outcome: "added" | "cancelled") => void) | null = null;

  async function commitAdd(playlistId: number, trackIds: number[]): Promise<void> {
    if (!trackIds.length) return;
    try {
      await addTracks(playlistId, trackIds);
    } catch (e) {
      showToast(`Failed to add tracks: ${(e as Error).message}`);
      throw e;
    }
  }

  async function runAddCheck(playlistId: number, trackIds: number[]): Promise<"added" | "cancelled"> {
    if (!trackIds.length) return "added";
    const existing = await loadTrackIdsForPlaylist(playlistId);
    const dupeIds = trackIds.filter((id) => existing.has(id));
    const newIds = trackIds.filter((id) => !existing.has(id));
    if (!dupeIds.length) {
      await commitAdd(playlistId, trackIds).catch(() => undefined);
      return "added";
    }
    const name = playlists.value.find((p) => p.id === playlistId)?.name ?? "";
    // The project's TS lib target (ES2020) predates Promise.withResolvers
    // (ES2024), so the executor form is used here deliberately.
    return new Promise<"added" | "cancelled">((resolve) => {
      resolvePendingAdd = resolve;
      pendingAdd.value = { playlistId, playlistName: name, allIds: trackIds, dupeIds, newIds };
    });
  }

  /**
   * Add tracks to a regular playlist, asking the user when some are already
   * members (desktop's usePlaylistAdd, moved to the store so every surface —
   * PlaylistPickerSheet, playlist drop targets — shares one implementation).
   * Resolves once the add has actually happened (or the user cancelled).
   */
  function addTracksChecked(playlistId: number, trackIds: number[]): Promise<"added" | "cancelled"> {
    const run = addCheckChain.then(() => runAddCheck(playlistId, trackIds));
    addCheckChain = run.catch(() => undefined);
    return run;
  }

  async function confirmAddAll(): Promise<void> {
    const pending = pendingAdd.value;
    if (!pending) return;
    pendingAdd.value = null;
    await commitAdd(pending.playlistId, pending.allIds).catch(() => undefined);
    resolvePendingAdd?.("added");
    resolvePendingAdd = null;
  }

  async function confirmAddDeduped(): Promise<void> {
    const pending = pendingAdd.value;
    if (!pending) return;
    pendingAdd.value = null;
    await commitAdd(pending.playlistId, pending.newIds).catch(() => undefined);
    resolvePendingAdd?.("added");
    resolvePendingAdd = null;
  }

  function cancelPendingAdd(): void {
    pendingAdd.value = null;
    resolvePendingAdd?.("cancelled");
    resolvePendingAdd = null;
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

  /**
   * Reorder the playlists themselves. `sortedPlaylists` renders pinned ids
   * first (in pin order) regardless, so this only moves the unpinned tail —
   * same simplification as the desktop sidebar's reorder.
   */
  async function reorderPlaylists(ids: number[]): Promise<void> {
    const byId = new Map(playlists.value.map((p) => [p.id, p]));
    const next = ids.map((id) => byId.get(id)).filter((p): p is Playlist => p != null);
    const previous = playlists.value;
    playlists.value = next;
    try {
      await apiReorderPlaylists(ids);
    } catch (e) {
      playlists.value = previous;
      showToast(`Failed to reorder playlists: ${(e as Error).message}`);
    }
  }

  async function ensureFavoritesPlaylist(): Promise<Playlist> {
    // If playlists haven't loaded yet, load them first — otherwise we'd
    // create a duplicate "Favorites" playlist (the server has no uniqueness
    // constraint on names) and split the user's favorites across two.
    if (playlists.value.length === 0 && !loading.value) await loadPlaylists();
    const existing = playlists.value.find((p) => p.name === FAVORITES_NAME);
    if (existing) return existing;
    return createPlaylist(FAVORITES_NAME, FAVORITES_ICON);
  }

  /** The server-owned Favorites playlist, matched by exact name. The server
   *  refuses to delete or rename it (403), so the UI hides those actions. */
  function isFavoritesPlaylist(p: Playlist): boolean {
    return p.name === FAVORITES_NAME;
  }

  function reset(): void {
    playlists.value = [];
    loading.value = false;
    trackIdSets.value = new Map();
    trackOrders.value = new Map();
    pinnedIds.value = [];
    pendingAdd.value = null;
    resolvePendingAdd = null;
  }

  return {
    playlists,
    loading,
    trackIdSets,
    pinnedIds,
    pendingAdd,
    sortedPlaylists,
    isPinned,
    togglePin,
    loadPlaylists,
    createPlaylist,
    createSmartPlaylist,
    updateSmartPlaylistRules,
    renamePlaylist,
    deletePlaylist,
    loadTrackIdsForPlaylist,
    loadTrackOrderForPlaylist,
    loadAllTrackIds,
    getPlaylistsContainingTrack,
    addTracks,
    addTracksChecked,
    confirmAddAll,
    confirmAddDeduped,
    cancelPendingAdd,
    removeTracks,
    reorderTracks,
    reorderPlaylists,
    ensureFavoritesPlaylist,
    isFavoritesPlaylist,
    reset,
  };
});

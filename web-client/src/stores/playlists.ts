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
  getTracksForPlaylist,
  createSmartPlaylist as apiCreateSmart,
  updateSmartPlaylistRules as apiUpdateSmartRules,
  getSmartTracks,
} from "../api/playlists";
import type { Playlist, SmartRule } from "../types";
import { usePlayerStore } from "./player";

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

/** Client-side pin state (per device, survives reloads). Pins may move to the
 *  server later; for now order = the order in which playlists were pinned. */
const PIN_KEY = "muorg:pinned-playlists";

function loadPinnedIds(): number[] {
  try {
    const raw: unknown = JSON.parse(localStorage.getItem(PIN_KEY) ?? "[]");
    return Array.isArray(raw) ? raw.filter((x): x is number => typeof x === "number") : [];
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

  /** Pinned playlist ids in pin order (earliest pin first). */
  const pinnedIds = ref<number[]>(loadPinnedIds());

  function persistPinned(): void {
    localStorage.setItem(PIN_KEY, JSON.stringify(pinnedIds.value));
  }

  function isPinned(playlistId: number): boolean {
    return pinnedIds.value.includes(playlistId);
  }

  /** Pin/unpin a playlist; pin order = order in which pins happened. */
  function togglePin(playlistId: number): void {
    pinnedIds.value = isPinned(playlistId)
      ? pinnedIds.value.filter((id) => id !== playlistId)
      : [...pinnedIds.value, playlistId];
    persistPinned();
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
      pinnedIds.value = pinnedIds.value.filter((x) => x !== id);
      persistPinned();
    }
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

  /** Ordered track ids — smart playlists resolve through the smart endpoint.
   *  Cached so PlaylistCard and the cover-preload composable don't refetch. */
  async function loadTrackOrderForPlaylist(playlistId: number, force = false): Promise<number[]> {
    if (!force) {
      const cached = trackOrders.value.get(playlistId);
      if (cached) return cached;
    }
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
    trackOrders.value = new Map();
  }

  return {
    playlists,
    loading,
    trackIdSets,
    pinnedIds,
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
    removeTracks,
    reorderTracks,
    ensureFavoritesPlaylist,
    reset,
  };
});

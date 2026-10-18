import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { Playlist } from "../types";

/**
 * The playlists store, with the API module stubbed.
 *
 * Pins live on the server (Q24): the store fetches them with the playlists
 * and writes the whole set back on every change. The player store is stubbed
 * so hydrateFavorites does not pull in the whole playback stack.
 */

const getPlaylists = vi.fn();
const getPlaylistPins = vi.fn();
const setPlaylistPins = vi.fn();
const deletePlaylistApi = vi.fn();
const getPlaylistTracks = vi.fn();
const getSmartTracks = vi.fn();
const toastShow = vi.fn();

vi.mock("../api/playlists", () => ({
  getPlaylists: () => getPlaylists(),
  getPlaylistPins: () => getPlaylistPins(),
  setPlaylistPins: (...a: unknown[]) => setPlaylistPins(...a),
  deletePlaylist: (...a: unknown[]) => deletePlaylistApi(...a),
  getPlaylistTracks: (...a: unknown[]) => getPlaylistTracks(...a),
  getSmartTracks: (...a: unknown[]) => getSmartTracks(...a),
  getTracksForPlaylist: (p: Playlist) =>
    p.smart_rules != null ? getSmartTracks(p.id) : getPlaylistTracks(p.id),
  createPlaylist: vi.fn(),
  createSmartPlaylist: vi.fn(),
  updateSmartPlaylistRules: vi.fn(),
  renamePlaylist: vi.fn(),
  addTracksToPlaylist: vi.fn(),
  removeTracksFromPlaylist: vi.fn(),
  reorderPlaylistTracks: vi.fn(),
}));

vi.mock("../composables/useToast", () => ({
  showToast: (...a: unknown[]) => toastShow(...a),
  showWarning: vi.fn(),
}));

const playerMock = { favorites: new Set<number>() };
vi.mock("./player", () => ({
  usePlayerStore: () => playerMock,
}));

const { usePlaylistStore } = await import("./playlists");

function playlist(p: Partial<Playlist> & { id: number }): Playlist {
  return {
    name: `pl${p.id}`,
    icon: null,
    smart_rules: null,
    track_count: 0,
    ...p,
  };
}

beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
  vi.clearAllMocks();
  getPlaylists.mockResolvedValue([playlist({ id: 1 }), playlist({ id: 2 })]);
  getPlaylistPins.mockResolvedValue([]);
  setPlaylistPins.mockResolvedValue(undefined);
  deletePlaylistApi.mockResolvedValue(undefined);
  getPlaylistTracks.mockResolvedValue([]);
  getSmartTracks.mockResolvedValue([]);
});

describe("pins on the server (Q24)", () => {
  it("fetches the pin set with the playlists", async () => {
    getPlaylistPins.mockResolvedValue([2]);
    const store = usePlaylistStore();
    await store.loadPlaylists();

    expect(getPlaylistPins).toHaveBeenCalled();
    expect(store.pinnedIds).toEqual([2]);
    expect(store.isPinned(2)).toBe(true);
    expect(store.isPinned(1)).toBe(false);
  });

  it("keeps booting when the pin read fails", async () => {
    getPlaylistPins.mockRejectedValue(new Error("offline"));
    const store = usePlaylistStore();
    await store.loadPlaylists();

    expect(store.playlists).toHaveLength(2);
    expect(store.pinnedIds).toEqual([]);
  });

  it("writes the whole pin set back when a playlist is pinned", async () => {
    getPlaylistPins.mockResolvedValue([2]);
    const store = usePlaylistStore();
    await store.loadPlaylists();

    await store.togglePin(1);

    // Pin order: existing pins first, the new pin appended.
    expect(setPlaylistPins).toHaveBeenCalledWith([2, 1]);
    expect(store.pinnedIds).toEqual([2, 1]);
  });

  it("unpins by writing the set without the id", async () => {
    getPlaylistPins.mockResolvedValue([1, 2]);
    const store = usePlaylistStore();
    await store.loadPlaylists();

    await store.togglePin(1);

    expect(setPlaylistPins).toHaveBeenCalledWith([2]);
    expect(store.pinnedIds).toEqual([2]);
  });

  it("reverts the optimistic pin when the server rejects it", async () => {
    getPlaylistPins.mockResolvedValue([2]);
    setPlaylistPins.mockRejectedValue(new Error("403"));
    const store = usePlaylistStore();
    await store.loadPlaylists();

    await store.togglePin(1);

    expect(store.pinnedIds).toEqual([2]);
    expect(toastShow).toHaveBeenCalledWith("Failed to update pins: 403");
  });

  it("drops a deleted playlist from the server pin set", async () => {
    getPlaylistPins.mockResolvedValue([1, 2]);
    const store = usePlaylistStore();
    await store.loadPlaylists();

    await store.deletePlaylist(1);

    expect(store.playlists.map((p) => p.id)).toEqual([2]);
    expect(setPlaylistPins).toHaveBeenCalledWith([2]);
  });
});

describe("favorites playlist (Q21)", () => {
  it("matches the server-owned Favorites playlist by exact name", async () => {
    getPlaylists.mockResolvedValue([
      playlist({ id: 1, name: "Favorites" }),
      playlist({ id: 2, name: "favorites" }),
      playlist({ id: 3, name: "My Favorites" }),
    ]);
    const store = usePlaylistStore();
    await store.loadPlaylists();

    expect(store.isFavoritesPlaylist(playlist({ id: 1, name: "Favorites" }))).toBe(true);
    expect(store.isFavoritesPlaylist(playlist({ id: 2, name: "favorites" }))).toBe(false);
    expect(store.isFavoritesPlaylist(playlist({ id: 3, name: "My Favorites" }))).toBe(false);
  });
});

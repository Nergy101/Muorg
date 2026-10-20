import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

/**
 * A tiny stateful stand-in for the backend: the Favorites playlist (id 7)
 * holds `favTracks.ids`, and the add/remove mocks mutate it, so a reload
 * re-hydrates exactly what the "server" now holds.
 */
const backend = vi.hoisted(() => ({
  favTracks: { ids: [42] as number[] },
  playlists: [
    { id: 1, name: "Rock", track_count: 2 },
    { id: 7, name: "Favorites", icon: "⭐", track_count: 1 },
  ] as { id: number; name: string; icon?: string | null; track_count: number }[],
}));

vi.mock("../api/playlists", () => {
  // The Favorites playlist is located by exact name, like the real server does.
  const favId = () => backend.playlists.find((p) => p.name === "Favorites")?.id ?? -1;
  return {
    getPlaylists: vi.fn(async () => backend.playlists),
    getPlaylistPins: vi.fn(async () => []),
    setPlaylistPins: vi.fn(async (_ids: number[]) => {}),
    getPlaylistTracks: vi.fn(async (id: number) => (id === favId() ? [...backend.favTracks.ids] : [])),
    createPlaylist: vi.fn(async (name: string) => {
      const playlist = { id: 9, name, track_count: 0 };
      backend.playlists = [...backend.playlists, playlist];
      return playlist;
    }),
    setPlaylistIcon: vi.fn(async (id: number, icon: string | null) => {
      backend.playlists = backend.playlists.map((p) => (p.id === id ? { ...p, icon } : p));
    }),
    addTracksToPlaylist: vi.fn(async (id: number, trackIds: number[]) => {
      if (id === favId()) backend.favTracks.ids = [...new Set([...backend.favTracks.ids, ...trackIds])];
    }),
    removeTracksFromPlaylist: vi.fn(async (id: number, trackIds: number[]) => {
      if (id === favId()) backend.favTracks.ids = backend.favTracks.ids.filter((t) => !trackIds.includes(t));
    }),
  };
});

vi.mock("../api/client", () => ({
  getBackendMode: () => "online",
  setBackendMode: vi.fn(),
}));

import * as api from "../api/playlists";
import { FAVORITES_PLAYLIST_NAME, usePlaylistStore } from "./playlists";

/**
 * Q21: favorites are the playlist named exactly "Favorites" on whichever
 * backend is active. The store seeds the favorite ids from that playlist on
 * load, toggles membership optimistically, and reverts when the write fails.
 */
describe("favorites", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    backend.favTracks.ids = [42];
    backend.playlists = [
      { id: 1, name: "Rock", track_count: 2 },
      { id: 7, name: "Favorites", icon: "⭐", track_count: 1 },
    ];
  });

  it("seeds favorite ids from the Favorites playlist on load", async () => {
    const store = usePlaylistStore();
    await store.loadPlaylists();

    expect(store.favoritesPlaylist?.id).toBe(7);
    expect(store.favoriteTrackIds).toEqual([42]);
    expect(store.isFavorite(42)).toBe(true);
    expect(store.isFavorite(1)).toBe(false);
  });

  it("matches the Favorites playlist by exact name only", () => {
    const store = usePlaylistStore();
    expect(store.isFavoritesPlaylist({ id: 1, name: FAVORITES_PLAYLIST_NAME, icon: null, smart_rules: null, track_count: 0 })).toBe(true);
    expect(store.isFavoritesPlaylist({ id: 2, name: "favorites", icon: null, smart_rules: null, track_count: 0 })).toBe(false);
    expect(store.isFavoritesPlaylist({ id: 3, name: "Favorites 2", icon: null, smart_rules: null, track_count: 0 })).toBe(false);
  });

  it("adds a track to the Favorites playlist and re-hydrates from the backend", async () => {
    const store = usePlaylistStore();
    await store.loadPlaylists();

    await store.toggleFavorite(1);

    expect(api.addTracksToPlaylist).toHaveBeenCalledWith(7, [1]);
    expect(backend.favTracks.ids).toEqual([42, 1]);
    expect(store.favoriteTrackIds).toEqual([42, 1]);
    expect(store.isFavorite(1)).toBe(true);
  });

  it("removes a track from the Favorites playlist", async () => {
    const store = usePlaylistStore();
    await store.loadPlaylists();

    await store.toggleFavorite(42);

    expect(api.removeTracksFromPlaylist).toHaveBeenCalledWith(7, [42]);
    expect(backend.favTracks.ids).toEqual([]);
    expect(store.favoriteTrackIds).toEqual([]);
    expect(store.isFavorite(42)).toBe(false);
  });

  it("reverts the optimistic state when the write fails", async () => {
    const store = usePlaylistStore();
    await store.loadPlaylists();
    vi.mocked(api.addTracksToPlaylist).mockRejectedValueOnce(new Error("boom"));

    await store.toggleFavorite(1);

    expect(store.favoriteTrackIds).toEqual([42]);
    expect(store.isFavorite(1)).toBe(false);
    expect(store.error).toBe("boom");
  });

  it("creates the Favorites playlist (with the star icon) when missing", async () => {
    backend.playlists = [{ id: 1, name: "Rock", track_count: 2 }];
    const store = usePlaylistStore();
    await store.loadPlaylists();
    expect(store.favoritesPlaylist).toBeNull();

    await store.toggleFavorite(5);

    expect(api.createPlaylist).toHaveBeenCalledWith(FAVORITES_PLAYLIST_NAME);
    expect(api.setPlaylistIcon).toHaveBeenCalledWith(9, "⭐");
    expect(api.addTracksToPlaylist).toHaveBeenCalledWith(9, [5]);
    expect(store.isFavorite(5)).toBe(true);
  });

  it("loads playlists first when favoriting before the initial load, so it never creates a duplicate Favorites playlist", async () => {
    // The backend already has a Favorites playlist (id 7); the store has not
    // loaded yet — e.g. the user clicked the heart while the first
    // loadPlaylists() was still in flight (online mode has no startup overlay).
    const store = usePlaylistStore();
    expect(store.playlists).toEqual([]);

    await store.toggleFavorite(1);

    expect(api.createPlaylist).not.toHaveBeenCalled();
    expect(api.addTracksToPlaylist).toHaveBeenCalledWith(7, [1]);
    expect(backend.playlists.filter((p) => p.name === FAVORITES_PLAYLIST_NAME)).toHaveLength(1);
    expect(store.isFavorite(1)).toBe(true);
  });
});

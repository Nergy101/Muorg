import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { CatalogTrack } from "../types";

/**
 * The library store, with the API module stubbed.
 *
 * The load path is the interesting part: `/api/tracks` is paginated, the first
 * page has to be visible before the rest arrive, and a duplicate id across a
 * page boundary must not produce a duplicate row — the server's
 * `ORDER BY artist, album, track_number, title` has ties, so that happens.
 */
const getTracks = vi.fn();
const getStats = vi.fn();
const getCoverBlob = vi.fn();

vi.mock("../api/catalog", () => ({
  getTracks: (...a: unknown[]) => getTracks(...a),
  getStats: (...a: unknown[]) => getStats(...a),
  getCoverBlob: (...a: unknown[]) => getCoverBlob(...a),
}));

const { useLibraryStore } = await import("./library");

function track(p: Partial<CatalogTrack> & { id: number }): CatalogTrack {
  return {
    path: `/m/${p.id}.mp3`,
    root_id: 1,
    title: `t${p.id}`,
    artist: null,
    album: null,
    album_artist: null,
    featuring: null,
    year: null,
    genre: null,
    track_number: null,
    disc_number: null,
    duration_secs: null,
    format: "mp3",
    mtime_secs: 0,
    has_cover: false,
    rating: null,
    play_count: 0,
    last_played_at: null,
    ...p,
  };
}

const page = (from: number, count: number, total: number) => ({
  tracks: Array.from({ length: count }, (_, i) => track({ id: from + i })),
  total,
});

/** Lets the detached `loadRemainingPages` promise chain settle. */
const settle = () => new Promise((r) => setTimeout(r, 0));

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
  getStats.mockResolvedValue({
    track_count: 0,
    artist_count: 0,
    album_count: 0,
    total_duration_secs: 0,
  });
  localStorage.clear();
});

describe("loadLibrary", () => {
  it("exposes the first page before the rest have arrived", async () => {
    getTracks.mockResolvedValueOnce(page(0, 500, 1200));
    getTracks.mockResolvedValue(page(500, 0, 1200));

    const store = useLibraryStore();
    await store.loadLibrary();

    expect(store.tracks).toHaveLength(500);
    expect(store.totalTracks).toBe(1200);
    expect(store.loading).toBe(false);
  });

  it("streams the remaining pages in behind the first", async () => {
    getTracks
      .mockResolvedValueOnce(page(0, 500, 1200))
      .mockResolvedValueOnce(page(500, 500, 1200))
      .mockResolvedValueOnce(page(1000, 200, 1200));

    const store = useLibraryStore();
    await store.loadLibrary();
    await settle();

    expect(store.tracks).toHaveLength(1200);
    expect(store.loadingMore).toBe(false);
  });

  it("does not fetch a second page when the first is the whole catalog", async () => {
    getTracks.mockResolvedValueOnce(page(0, 12, 12));

    const store = useLibraryStore();
    await store.loadLibrary();
    await settle();

    expect(getTracks).toHaveBeenCalledTimes(1);
    expect(store.tracks).toHaveLength(12);
  });

  it("drops a track that appears on two pages", async () => {
    // Ties in the server's ordering are not unique, so a row can straddle a
    // page boundary and come back twice.
    getTracks
      .mockResolvedValueOnce({ tracks: [track({ id: 1 }), track({ id: 2 })], total: 4 })
      .mockResolvedValueOnce({ tracks: [track({ id: 2 }), track({ id: 3 })], total: 4 });

    const store = useLibraryStore();
    await store.loadLibrary();
    await settle();

    expect(store.tracks.map((t) => t.id)).toEqual([1, 2, 3]);
  });

  it("records the error and stops loading when the first page fails", async () => {
    getTracks.mockRejectedValueOnce(new Error("HTTP 401"));

    const store = useLibraryStore();
    await store.loadLibrary();

    expect(store.error).toBe("HTTP 401");
    expect(store.loading).toBe(false);
    expect(store.tracks).toEqual([]);
  });

  it("keeps the pages it already has when a later one fails", async () => {
    getTracks
      .mockResolvedValueOnce(page(0, 500, 1200))
      .mockRejectedValueOnce(new Error("connection reset"));

    const store = useLibraryStore();
    await store.loadLibrary();
    await settle();

    expect(store.tracks).toHaveLength(500);
    expect(store.error).toBe("connection reset");
    expect(store.loadingMore).toBe(false);
  });
});

describe("filtering", () => {
  async function loadedStore(tracks: CatalogTrack[]) {
    getTracks.mockResolvedValueOnce({ tracks, total: tracks.length });
    const store = useLibraryStore();
    await store.loadLibrary();
    return store;
  }

  it("passes everything through with no filters set", async () => {
    const store = await loadedStore([track({ id: 1 }), track({ id: 2 })]);
    expect(store.filteredTracks).toHaveLength(2);
  });

  it("searches title, artist, album and album artist", async () => {
    const store = await loadedStore([
      track({ id: 1, title: "Needle" }),
      track({ id: 2, artist: "Needle" }),
      track({ id: 3, album: "Needle" }),
      track({ id: 4, album_artist: "Needle" }),
      track({ id: 5, title: "Haystack" }),
    ]);
    store.searchQuery = "needle";
    expect(store.filteredTracks.map((t) => t.id)).toEqual([1, 2, 3, 4]);
  });

  it("matches a substring, case-insensitively", async () => {
    const store = await loadedStore([track({ id: 1, title: "The Great Escape" })]);
    store.searchQuery = "  GREAT ";
    expect(store.filteredTracks).toHaveLength(1);
  });

  it("filters by artist, falling back to album artist", async () => {
    const store = await loadedStore([
      track({ id: 1, artist: "Boards" }),
      track({ id: 2, artist: null, album_artist: "Boards" }),
      track({ id: 3, artist: "Someone Else" }),
    ]);
    store.artistFilter = "boards";
    expect(store.filteredTracks.map((t) => t.id)).toEqual([1, 2]);
  });

  it("filters by genre on the normalised value", async () => {
    const store = await loadedStore([
      track({ id: 1, genre: "Metal" }),
      track({ id: 2, genre: "Jazz" }),
    ]);
    store.genreFilter = "metal";
    expect(store.filteredTracks.map((t) => t.id)).toEqual([1]);
  });

  it("applies genre and search together", async () => {
    const store = await loadedStore([
      track({ id: 1, genre: "Metal", title: "Alpha" }),
      track({ id: 2, genre: "Metal", title: "Beta" }),
      track({ id: 3, genre: "Jazz", title: "Alpha" }),
    ]);
    store.genreFilter = "metal";
    store.searchQuery = "alpha";
    expect(store.filteredTracks.map((t) => t.id)).toEqual([1]);
  });

  it("lists distinct genres once, in original casing", async () => {
    const store = await loadedStore([
      track({ id: 1, genre: "Metal" }),
      track({ id: 2, genre: "metal" }),
      track({ id: 3, genre: "Ambient" }),
      track({ id: 4, genre: null }),
    ]);
    expect(store.genres).toEqual([
      { value: "ambient", label: "Ambient" },
      { value: "metal", label: "Metal" },
    ]);
  });
});

describe("albumGridItems", () => {
  async function loadedStore(tracks: CatalogTrack[]) {
    getTracks.mockResolvedValueOnce({ tracks, total: tracks.length });
    const store = useLibraryStore();
    await store.loadLibrary();
    return store;
  }

  it("counts only the filtered tracks but keeps the album key stable", async () => {
    // Searching inside an album must narrow the count without renaming or
    // re-splitting the album, so navigation keys stay valid.
    const store = await loadedStore([
      track({ id: 1, album: "Ride", album_artist: "Boards", title: "Alpha" }),
      track({ id: 2, album: "Ride", album_artist: "Boards", title: "Beta" }),
    ]);
    const before = store.albumGridItems[0].key;

    store.searchQuery = "alpha";
    expect(store.albumGridItems).toHaveLength(1);
    expect(store.albumGridItems[0].key).toBe(before);
    expect(store.albumGridItems[0].trackCount).toBe(1);
  });

  it("takes the earliest year and the first track that has art", async () => {
    const store = await loadedStore([
      track({ id: 1, album: "X", album_artist: "A", year: 2010, has_cover: false }),
      track({ id: 2, album: "X", album_artist: "A", year: 1998, has_cover: true }),
    ]);
    const [item] = store.albumGridItems;
    expect(item.year).toBe(1998);
    expect(item.coverTrackId).toBe(2);
  });
});

describe("reset", () => {
  it("clears the catalog and the filters", async () => {
    getTracks.mockResolvedValueOnce({ tracks: [track({ id: 1 })], total: 1 });
    const store = useLibraryStore();
    await store.loadLibrary();
    store.searchQuery = "x";

    store.reset();

    expect(store.tracks).toEqual([]);
    expect(store.searchQuery).toBe("");
    expect(store.totalTracks).toBe(0);
  });
});

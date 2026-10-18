import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { CatalogTrack } from "../types";

/**
 * The offline store (Q11), with Cache Storage, the storage estimate and the
 * network stubbed. The store writes whole track files via cache.put and reads
 * them back through the service worker's runtime cache — these tests verify
 * the download, quota, eviction and clear behaviour against an in-memory fake.
 */

const issueStreamToken = vi.fn();
const getRecentPlayHistory = vi.fn();
const getPlaylists = vi.fn();
const getPlaylistPins = vi.fn();
const setPlaylistPins = vi.fn();
const getPlaylistTracks = vi.fn();
const getSmartTracks = vi.fn();
const toastShow = vi.fn();
const fetchMock = vi.fn();

vi.mock("../api/catalog", () => ({
  issueStreamToken: (...a: unknown[]) => issueStreamToken(...a),
  getRecentPlayHistory: (...a: unknown[]) => getRecentPlayHistory(...a),
}));

vi.mock("../api/client", () => ({
  api: {},
  streamUrl: (id: number, token: string) => `http://server/stream/${id}?token=${token}`,
  getServerUrl: () => "http://server",
}));

vi.mock("../api/playlists", () => ({
  getPlaylists: (...a: unknown[]) => getPlaylists(...a),
  getPlaylistPins: (...a: unknown[]) => getPlaylistPins(...a),
  setPlaylistPins: (...a: unknown[]) => setPlaylistPins(...a),
  getPlaylistTracks: (...a: unknown[]) => getPlaylistTracks(...a),
  getSmartTracks: (...a: unknown[]) => getSmartTracks(...a),
  // Mirror the real module: smart playlists resolve through the smart endpoint.
  getTracksForPlaylist: (p: { id: number; smart_rules: unknown }) =>
    p.smart_rules != null ? getSmartTracks(p.id) : getPlaylistTracks(p.id),
  createPlaylist: vi.fn(),
  createSmartPlaylist: vi.fn(),
  updateSmartPlaylistRules: vi.fn(),
  renamePlaylist: vi.fn(),
  deletePlaylist: vi.fn(),
  addTracksToPlaylist: vi.fn(),
  removeTracksFromPlaylist: vi.fn(),
  reorderPlaylistTracks: vi.fn(),
}));

vi.mock("../composables/useToast", () => ({
  showToast: (...a: unknown[]) => toastShow(...a),
  showWarning: vi.fn(),
}));

// Break the player → offline → playlists → player import cycle.
vi.mock("./player", () => ({
  usePlayerStore: () => ({ favorites: new Set<number>() }),
}));

// ── In-memory Cache Storage ────────────────────────────────────────────────

class FakeCache {
  private store = new Map<string, Response>();

  async put(request: Request | string, response: Response): Promise<void> {
    const url = typeof request === "string" ? request : request.url;
    this.store.set(url, response.clone());
  }

  async match(request: Request | string): Promise<Response | undefined> {
    const url = typeof request === "string" ? request : request.url;
    return this.store.get(url)?.clone();
  }

  async keys(): Promise<Request[]> {
    return [...this.store.keys()].map((url) => new Request(url));
  }

  async delete(request: Request | string): Promise<boolean> {
    const url = typeof request === "string" ? request : request.url;
    return this.store.delete(url);
  }
}

const fakeCaches = {
  opened: new Map<string, FakeCache>(),
  async open(name: string): Promise<FakeCache> {
    let c = this.opened.get(name);
    if (!c) {
      c = new FakeCache();
      this.opened.set(name, c);
    }
    return c;
  },
  async delete(name: string): Promise<boolean> {
    return this.opened.delete(name);
  },
};

Object.defineProperty(window, "caches", { value: fakeCaches, configurable: true });

const estimateMock = vi.fn();
Object.defineProperty(navigator, "storage", {
  value: { estimate: () => estimateMock() },
  configurable: true,
});

globalThis.fetch = fetchMock as unknown as typeof fetch;

const { useOfflineStore, OFFLINE_CACHE_NAME, offlineStreamUrl } = await import("./offline");
const { useLibraryStore } = await import("./library");
const { usePlaylistStore } = await import("./playlists");

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
    duration_secs: 200,
    format: "mp3",
    mtime_secs: 0,
    has_cover: false,
    rating: null,
    play_count: 0,
    last_played_at: null,
    ...p,
  };
}

/** A 200 response carrying `bytes` of body and a matching content-length. */
function audioResponse(bytes: number): Response {
  return new Response("x".repeat(bytes), {
    status: 200,
    headers: { "content-length": String(bytes) },
  });
}

beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
  vi.clearAllMocks();
  fakeCaches.opened.clear();
  issueStreamToken.mockResolvedValue("tok");
  getRecentPlayHistory.mockResolvedValue([]);
  getPlaylists.mockResolvedValue([]);
  getPlaylistPins.mockResolvedValue([]);
  setPlaylistPins.mockResolvedValue(undefined);
  getPlaylistTracks.mockResolvedValue([]);
  getSmartTracks.mockResolvedValue([]);
  estimateMock.mockResolvedValue({ quota: 10 * 1024 * 1024 * 1024, usage: 0 });
  fetchMock.mockImplementation(async (input: Request | string) => {
    const url = typeof input === "string" ? input : input.url;
    if (!/\/stream\/\d+/.test(url)) throw new Error(`unexpected fetch: ${url}`);
    return audioResponse(100);
  });
});

describe("downloadTrack", () => {
  it("fetches the track whole and stores it under the bare stream URL", async () => {
    const offline = useOfflineStore();
    await offline.init();

    const ok = await offline.downloadTrack(track({ id: 7 }));

    expect(ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith("http://server/stream/7?token=tok");
    const cache = await fakeCaches.open(OFFLINE_CACHE_NAME);
    const stored = await cache.match("http://server/stream/7");
    expect(stored).toBeDefined();
    expect(stored!.status).toBe(200);
    expect(offline.isCached(7)).toBe(true);
    expect(offline.cachedBytes).toBe(100);
  });

  it("is a no-op for a track that is already cached", async () => {
    const offline = useOfflineStore();
    await offline.init();
    await offline.downloadTrack(track({ id: 7 }));
    fetchMock.mockClear();

    const ok = await offline.downloadTrack(track({ id: 7 }));

    expect(ok).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects a download that would exceed the storage quota", async () => {
    estimateMock.mockResolvedValue({ quota: 50, usage: 0 });
    const offline = useOfflineStore();
    await offline.init();

    const ok = await offline.downloadTrack(track({ id: 7 }));

    expect(ok).toBe(false);
    expect(offline.isCached(7)).toBe(false);
    expect(toastShow).toHaveBeenCalledWith("Not enough storage for offline downloads");
  });

  it("rejects a download that would exceed the local 2 GiB cap", async () => {
    const offline = useOfflineStore();
    await offline.init();
    // Pretend the cache already holds nearly the whole cap.
    offline.cachedBytes = 2 * 1024 * 1024 * 1024;

    const ok = await offline.downloadTrack(track({ id: 7 }));

    expect(ok).toBe(false);
    expect(offline.isCached(7)).toBe(false);
  });
});

describe("downloadTracks", () => {
  it("stops the batch at the first failure", async () => {
    const offline = useOfflineStore();
    await offline.init();
    // Each track is 100 bytes; the quota allows one but not two. Report the
    // store's own byte count as current usage so the quota check bites.
    estimateMock.mockImplementation(async () => ({ quota: 150, usage: offline.cachedBytes }));

    await offline.downloadTracks([track({ id: 1 }), track({ id: 2 }), track({ id: 3 })]);

    expect(offline.isCached(1)).toBe(true);
    expect(offline.isCached(2)).toBe(false);
    expect(offline.isCached(3)).toBe(false);
    expect(offline.downloadProgress).toEqual({ done: 2, total: 3 });
  });
});

describe("init", () => {
  it("rebuilds the index from what is already in the cache", async () => {
    const cache = await fakeCaches.open(OFFLINE_CACHE_NAME);
    await cache.put("http://server/stream/5", audioResponse(40));
    await cache.put("http://server/stream/6", audioResponse(60));

    const offline = useOfflineStore();
    await offline.init();

    expect(offline.isCached(5)).toBe(true);
    expect(offline.isCached(6)).toBe(true);
    expect(offline.cachedTrackCount).toBe(2);
    expect(offline.cachedBytes).toBe(100);
  });
});

describe("refreshRecentCache", () => {
  it("downloads missing recent tracks and evicts stale auto-cached ones", async () => {
    const cache = await fakeCaches.open(OFFLINE_CACHE_NAME);
    await cache.put("http://server/stream/1", audioResponse(100));

    const offline = useOfflineStore();
    await offline.init();
    offline.recentIds = [1]; // track 1 was auto-cached earlier

    getRecentPlayHistory.mockResolvedValue([track({ id: 2 }), track({ id: 3 })]);
    await offline.refreshRecentCache();

    expect(offline.isCached(1)).toBe(false); // evicted — no longer recent
    expect(offline.isCached(2)).toBe(true);
    expect(offline.isCached(3)).toBe(true);
    expect(offline.recentIds).toEqual([2, 3]);
  });

  it("never evicts a track a downloaded playlist needs", async () => {
    const cache = await fakeCaches.open(OFFLINE_CACHE_NAME);
    await cache.put("http://server/stream/1", audioResponse(100));

    const offline = useOfflineStore();
    await offline.init();
    offline.recentIds = [1];
    offline.downloadedPlaylists = [10];
    offline.playlistTrackIds = { 10: [1] };

    getRecentPlayHistory.mockResolvedValue([track({ id: 2 })]);
    await offline.refreshRecentCache();

    expect(offline.isCached(1)).toBe(true); // protected by the playlist download
    expect(offline.isCached(2)).toBe(true);
    expect(offline.recentIds).toEqual([2]);
  });

  it("keeps the cache when the server is unreachable", async () => {
    const cache = await fakeCaches.open(OFFLINE_CACHE_NAME);
    await cache.put("http://server/stream/1", audioResponse(100));

    const offline = useOfflineStore();
    await offline.init();
    offline.recentIds = [1];

    getRecentPlayHistory.mockRejectedValue(new Error("offline"));
    await offline.refreshRecentCache();

    expect(offline.isCached(1)).toBe(true);
    expect(offline.recentIds).toEqual([1]);
  });
});

describe("downloadPlaylist", () => {
  it("downloads every track and marks the playlist downloaded", async () => {
    const playlistStore = usePlaylistStore();
    const lib = useLibraryStore();
    playlistStore.playlists = [
      { id: 10, name: "Mix", icon: null, smart_rules: null, track_count: 2 },
    ];
    lib.tracks = [track({ id: 7 }), track({ id: 8 })];

    getPlaylistTracks.mockResolvedValue([7, 8]);

    const offline = useOfflineStore();
    await offline.init();
    await offline.downloadPlaylist(10);

    expect(offline.isCached(7)).toBe(true);
    expect(offline.isCached(8)).toBe(true);
    expect(offline.isPlaylistDownloaded(10)).toBe(true);
    expect(offline.playlistTrackIds[10]).toEqual([7, 8]);
  });
});

describe("clearAll", () => {
  it("deletes the cache and its bookkeeping", async () => {
    const offline = useOfflineStore();
    await offline.init();
    await offline.downloadTrack(track({ id: 7 }));
    offline.recentIds = [7];
    offline.downloadedPlaylists = [10];
    expect(offline.isCached(7)).toBe(true);

    await offline.clearAll();

    expect(offline.isCached(7)).toBe(false);
    expect(offline.cachedTrackCount).toBe(0);
    expect(offline.recentIds).toEqual([]);
    expect(offline.downloadedPlaylists).toEqual([]);
    expect(fakeCaches.opened.has(OFFLINE_CACHE_NAME)).toBe(false);
  });
});

describe("offlineStreamUrl", () => {
  it("marks the URL so the service worker may cache it", () => {
    expect(offlineStreamUrl(7)).toBe("http://server/stream/7?offline=1");
  });
});

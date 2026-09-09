import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { nextTick, watchEffect } from "vue";

vi.mock("../api/catalog", () => ({
  getCover: vi.fn(async (id: number) => ({
    base64: `cover-${id}`,
    mime: "image/jpeg",
    size_bytes: 1,
  })),
}));

import * as api from "../api/catalog";
import { MAX_COVERS, useCatalogStore } from "./catalog";

/**
 * The album-cover cache.
 *
 * The bug these exist for: covers popped in and out at random on a 3000-track
 * library, with the view completely still, in both local and online mode.
 *
 * Three faults compounded. The LRU order lived in reactive state, so `getCover`
 * could not touch it on a read without re-invalidating the computed doing the
 * reading — eviction therefore ran in insertion order and happily threw away
 * art that was on screen. Every write replaced the whole `coverCache` object,
 * waking every reader of every other path. And the prefetch queued a cover for
 * every album in the library, far past what the cache holds. Together they made
 * a loop that fed itself: evict something visible, its component refetches it,
 * that insertion evicts the next visible one, forever.
 */
describe("cover cache", () => {
  beforeEach(async () => {
    setActivePinia(createPinia());
    // The fetch queue and its concurrency counter are module state, so they
    // outlive the store: without this, one test's prefetch keeps draining into
    // the next and holds every concurrency slot while it does.
    useCatalogStore().clearCoverCache();
    await new Promise((resolve) => setTimeout(resolve, 0));
    vi.clearAllMocks();
  });

  /** Fill the cache with n synthetic covers, oldest first. */
  function fill(store: ReturnType<typeof useCatalogStore>, n: number, prefix = "p") {
    for (let i = 0; i < n; i++) {
      store._setCover(`${prefix}${i}`, { base64: `b${i}`, mime: "image/jpeg", size_bytes: 1 });
    }
  }

  it("never grows past its bound", () => {
    const store = useCatalogStore();
    fill(store, MAX_COVERS + 250);
    expect(Object.keys(store.coverCache).length).toBe(MAX_COVERS);
  });

  it("evicts the least recently used, not the oldest inserted", () => {
    const store = useCatalogStore();
    fill(store, MAX_COVERS);

    // p0 is the oldest insertion. Reading it is what a visible cover does on
    // every render, and that has to be enough to save it.
    store.getCover("p0");
    store._setCover("fresh", { base64: "x", mime: "image/jpeg", size_bytes: 1 });

    expect(store.coverCache["p0"]).toBeDefined();
    expect(store.coverCache["p1"]).toBeUndefined();
  });

  it("keeps a cover that is still being displayed, however much churns past it", () => {
    // The exact shape of the reported bug: a still view whose covers were
    // evicted underneath it by a library-sized flood of insertions.
    const store = useCatalogStore();
    const onScreen = ["v0", "v1", "v2"];
    fill(store, 3, "v");

    for (let i = 0; i < MAX_COVERS * 3; i++) {
      // Every render re-reads what is visible.
      for (const path of onScreen) store.getCover(path);
      store._setCover(`churn${i}`, { base64: "x", mime: "image/jpeg", size_bytes: 1 });
    }

    for (const path of onScreen) expect(store.coverCache[path]).toBeDefined();
  });

  it("does not refetch a displayed cover while the library prefetches around it", async () => {
    // The pop-out, end to end: an evicted path is a cache miss, and a cache
    // miss is what makes TrackAlbumArt ask for it again.
    const store = useCatalogStore();
    // fetchCover resolves the path against the catalog, so the track has to
    // exist or the fetch quietly turns into a no-op and proves nothing.
    store.tracks = [{ id: 1, path: "visible", album: "A", has_cover: true }] as never;
    store._setCover("visible", { base64: "v", mime: "image/jpeg", size_bytes: 1 });

    for (let i = 0; i < MAX_COVERS * 2; i++) {
      store.getCover("visible");
      store._setCover(`other${i}`, { base64: "x", mime: "image/jpeg", size_bytes: 1 });
    }

    store.fetchCover("visible");
    await nextTick();
    expect(api.getCover).not.toHaveBeenCalled();
  });

  it("waking one reader does not wake the others", async () => {
    // Replacing the cache object made one arriving cover re-run the watchEffect
    // of every mounted TrackAlbumArt — thousands of them during a prefetch.
    const store = useCatalogStore();
    let runs = 0;
    const stop = watchEffect(() => {
      void store.getCover("mine");
      runs++;
    });
    await nextTick();
    const before = runs;

    store._setCover("someone-elses", { base64: "x", mime: "image/jpeg", size_bytes: 1 });
    await nextTick();
    expect(runs).toBe(before);

    store._setCover("mine", { base64: "m", mime: "image/jpeg", size_bytes: 1 });
    await nextTick();
    expect(runs).toBe(before + 1);
    stop();
  });

  it("reading an uncached path does not reserve a slot for it", () => {
    // A touch on a miss would put a phantom key in the LRU order, which then
    // evicts a real cover in its place.
    const store = useCatalogStore();
    fill(store, MAX_COVERS);
    for (let i = 0; i < 100; i++) store.getCover(`never-cached-${i}`);

    store._setCover("fresh", { base64: "x", mime: "image/jpeg", size_bytes: 1 });
    expect(Object.keys(store.coverCache).length).toBe(MAX_COVERS);
    expect(store.coverCache["fresh"]).toBeDefined();
  });

  it("reads a cover through its data url too", () => {
    const store = useCatalogStore();
    fill(store, MAX_COVERS);
    expect(store.getCoverDataUrl("p0")).toBe("data:image/jpeg;base64,b0");

    store._setCover("fresh", { base64: "x", mime: "image/jpeg", size_bytes: 1 });
    expect(store.coverCache["p0"]).toBeDefined();
  });

  it("caches a known absence so it is not asked for again", async () => {
    const store = useCatalogStore();
    store._setCover("no-art", null);
    store.fetchCover("no-art");
    await nextTick();
    expect(api.getCover).not.toHaveBeenCalled();
    expect(store.getCover("no-art")).toBeNull();
  });

  it("prefetches at most what the cache can hold", () => {
    // Unbounded, this queued a cover for every album in the library. Past the
    // cache's capacity each arrival evicted an earlier one, so the prefetch
    // spent itself evicting its own results — the flood behind the churn.
    const store = useCatalogStore();
    store.tracks = Array.from({ length: MAX_COVERS * 3 }, (_, i) => ({
      id: i,
      path: `/music/${i}.mp3`,
      album: `Album ${i}`,
      has_cover: true,
    })) as never;
    const requested = vi.spyOn(store, "fetchCover");

    store._prefetchAllCovers();
    expect(requested.mock.calls.length).toBeLessThanOrEqual(MAX_COVERS);
    expect(requested.mock.calls.length).toBeGreaterThan(0);
  });

  it("prefetches one cover per album, not one per track", () => {
    const store = useCatalogStore();
    store.tracks = Array.from({ length: 30 }, (_, i) => ({
      id: i,
      path: `/music/${i}.mp3`,
      album: `Album ${i % 3}`,
      has_cover: true,
    })) as never;
    const requested = vi.spyOn(store, "fetchCover");

    store._prefetchAllCovers();
    expect(requested).toHaveBeenCalledTimes(3);
  });

  it("skips tracks that have no artwork to fetch", () => {
    const store = useCatalogStore();
    store.tracks = Array.from({ length: 10 }, (_, i) => ({
      id: i,
      path: `/music/${i}.mp3`,
      album: `Album ${i}`,
      has_cover: i < 4,
    })) as never;
    const requested = vi.spyOn(store, "fetchCover");

    store._prefetchAllCovers();
    expect(requested).toHaveBeenCalledTimes(4);
  });

  it("drops a fetch that was in flight when the cache was cleared", async () => {
    // Reconnecting to a different server used to let the previous library's
    // covers land in the freshly cleared cache.
    const store = useCatalogStore();
    store.tracks = [{ id: 1, path: "/music/1.mp3", album: "A", has_cover: true }] as never;

    store.fetchCover("/music/1.mp3");
    store.clearCoverCache();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(store.coverCache["/music/1.mp3"]).toBeUndefined();
  });

  it("invalidating a path frees its slot rather than leaving a phantom", () => {
    const store = useCatalogStore();
    fill(store, MAX_COVERS);
    store.invalidateCover("p0");
    expect(store.coverCache["p0"]).toBeUndefined();

    // p0's slot is genuinely free, so this insert evicts nothing.
    store._setCover("fresh", { base64: "x", mime: "image/jpeg", size_bytes: 1 });
    expect(store.coverCache["p1"]).toBeDefined();
    expect(Object.keys(store.coverCache).length).toBe(MAX_COVERS);
  });

  it("clearing forgets the order as well as the covers", () => {
    // A stale order after a reconnect evicts freshly fetched covers on sight.
    const store = useCatalogStore();
    fill(store, MAX_COVERS);
    store.clearCoverCache();
    expect(Object.keys(store.coverCache).length).toBe(0);

    fill(store, 10, "after");
    expect(Object.keys(store.coverCache).length).toBe(10);
  });
});

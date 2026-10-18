import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

vi.mock("../api/catalog", () => ({
  getCover: vi.fn(async (id: number) => ({
    base64: `cover-${id}`,
    mime: "image/jpeg",
    size_bytes: 1,
  })),
}));

import { useCatalogStore } from "./catalog";
import { useSettingsStore } from "./settings";
import type { CatalogTrack } from "../types";

/**
 * "Shuffle all" (Q23): the library header button replaces the queue with a
 * shuffled batch of the library and keeps a pool that tops the queue up as it
 * drains, so every track plays exactly once before the queue runs dry.
 */
describe("shuffle all", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  function makeTracks(n: number): CatalogTrack[] {
    return Array.from({ length: n }, (_, i) => ({
      id: i + 1,
      path: `/music/${i + 1}.mp3`,
      title: `Track ${i + 1}`,
    })) as CatalogTrack[];
  }

  /** Simulate the player advancing: consume the queue head, make it current. */
  function advance(store: ReturnType<typeof useCatalogStore>) {
    const next = store.queueTrackIds[0];
    store.removeFromQueueAtIndex(0);
    store.setCurrentPlaying(next);
  }

  it("queues a shuffled batch and starts playing its first track", () => {
    const store = useCatalogStore();
    const settings = useSettingsStore();
    settings.setShuffle(true);
    const tracks = makeTracks(50);
    store.tracks = tracks;

    store.startShuffleAll(tracks);

    expect(settings.shuffle).toBe(false);
    expect(store.shuffleAllActive).toBe(true);
    expect(store.queueTrackIds).toHaveLength(20);
    expect(store.selectedTrackIds).toEqual([store.queueTrackIds[0]]);
    expect(store.playRequestTrackId).toBe(store.queueTrackIds[0]);
    expect(store.shuffleAllPoolIds).toHaveLength(30);
    // Every track is either queued, pooled, or the one being played — once.
    const all = [...store.queueTrackIds, ...store.shuffleAllPoolIds];
    expect(new Set(all).size).toBe(50);
  });

  it("plays every track exactly once as the queue drains", () => {
    const store = useCatalogStore();
    const tracks = makeTracks(50);
    store.tracks = tracks;
    store.startShuffleAll(tracks);

    // The player makes the first queued track current, which pulls it out of the queue.
    store.setCurrentPlaying(store.queueTrackIds[0]);
    const played: number[] = [store.currentPlayingTrackId!];
    while (store.queueTrackIds.length > 0) {
      advance(store);
      played.push(store.currentPlayingTrackId!);
      // Top-up keeps the queue at most one batch plus the track about to play.
      expect(store.queueTrackIds.length).toBeLessThanOrEqual(21);
    }

    expect(played).toHaveLength(50);
    expect(new Set(played).size).toBe(50);
    expect(store.shuffleAllPoolIds).toHaveLength(0);
  });

  it("clearing the queue ends shuffle-all so it does not refill", () => {
    const store = useCatalogStore();
    const tracks = makeTracks(50);
    store.tracks = tracks;
    store.startShuffleAll(tracks);

    store.clearQueue();
    expect(store.shuffleAllActive).toBe(false);
    expect(store.shuffleAllPoolIds).toHaveLength(0);

    store.setCurrentPlaying(1);
    expect(store.queueTrackIds).toHaveLength(0);
  });
});

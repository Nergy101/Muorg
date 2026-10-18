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
import { playFuture, playHistory, recordPlayHistory } from "../state/playback";
import type { CatalogTrack } from "../types";

/**
 * Q32: switching local ↔ online clears the queue and the selection — their
 * track ids belong to the other catalog.
 */
describe("mode switch clears queue and selection", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    playHistory.value = [];
    playFuture.value = [];
  });

  it("clears the queue, selection, shuffle-all and play history", () => {
    const store = useCatalogStore();
    store.tracks = Array.from({ length: 5 }, (_, i) => ({
      id: i + 1,
      path: `/music/${i + 1}.mp3`,
    })) as CatalogTrack[];
    store.startShuffleAll(store.tracks);
    store.setCurrentPlaying(store.queueTrackIds[0]);
    recordPlayHistory(1, 2);
    recordPlayHistory(2, 3);
    store.setSelection([1, 2]);

    expect(store.queueTrackIds.length).toBeGreaterThan(0);
    expect(store.selectedTrackIds.length).toBeGreaterThan(0);
    expect(store.shuffleAllActive).toBe(true);
    expect(playHistory.value.length).toBeGreaterThan(0);

    store.clearQueueAndSelection();

    expect(store.queueTrackIds).toEqual([]);
    expect(store.selectedTrackIds).toEqual([]);
    expect(store.currentPlayingTrackId).toBeNull();
    expect(store.shuffleAllActive).toBe(false);
    expect(store.shuffleAllPoolIds).toEqual([]);
    expect(playHistory.value).toEqual([]);
    expect(playFuture.value).toEqual([]);
  });
});

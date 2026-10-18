import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

vi.mock("../api/catalog", () => ({
  getCover: vi.fn(async (id: number) => ({
    base64: `cover-${id}`,
    mime: "image/jpeg",
    size_bytes: 1,
  })),
  setRating: vi.fn(async () => {}),
  patchMetadata: vi.fn(async () => {}),
  getLatestBackup: vi.fn(async () => null),
  streamTracks: vi.fn(async function* () {
    yield {
      total: 2,
      tracks: [
        { id: 1, path: "/music/1.mp3", title: "One", rating: 4 },
        { id: 2, path: "/music/2.mp3", title: "Two", rating: null },
      ],
    };
  }),
}));

import * as api from "../api/catalog";
import { useCatalogStore } from "./catalog";

/**
 * Q4: rating changes (digit keys, star widgets, context menu) push onto the
 * undo stack, and undoing a rating restores the DB rating only — it must not
 * rewrite file tags through patchMetadata.
 */
describe("rating undo", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  function seed(store: ReturnType<typeof useCatalogStore>) {
    store.tracks = [
      { id: 1, path: "/music/1.mp3", title: "One", rating: null },
      { id: 2, path: "/music/2.mp3", title: "Two", rating: 3 },
    ] as never;
  }

  it("pushes a rating undo entry and restores the old rating on undo", async () => {
    const store = useCatalogStore();
    seed(store);

    await store.setRating(["/music/1.mp3"], 4);
    expect(api.setRating).toHaveBeenCalledWith(1, 4);
    expect(store.undoStack).toHaveLength(1);
    expect(store.undoStack[0].kind).toBe("rating");
    expect(store.tracks.find((t) => t.id === 1)?.rating).toBe(4);

    await store.undo();
    // The previous rating (null) is restored through the rating endpoint only.
    expect(api.setRating).toHaveBeenLastCalledWith(1, null);
    expect(api.patchMetadata).not.toHaveBeenCalled();
    expect(store.redoStack).toHaveLength(1);

    await store.redo();
    expect(api.setRating).toHaveBeenLastCalledWith(1, 4);
    expect(api.patchMetadata).not.toHaveBeenCalled();
  });

  it("undoes a multi-track rating as one entry", async () => {
    const store = useCatalogStore();
    seed(store);

    await store.setRating(["/music/1.mp3", "/music/2.mp3"], 5);
    expect(store.undoStack).toHaveLength(1);
    expect(store.undoStack[0].snapshots).toHaveLength(2);

    await store.undo();
    expect(api.setRating).toHaveBeenCalledWith(1, null);
    expect(api.setRating).toHaveBeenCalledWith(2, 3);
    expect(api.patchMetadata).not.toHaveBeenCalled();
  });
});

import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

vi.mock("../api/playlists", () => ({
  getPlaylists: vi.fn(async () => [
    { id: 1, name: "Rock", track_count: 10 },
    { id: 2, name: "Jazz", track_count: 5 },
    { id: 3, name: "Classical", track_count: 7 },
  ]),
  getPlaylistPins: vi.fn(async () => [3, 1]),
  setPlaylistPins: vi.fn(async (_ids: number[]) => {}),
}));

const getBackendModeMock = vi.fn<() => "local" | "online">(() => "online");
vi.mock("../api/client", () => ({
  getBackendMode: () => getBackendModeMock(),
  setBackendMode: vi.fn(),
}));

import * as api from "../api/playlists";
import { usePlaylistStore } from "./playlists";
import { useSettingsStore } from "./settings";

/**
 * Q24: playlist pinning, in both modes.
 *
 * Online mode keeps the pin set on the server (getPlaylistPins /
 * setPlaylistPins); local mode keeps it on the device in the settings store.
 * The two stores are never merged — the pin set always comes from exactly one
 * of them, depending on the backend mode.
 */
describe("playlist pins", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    getBackendModeMock.mockReturnValue("online");
  });

  it("loads pins from the server in online mode and sorts pinned first in pin order", async () => {
    const store = usePlaylistStore();
    await store.loadPlaylists();

    expect(api.getPlaylistPins).toHaveBeenCalledTimes(1);
    expect(store.pinnedIds).toEqual([3, 1]);
    expect(store.sortedPlaylists.map((p) => p.id)).toEqual([3, 1, 2]);
    expect(store.isPinned(3)).toBe(true);
    expect(store.isPinned(2)).toBe(false);
  });

  it("pins and unpins optimistically, persisting the full pin set to the server", async () => {
    const store = usePlaylistStore();
    await store.loadPlaylists();

    await store.togglePin(2);
    expect(store.pinnedIds).toEqual([3, 1, 2]);
    expect(api.setPlaylistPins).toHaveBeenLastCalledWith([3, 1, 2]);

    await store.togglePin(3);
    expect(store.pinnedIds).toEqual([1, 2]);
    expect(api.setPlaylistPins).toHaveBeenLastCalledWith([1, 2]);
    expect(store.sortedPlaylists.map((p) => p.id)).toEqual([1, 2, 3]);
  });

  it("reverts the pin when the server write fails", async () => {
    const store = usePlaylistStore();
    await store.loadPlaylists();
    vi.mocked(api.setPlaylistPins).mockRejectedValueOnce(new Error("offline"));

    await store.togglePin(2);

    expect(store.pinnedIds).toEqual([3, 1]);
    expect(store.error).toBe("offline");
  });

  it("keeps pins on the device in local mode and never calls the pins API", async () => {
    getBackendModeMock.mockReturnValue("local");
    const settings = useSettingsStore();
    settings.pinnedPlaylistIds = [2];

    const store = usePlaylistStore();
    await store.loadPlaylists();

    expect(api.getPlaylistPins).not.toHaveBeenCalled();
    expect(store.pinnedIds).toEqual([2]);
    expect(store.sortedPlaylists.map((p) => p.id)).toEqual([2, 1, 3]);

    await store.togglePin(1);
    expect(api.setPlaylistPins).not.toHaveBeenCalled();
    expect(settings.pinnedPlaylistIds).toEqual([2, 1]);
    expect(store.pinnedIds).toEqual([2, 1]);
  });
});

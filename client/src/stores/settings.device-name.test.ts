import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

vi.mock("../api/catalog", () => ({
  getCover: vi.fn(async (id: number) => ({
    base64: `cover-${id}`,
    mime: "image/jpeg",
    size_bytes: 1,
  })),
  recordPlay: vi.fn(async (_id: number, _deviceName?: string) => {}),
}));

import * as api from "../api/catalog";
import { useCatalogStore } from "./catalog";
import { useSettingsStore } from "./settings";
import type { CatalogTrack } from "../types";

/**
 * Q19: the device name is a required, user-editable setting with a sensible
 * default, and every recorded play is attributed to it on the server.
 */
describe("device name", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it("defaults to a non-empty \"Muorg on OS\" label", () => {
    const settings = useSettingsStore();
    expect(settings.deviceName).toMatch(/^Muorg on .+/);
  });

  it("trims the user's value and never falls back to empty", () => {
    const settings = useSettingsStore();
    const fallback = settings.deviceName;

    settings.setDeviceName("  Studio PC  ");
    expect(settings.deviceName).toBe("Studio PC");

    settings.setDeviceName("   ");
    expect(settings.deviceName).toBe(fallback);
    expect(settings.deviceName.length).toBeGreaterThan(0);
  });

  it("is passed to the server when a play is recorded", async () => {
    const settings = useSettingsStore();
    settings.setDeviceName("Studio PC");
    const store = useCatalogStore();
    store.tracks = [{ id: 5, path: "/music/a.mp3" }] as CatalogTrack[];

    await store.recordPlay("/music/a.mp3");

    expect(api.recordPlay).toHaveBeenCalledWith(5, "Studio PC");
    // The local play count still increments alongside the server write.
    expect(store.tracks[0].play_count).toBe(1);
  });
});

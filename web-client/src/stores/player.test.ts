import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { nextTick } from "vue";
import type { CatalogTrack } from "../types";

/**
 * The player store, with the API and toast modules stubbed.
 *
 * Covers the audit fixes that live in this store: a play counts after 30 s
 * of listening, not at track start (Q20).
 */

const issueStreamToken = vi.fn();
const recordPlay = vi.fn();
const getTracks = vi.fn();
const getStats = vi.fn();
const getCoverBlob = vi.fn();
const getRecentPlayHistory = vi.fn();
const getMetadata = vi.fn();
const toastShow = vi.fn();

vi.mock("../api/catalog", () => ({
  issueStreamToken: (...a: unknown[]) => issueStreamToken(...a),
  recordPlay: (...a: unknown[]) => recordPlay(...a),
  getTracks: (...a: unknown[]) => getTracks(...a),
  getStats: (...a: unknown[]) => getStats(...a),
  getCoverBlob: (...a: unknown[]) => getCoverBlob(...a),
  getRecentPlayHistory: (...a: unknown[]) => getRecentPlayHistory(...a),
  getMetadata: (...a: unknown[]) => getMetadata(...a),
}));

vi.mock("../api/client", () => ({
  api: {
    castStatus: vi.fn(),
    castPlay: vi.fn(),
    castStop: vi.fn(),
    castPause: vi.fn(),
    castResume: vi.fn(),
    castSeek: vi.fn(),
    castSetVolume: vi.fn(),
    castDevices: vi.fn(),
    castStartDiscovery: vi.fn(),
    castStopDiscovery: vi.fn(),
  },
  streamUrl: (id: number, token: string, start?: number) =>
    `http://server/stream/${id}?token=${token}${start ? `&start=${start}` : ""}`,
  getServerUrl: () => "http://server",
}));

vi.mock("../composables/useToast", () => ({
  showToast: (...a: unknown[]) => toastShow(...a),
  showWarning: vi.fn(),
}));

// happy-dom's media elements don't really play; stub the transport so the
// player store's event listeners fire deterministically.
HTMLMediaElement.prototype.play = vi.fn(function (this: HTMLMediaElement) {
  this.dispatchEvent(new Event("play"));
  return Promise.resolve();
});
HTMLMediaElement.prototype.pause = vi.fn(function (this: HTMLMediaElement) {
  this.dispatchEvent(new Event("pause"));
});
HTMLMediaElement.prototype.load = vi.fn();

const { usePlayerStore } = await import("./player");
const { useSettingsStore } = await import("./settings");

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

beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
  vi.clearAllMocks();
  vi.useFakeTimers();
  issueStreamToken.mockResolvedValue("tok");
  recordPlay.mockResolvedValue(undefined);
  getTracks.mockResolvedValue({ tracks: [], total: 0 });
  getStats.mockResolvedValue({
    track_count: 0,
    album_count: 0,
    artist_count: 0,
    total_duration_secs: 0,
  });
  getCoverBlob.mockResolvedValue(null);
  getRecentPlayHistory.mockResolvedValue([]);
  getMetadata.mockResolvedValue(null);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("play counting (Q20)", () => {
  it("does not count a play at track start", async () => {
    const player = usePlayerStore();
    const t = track({ id: 7 });
    await player.playTrack(t, [t]);
    await nextTick();

    expect(player.isPlaying).toBe(true);
    expect(recordPlay).not.toHaveBeenCalled();
  });

  it("counts a play once the track has been playing for 30 s", async () => {
    const settings = useSettingsStore();
    settings.setDeviceName("Test Device");
    const player = usePlayerStore();
    const t = track({ id: 7 });
    await player.playTrack(t, [t]);
    await nextTick();
    expect(recordPlay).not.toHaveBeenCalled();

    // 29 s — still under the threshold.
    player.positionSecs = 29;
    await nextTick();
    expect(recordPlay).not.toHaveBeenCalled();

    // 30 s — the play counts, attributed to this device's name (Q19).
    player.positionSecs = 30;
    await nextTick();
    expect(recordPlay).toHaveBeenCalledTimes(1);
    expect(recordPlay).toHaveBeenCalledWith(7, "Test Device");
  });

  it("counts a play only once per track", async () => {
    const player = usePlayerStore();
    const t = track({ id: 7 });
    await player.playTrack(t, [t]);
    await nextTick();

    player.positionSecs = 45;
    await nextTick();
    player.positionSecs = 60;
    await nextTick();

    expect(recordPlay).toHaveBeenCalledTimes(1);
  });

  it("does not count while paused", async () => {
    const player = usePlayerStore();
    const t = track({ id: 7 });
    await player.playTrack(t, [t]);
    await nextTick();

    player.pauseForCast(); // pauses the element and clears isPlaying
    await nextTick();
    player.positionSecs = 45;
    await nextTick();

    expect(recordPlay).not.toHaveBeenCalled();
  });
});

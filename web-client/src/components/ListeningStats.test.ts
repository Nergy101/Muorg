import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick } from "vue";
import { createPinia } from "pinia";
import ListeningStats from "./ListeningStats.vue";
import type {
  ClockBucket,
  ListeningSummary,
  ListeningWeek,
  TopAlbumStat,
  TopArtistStat,
  TopTrackStat,
} from "../types";

/**
 * The panel is self-fetching, so the suite mocks the api/catalog wrappers and
 * asserts three things: the fetched numbers render, switching the period
 * re-fetches with the new window, and a failed fetch shows the retry state
 * instead of a half-drawn panel.
 */

const mocks = vi.hoisted(() => ({
  getListeningSummary: vi.fn(),
  getListeningTime: vi.fn(),
  getPlayClock: vi.fn(),
  getTopTracksByTime: vi.fn(),
  getTopArtistsByTime: vi.fn(),
  getTopAlbumsByTime: vi.fn(),
  listProfiles: vi.fn(),
}));

vi.mock("../api/catalog", () => ({ ...mocks }));
vi.mock("./MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));

const summary: ListeningSummary = {
  days: 30,
  plays: 12,
  listening_secs: 3900, // 1h 5m
  unique_tracks: 7,
  unique_artists: 4,
  unique_albums: 3,
};
const tracks: TopTrackStat[] = [
  { track_id: 1, title: "So What", artist: "Miles Davis", album: "Kind of Blue", plays: 3, listening_secs: 1800 },
];
const artists: TopArtistStat[] = [
  { artist: "Miles Davis", plays: 9, listening_secs: 3300 },
];
const albums: TopAlbumStat[] = [
  { album: "Kind of Blue", album_artist: "Miles Davis", plays: 9, listening_secs: 3300 },
];
const weeks: ListeningWeek[] = Array.from({ length: 12 }, (_, i) => ({
  week_start: 1_700_000_000 + i * 7 * 86_400,
  plays: i,
  listening_secs: i * 600,
}));
const clock: ClockBucket[] = [
  { hour: 8, plays: 2, listening_secs: 400 },
  { hour: 21, plays: 5, listening_secs: 1200 },
];

function resolveAll(): void {
  mocks.listProfiles.mockResolvedValue([{ id: 1, name: "You", is_default: true }]);
  mocks.getListeningSummary.mockResolvedValue(summary);
  mocks.getTopTracksByTime.mockResolvedValue(tracks);
  mocks.getTopArtistsByTime.mockResolvedValue(artists);
  mocks.getTopAlbumsByTime.mockResolvedValue(albums);
  mocks.getPlayClock.mockResolvedValue(clock);
  mocks.getListeningTime.mockResolvedValue(weeks);
}

/** Drain the microtask queue so the component's fetches land before asserting. */
async function flush(): Promise<void> {
  for (let i = 0; i < 6; i++) await Promise.resolve();
  await nextTick();
  await nextTick();
}

function render() {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(ListeningStats);
  app.use(createPinia());
  app.mount(root);
  return {
    root,
    done() {
      app.unmount();
      root.remove();
    },
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  resolveAll();
});

describe("ListeningStats", () => {
  it("renders the server's listening totals, top rows and weekly graph", async () => {
    const view = render();
    await flush();
    const text = view.root.textContent ?? "";

    expect(mocks.getListeningSummary).toHaveBeenCalledWith(30, undefined);
    expect(mocks.getPlayClock).toHaveBeenCalledWith(30, undefined);
    // Window totals.
    expect(text).toContain("12"); // plays
    expect(text).toContain("1h 5m"); // listening time
    expect(text).toContain("7"); // unique tracks
    // Top lists, ranked by listening time.
    expect(text).toContain("So What");
    expect(text).toContain("Miles Davis");
    expect(text).toContain("Kind of Blue");
    // Weekly rollup panel is up too.
    expect(text).toContain("Listening time per week");
    expect(mocks.getListeningTime).toHaveBeenCalledWith(12, undefined);
    view.done();
  });

  it("re-fetches with the new window when the period is switched", async () => {
    const view = render();
    await flush();

    const buttons = Array.from(view.root.querySelectorAll<HTMLButtonElement>("button"));
    const ninety = buttons.find((b) => b.textContent?.trim() === "90d");
    expect(ninety).toBeDefined();
    ninety!.click();
    await flush();

    expect(mocks.getListeningSummary).toHaveBeenCalledWith(90, undefined);
    expect(mocks.getTopTracksByTime).toHaveBeenCalledWith(5, 90, undefined);
    // The weekly graph is window-independent and must not refetch.
    expect(mocks.getListeningTime).toHaveBeenCalledTimes(1);
    view.done();
  });

  it("shows a retry state when the fetch fails, and recovers", async () => {
    const rejection = Object.assign(new Error("boom"), {});
    mocks.getListeningSummary.mockRejectedValue(rejection);
    mocks.getTopTracksByTime.mockRejectedValue(rejection);
    mocks.getTopArtistsByTime.mockRejectedValue(rejection);
    mocks.getTopAlbumsByTime.mockRejectedValue(rejection);
    mocks.getPlayClock.mockRejectedValue(rejection);
    mocks.getListeningTime.mockRejectedValue(rejection);

    const view = render();
    await flush();
    expect(view.root.textContent).toContain("Listening statistics are unavailable.");
    expect(view.root.textContent).not.toContain("So What");

    const retry = [...view.root.querySelectorAll<HTMLButtonElement>("button")].find((b) =>
      b.textContent?.includes("Try again"),
    );
    expect(retry).toBeDefined();
    resolveAll(); // the retry succeeds: the server is back
    retry!.click();
    await flush();

    expect(view.root.textContent).toContain("So What");
    view.done();
  });

  it("renders an empty-lists placeholder when nothing was listened", async () => {
    mocks.getListeningSummary.mockResolvedValue({
      days: 30, plays: 0, listening_secs: 0, unique_tracks: 0, unique_artists: 0, unique_albums: 0,
    });
    mocks.getTopTracksByTime.mockResolvedValue([]);
    mocks.getTopArtistsByTime.mockResolvedValue([]);
    mocks.getTopAlbumsByTime.mockResolvedValue([]);
    mocks.getPlayClock.mockResolvedValue([]);
    mocks.getListeningTime.mockResolvedValue([]);

    const view = render();
    await flush();
    const text = view.root.textContent ?? "";
    expect(text).toContain("Nothing listened yet.");
    view.done();
  });
});

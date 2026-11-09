import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive } from "vue";
import StatsView from "./StatsView.vue";
import type { CatalogTrack } from "../types";

const mocks = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
  loadLibrary: vi.fn(),
  lib: {
    tracks: [] as CatalogTrack[],
    stats: null as null | { track_count: number; album_count: number; artist_count: number; total_duration_secs: number },
    loading: false,
    keyForTrack: (t: CatalogTrack) => `${t.album ?? ""}|${t.album_artist ?? t.artist ?? ""}`,
    loadLibrary: (...args: unknown[]) => mocks.loadLibrary(...args),
  },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ back: mocks.back, push: mocks.push }),
}));
vi.mock("../stores/library", () => ({ useLibraryStore: () => reactive(mocks.lib) }));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));
vi.mock("@shared/components/stats/GenrePieChart.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));
vi.mock("@shared/components/stats/YearLineChart.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));
vi.mock("@shared/components/stats/RatingChart.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));
vi.mock("@shared/components/stats/TopArtistsChart.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));
vi.mock("@shared/components/stats/MetadataHealthChart.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));
vi.mock("@shared/components/stats/FormatDonutChart.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));
vi.mock("@shared/components/stats/DurationHistogram.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));
vi.mock("@shared/components/stats/TopPlayedChart.vue", () => ({
  default: { props: ["tracks"], emits: ["view-field"], template: '<div class="chart"></div>' },
}));

function track(id: number, title: string, artist: string | null, album: string | null, duration_secs = 60): CatalogTrack {
  return { id, title, artist, album, album_artist: artist, duration_secs } as CatalogTrack;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.lib.tracks = [
    track(1, "A", "Miles Davis", "Kind of Blue", 300),
    track(2, "B", "Miles Davis", "Kind of Blue", 300),
    track(3, "C", "New Order", "Power", 600),
  ];
  mocks.lib.stats = null;
  mocks.lib.loading = false;
});

function renderStatsView() {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(StatsView);
  app.mount(root);
  return {
    root,
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

describe("StatsView", () => {
  it("derives totals from the catalog when /api/stats has not landed", async () => {
    const view = renderStatsView();
    await nextTick();
    const text = view.root.textContent ?? "";
    expect(text).toContain("3 tracks in the catalog");
    expect(text).toContain("2"); // artists
    expect(text).toContain("2"); // albums
    expect(text).toContain("20m"); // 300+300+600 = 1200s
    view.unmount();
  });

  it("prefers server stats totals when available", async () => {
    mocks.lib.stats = { track_count: 42, album_count: 7, artist_count: 5, total_duration_secs: 3660 };
    const view = renderStatsView();
    await nextTick();
    const text = view.root.textContent ?? "";
    expect(text).toContain("42");
    expect(text).toContain("1h 1m");
    view.unmount();
  });

  it("refresh button reloads the library and back navigates back", async () => {
    mocks.loadLibrary.mockResolvedValue(undefined);
    const view = renderStatsView();
    await nextTick();

    view.root.querySelector<HTMLButtonElement>('button[aria-label="Refresh statistics"]')!.click();
    await nextTick();
    expect(mocks.loadLibrary).toHaveBeenCalled();

    view.root.querySelector<HTMLButtonElement>('button[aria-label="Back"]')!.click();
    expect(mocks.back).toHaveBeenCalled();
    view.unmount();
  });
});

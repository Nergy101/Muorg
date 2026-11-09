import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive } from "vue";
import ReportsView from "./ReportsView.vue";
import type { CatalogTrack } from "../types";

const mocks = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
  loadLibrary: vi.fn(),
  lib: {
    tracks: [] as CatalogTrack[],
    loading: false,
    loadLibrary: (...args: unknown[]) => mocks.loadLibrary(...args),
  },
  settings: {
    missingMetadataFields: ["title", "artist", "album"] as string[],
  },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ back: mocks.back, push: mocks.push }),
}));
vi.mock("../stores/library", () => ({ useLibraryStore: () => reactive(mocks.lib) }));
vi.mock("../stores/settings", () => ({ useSettingsStore: () => reactive(mocks.settings) }));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));

function track(id: number, title: string | null, artist: string | null, album: string | null): CatalogTrack {
  return { id, title, artist, album, album_artist: artist } as CatalogTrack;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.loadLibrary.mockResolvedValue(undefined);
  mocks.lib.tracks = [
    track(1, "A", "X", "Y"),
    track(2, null, "X", "Y"), // missing title
    track(3, "A", "X", "Y"), // duplicate of 1
    track(4, null, null, null), // missing everything
  ];
  mocks.lib.loading = false;
});

function renderReportsView() {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(ReportsView);
  app.component("RouterLink", { props: { to: { type: Object, required: true } }, template: "<a><slot /></a>" });
  app.mount(root);
  return {
    root,
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

describe("ReportsView", () => {
  it("lists all five reports with count badges only on the problem reports", async () => {
    const view = renderReportsView();
    await nextTick();
    const text = view.root.textContent ?? "";
    for (const label of ["Missing metadata", "Duplicates", "Missing album cover", "Recently played", "Most played"]) {
      expect(text).toContain(label);
    }
    // 2 tracks missing metadata, 1 duplicate pair, 4 missing cover.
    const badges = [...view.root.querySelectorAll("a span.rounded-full")].map((s) => s.textContent?.trim());
    expect(badges).toEqual(["2", "1", "4"]);
    view.unmount();
  });

  it("shows the catalog size in the subtitle and refreshes via the library", async () => {
    const view = renderReportsView();
    await nextTick();
    expect(view.root.textContent).toContain("4 tracks in the catalog");

    view.root.querySelector<HTMLButtonElement>('button[aria-label="Refresh reports"]')!.click();
    await nextTick();
    expect(mocks.loadLibrary).toHaveBeenCalled();

    view.root.querySelector<HTMLButtonElement>('button[aria-label="Back"]')!.click();
    expect(mocks.back).toHaveBeenCalled();
    view.unmount();
  });
});

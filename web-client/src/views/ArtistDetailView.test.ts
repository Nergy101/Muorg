import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive } from "vue";
import ArtistDetailView from "./ArtistDetailView.vue";
import type { CatalogTrack } from "../types";

const mocks = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
  playTrack: vi.fn(),
  provideVisibleIds: vi.fn(),
  lib: {
    tracks: [] as CatalogTrack[],
    keyForTrack: (t: CatalogTrack) => `${t.album ?? ""}|${t.album_artist ?? t.artist ?? ""}`,
    albumByKey: (key: string) => {
      const [album, artist] = key.split("|");
      return { key, album, artist, coverUrl: null } as never;
    },
  },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ back: mocks.back, push: mocks.push }),
}));
vi.mock("../stores/library", () => ({ useLibraryStore: () => reactive(mocks.lib) }));
vi.mock("../stores/player", () => ({
  usePlayerStore: () => ({ playTrack: mocks.playTrack }),
}));
vi.mock("../stores/selection", () => ({
  useSelectionStore: () => ({ selecting: false, has: () => false, click: () => true }),
}));
vi.mock("../composables/useScrollMemory", () => ({ useScrollMemory: () => ({}) }));
vi.mock("../composables/useSelectableList", () => ({
  provideVisibleIds: (...args: unknown[]) => mocks.provideVisibleIds(...args),
}));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));
vi.mock("../components/AlbumCard.vue", () => ({
  default: { props: ["item"], emits: ["open"], template: '<div class="album-card">{{ item.album }}</div>' },
}));
vi.mock("../components/TrackListRow.vue", () => ({
  default: {
    props: ["track"],
    emits: ["play", "select", "actions"],
    template: '<div class="row" @click="$emit(\'play\', track)">{{ track.title }}</div>',
  },
}));
vi.mock("../components/TrackActionsSheet.vue", () => ({
  default: { props: ["open", "track"], emits: ["close"], template: '<div v-if="open" class="sheet"></div>' },
}));

function track(id: number, title: string, artist: string | null, album: string | null, album_artist: string | null = null): CatalogTrack {
  return { id, title, artist, album, album_artist } as CatalogTrack;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.lib.tracks = [
    track(1, "So What", "Miles Davis", "Kind of Blue", "Miles Davis"),
    track(2, "Freddie Freeloader", "Miles Davis", "Kind of Blue", "Miles Davis"),
    track(3, "Blue in Green", "Miles Davis", "Kind of Blue", "Miles Davis"),
    track(4, "Flamenco Sketches", "Miles Davis", "Sketches of Spain", "Miles Davis"),
    track(5, "Cantaloupe Island", "Herbie Hancock", "Empyrean", "Herbie Hancock"),
    track(6, "feat. Miles", "Herbie Hancock", "Empyrean", "Miles Davis"), // album artist match
  ];
});

function renderArtistView(name: string) {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(ArtistDetailView, { name });
  app.mount(root);
  return {
    root,
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

describe("ArtistDetailView", () => {
  it("shows only the artist's tracks, matched on artist or album artist, grouped into albums", async () => {
    const view = renderArtistView("Miles Davis");
    await nextTick();
    expect(view.root.querySelector("h1")?.textContent).toBe("Miles Davis");
    const rows = [...view.root.querySelectorAll(".row")].map((r) => r.textContent);
    expect(rows).toEqual(["So What", "Freddie Freeloader", "Blue in Green", "Flamenco Sketches", "feat. Miles"]);
    const albums = [...view.root.querySelectorAll(".album-card")].map((a) => a.textContent);
    // "Empyrean" appears because track 6 credits Miles Davis as album artist.
    expect(albums).toEqual(["Empyrean", "Kind of Blue", "Sketches of Spain"]);
    view.unmount();
  });

  it("is case-insensitive on the artist name", async () => {
    const view = renderArtistView("miles davis");
    await nextTick();
    expect(view.root.querySelectorAll(".row").length).toBe(5);
    view.unmount();
  });

  it("shows an empty state with a back-to-library action when there are no tracks", async () => {
    const view = renderArtistView("Nobody");
    await nextTick();
    expect(view.root.textContent).toContain("No tracks by this artist");
    const btn = [...view.root.querySelectorAll("button")].find((b) => b.textContent?.includes("Back to library"))!;
    btn.click();
    expect(mocks.push).toHaveBeenCalledWith({ name: "library" });
    view.unmount();
  });

  it("plays a track with the artist's track list as the queue", async () => {
    const view = renderArtistView("Herbie Hancock");
    await nextTick();
    view.root.querySelector<HTMLElement>(".row")!.click();
    await nextTick();
    expect(mocks.playTrack).toHaveBeenCalledWith(
      expect.objectContaining({ id: 5 }),
      expect.arrayContaining([expect.objectContaining({ id: 5 }), expect.objectContaining({ id: 6 })]),
    );
    view.unmount();
  });
});

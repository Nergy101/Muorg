import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive } from "vue";
import ArtistDetailView from "./ArtistDetailView.vue";
import { albumKeyFor, compareTrackOrder, groupAlbums } from "../stores/library";
import type { AlbumGridItem, CatalogTrack } from "../types";

const mocks = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
  playTrack: vi.fn(),
  toggleShuffle: vi.fn(),
  provideVisibleIds: vi.fn(),
  lib: {
    loading: false,
    tracks: [] as CatalogTrack[],
    keyForTrack: (_t: CatalogTrack): string => "",
    albumByKey: (_key: string): AlbumGridItem | null => null,
    tracksForAlbum: (_key: string): CatalogTrack[] => [],
  },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ back: mocks.back, push: mocks.push }),
}));
vi.mock("../stores/library", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../stores/library")>()),
  useLibraryStore: () => reactive(mocks.lib),
}));
vi.mock("../stores/player", () => ({
  usePlayerStore: () => ({ playTrack: mocks.playTrack, toggleShuffle: mocks.toggleShuffle, shuffleEnabled: false }),
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
  default: { props: ["item"], emits: ["open", "actions"], template: '<div class="album-card" @click="$emit(\'open\')">{{ item.album }}</div>' },
}));
vi.mock("../components/AlbumActionsHost.vue", () => ({
  default: { props: ["item"], emits: ["close"], template: "<div></div>" },
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

let nextId = 1;
function track(
  title: string,
  artist: string | null,
  album: string | null,
  opts: Partial<CatalogTrack> = {},
): CatalogTrack {
  return { id: nextId++, title, artist, album, album_artist: null, duration_secs: 240, ...opts } as CatalogTrack;
}

function useCatalog(tracks: CatalogTrack[]): void {
  const albums = groupAlbums(tracks);
  const keyById = new Map<number, string>();
  for (const [key, item] of albums) for (const id of item.trackIds) keyById.set(id, key);
  const byId = new Map(tracks.map((t) => [t.id, t]));
  mocks.lib.tracks = tracks;
  mocks.lib.keyForTrack = (t) => keyById.get(t.id) ?? albumKeyFor(t);
  mocks.lib.albumByKey = (key) => albums.get(key) ?? null;
  mocks.lib.tracksForAlbum = (key) =>
    (albums.get(key)?.trackIds ?? []).map((id) => byId.get(id)!).sort(compareTrackOrder);
}

beforeEach(() => {
  vi.clearAllMocks();
  nextId = 1;
  mocks.lib.loading = false;
  useCatalog([
    ...Array.from({ length: 8 }, (_, i) =>
      track(`Kind ${i + 1}`, "Miles Davis", "Kind of Blue", { album_artist: "Miles Davis", year: 1959, track_number: i + 1 }),
    ),
    ...Array.from({ length: 4 }, (_, i) => track(`Four ${i + 1}`, "Miles Davis", "Four", { track_number: i + 1 })),
    track("So What (Radio Edit)", "Miles Davis", "So What - Single"),
    track("Guest Spot", "Herbie Hancock", "Empyrean", { featuring: "Miles Davis" }),
    track("Cantaloupe Island", "Herbie Hancock", "Empyrean"),
    track("Untagged Jam", "Miles Davis", null),
  ]);
});

function renderArtistView(name: string) {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(ArtistDetailView, { name });
  app.mount(root);
  return {
    root,
    section(id: string) {
      const el = root.querySelector(`[data-section="${id}"]`);
      return el ? [...el.querySelectorAll(".album-card, .row")].map((n) => n.textContent) : null;
    },
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

describe("ArtistDetailView", () => {
  it("splits the artist's releases into albums, EPs, singles, appears-on and loose tracks", async () => {
    const view = renderArtistView("Miles Davis");
    await nextTick();
    expect(view.root.querySelector("h1")?.textContent).toBe("Miles Davis");
    expect(view.section("albums")).toEqual(["Kind of Blue"]);
    expect(view.section("eps")).toEqual(["Four"]);
    expect(view.section("singles")).toEqual(["So What - Single"]);
    expect(view.section("appears-on")).toEqual(["Empyrean"]);
    expect(view.section("loose")).toEqual(["Untagged Jam"]);
    expect(view.root.textContent).toContain("1 album · 1 EP · 1 single · 15 tracks");
    view.unmount();
  });

  it("hides sections the artist has nothing in", async () => {
    const view = renderArtistView("Herbie Hancock");
    await nextTick();
    expect(view.section("albums")).toBeNull();
    expect(view.section("loose")).toBeNull();
    expect(view.section("singles")).toEqual(["Empyrean"]);
    view.unmount();
  });

  it("is case-insensitive on the artist name", async () => {
    const view = renderArtistView("miles davis");
    await nextTick();
    expect(view.section("albums")).toEqual(["Kind of Blue"]);
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

  it("plays only the artist's credited tracks, release by release, then the loose ones", async () => {
    const view = renderArtistView("Miles Davis");
    await nextTick();
    view.root.querySelector<HTMLButtonElement>('[aria-label="Play artist"]')!.click();
    const [first, queue] = mocks.playTrack.mock.calls[0] as [CatalogTrack, CatalogTrack[]];
    expect(first.title).toBe("Kind 1");
    const titles = queue.map((t) => t.title);
    expect(titles).toHaveLength(15);
    expect(titles.slice(0, 2)).toEqual(["Kind 1", "Kind 2"]);
    expect(titles).toContain("Guest Spot");
    expect(titles).not.toContain("Cantaloupe Island");
    expect(titles.at(-1)).toBe("Untagged Jam");
    view.unmount();
  });

  it("plays a loose track with the loose tracks as the queue", async () => {
    const view = renderArtistView("Miles Davis");
    await nextTick();
    view.root.querySelector<HTMLElement>('[data-section="loose"] .row')!.click();
    expect(mocks.playTrack).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Untagged Jam" }),
      [expect.objectContaining({ title: "Untagged Jam" })],
    );
    view.unmount();
  });

  it("opens an album from its card", async () => {
    const view = renderArtistView("Miles Davis");
    await nextTick();
    view.root.querySelector<HTMLElement>('[data-section="eps"] .album-card')!.click();
    expect(mocks.push).toHaveBeenCalledWith({ name: "album", params: { albumKey: "four|||" } });
    view.unmount();
  });
});

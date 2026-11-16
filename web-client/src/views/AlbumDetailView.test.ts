import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive } from "vue";
import AlbumDetailView from "./AlbumDetailView.vue";
import { albumKeyFor, groupAlbums, recordAlbumKeyRedirect } from "../stores/library";
import type { AlbumGridItem, CatalogTrack } from "../types";

const mocks = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  routeQuery: {} as Record<string, string>,
  lib: {
    loading: false,
    tracks: [] as CatalogTrack[],
    trackById: new Map<number, CatalogTrack>(),
    coverCache: new Map<number, string>(),
    keyForTrack: (_t: CatalogTrack): string => "",
    albumByKey: (_key: string): AlbumGridItem | null => null,
    tracksForAlbum: (_key: string): CatalogTrack[] => [],
    requestCover: vi.fn(),
  },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ back: mocks.back, push: mocks.push, replace: mocks.replace }),
  useRoute: () => ({ query: mocks.routeQuery }),
}));
vi.mock("../stores/library", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../stores/library")>()),
  useLibraryStore: () => reactive(mocks.lib),
}));
vi.mock("../stores/player", () => ({
  usePlayerStore: () => ({ pendingTrackId: null, currentTrack: null, playTrack: vi.fn() }),
}));
vi.mock("../stores/playlists", () => ({
  usePlaylistStore: () => ({
    playlists: [],
    trackIdSets: new Map(),
    loadTrackIdsForPlaylist: async () => new Set<number>(),
    loadAllTrackIds: async () => {},
  }),
}));
vi.mock("../stores/offline", () => ({
  useOfflineStore: () => ({
    activeAlbumKey: null,
    downloadProgress: { done: 0, total: 0 },
    isAlbumDownloaded: () => false,
    downloadAlbum: vi.fn(),
    removeAlbumDownload: vi.fn(),
  }),
}));
vi.mock("../stores/selection", () => ({
  useSelectionStore: () => ({ selecting: false, has: () => false, click: () => true }),
}));
vi.mock("../composables/useScrollMemory", () => ({ useScrollMemory: () => ({}) }));
vi.mock("../composables/useSelectableList", () => ({ provideVisibleIds: () => {} }));
vi.mock("../composables/useDominantColor", () => ({
  useDominantColor: () => ({ value: null }),
  rawMainColor: () => null,
}));
vi.mock("../composables/useToast", () => ({ showToast: vi.fn(), showWarning: vi.fn() }));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));
vi.mock("../components/MarqueeText.vue", () => ({
  default: { props: ["text"], template: "<span>{{ text }}</span>" },
}));
vi.mock("../components/ArtistLink.vue", () => ({
  default: { props: ["name"], template: "<span>{{ name }}</span>" },
}));
vi.mock("../components/CoverBackdrop.vue", () => ({ default: { template: "<div></div>" } }));
vi.mock("../components/TrackListRow.vue", () => ({
  default: { props: ["track"], template: '<div class="row">{{ track.title }}</div>' },
}));
vi.mock("../components/TrackActionsSheet.vue", () => ({
  default: { props: ["open", "track"], template: '<div v-if="open" class="sheet"></div>' },
}));
vi.mock("../components/BottomSheet.vue", () => ({
  default: {
    props: ["open"],
    emits: ["close"],
    template: '<div v-if="open" class="bottom-sheet"><slot /></div>',
  },
}));
vi.mock("../components/PlaylistPickerSheet.vue", () => ({
  default: { props: ["open"], template: '<div v-if="open"></div>' },
}));

let nextId = 1;
function track(title: string, album: string | null, opts: Partial<CatalogTrack> = {}): CatalogTrack {
  return { id: nextId++, title, artist: "Miles Davis", album, album_artist: "Miles Davis", duration_secs: 240, ...opts } as CatalogTrack;
}

beforeEach(() => {
  vi.clearAllMocks();
  nextId = 1;
  mocks.routeQuery = {};
  const tracks = [
    track("So What", "Kind of Blue", { year: 1959, track_number: 1 }),
    track("Freddie Freeloader", "Kind of Blue", { year: 1959, track_number: 2 }),
  ];
  const albums = groupAlbums(tracks);
  const keyById = new Map<number, string>();
  for (const [key, item] of albums) for (const id of item.trackIds) keyById.set(id, key);
  mocks.lib.tracks = tracks;
  mocks.lib.trackById = new Map(tracks.map((t) => [t.id, t]));
  mocks.lib.keyForTrack = (t) => keyById.get(t.id) ?? albumKeyFor(t);
  mocks.lib.albumByKey = (key) => albums.get(key) ?? null;
  mocks.lib.tracksForAlbum = (key) =>
    (albums.get(key)?.trackIds ?? []).map((id) => mocks.lib.trackById.get(id)!);
});

function renderAlbumView(albumKey: string) {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(AlbumDetailView, { albumKey });
  app.mount(root);
  return {
    root,
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

describe("AlbumDetailView album actions", () => {
  function setDesktopViewport(desktop: boolean): void {
    window.matchMedia = ((query: string) => ({
      matches: desktop && query.includes("1024px"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    })) as unknown as typeof window.matchMedia;
  }

  it("on desktop shows the actions inline and 'Edit album metadata' routes to the album editor", async () => {
    setDesktopViewport(true);
    const key = albumKeyFor(mocks.lib.tracks[0]);
    const view = renderAlbumView(key);
    await nextTick();
    // No 3-dots button on desktop; the actions sit inline in the header row.
    expect(
      [...view.root.querySelectorAll("button")].find(
        (b) => b.getAttribute("aria-label") === "More album options",
      ),
    ).toBeUndefined();
    const edit = [...view.root.querySelectorAll("button")].find(
      (b) => b.getAttribute("aria-label") === "Edit album metadata",
    )!;
    edit.click();
    await nextTick();
    expect(mocks.push).toHaveBeenCalledWith({ name: "album-edit", params: { albumKey: key } });
    view.unmount();
  });

  it("below desktop keeps the 3-dots menu with 'Edit album metadata'", async () => {
    setDesktopViewport(false);
    const key = albumKeyFor(mocks.lib.tracks[0]);
    const view = renderAlbumView(key);
    await nextTick();
    const dots = [...view.root.querySelectorAll("button")].find(
      (b) => b.getAttribute("aria-label") === "More album options",
    )!;
    dots.click();
    await nextTick();
    const edit = [...view.root.querySelectorAll(".bottom-sheet button")].find((b) =>
      b.textContent?.includes("Edit album metadata"),
    );
    expect(edit).toBeTruthy();
    (edit as HTMLElement).click();
    await nextTick();
    expect(mocks.push).toHaveBeenCalledWith({ name: "album-edit", params: { albumKey: key } });
    view.unmount();
  });

  it("forwards a stale pre-rename album URL to the renamed album", async () => {
    const oldKey = albumKeyFor(mocks.lib.tracks[0]);
    const newKey = albumKeyFor({ ...mocks.lib.tracks[0], album_artist: "Tremonti" } as CatalogTrack);
    recordAlbumKeyRedirect(oldKey, newKey);
    const view = renderAlbumView(oldKey);
    await nextTick();
    expect(mocks.replace).toHaveBeenCalledWith({ name: "album", params: { albumKey: newKey } });
    view.unmount();
  });
});

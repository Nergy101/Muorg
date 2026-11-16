import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive } from "vue";
import AlbumEditView from "./AlbumEditView.vue";
import TrackEditView from "./TrackEditView.vue";
import { albumKeyFor, groupAlbums, recordAlbumKeyRedirect } from "../stores/library";
import type { AlbumGridItem, CatalogTrack } from "../types";

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  saveBulk: vi.fn(async (_ids: number[], _update: Record<string, unknown>) => true),
  setCover: vi.fn(async (_ids: number[], _base64: string | null) => true),
  saveSingle: vi.fn(async (_id: number, _update: Record<string, unknown>) => true),
  lib: {
    loading: false,
    tracks: [] as CatalogTrack[],
    trackById: new Map<number, CatalogTrack>(),
    keyForTrack: (_t: CatalogTrack): string => "",
    albumByKey: (_key: string): AlbumGridItem | null => null,
    tracksForAlbum: (_key: string): CatalogTrack[] => [],
  },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace, back: vi.fn() }),
}));
vi.mock("../stores/library", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../stores/library")>()),
  useLibraryStore: () => reactive(mocks.lib),
}));
vi.mock("../stores/settings", () => ({
  useSettingsStore: () => ({ backupBeforeWrite: false }),
}));
vi.mock("../stores/metadata", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../stores/metadata")>()),
  useMetadataStore: () => ({ saveBulk: mocks.saveBulk, saveSingle: mocks.saveSingle, setCover: mocks.setCover }),
}));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));
vi.mock("../components/metadata/GenreCombobox.vue", () => ({
  default: {
    props: ["modelValue"],
    emits: ["update:modelValue", "edited", "clear"],
    template: '<input class="genre" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'edited\')" />',
  },
}));

let nextId = 1;
function track(title: string, album: string | null, opts: Partial<CatalogTrack> = {}): CatalogTrack {
  return { id: nextId++, title, artist: "Miles Davis", album, album_artist: "Miles Davis", duration_secs: 240, ...opts } as CatalogTrack;
}

function useCatalog(tracks: CatalogTrack[]): void {
  mocks.lib.tracks = tracks;
  mocks.lib.trackById = new Map(tracks.map((t) => [t.id, t]));
  // Resolved dynamically from the tracks' CURRENT tags, like the real store, so
  // a rename re-keys the album exactly as in production.
  mocks.lib.keyForTrack = (t) => albumKeyFor(t);
  mocks.lib.albumByKey = (key) => groupAlbums(mocks.lib.tracks).get(key) ?? null;
  mocks.lib.tracksForAlbum = (key) =>
    mocks.lib.tracks.filter((t) => albumKeyFor(t) === key);
  // Mirror the real store: a successful bulk save applies the update in place.
  // Mutate through the reactive proxy so the view's computeds re-evaluate.
  const libProxy = reactive(mocks.lib);
  mocks.saveBulk.mockImplementation(async (ids: number[], update: Record<string, unknown>) => {
    for (const id of ids) {
      const t = libProxy.trackById.get(id);
      if (t) Object.assign(t, update);
    }
    return true;
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  nextId = 1;
  useCatalog([
    track("So What", "Kind of Blue", { year: 1959, genre: "Jazz", track_number: 1 }),
    track("Freddie Freeloader", "Kind of Blue", { year: 1959, genre: "Jazz", track_number: 2 }),
  ]);
});

function render(component: typeof AlbumEditView | typeof TrackEditView, props: Record<string, string>) {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(component, props);
  app.mount(root);
  return {
    root,
    app,
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

function fieldValue(root: HTMLElement, label: string): string {
  const labels = [...root.querySelectorAll("label")];
  const el = labels.find((l) => l.textContent === label);
  const input = el?.parentElement?.querySelector("input");
  return (input as HTMLInputElement | null)?.value ?? "";
}

describe("AlbumEditView", () => {
  it("pre-fills the album fields with the values shared by all tracks", async () => {
    const view = render(AlbumEditView, { albumKey: albumKeyFor(mocks.lib.tracks[0]) });
    await nextTick();
    expect(view.root.querySelector("h1")?.textContent).toBe("Kind of Blue");
    expect(fieldValue(view.root, "Album title")).toBe("Kind of Blue");
    expect(fieldValue(view.root, "Album artist")).toBe("Miles Davis");
    expect(fieldValue(view.root, "Release year")).toBe("1959");
    view.unmount();
  });

  it("lists the album's tracks and routes to the track editor on tap", async () => {
    const view = render(AlbumEditView, { albumKey: albumKeyFor(mocks.lib.tracks[0]) });
    await nextTick();
    const rows = [...view.root.querySelectorAll("button")].filter((b) =>
      ["So What", "Freddie Freeloader"].some((t) => b.textContent?.includes(t)),
    );
    expect(rows).toHaveLength(2);
    rows[0].click();
    await nextTick();
    expect(mocks.push).toHaveBeenCalledWith({
      name: "track-edit",
      params: { trackId: String(mocks.lib.tracks[0].id) },
    });
    view.unmount();
  });

  it("saves only the touched fields to every track of the album", async () => {
    const view = render(AlbumEditView, { albumKey: albumKeyFor(mocks.lib.tracks[0]) });
    await nextTick();
    const labels = [...view.root.querySelectorAll("label")];
    const yearLabel = labels.find((l) => l.textContent === "Release year")!;
    const yearInput = yearLabel.parentElement!.querySelector("input")!;
    yearInput.value = "1960";
    yearInput.dispatchEvent(new Event("input"));
    await nextTick();
    const save = [...view.root.querySelectorAll("button")].find((b) => b.textContent?.includes("Save"))!;
    save.click();
    await nextTick();
    // One batch call carries every track id — the batch endpoint writes them
    // all in a single request.
    await vi.waitFor(() => expect(mocks.saveBulk).toHaveBeenCalledTimes(1));
    const [ids, update] = mocks.saveBulk.mock.calls[0];
    expect(ids).toEqual(mocks.lib.tracks.map((t) => t.id));
    expect(update).toEqual({ year: 1960 });
    view.unmount();
  });

  it("applies a changed cover to every track of the album", async () => {
    const view = render(AlbumEditView, { albumKey: albumKeyFor(mocks.lib.tracks[0]) });
    await nextTick();
    // Stage a cover via the crop-apply path (data URL gets stripped to raw base64).
    const setupState = (view.app as unknown as { _instance: { setupState: Record<string, unknown> } })._instance.setupState;
    (setupState.onCropApply as (d: string) => void)("data:image/jpeg;base64,QUJD");
    await nextTick();
    const save = [...view.root.querySelectorAll("button")].find((b) => b.textContent?.includes("Save"))!;
    save.click();
    await vi.waitFor(() =>
      expect(mocks.saveBulk.mock.calls.some(([, u]) => "picture_base64" in (u as object))).toBe(true),
    );
    // The cover goes out in a single batch call carrying every track id, so
    // every track of the album gets it.
    const coverCall = mocks.saveBulk.mock.calls.find(([, u]) => "picture_base64" in (u as object))!;
    expect(coverCall[0]).toEqual(mocks.lib.tracks.map((t) => t.id));
    expect((coverCall[1] as Record<string, unknown>).picture_base64).toBe("QUJD");
    view.unmount();
  });

  it("stays on the editor with the renamed album after saving a new title/artist", async () => {
    const view = render(AlbumEditView, { albumKey: albumKeyFor(mocks.lib.tracks[0]) });
    await nextTick();
    const labels = [...view.root.querySelectorAll("label")];
    const titleInput = labels.find((l) => l.textContent === "Album title")!.parentElement!.querySelector("input")!;
    titleInput.value = "Blue in Green";
    titleInput.dispatchEvent(new Event("input"));
    await nextTick();
    const save = [...view.root.querySelectorAll("button")].find((b) => b.textContent?.includes("Save"))!;
    save.click();
    await nextTick();
    await vi.waitFor(() => expect(mocks.saveBulk).toHaveBeenCalled());
    await nextTick();
    // The album key changed with the title, so the URL is rewritten to the new
    // key and the page still shows the (renamed) album instead of "not found".
    const expectedKey = albumKeyFor({ ...mocks.lib.tracks[0], album: "Blue in Green" } as CatalogTrack);
    expect(mocks.replace).toHaveBeenCalledWith({ name: "album-edit", params: { albumKey: expectedKey } });
    expect(view.root.textContent).not.toContain("Album not found");
    expect(view.root.querySelector("h1")?.textContent).toBe("Blue in Green");
    expect(view.root.textContent).toContain("Blue in Green");
    // The track list still resolves by id.
    expect(view.root.textContent).toContain("So What");
    view.unmount();
  });

  it("forwards a stale pre-rename album key to the renamed album's editor", async () => {
    const oldKey = albumKeyFor({ ...mocks.lib.tracks[0], album: "Stale Name", album_artist: "Someone" } as CatalogTrack);
    const newKey = albumKeyFor({ ...mocks.lib.tracks[0], album: "Fresh Name", album_artist: "Someone" } as CatalogTrack);
    recordAlbumKeyRedirect(oldKey, newKey);
    const view = render(AlbumEditView, { albumKey: oldKey });
    await nextTick();
    expect(mocks.replace).toHaveBeenCalledWith({ name: "album-edit", params: { albumKey: newKey } });
    view.unmount();
  });
});

describe("TrackEditView", () => {
  it("pre-fills every field from the track and saves through the single-track endpoint", async () => {
    const t = mocks.lib.tracks[0];
    const view = render(TrackEditView, { trackId: String(t.id) });
    await nextTick();
    expect(view.root.querySelector("h1")?.textContent).toBe("Edit track");
    expect(fieldValue(view.root, "Title")).toBe("So What");
    expect(fieldValue(view.root, "Track #")).toBe("1");
    const titleLabel = [...view.root.querySelectorAll("label")].find((l) => l.textContent === "Title")!;
    const titleInput = titleLabel.parentElement!.querySelector("input")!;
    titleInput.value = "So What (Remaster)";
    titleInput.dispatchEvent(new Event("input"));
    await nextTick();
    const save = [...view.root.querySelectorAll("button")].find((b) => b.textContent?.includes("Save"))!;
    save.click();
    await nextTick();
    await vi.waitFor(() => expect(mocks.saveSingle).toHaveBeenCalled());
    const [id, update] = mocks.saveSingle.mock.calls[0];
    expect(id).toBe(t.id);
    // Single-track saves write the full form; the untouched fields round-trip.
    expect(update).toMatchObject({ title: "So What (Remaster)", album: "Kind of Blue", year: 1959 });
    view.unmount();
  });

  it("shows a not-found state for an unknown track id", async () => {
    const view = render(TrackEditView, { trackId: "9999" });
    await nextTick();
    expect(view.root.textContent).toContain("Track not found");
    view.unmount();
  });
});

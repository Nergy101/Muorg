import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive } from "vue";
import SearchView from "./SearchView.vue";
import { searchFocusSignal } from "../composables/useSearchFocus";
import type { CatalogTrack, SearchHistoryEntry } from "../types";

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  playTrack: vi.fn(),
  provideVisibleIds: vi.fn(),
  settings: {
    searchHistory: [] as SearchHistoryEntry[],
    recentSearchResultIds: [] as number[],
    addSearch: vi.fn(),
    addArtistSearch: vi.fn(),
    addSearchResult: vi.fn(),
    clearSearchHistory: vi.fn(),
    clearSearchResults: vi.fn(),
  },
  lib: {
    tracks: [] as CatalogTrack[],
    loading: false,
    keyForTrack: (t: CatalogTrack) => `${t.album ?? ""}|${t.album_artist ?? ""}`,
  },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => ({ query: {} }),
}));
vi.mock("../stores/library", () => ({ useLibraryStore: () => reactive(mocks.lib) }));
vi.mock("../stores/player", () => ({
  usePlayerStore: () => ({ currentTrack: null, pendingTrackId: null, playTrack: mocks.playTrack }),
}));
vi.mock("../stores/settings", () => ({ useSettingsStore: () => reactive(mocks.settings) }));
vi.mock("../stores/selection", () => ({
  useSelectionStore: () => ({ selecting: false, has: () => false, click: () => true }),
}));
vi.mock("../composables/useSelectableList", () => ({
  provideVisibleIds: (...args: unknown[]) => mocks.provideVisibleIds(...args),
}));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));
vi.mock("../components/EmptyState.vue", () => ({
  default: { props: ["title", "body"], template: '<div class="empty">{{ title }}</div>' },
}));
vi.mock("../components/TrackListRow.vue", () => ({
  default: {
    props: ["track", "subtitle"],
    emits: ["play", "select", "actions"],
    template: '<div class="row" @click="$emit(\'play\', track)">{{ track.title }}</div>',
  },
}));
vi.mock("../components/TrackActionsSheet.vue", () => ({
  default: { props: ["open", "track"], emits: ["close"], template: "<div v-if=\"open\" class=\"sheet\"></div>" },
}));
vi.mock("../components/SearchResultCardGrid.vue", () => ({
  default: {
    props: ["cards"],
    emits: ["open"],
    template: '<div class="card-grid">{{ cards.length }}</div>',
  },
}));

function track(id: number, title: string, artist: string | null = null, album: string | null = null): CatalogTrack {
  return { id, title, artist, album, album_artist: null, path: `/music/${title}.flac` } as CatalogTrack;
}

async function flushDebounce(): Promise<void> {
  await new Promise((r) => setTimeout(r, 300));
  await nextTick();
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.settings.searchHistory = [];
  mocks.settings.recentSearchResultIds = [];
  mocks.lib.tracks = [
    track(1, "Blue in Green", "Miles Davis", "Kind of Blue"),
    track(2, "So What", "Miles Davis", "Kind of Blue"),
    track(3, "Blue Monday", "New Order", "Power, Corruption & Lies"),
  ];
  mocks.lib.loading = false;
});

function renderSearchView() {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(SearchView);
  app.mount(root);
  return {
    root,
    input: () => root.querySelector<HTMLInputElement>("input.search-input")!,
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

async function type(view: ReturnType<typeof renderSearchView>, text: string): Promise<void> {
  const input = view.input();
  input.value = text;
  input.dispatchEvent(new Event("input", { bubbles: true }));
  await nextTick();
  await flushDebounce();
}

describe("SearchView", () => {
  it("shows recent search chips when the query is empty and re-runs a chip on click", async () => {
    mocks.settings.searchHistory = [
      { type: "query", value: "miles" },
      { type: "query", value: "blue" },
    ];
    const view = renderSearchView();
    await nextTick();

    const chips = [...view.root.querySelectorAll("button")].filter((b) => b.textContent?.trim() === "miles");
    expect(chips.length).toBe(1);
    chips[0].click();
    await nextTick();
    await flushDebounce();

    expect(view.input().value).toBe("miles");
    expect(mocks.settings.addSearch).toHaveBeenCalledWith("miles");
    view.unmount();
  });

  it("ranks title matches above artist matches and shows no-results state", async () => {
    const view = renderSearchView();
    await type(view, "blue");

    const rows = [...view.root.querySelectorAll(".row")].map((r) => r.textContent);
    // Title hits first (alphabetical on tie), then the album-title hit.
    expect(rows).toEqual(["Blue in Green", "Blue Monday", "So What"]);
    view.unmount();

    const view2 = renderSearchView();
    await type(view2, "miles");
    const rows2 = [...view2.root.querySelectorAll(".row")].map((r) => r.textContent);
    expect(rows2).toEqual(["Blue in Green", "So What"]); // artist hits
    view2.unmount();

    const view3 = renderSearchView();
    await type(view3, "zzz-no-such-thing");
    expect(view3.root.querySelector(".empty")?.textContent).toContain("No results");
    view3.unmount();
  });

  it("records the query on Enter", async () => {
    const view = renderSearchView();
    await type(view, "blue");
    view.input().dispatchEvent(new KeyboardEvent("keyup", { key: "Enter", bubbles: true }));
    await nextTick();
    expect(mocks.settings.addSearch).toHaveBeenCalledWith("blue");
    view.unmount();
  });

  it("focuses the input when the search focus signal bumps (sidebar click / '/' shortcut)", async () => {
    const view = renderSearchView();
    await nextTick();
    expect(document.activeElement).not.toBe(view.input());

    searchFocusSignal.value++;
    await nextTick();
    await nextTick();

    expect(document.activeElement).toBe(view.input());
    view.unmount();
  });

  it("plays a result and records both the query and the selected result", async () => {
    const view = renderSearchView();
    await type(view, "blue");
    view.root.querySelector<HTMLElement>(".row")!.click();
    await nextTick();

    expect(mocks.playTrack).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }), expect.any(Array));
    expect(mocks.settings.addSearchResult).toHaveBeenCalledWith(1);
    expect(mocks.settings.addSearch).toHaveBeenCalledWith("blue");
    view.unmount();
  });

  it("records a selected artist as a recent search and navigates to it", async () => {
    const view = renderSearchView();
    await type(view, "miles");

    const card = view.root.querySelector<HTMLElement>('[aria-label="Browse artist Miles Davis"]');
    expect(card).not.toBeNull();
    card!.click();
    await nextTick();

    expect(mocks.settings.addArtistSearch).toHaveBeenCalledWith("Miles Davis");
    expect(mocks.push).toHaveBeenCalledWith({ name: "artist", params: { name: "Miles Davis" } });
    view.unmount();
  });

  it("renders a recent artist entry as a large artist card when the query is empty", async () => {
    mocks.settings.searchHistory = [{ type: "artist", value: "Miles Davis" }];
    const view = renderSearchView();
    await nextTick();

    const card = view.root.querySelector('[aria-label="Browse artist Miles Davis"]');
    expect(card).not.toBeNull();
    // Title inside the card on the scrim, plus the artist's total song count.
    expect(card!.textContent).toContain("Miles Davis");
    expect(card!.querySelector(".scrim-label")?.textContent).toContain("2");
    view.unmount();
  });

  it("combines recent searches and recent results into one section with a single Clear all", async () => {
    mocks.settings.searchHistory = [
      { type: "artist", value: "Miles Davis" },
      { type: "query", value: "blue" },
    ];
    mocks.settings.recentSearchResultIds = [1];
    const view = renderSearchView();
    await nextTick();

    // One section header, one Clear all covering both stores.
    const clearButtons = [...view.root.querySelectorAll("button")].filter(
      (b) => b.textContent?.trim() === "Clear all",
    );
    expect(clearButtons.length).toBe(1);
    // Artist card, query chip, and recent track row all present together.
    expect(view.root.querySelector('[aria-label="Browse artist Miles Davis"]')).not.toBeNull();
    expect([...view.root.querySelectorAll("button")].some((b) => b.textContent?.trim() === "blue")).toBe(true);
    expect(view.root.querySelectorAll(".row").length).toBe(1);

    clearButtons[0].click();
    await nextTick();
    expect(mocks.settings.clearSearchHistory).toHaveBeenCalled();
    expect(mocks.settings.clearSearchResults).toHaveBeenCalled();
    view.unmount();
  });

  it("reacts to the layout toggle: recent results and live results switch between rows and cards", async () => {
    mocks.settings.recentSearchResultIds = [1, 2];
    const view = renderSearchView();
    await nextTick();

    // List layout (default): recent results render as track rows.
    expect(view.root.querySelectorAll(".row").length).toBe(2);
    expect(view.root.querySelector(".card-grid")).toBeNull();

    // Toggle to grid: recent results render as cards instead.
    const toggle = view.root.querySelector<HTMLButtonElement>('[aria-label="Show results as album covers"]')!;
    toggle.click();
    await nextTick();
    expect(view.root.querySelector(".card-grid")).not.toBeNull();
    expect(view.root.querySelectorAll(".row").length).toBe(0);

    // Live results follow the same layout.
    await type(view, "blue");
    expect(view.root.querySelector(".card-grid")).not.toBeNull();
    expect(view.root.querySelectorAll(".row").length).toBe(0);

    // Back to list: live results render as rows again.
    view.root.querySelector<HTMLButtonElement>('[aria-label="Show results as a list"]')!.click();
    await nextTick();
    expect(view.root.querySelector(".card-grid")).toBeNull();
    expect(view.root.querySelectorAll(".row").length).toBeGreaterThan(0);
    view.unmount();
  });
});

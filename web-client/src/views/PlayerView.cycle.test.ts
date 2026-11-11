import { describe, expect, it, vi } from "vitest";
import { createApp, nextTick, reactive, ref } from "vue";
import PlayerView from "./PlayerView.vue";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  back: vi.fn(),
  player: null as ReturnType<typeof makePlayer> | null,
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ replace: mocks.replace, back: mocks.back, push: vi.fn() }),
}));
vi.mock("../api/catalog", () => ({ getTrackLyrics: () => Promise.resolve(null) }));
vi.mock("../composables/useDominantColor", () => ({
  useDominantColor: () => ref("128,128,128"),
  isColorBland: () => false,
}));
vi.mock("../composables/usePopoutPlayer", () => ({
  usePopoutPlayer: () => ({ popoutSupported: ref(false), pipActive: ref(false), togglePopout: vi.fn() }),
}));
vi.mock("../composables/useSwipeDown", () => ({
  useSwipeDown: () => ({ progress: ref(0), onPointerdown: vi.fn() }),
}));
vi.mock("../stores/settings", () => ({ useSettingsStore: () => ({ sleepTimerEnabled: false }) }));
vi.mock("../stores/library", () => ({
  useLibraryStore: () => ({}),
  formatDuration: (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`,
}));
vi.mock("../stores/cast", () => ({
  useCastStore: () => ({
    isCasting: false, isPlaying: false, positionSecs: 0,
    pause: vi.fn(), resume: vi.fn(), seek: vi.fn(),
  }),
}));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: '<span aria-hidden="true"></span>' },
}));
vi.mock("../components/MarqueeText.vue", () => ({
  default: { props: ["text"], template: "<span>{{ text }}</span>" },
}));
vi.mock("../components/BottomSheet.vue", () => ({
  default: { props: ["open"], emits: ["close"], template: '<div v-if="open"><slot /></div>' },
}));
vi.mock("../components/ConfirmDialog.vue", () => ({
  default: { props: ["open"], emits: ["close"], template: '<div v-if="open"></div>' },
}));
vi.mock("../components/TrackActionsSheet.vue", () => ({
  default: { props: ["open", "track"], emits: ["close"], template: '<div v-if="open"></div>' },
}));
vi.mock("../components/CoverBackdrop.vue", () => ({
  default: { props: ["coverUrl", "variant", "sharp"], template: "<div></div>" },
}));

function makePlayer() {
  return reactive({
    artFullscreen: false,
    fsShowCover: false, // persisted "hidden" — the case that used to collapse the cycle
    currentTrack: { id: 1, title: "So What", artist: "Miles Davis", album: "Kind of Blue" },
    currentCoverUrl: "http://x/cover.jpg",
    isPlaying: true,
    progress: 0.5,
    positionSecs: 100,
    durationSecs: 200,
    repeatMode: "off",
    shuffleEnabled: false,
    sleepTimerActive: false,
    sleepTimerRemainingMs: 0,
    favorites: new Set<number>(),
    toggleArtFullscreen() { this.artFullscreen = !this.artFullscreen; },
    toggleFsShowCover() { this.fsShowCover = !this.fsShowCover; },
    playPause: vi.fn(), seekTo: vi.fn(), skipNext: vi.fn(), skipPrevious: vi.fn(),
    cycleRepeatMode: vi.fn(), toggleShuffle: vi.fn(), toggleFavorite: vi.fn(),
    cancelSleepTimer: vi.fn(), startSleepTimer: vi.fn(),
  });
}

vi.mock("../stores/player", () => ({ usePlayerStore: () => mocks.player }));

describe("PlayerView cover 3-variant cycle", () => {
  it("cycles normal → FS+cover → FS-hidden → normal", async () => {
    const player = makePlayer();
    mocks.player = player;

    const root = document.createElement("div");
    document.body.append(root);
    const app = createApp(PlayerView);
    app.mount(root);
    await nextTick();

    const cover = root.querySelector('[aria-label="Cycle player display"]') as HTMLElement;
    expect(cover).toBeTruthy();

    const states: string[] = [];
    const snap = () => states.push(`fs=${player.artFullscreen},cover=${player.fsShowCover}`);

    snap();
    cover.click(); await nextTick(); snap();
    cover.click(); await nextTick(); snap();
    cover.click(); await nextTick(); snap();
    cover.click(); await nextTick(); snap();

    app.unmount();
    root.remove();
    expect(states).toEqual([
      "fs=false,cover=false",
      "fs=true,cover=true",
      "fs=true,cover=false",
      "fs=false,cover=false",
      "fs=true,cover=true",
    ]);
  });
});

import { defineStore } from "pinia";
import { computed, ref, watch, watchEffect } from "vue";
import { getMetadata, issueStreamToken, recordPlay } from "../api/catalog";
import type { TrackMetadata } from "../api/catalog";
import { streamUrl } from "../api/client";
import { needsTranscode } from "@shared/formats";
import { computeReplayGainVolume } from "../audio/replayGain";
import { clearPlayHistory, recordPlayHistory, stepBackPlayHistory } from "../audio/playHistory";
import { showToast } from "../composables/useToast";
import { visibleAccentFromRgb } from "../composables/dynamicAccent";
import { useDominantColor } from "../composables/useDominantColor";
import { useCastStore } from "./cast";
import { useLibraryStore } from "./library";
import { useOfflineStore, offlineStreamUrl } from "./offline";
import { usePlaylistStore } from "./playlists";
import { useSettingsStore } from "./settings";
import type { CatalogTrack, RepeatMode } from "../types";

/** How many tracks "Shuffle all" queues up front and tops up with. */
const SHUFFLE_ALL_BATCH = 20;
/** Below this many seconds, Previous restarts the track instead of stepping back. */
const RESTART_THRESHOLD_SECS = 3;
const ERROR_CLEAR_MS = 4000;
/** localStorage key for the queue/position snapshot (NER-305). */
const PLAYER_STATE_KEY = "muorg-player-state";
/** Queue/mode changes are debounced before writing the snapshot. */
const SNAPSHOT_DEBOUNCE_MS = 500;
/** While playing, the position is persisted at most this often. */
const POSITION_SAVE_INTERVAL_MS = 5000;
/** A play is counted after this many seconds of listening (Q20), matching the
 *  desktop and Android apps — not at track start. */
const PLAY_COUNT_THRESHOLD_SECS = 30;
/** Per-track ReplayGain tag cache cap — small, just enough to cover the
 *  current track plus a few steps of history/lookahead. */
const METADATA_CACHE_LIMIT = 50;

interface PlayerStateSnapshot {
  v: 2;
  queue: CatalogTrack[];
  playOrder: number[];
  playOrderPos: number;
  /** The user-queued tracks ("add to queue" / "play next") and the index of
   *  the one currently playing, or -1 when none is active. */
  userQueue: CatalogTrack[];
  userQueuePos: number;
  shuffleEnabled: boolean;
  repeatMode: RepeatMode;
  /** Last known playback position; null once the queue was replaced. */
  position: { trackId: number; secs: number } | null;
}

/** Tolerant reader: any malformed/partial payload simply restores nothing.
 *  This only validates shape — dropping ids the catalog no longer has is the
 *  caller's job, done once the library has actually loaded (see
 *  `applyRestoredSnapshot`). */
function readPlayerSnapshot(): PlayerStateSnapshot | null {
  try {
    const raw = localStorage.getItem(PLAYER_STATE_KEY);
    if (!raw) return null;
    const data = JSON.parse(raw) as Partial<PlayerStateSnapshot>;
    if (data.v !== 2) return null;
    const queue = Array.isArray(data.queue) ? (data.queue as CatalogTrack[]) : [];
    const userQueue = Array.isArray(data.userQueue) ? (data.userQueue as CatalogTrack[]) : [];
    const queueLen = queue.length;
    const playOrder = Array.isArray(data.playOrder)
      ? data.playOrder.filter((i): i is number => Number.isInteger(i) && i >= 0 && i < queueLen)
      : [];
    if (playOrder.length === 0 && userQueue.length === 0) return null;
    let playOrderPos = -1;
    if (playOrder.length > 0) {
      playOrderPos =
        typeof data.playOrderPos === "number" &&
        Number.isInteger(data.playOrderPos) &&
        data.playOrderPos >= 0
          ? Math.min(data.playOrderPos, playOrder.length - 1)
          : 0;
    }
    let userQueuePos = -1;
    if (userQueue.length > 0) {
      userQueuePos =
        typeof data.userQueuePos === "number" &&
        Number.isInteger(data.userQueuePos) &&
        data.userQueuePos >= 0
          ? Math.min(data.userQueuePos, userQueue.length - 1)
          : -1;
    }
    const repeatMode: RepeatMode =
      data.repeatMode === "off" || data.repeatMode === "all" || data.repeatMode === "one"
        ? data.repeatMode
        : "off";
    const position =
      data.position && typeof data.position.trackId === "number"
        ? { trackId: data.position.trackId, secs: Math.max(0, data.position.secs ?? 0) }
        : null;
    return {
      v: 2,
      queue,
      playOrder,
      playOrderPos,
      userQueue,
      userQueuePos,
      shuffleEnabled: !!data.shuffleEnabled,
      repeatMode,
      position,
    };
  } catch {
    return null;
  }
}

function sample(pool: CatalogTrack[], n: number): CatalogTrack[] {
  const copy = [...pool];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, n);
}

export const usePlayerStore = defineStore("player", () => {
  const settings = useSettingsStore();

  /** Display order — what QueuePanel shows and reorders. */
  const queue = ref<CatalogTrack[]>([]);
  /** Indices into `queue`, in playback order. Shuffle is a permutation of this. */
  const playOrder = ref<number[]>([]);
  const playOrderPos = ref(-1);
  const isPlaying = ref(false);
  const positionSecs = ref(0);
  const durationSecs = ref(0);
  const volume = ref(settings.volume);
  const shuffleEnabled = ref(false);
  const repeatMode = ref<RepeatMode>(settings.continuousPlayback ? "all" : "off");
  const favorites = ref<Set<number>>(new Set());
  const errorMessage = ref<string | null>(null);
  const sleepTimerRemainingMs = ref(0);
  /**
   * True while a "Shuffle all" queue is what is playing. Stays true after the
   * pool drains — the shuffled playlist is still on — and only clears when the
   * user starts something else or the player is reset.
   */
  const shuffleAllActive = ref(false);
  /**
   * The user queue: tracks the user explicitly added ("add to queue" appends to
   * the bottom, "play next" inserts at the top). It has priority over the
   * system queue — playback runs through it first, and only continues the
   * system queue (album/playlist/mix/shuffle-all context) once it is empty.
   */
  const userQueue = ref<CatalogTrack[]>([]);
  /** Index of the track currently playing from `userQueue`, or -1. */
  const userQueuePos = ref(-1);

  const currentIndex = computed(() => playOrder.value[playOrderPos.value] ?? -1);
  const currentTrack = computed(() => {
    if (userQueuePos.value >= 0) return userQueue.value[userQueuePos.value] ?? null;
    return queue.value[currentIndex.value] ?? null;
  });
  const progress = computed(() =>
    durationSecs.value ? positionSecs.value / durationSecs.value : 0,
  );
  const sleepTimerActive = computed(() => sleepTimerRemainingMs.value > 0);
  /**
   * Up-next entries, user queue first (it has priority), then the system
   * queue's remainder. `orderPos` is the entry's position within its own
   * queue (`userQueue` index or `playOrder` position) — that is what
   * reordering acts on, since this list *is* the play order.
   */
  const upNext = computed(() => {
    const userStart = userQueuePos.value >= 0 ? userQueuePos.value + 1 : 0;
    const user = userQueue.value
      .slice(userStart)
      .map((track, i) => ({
        track,
        queueIndex: -1,
        orderPos: userStart + i,
        origin: "user" as const,
      }))
      .filter((e) => e.track != null);
    const system = playOrder.value
      .slice(playOrderPos.value + 1)
      .map((queueIndex, i) => ({
        track: queue.value[queueIndex],
        queueIndex,
        orderPos: playOrderPos.value + 1 + i,
        origin: "system" as const,
      }))
      .filter((e) => e.track != null);
    return [...user, ...system];
  });

  const audioEl = ref<HTMLAudioElement | null>(null);
  /**
   * Second hidden element that buffers the NEXT track while the current one
   * plays, so the handoff is a resume instead of a cold start: gapless in the
   * foreground, and the best shot iOS's suspended background page has at
   * auto-advance (WebKit wakes it briefly at 'ended', but a fresh token fetch
   * + src swap + play() usually gets suspended before audio starts).
   */
  let preloadEl: HTMLAudioElement | null = null;
  /** The track currently sitting in `preloadEl` (null = empty/stale). */
  let preloadedTrack: CatalogTrack | null = null;
  /** ReplayGain tags for the current track (null = none fetched/available).
   *  Resolved per track via `fetchMetadataCached` and applied on top of the
   *  user's volume by `applyEffectiveVolume`. */
  const replayGainTags = ref<TrackMetadata | null>(null);
  /** Guards async preloads against queue changes racing the token fetch. */
  let preloadSeq = 0;
  /** ReplayGain tags for whatever is sitting in `preloadEl`, resolved ahead of
   *  the handoff so the swap doesn't have a volume glitch while a fetch
   *  catches up (null = none fetched/available). */
  let preloadedMetadata: TrackMetadata | null = null;
  // FLAC seeks reload the stream, so el.currentTime restarts at 0; this holds
  // the seconds the server skipped so the wall-clock position stays right.
  let flacSeekOffset = 0;
  let _seekSeq = 0;
  /** Track id whose play has already been counted this session (Q20): a play
   *  counts once per track after 30 s, not on every start. */
  let playCountedTrackId: number | null = null;
  let errorTimer: ReturnType<typeof setTimeout> | undefined;
  let sleepInterval: ReturnType<typeof setInterval> | undefined;
  /**
   * Screen Wake Lock handle, held while playing. On browsers that support it
   * (Chrome/Edge on Android and desktop) this keeps the CPU awake across the
   * track boundary so the next stream's load does not stall — the same fix
   * the Android app got with ExoPlayer's WAKE_MODE_LOCAL. iOS Safari does not
   * implement the Wake Lock API (it is silently unavailable), where the
   * preload handoff + visibility self-heal below are the mitigation.
   */
  let wakeLock: WakeLockSentinel | null = null;
  /**
   * Backing pool for "Shuffle all". Empties once every track has been queued,
   * which is why it cannot double as the button's active state.
   */
  let shuffleAllPool: CatalogTrack[] = [];

  function setError(msg: string): void {
    errorMessage.value = msg;
    clearTimeout(errorTimer);
    errorTimer = setTimeout(() => {
      errorMessage.value = null;
      errorTimer = undefined;
    }, ERROR_CLEAR_MS);
  }

  // --- ReplayGain ---
  //
  // Mirrors the desktop app's PlayerBar: fetch each track's tags once,
  // compute the effective <audio>.volume from the user's volume + the tag's
  // gain (plus preamp, clipping cap), and apply it on top of what the user
  // picked. Boosts above unity would need a Web Audio GainNode — this app
  // only scales the element's own [0,1] range, so a boosted track just
  // clamps there (see audio/replayGain.ts for the full math and the
  // clipping-prevention peak cap).

  const metadataCache = new Map<number, TrackMetadata | null>();

  /** Fetches a track's tags once and caches the result (including a `null`
   *  "no tags"/failure outcome, so a bad fetch isn't retried every track
   *  change). Capped so a very long session doesn't grow this unbounded. */
  async function fetchMetadataCached(trackId: number): Promise<TrackMetadata | null> {
    if (metadataCache.has(trackId)) return metadataCache.get(trackId) ?? null;
    let tags: TrackMetadata | null;
    try {
      tags = await getMetadata(trackId);
    } catch {
      tags = null;
    }
    if (metadataCache.size >= METADATA_CACHE_LIMIT) {
      const oldest = metadataCache.keys().next().value;
      if (oldest !== undefined) metadataCache.delete(oldest);
    }
    metadataCache.set(trackId, tags);
    return tags;
  }

  function replayGainOptions(): { mode: typeof settings.replayGainMode; preampDb: number; preventClipping: boolean } {
    return {
      mode: settings.replayGainMode,
      preampDb: settings.replayGainPreampDb,
      preventClipping: settings.replayGainPreventClipping,
    };
  }

  /** `<audio>.volume` throws outside [0, 1]; ReplayGain's peak-limiting math
   *  can legitimately report just past 1 when the cap binds (see
   *  audio/replayGain.ts's own doc comment) — clamp before every DOM
   *  assignment, not inside the math itself. */
  function clampElementVolume(v: number): number {
    return Math.max(0, Math.min(1, v));
  }

  /** Recomputes and applies the active element's volume from the user's
   *  volume + the current track's ReplayGain tags. */
  function applyEffectiveVolume(): void {
    const el = audioEl.value;
    if (!el) return;
    el.volume = clampElementVolume(
      computeReplayGainVolume(volume.value, replayGainTags.value, replayGainOptions()),
    );
  }

  /** Wires the shared listener set onto one hidden, DOM-attached audio element.
   * Only the ACTIVE element may write playback state; the preload element fires
   * durationchange (and briefly play/pause) while it's just buffering. */
  function makeAudioEl(): HTMLAudioElement {
    const el = new Audio();
    el.addEventListener("timeupdate", () => {
      if (el !== audioEl.value) return;
      positionSecs.value = el.currentTime + flacSeekOffset;
    });
    el.addEventListener("durationchange", () => {
      if (el !== audioEl.value) return;
      if (Number.isFinite(el.duration)) {
        durationSecs.value = el.duration + flacSeekOffset;
      } else {
        durationSecs.value = currentTrack.value?.duration_secs ?? 0;
      }
    });
    el.addEventListener("ended", () => {
      if (el !== audioEl.value) return;
      isPlaying.value = false;
      positionSecs.value = 0;
      if (repeatMode.value === "one") {
        void playCurrent(0);
        return;
      }
      const next = nextPlayOrderPos();
      if (next < 0) return; // end of queue — playback is done
      playOrderPos.value = next;
      void playIndexAt(0);
    });
    el.addEventListener("play", () => {
      if (el === audioEl.value) isPlaying.value = true;
    });
    el.addEventListener("pause", () => {
      if (el === audioEl.value) isPlaying.value = false;
    });
    // Mid-stream failures (dropped connection, server error) previously went
    // unnoticed: playback silently halted with no toast and no advance.
    el.addEventListener("error", () => {
      if (el !== audioEl.value) return; // preload elements just go stale
      // src="" during a FLAC seek reload fires a benign error — the attribute
      // is empty then; a real failure always has the stream URL set.
      if (!el.getAttribute("src") || el.error === null) return;
      setError("Playback failed — connection lost. Skipping to next track…");
      isPlaying.value = false;
      // Auto-advance so the queue keeps moving; advance() handles the
      // user-queue-first logic and the end-of-queue case.
      setTimeout(() => advance(false), 300);
    });
    el.volume = volume.value;
    // Must be in the DOM for the iOS Now Playing widget to register.
    el.style.display = "none";
    // playsinline keeps playback inline on iOS instead of forcing fullscreen.
    el.setAttribute("playsinline", "");
    el.setAttribute("webkit-playsinline", "");
    document.body.appendChild(el);
    return el;
  }

  function initAudio(): HTMLAudioElement {
    if (!audioEl.value) audioEl.value = makeAudioEl();
    // reset() keeps the active element but drops the preload one — recreate it.
    if (!preloadEl) preloadEl = makeAudioEl();
    setupMediaSession();
    return audioEl.value;
  }

  /** Rebuilds `playOrder` around `anchor` (an index into `queue`). */
  function rebuildPlayOrder(anchor: number): void {
    const n = queue.value.length;
    if (n === 0) {
      playOrder.value = [];
      playOrderPos.value = -1;
      return;
    }
    const a = anchor >= 0 && anchor < n ? anchor : 0;
    if (!shuffleEnabled.value) {
      playOrder.value = Array.from({ length: n }, (_, i) => i);
      playOrderPos.value = a;
      return;
    }
    const rest: number[] = [];
    for (let i = 0; i < n; i++) if (i !== a) rest.push(i);
    for (let i = rest.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [rest[i], rest[j]] = [rest[j], rest[i]];
    }
    playOrder.value = [a, ...rest];
    playOrderPos.value = 0;
  }

  /** Resolves the <audio> src for a track. A cached track plays the complete
   *  file from Cache Storage (via the service worker), so no ?start= transcode
   *  is needed and the browser seeks natively; a fresh token keeps online
   *  playback working when the SW is not controlling the page, and when the
   *  server is unreachable the offline copy is used (Q11). Offline mode (the
   *  settings toggle, mirroring the Android app) makes no network calls at
   *  all: cached tracks play from the cache, anything else is an error. */
  async function resolveStreamSrc(track: CatalogTrack, startSecs: number): Promise<string> {
    const offline = useOfflineStore();
    if (useSettingsStore().offlineMode) {
      if (!offline.isCached(track.id)) {
        throw new Error("Not downloaded — download it or turn off offline mode");
      }
      return offlineStreamUrl(track.id);
    }
    if (offline.isCached(track.id)) {
      try {
        const token = await issueStreamToken(track.id);
        return streamUrl(track.id, token);
      } catch {
        return offlineStreamUrl(track.id);
      }
    }
    const token = await issueStreamToken(track.id);
    return streamUrl(track.id, token, startSecs);
  }

  async function playCurrent(startSecs = 0): Promise<void> {
    const track = currentTrack.value;
    if (!track) return;
    const seq = ++_seekSeq;
    const cached = useOfflineStore().isCached(track.id);
    try {
      const [src, tags] = await Promise.all([
        resolveStreamSrc(track, startSecs),
        fetchMetadataCached(track.id),
      ]);
      if (seq !== _seekSeq) return;
      replayGainTags.value = tags;
      const el = initAudio();
      // A cached file is complete, so even FLAC/WAV seeks natively — no
      // transcode offset to track.
      flacSeekOffset = !cached && needsTranscode(track.format) ? startSecs : 0;
      el.src = src;
      applyEffectiveVolume();
      positionSecs.value = startSecs;
      durationSecs.value = track.duration_secs ?? 0;
      await el.play();
      if (cached && startSecs > 0) el.currentTime = startSecs;
      void preloadNext();
    } catch (e) {
      // The queue is already set, so currentTrack is non-null and the
      // MiniPlayer error banner (gated on !currentTrack) stays hidden —
      // surface the failure as a toast as well.
      setError((e as Error).message);
      showToast((e as Error).message);
    }
  }

  /** Index of the next entry in `playOrder`, or -1 at the end of the queue
   * (repeat-all wraps around to 0). */
  function nextPlayOrderPos(): number {
    if (playOrderPos.value + 1 < playOrder.value.length) {
      return playOrderPos.value + 1;
    }
    if (repeatMode.value === "all" && playOrder.value.length > 0) {
      return 0;
    }
    return -1;
  }

  /** Starts the track at `playOrder[playOrderPos]`, preferring the preloaded
   * element (a resume) over a cold start. */
  async function playIndexAt(startSecs: number): Promise<void> {
    if (startSecs === 0 && tryHandoffToPreloaded()) return;
    await playCurrent(startSecs);
  }

  /**
   * Swaps the preloaded element in as the active one if it already holds the
   * current track with data buffered. Returns false to fall back to a cold
   * start (stale preload, repeat-one, seek, or the buffer never filled).
   */
  function tryHandoffToPreloaded(): boolean {
    const target = currentTrack.value;
    const buf = preloadEl;
    const old = audioEl.value;
    if (!target || !buf || !old) return false;
    if (preloadedTrack?.id !== target.id) return false;
    if (buf.readyState < 2) return false; // HAVE_CURRENT_DATA — not ready yet

    audioEl.value = buf;
    preloadEl = old;
    preloadedTrack = null;
    replayGainTags.value = preloadedMetadata;
    preloadedMetadata = null;
    // Free the old element so it becomes the next preload target.
    old.pause();
    old.removeAttribute("src");
    old.load();
    flacSeekOffset = 0;
    positionSecs.value = 0;
    durationSecs.value = target.duration_secs ?? 0;
    // The settings or volume may have changed since the preload was primed —
    // reapply now rather than trust the volume it was buffered at.
    applyEffectiveVolume();
    buf.play().catch((e) => setError((e as Error).message));
    void preloadNext();
    return true;
  }

  /**
   * Starts buffering the next track (per the play order) into `preloadEl`
   * while the current one plays. The user queue has priority — its next track
   * is what will actually play. Tokens live 8h on the server
   * (`state.tokens.issue(id, 28800)`), so a token minted now still validates
   * whenever the handoff happens. Failure just means a cold start later.
   */
  async function preloadNext(): Promise<void> {
    const buf = preloadEl;
    if (!buf) return;
    let next: CatalogTrack | null = null;
    if (userQueue.value.length > 0) {
      const ui = userQueuePos.value + 1;
      if (ui < userQueue.value.length) next = userQueue.value[ui];
    }
    if (!next) {
      const nextPos = nextPlayOrderPos();
      next = nextPos >= 0 ? queue.value[playOrder.value[nextPos]] : null;
    }
    if (!next) {
      preloadedTrack = null;
      preloadedMetadata = null;
      buf.removeAttribute("src");
      buf.load();
      return;
    }
    if (preloadedTrack?.id === next.id && buf.src) return; // already buffering it
    const seq = ++preloadSeq;
    try {
      const [src, tags] = await Promise.all([
        resolveStreamSrc(next, 0),
        fetchMetadataCached(next.id),
      ]);
      if (seq !== preloadSeq || !preloadEl) return;
      preloadedTrack = next;
      preloadedMetadata = tags;
      buf.src = src;
      buf.volume = clampElementVolume(computeReplayGainVolume(volume.value, tags, replayGainOptions()));
      // preload=auto on iOS/Safari: WebKit only starts buffering once the
      // element is "needed", so force it with load(). "metadata" alone would
      // not fill the buffer the lock-screen handoff depends on.
      buf.preload = "auto";
      buf.load();
    } catch {
      preloadedTrack = null;
      preloadedMetadata = null;
    }
  }

  /** Steps to the next entry, honouring repeat-all wraparound. The user queue
   *  has priority: queued tracks play first, and only once it is empty does
   *  playback continue the system queue where it left off. */
  function advance(auto: boolean): void {
    if (userQueuePos.value >= 0 || userQueue.value.length > 0) {
      const next = userQueuePos.value + 1;
      if (next < userQueue.value.length) {
        userQueuePos.value = next;
        void playIndexAt(0);
        return;
      }
      // User queue exhausted — resume the system queue.
      userQueuePos.value = -1;
      userQueue.value = [];
    }
    const next = nextPlayOrderPos();
    if (next < 0) {
      if (auto) isPlaying.value = false;
      return;
    }
    playOrderPos.value = next;
    void playIndexAt(0);
  }

  async function playTrack(track: CatalogTrack, newQueue: CatalogTrack[]): Promise<void> {
    shuffleAllPool = [];
    shuffleAllActive.value = false;
    // A new context (album/playlist/mix/shuffle-all) resets the user queue —
    // queued tracks belonged to the previous context.
    userQueue.value = [];
    userQueuePos.value = -1;
    queue.value = [...newQueue];
    rebuildPlayOrder(newQueue.findIndex((t) => t.id === track.id));
    // A fresh play session replaces the stored position so a reload cannot
    // resurrect the previous session's spot (the queue itself is re-saved by
    // the snapshot watcher with the new state).
    writeSnapshot(null);
    await playCurrent(0);
  }

  /**
   * Silence and detach the local element so a cast session can take over.
   *
   * The server streams the audio to the device itself; leaving the browser
   * playing would play the same track twice, out of sync.
   */
  function pauseForCast(): void {
    const el = audioEl.value;
    if (el) {
      el.pause();
      el.src = "";
    }
    isPlaying.value = false;
  }

  function playPause(): void {
    const el = audioEl.value;
    if (!el) {
      // Restored session: no <audio> element exists yet — start playback from
      // the restored position instead of silently doing nothing.
      if (currentTrack.value) void playCurrent(positionSecs.value);
      return;
    }
    if (el.paused) el.play().catch(() => null);
    else el.pause();
  }

  /** Last non-zero level, so unmute restores where the user was. */
  let lastVolume = 0.8;

  function setVolume(v: number): void {
    const clamped = Math.min(1, Math.max(0, v));
    volume.value = clamped;
    settings.setVolume(clamped);
    applyEffectiveVolume();
  }

  function toggleMute(): void {
    if (volume.value > 0) {
      lastVolume = volume.value;
      setVolume(0);
    } else {
      setVolume(lastVolume || 0.8);
    }
  }

  function skipNext(): void {
    advance(false);
  }

  function skipPrevious(): void {
    if (positionSecs.value > RESTART_THRESHOLD_SECS) {
      void seekTo(0);
      return;
    }
    const current = currentTrack.value;
    if (current) {
      // Q31 parity: step back through what was actually played, not the
      // queue neighbour — which is simply wrong while shuffling. "Known"
      // here means still reachable through one of the two live queues (the
      // desktop app checks its whole flat catalog; this app's two-queue
      // model makes the live queues the equivalent — entries played and
      // then explicitly removed are skipped, same as a deleted track there).
      const isKnown = (id: number): boolean =>
        queue.value.some((t) => t.id === id) || userQueue.value.some((t) => t.id === id);
      const backId = stepBackPlayHistory(current.id, isKnown);
      if (backId != null) {
        const target =
          userQueue.value.find((t) => t.id === backId) ?? queue.value.find((t) => t.id === backId);
        if (target) {
          skipTo(target);
          return;
        }
      }
    }
    if (userQueuePos.value > 0) {
      userQueuePos.value--;
      void playCurrent(0);
      return;
    }
    if (userQueuePos.value === 0) return; // first user track: nothing before it
    if (playOrderPos.value > 0) {
      playOrderPos.value--;
      void playCurrent(0);
    }
  }

  async function seekTo(secs: number): Promise<void> {
    const track = currentTrack.value;
    if (!track) return;
    const el = audioEl.value;
    if (!el) return;

    const seq = ++_seekSeq;
    const wasPlaying = !el.paused;
    const cached = useOfflineStore().isCached(track.id);

    if (needsTranscode(track.format) && !cached) {
      // Streamed FLAC/WAV: the server transcodes from ?start=, so the stream
      // must reload. Issue a fresh token — the original expires 60s after track
      // start. A cached file is complete and seeks natively below.
      el.pause();
      flacSeekOffset = secs;
      el.src = "";
      try {
        const token = await issueStreamToken(track.id);
        if (seq !== _seekSeq) return;
        el.src = streamUrl(track.id, token, secs);
        el.load();
        positionSecs.value = secs;
        if (wasPlaying) {
          el.play().catch(() => {
            el.addEventListener(
              "canplay",
              () => {
                if (seq === _seekSeq) el.play().catch(() => null);
              },
              { once: true },
            );
          });
        }
      } catch {
        // seek aborted or token fetch failed
      }
    } else {
      // MP3, or a cached track (complete file): the browser seeks natively.
      positionSecs.value = secs;
      el.currentTime = secs;
      if (wasPlaying) {
        el.play().catch(() => {
          el.addEventListener(
            "canplay",
            () => {
              if (seq === _seekSeq) el.play().catch(() => null);
            },
            { once: true },
          );
        });
      }
    }
  }

  function toggleShuffle(): void {
    shuffleEnabled.value = !shuffleEnabled.value;
    rebuildPlayOrder(currentIndex.value);
  }

  function cycleRepeatMode(): void {
    repeatMode.value =
      repeatMode.value === "off" ? "all" : repeatMode.value === "all" ? "one" : "off";
  }

  function skipTo(track: CatalogTrack): void {
    // The user queue has priority — jumping to one of its tracks plays it now.
    const ui = userQueue.value.findIndex((t) => t.id === track.id);
    if (ui >= 0) {
      userQueuePos.value = ui;
      void playCurrent(0);
      return;
    }
    const i = queue.value.findIndex((t) => t.id === track.id);
    if (i < 0) return;
    const pos = playOrder.value.indexOf(i);
    if (pos < 0) return;
    playOrderPos.value = pos;
    void playCurrent(0);
  }

  /** Queue mutation without the toast — used by the shuffle-all top-up. */
  function appendToQueue(tracks: CatalogTrack[]): void {
    if (tracks.length === 0) return;
    const wasIdle = currentIndex.value < 0;
    const startIdx = queue.value.length;
    queue.value = [...queue.value, ...tracks];
    const newIndices = tracks.map((_, i) => startIdx + i);

    if (shuffleEnabled.value && playOrder.value.length > 0) {
      const order = [...playOrder.value];
      for (const idx of newIndices) {
        const lo = playOrderPos.value + 1;
        const pos = lo + Math.floor(Math.random() * (order.length - lo + 1));
        order.splice(pos, 0, idx);
      }
      playOrder.value = order;
    } else {
      playOrder.value = [...playOrder.value, ...newIndices];
    }

    // An idle <audio> would otherwise silently do nothing; Android's session
    // is already live, so it just picks the addition up.
    if (wasIdle) {
      playOrderPos.value = playOrder.value.indexOf(startIdx);
      void playCurrent(0);
    }
  }

  /** Appends to the bottom of the user queue. The user queue plays before the
   *  system queue, so these tracks come up before the rest of the album /
   *  playlist / shuffle-all context. */
  function addTracksToQueue(tracks: CatalogTrack[]): void {
    if (tracks.length === 0) return;
    userQueue.value = [...userQueue.value, ...tracks];
    // Nothing playing at all: start with the first added track.
    if (currentTrack.value == null) {
      userQueuePos.value = 0;
      void playCurrent(0);
    }
    showToast("Added to queue");
  }

  function addToQueue(track: CatalogTrack): void {
    addTracksToQueue([track]);
  }

  /** Moves a track from the system "Up next" remainder into the user queue
   *  ("Your queue"), removing it from the system queue so it isn't played twice.
   *  Used by the queue screen's "Add to queue" action, the "Play next" action
   *  and the Up next → Your queue drag. No-op if the track is already in the
   *  user queue. With `asNext`, it's inserted right after the current track
   *  instead of appended to the bottom. */
  function moveToUserQueue(track: CatalogTrack, asNext = false): void {
    if (userQueue.value.some((t) => t.id === track.id)) return;
    const i = queue.value.findIndex((t) => t.id === track.id);
    if (i >= 0) {
      const wasCurrent = i === currentIndex.value;
      const orderPos = playOrder.value.indexOf(i);
      const nextQueue = [...queue.value];
      nextQueue.splice(i, 1);
      const nextOrder = playOrder.value.filter((x) => x !== i).map((x) => (x > i ? x - 1 : x));
      let nextPos = playOrderPos.value;
      if (orderPos >= 0 && orderPos < playOrderPos.value) nextPos--;
      queue.value = nextQueue;
      playOrder.value = nextOrder;
      playOrderPos.value = Math.min(nextPos, nextOrder.length - 1);
      if (wasCurrent && userQueuePos.value < 0) {
        if (currentTrack.value) void playCurrent(0);
      }
    }
    if (asNext) {
      const at = userQueuePos.value >= 0 ? userQueuePos.value + 1 : 0;
      userQueue.value = [...userQueue.value.slice(0, at), track, ...userQueue.value.slice(at)];
    } else {
      userQueue.value = [...userQueue.value, track];
    }
    if (currentTrack.value == null) {
      userQueuePos.value = 0;
      void playCurrent(0);
    }
    showToast(asNext ? "Will play next" : "Moved to your queue");
  }

  /** Inserts a track at the top of the user queue — it plays immediately after
   *  the current one, ahead of anything else queued. */
  function playNext(track: CatalogTrack): void {
    const at = userQueuePos.value >= 0 ? userQueuePos.value + 1 : 0;
    userQueue.value = [...userQueue.value.slice(0, at), track, ...userQueue.value.slice(at)];
    if (currentTrack.value == null) {
      userQueuePos.value = 0;
      void playCurrent(0);
    }
    showToast("Will play next");
  }

  function removeFromQueue(track: CatalogTrack): void {
    const ui = userQueue.value.findIndex((t) => t.id === track.id);
    if (ui >= 0) {
      const wasCurrent = ui === userQueuePos.value;
      const next = [...userQueue.value];
      next.splice(ui, 1);
      userQueue.value = next;
      if (wasCurrent) {
        if (userQueuePos.value < next.length) {
          void playCurrent(0); // next user track keeps playing
        } else {
          // The last user track is gone — fall back to the system queue.
          userQueuePos.value = -1;
          if (currentTrack.value) void playCurrent(0);
          else {
            audioEl.value?.pause();
            positionSecs.value = 0;
          }
        }
      } else if (ui < userQueuePos.value) {
        userQueuePos.value--;
      }
      return;
    }
    const i = queue.value.findIndex((t) => t.id === track.id);
    if (i < 0) return;
    const wasCurrent = i === currentIndex.value;
    const orderPos = playOrder.value.indexOf(i);

    const nextQueue = [...queue.value];
    nextQueue.splice(i, 1);
    const nextOrder = playOrder.value.filter((x) => x !== i).map((x) => (x > i ? x - 1 : x));
    let nextPos = playOrderPos.value;
    if (orderPos >= 0 && orderPos < playOrderPos.value) nextPos--;

    queue.value = nextQueue;
    playOrder.value = nextOrder;
    playOrderPos.value = Math.min(nextPos, nextOrder.length - 1);

    // Only the system track is really "current" when the user queue isn't
    // playing — while a user track is up, removing the paused system entry
    // must not restart playback.
    if (wasCurrent && userQueuePos.value < 0) {
      if (currentTrack.value) void playCurrent(0);
      else {
        audioEl.value?.pause();
        positionSecs.value = 0;
      }
    }
  }

  /** Drops everything after the current track; the current one keeps playing.
   *  Clears the user queue (it is all up-next) and the system queue's
   *  remainder after the current position. */
  function clearQueue(): void {
    if (userQueuePos.value >= 0) {
      // Keep the currently-playing user track and everything before it.
      userQueue.value = userQueue.value.slice(0, userQueuePos.value + 1);
    } else {
      userQueue.value = [];
    }
    if (currentIndex.value < 0) {
      queue.value = [];
      playOrder.value = [];
      playOrderPos.value = -1;
      return;
    }
    const keptOrder = playOrder.value.slice(0, playOrderPos.value + 1);
    const kept = [...new Set(keptOrder)].sort((a, b) => a - b);
    const remap = new Map<number, number>();
    kept.forEach((old, i) => remap.set(old, i));
    const nextQueue = kept.map((i) => queue.value[i]);
    queue.value = nextQueue;
    playOrder.value = keptOrder.map((i) => remap.get(i)!);
    playOrderPos.value = playOrder.value.length - 1;
  }

  /**
   * Moves an entry within `playOrder`; `from`/`to` are positions in that array
   * (an `upNext` entry's `orderPos`), NOT indices into `queue`.
   *
   * Reordering has to act on the play order because that is what the Queue
   * screen renders. Permuting `queue` instead and remapping `playOrder` by
   * index — as an earlier revision did — provably cancels out and is a no-op.
   */
  function reorderQueue(from: number, to: number): void {
    const n = playOrder.value.length;
    if (from === to || from < 0 || from >= n || to < 0 || to >= n) return;

    const currentEntry = playOrder.value[playOrderPos.value];
    const order = [...playOrder.value];
    const [moved] = order.splice(from, 1);
    order.splice(to, 0, moved);

    playOrder.value = order;
    // The current track must stay current even if positions shifted around it.
    playOrderPos.value = order.indexOf(currentEntry);
  }

  /** Moves an entry within the user queue. `from`/`to` are positions in that
   *  array (a user upNext entry's `orderPos`). */
  function reorderUserQueue(from: number, to: number): void {
    const n = userQueue.value.length;
    if (from === to || from < 0 || from >= n || to < 0 || to >= n) return;

    const currentEntry = userQueue.value[userQueuePos.value];
    const next = [...userQueue.value];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    userQueue.value = next;

    if (userQueuePos.value >= 0) {
      // The current track must stay current even if positions shifted.
      userQueuePos.value = next.indexOf(currentEntry);
    }
  }

  function startShuffleAll(allTracks: CatalogTrack[]): void {
    if (allTracks.length === 0) return;
    shuffleEnabled.value = false;
    const batch = sample(allTracks, SHUFFLE_ALL_BATCH);
    void playTrack(batch[0], batch);
    // playTrack clears both synchronously, so seed them afterwards.
    shuffleAllPool = allTracks;
    shuffleAllActive.value = true;
  }

  // Top up the queue as shuffle-all nears the end of what it queued.
  watch([playOrder, playOrderPos], () => {
    if (shuffleAllPool.length === 0) return;
    if (playOrder.value.length - playOrderPos.value - 1 > 1) return;
    const seen = new Set(queue.value.map((t) => t.id));
    const remaining = shuffleAllPool.filter((t) => !seen.has(t.id));
    if (remaining.length === 0) {
      shuffleAllPool = [];
      return;
    }
    appendToQueue(sample(remaining, SHUFFLE_ALL_BATCH));
  });

  async function toggleFavorite(track: CatalogTrack): Promise<void> {
    const wasFavorite = favorites.value.has(track.id);
    const next = new Set(favorites.value);
    if (wasFavorite) next.delete(track.id);
    else next.add(track.id);
    favorites.value = next;

    try {
      const playlistStore = usePlaylistStore();
      const fav = await playlistStore.ensureFavoritesPlaylist();
      if (wasFavorite) await playlistStore.removeTracks(fav.id, [track.id]);
      else await playlistStore.addTracks(fav.id, [track.id]);
      showToast(wasFavorite ? "Removed from favorites" : "Added to favorites");
    } catch (e) {
      const reverted = new Set(favorites.value);
      if (wasFavorite) reverted.add(track.id);
      else reverted.delete(track.id);
      favorites.value = reverted;
      showToast(`Failed: ${(e as Error).message}`);
    }
  }

  function startSleepTimer(ms: number): void {
    clearInterval(sleepInterval);
    sleepTimerRemainingMs.value = ms;
    sleepInterval = setInterval(() => {
      sleepTimerRemainingMs.value = Math.max(0, sleepTimerRemainingMs.value - 1000);
      if (sleepTimerRemainingMs.value <= 0) {
        clearInterval(sleepInterval);
        sleepInterval = undefined;
        if (isPlaying.value) playPause();
      }
    }, 1000);
  }

  function cancelSleepTimer(): void {
    clearInterval(sleepInterval);
    sleepInterval = undefined;
    sleepTimerRemainingMs.value = 0;
  }

  watch(
    () => settings.continuousPlayback,
    (on) => {
      repeatMode.value = on ? "all" : "off";
    },
  );

  // Mode/preamp/clipping changes apply immediately, not just on the next
  // track — including to whatever is already buffered in the preload
  // element, so a mid-track settings change doesn't get clobbered by a
  // stale volume at the next handoff.
  watch(
    [
      () => settings.replayGainMode,
      () => settings.replayGainPreampDb,
      () => settings.replayGainPreventClipping,
    ],
    () => {
      applyEffectiveVolume();
      if (preloadEl && preloadedTrack) {
        preloadEl.volume = clampElementVolume(
          computeReplayGainVolume(volume.value, preloadedMetadata, replayGainOptions()),
        );
      }
    },
  );

  // --- Media Session ---
  function setupMediaSession(): void {
    if (!("mediaSession" in navigator)) return;
    navigator.mediaSession.setActionHandler("play", () => playPause());
    navigator.mediaSession.setActionHandler("pause", () => playPause());
    navigator.mediaSession.setActionHandler("previoustrack", () => skipPrevious());
    navigator.mediaSession.setActionHandler("nexttrack", () => skipNext());
    navigator.mediaSession.setActionHandler("seekto", (d) => {
      if (d.seekTime != null) void seekTo(Math.floor(d.seekTime));
    });
    navigator.mediaSession.setActionHandler("seekbackward", (d) => {
      void seekTo(Math.max(0, Math.floor(positionSecs.value - (d.seekOffset ?? 10))));
    });
    navigator.mediaSession.setActionHandler("seekforward", (d) => {
      void seekTo(Math.min(durationSecs.value, Math.floor(positionSecs.value + (d.seekOffset ?? 10))));
    });
  }

  const currentCoverUrl = computed(() => {
    const t = currentTrack.value;
    if (!t || !t.has_cover) return null;
    return useLibraryStore().coverCache.get(t.id) ?? null;
  });

  // Dynamic accent: when the user picked "dynamic" in Settings, tint the app's
  // accent from the current track's album-art main color (the same
  // saturation-weighted average that drives the player glow). The color is
  // adjusted to stay legible for controls (see visibleAccentFromRgb); when no
  // usable color exists (no cover, gray art, or an edge-blur background) the
  // inline vars are dropped and the theme default shows.
  const accentGlowRgb = useDominantColor(currentCoverUrl);
  watchEffect(() => {
    const el = document.documentElement;
    if (settings.accent !== "dynamic") {
      el.style.removeProperty("--primary");
      el.style.removeProperty("--primary-container");
      el.style.removeProperty("--on-primary-container");
      el.style.removeProperty("--secondary");
      return;
    }
    const accent = visibleAccentFromRgb(accentGlowRgb.value);
    if (!accent) {
      el.style.removeProperty("--primary");
      el.style.removeProperty("--primary-container");
      el.style.removeProperty("--on-primary-container");
      el.style.removeProperty("--secondary");
      return;
    }
    // Container = the accent pulled ~40% toward the dark surface so fills made
    // of it stay subtle; on-container = the accent lifted for text on those fills.
    const [r, g, b] = accent.replace(/[^\d,]/g, "").split(",").map(Number);
    const mix = (c: number, t: number, f: number): number => Math.round(c + (t - c) * f);
    const container = `rgb(${mix(r, 30, 0.45)},${mix(g, 26, 0.45)},${mix(b, 22, 0.45)})`;
    const onContainer = `rgb(${mix(r, 255, 0.35)},${mix(g, 255, 0.35)},${mix(b, 255, 0.35)})`;
    el.style.setProperty("--primary", accent);
    el.style.setProperty("--primary-container", container);
    el.style.setProperty("--on-primary-container", onContainer);
    el.style.setProperty("--secondary", onContainer);
  });

  watch([currentTrack, currentCoverUrl], () => {
    if (!("mediaSession" in navigator)) return;
    const t = currentTrack.value;
    if (!t) {
      navigator.mediaSession.metadata = null;
      return;
    }
    const artwork: MediaImage[] = currentCoverUrl.value
      ? [{ src: currentCoverUrl.value, sizes: "512x512" }]
      : [];
    navigator.mediaSession.metadata = new MediaMetadata({
      title: t.title ?? "",
      artist: t.artist ?? t.album_artist ?? "",
      album: t.album ?? "",
      artwork,
    });
  });

  watch(isPlaying, (playing) => {
    if ("mediaSession" in navigator)
      navigator.mediaSession.playbackState = playing ? "playing" : "paused";
    if (playing) {
      void acquireWakeLock();
    } else {
      releaseWakeLock();
      // A pause is a natural checkpoint — save now instead of waiting for the
      // next throttled tick (and a pause might be the last thing that happens
      // before the tab closes).
      writeSnapshot(currentPositionSnapshot());
    }
  });

  // --- Lock-screen resilience (iOS Safari / installed PWA) ---
  //
  // When the phone sleeps, WebKit suspends the page's task queue. Audio keeps
  // playing (the media pipeline runs in the GPU process), and WebKit wakes the
  // page briefly when the track fires 'ended' — but a cold start (token fetch
  // + src swap + play()) usually gets suspended again before audio begins,
  // leaving the progress bar running with no sound. Three mitigations:
  //
  // 1. The preload element (above) turns the handoff into a resume: play() on
  //    an element that already has bytes needs no network round-trip.
  // 2. A screen wake lock (where supported) keeps the CPU awake across the
  //    boundary so even a cold start finishes.
  // 3. On returning to the foreground, a stalled element that claims to be
  //    playing but is not advancing is reinitialized — self-heal.

  async function acquireWakeLock(): Promise<void> {
    // iOS Safari does not implement the Wake Lock API; feature-detect.
    if (!("wakeLock" in navigator)) return;
    if (wakeLock && !wakeLock.released) return;
    try {
      wakeLock = await navigator.wakeLock.request("screen");
      wakeLock.addEventListener("release", () => {
        // The OS can release the lock on its own (e.g. battery saver); while
        // still playing and visible, ask for it again.
        if (isPlaying.value && document.visibilityState === "visible") {
          void acquireWakeLock();
        }
      });
    } catch {
      // Denied or interrupted — playback continues without the lock.
    }
  }

  function releaseWakeLock(): void {
    if (!wakeLock) return;
    wakeLock.release().catch(() => undefined);
    wakeLock = null;
  }

  /**
   * Self-heal after the screen locks and unlocks. WebKit can leave the media
   * element in a state where play() resolves and the play event fires but
   * currentTime never advances and no audio comes out — the exact symptom of
   * "the bar runs but no sound" after sleep. When we come back to the
   * foreground and the element claims to be playing but has stalled,
   * reinitialize the pipeline (load() + canplay-gated play()).
   */
  function onVisibilityChange(): void {
    if (document.visibilityState !== "visible") {
      // Hidden: a backgrounded tab can be killed at any time — save the
      // position now rather than waiting for the next throttled tick.
      writeSnapshot(currentPositionSnapshot());
      return;
    }
    // The lock is dropped while hidden; re-acquire now that we can.
    if (isPlaying.value) void acquireWakeLock();
    const el = audioEl.value;
    if (!el || !isPlaying.value) return;
    if (!el.paused && el.currentTime > 0 && el.currentTime < (el.duration || Infinity)) return; // genuinely playing
    el.load();
    el.addEventListener("canplay", () => el.play().catch(() => undefined), { once: true });
  }

  document.addEventListener("visibilitychange", onVisibilityChange);

  watch([positionSecs, durationSecs], () => {
    if (!("mediaSession" in navigator) || !durationSecs.value) return;
    try {
      navigator.mediaSession.setPositionState({
        duration: durationSecs.value,
        playbackRate: 1,
        position: Math.min(positionSecs.value, durationSecs.value),
      });
    } catch {
      /* Safari throws on out-of-range state */
    }
  });

  // Keep the cover warm so the OS notification has artwork.
  watch(currentTrack, (t, old) => {
    // A new track gets a fresh play-count window (Q20).
    if (t?.id !== old?.id) playCountedTrackId = null;
    // Q31: remember what was actually played so Previous can step back
    // through it instead of the queue neighbour.
    recordPlayHistory(old?.id ?? null, t?.id ?? null);
    if (t?.has_cover) useLibraryStore().requestCover(t.id);
  });

  // Q20: a play counts after 30 s of listening, not at track start — matching
  // the desktop and Android apps. The flag makes it once per track per session.
  watch([positionSecs, isPlaying], () => {
    const t = currentTrack.value;
    if (!t || !isPlaying.value) return;
    if (playCountedTrackId === t.id) return;
    if (positionSecs.value < PLAY_COUNT_THRESHOLD_SECS) return;
    playCountedTrackId = t.id;
    void recordPlay(t.id, settings.deviceName);
  });

  // --- Session persistence (queue, modes, position) across reloads ---

  let snapshotTimer: ReturnType<typeof setTimeout> | undefined;
  let lastPositionSave = 0;

  function currentPositionSnapshot(): { trackId: number; secs: number } | null {
    const t = currentTrack.value;
    if (!t) return null;
    return { trackId: t.id, secs: positionSecs.value };
  }

  function writeSnapshot(position: { trackId: number; secs: number } | null): void {
    try {
      const snapshot: PlayerStateSnapshot = {
        v: 2,
        queue: queue.value,
        playOrder: playOrder.value,
        playOrderPos: playOrderPos.value,
        userQueue: userQueue.value,
        userQueuePos: userQueuePos.value,
        shuffleEnabled: shuffleEnabled.value,
        repeatMode: repeatMode.value,
        position,
      };
      localStorage.setItem(PLAYER_STATE_KEY, JSON.stringify(snapshot));
    } catch {
      /* storage full or unavailable — persistence is best-effort */
    }
  }

  function scheduleSnapshot(): void {
    clearTimeout(snapshotTimer);
    snapshotTimer = setTimeout(() => writeSnapshot(currentPositionSnapshot()), SNAPSHOT_DEBOUNCE_MS);
  }

  // Queue/mode changes are debounced; a full reload can only ever restore what
  // was last written, so there is no point writing on every single mutation.
  watch(
    [queue, playOrder, playOrderPos, userQueue, userQueuePos, shuffleEnabled, repeatMode],
    () => {
      scheduleSnapshot();
    },
  );

  // While playing, persist the position at a throttled cadence so a reload
  // mid-track resumes close to where it was.
  watch([positionSecs, isPlaying], () => {
    if (!isPlaying.value) return;
    const now = Date.now();
    if (now - lastPositionSave >= POSITION_SAVE_INTERVAL_MS) {
      lastPositionSave = now;
      writeSnapshot(currentPositionSnapshot());
    }
  });

  /**
   * Applies a saved snapshot, dropping any track id the catalog no longer
   * has (deleted/moved files) and remapping the surviving indices. Only the
   * position matching the restored current track is applied; anything else
   * is discarded so a stale value never forces itself onto a fresh session.
   * Playback is never started here — restoring lands paused, same as the
   * existing "restored session" branch in `playPause`.
   */
  function applyRestoredSnapshot(): void {
    const snapshot = readPlayerSnapshot();
    if (!snapshot) return;
    const known = useLibraryStore().trackById;

    // System queue: drop unknown ids, remapping playOrder around the gaps.
    const indexRemap = new Map<number, number>();
    const filteredQueue: CatalogTrack[] = [];
    snapshot.queue.forEach((t, i) => {
      if (known.has(t.id)) {
        indexRemap.set(i, filteredQueue.length);
        filteredQueue.push(t);
      }
    });
    const filteredOrder = snapshot.playOrder
      .filter((i) => indexRemap.has(i))
      .map((i) => indexRemap.get(i)!);
    let filteredOrderPos = -1;
    if (filteredOrder.length > 0) {
      const originalCurrent = indexRemap.get(snapshot.playOrder[snapshot.playOrderPos]);
      const mappedPos = originalCurrent != null ? filteredOrder.indexOf(originalCurrent) : -1;
      filteredOrderPos = mappedPos >= 0 ? mappedPos : 0;
    }

    // User queue: drop unknown ids outright — there is no index to remap.
    const filteredUserQueue = snapshot.userQueue.filter((t) => known.has(t.id));
    let filteredUserQueuePos = -1;
    if (filteredUserQueue.length > 0 && snapshot.userQueuePos >= 0) {
      const originalId = snapshot.userQueue[snapshot.userQueuePos]?.id;
      const mappedPos = originalId != null ? filteredUserQueue.findIndex((t) => t.id === originalId) : -1;
      filteredUserQueuePos = mappedPos >= 0 ? mappedPos : 0;
    }

    if (filteredOrder.length === 0 && filteredUserQueue.length === 0) return;

    queue.value = filteredQueue;
    playOrder.value = filteredOrder;
    playOrderPos.value = filteredOrderPos;
    userQueue.value = filteredUserQueue;
    userQueuePos.value = filteredUserQueuePos;
    shuffleEnabled.value = snapshot.shuffleEnabled;
    repeatMode.value = snapshot.repeatMode;

    const resumed = currentTrack.value;
    if (
      snapshot.position &&
      resumed?.id === snapshot.position.trackId &&
      resumed?.duration_secs != null
    ) {
      positionSecs.value = Math.min(snapshot.position.secs, resumed.duration_secs);
      durationSecs.value = resumed.duration_secs;
    }
  }

  // Restore once the library has loaded (or confirmed loaded-with-nothing),
  // so ids dropped from the catalog are actually known by then — restoring
  // against an empty, not-yet-fetched library would just drop everything.
  {
    const lib = useLibraryStore();
    let restored = false;
    const restoreOnce = (): void => {
      if (restored) return;
      restored = true;
      applyRestoredSnapshot();
    };
    if (lib.tracks.length > 0) {
      restoreOnce();
    } else {
      let wasLoading = lib.loading;
      const stopRestoreWatch = watch(
        () => lib.loading,
        (loading) => {
          if (wasLoading && !loading) {
            stopRestoreWatch();
            restoreOnce();
          }
          wasLoading = loading;
        },
      );
    }
  }

  // Q10: cast auto-advance must work from any screen, not just the Player
  // view — the store owns the subscription so it survives view unmounts.
  useCastStore().onTrackEnded(() => skipNext());

  /** Stops playback and drops all session state (logout). */
  function reset(): void {
    const el = audioEl.value;
    if (el) {
      el.pause();
      el.src = "";
    }
    if (preloadEl) {
      preloadEl.pause();
      preloadEl.removeAttribute("src");
      preloadEl.load();
      preloadEl = null;
    }
    preloadedTrack = null;
    preloadedMetadata = null;
    preloadSeq++;
    releaseWakeLock();
    cancelSleepTimer();
    clearTimeout(errorTimer);
    errorTimer = undefined;
    clearTimeout(snapshotTimer);
    snapshotTimer = undefined;
    playCountedTrackId = null;
    replayGainTags.value = null;
    metadataCache.clear();
    clearPlayHistory();
    try {
      localStorage.removeItem(PLAYER_STATE_KEY);
    } catch {
      /* ignore */
    }
    shuffleAllPool = [];
    shuffleAllActive.value = false;
    queue.value = [];
    playOrder.value = [];
    playOrderPos.value = -1;
    userQueue.value = [];
    userQueuePos.value = -1;
    isPlaying.value = false;
    positionSecs.value = 0;
    durationSecs.value = 0;
    shuffleEnabled.value = false;
    favorites.value = new Set();
    errorMessage.value = null;
  }

  return {
    queue,
    playOrder,
    playOrderPos,
    userQueue,
    userQueuePos,
    isPlaying,
    positionSecs,
    durationSecs,
    volume,
    shuffleEnabled,
    shuffleAllActive,
    repeatMode,
    favorites,
    errorMessage,
    sleepTimerRemainingMs,
    currentIndex,
    currentTrack,
    currentCoverUrl,
    progress,
    sleepTimerActive,
    upNext,
    initAudio,
    pauseForCast,
    playTrack,
    playPause,
    setVolume,
    toggleMute,
    skipNext,
    skipPrevious,
    seekTo,
    toggleShuffle,
    cycleRepeatMode,
    skipTo,
    addToQueue,
    addTracksToQueue,
    playNext,
    removeFromQueue,
    moveToUserQueue,
    clearQueue,
    reorderQueue,
    reorderUserQueue,
    startShuffleAll,
    toggleFavorite,
    startSleepTimer,
    cancelSleepTimer,
    reset,
  };
});

import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";
import { issueStreamToken, recordPlay, patchMetadata } from "../api/catalog";
import type { MetadataUpdate } from "../api/catalog";
import { streamUrl } from "../api/client";
import { showToast } from "../composables/useToast";
import { useLibraryStore } from "./library";
import { usePlaylistStore } from "./playlists";
import { useSettingsStore } from "./settings";
import type { CatalogTrack, RepeatMode } from "../types";

/** How many tracks "Shuffle all" queues up front and tops up with. */
const SHUFFLE_ALL_BATCH = 20;
/** Below this many seconds, Previous restarts the track instead of stepping back. */
const RESTART_THRESHOLD_SECS = 3;
const ERROR_CLEAR_MS = 4000;

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

  /** Display order — what QueueView shows and reorders. */
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

  const currentIndex = computed(() => playOrder.value[playOrderPos.value] ?? -1);
  const currentTrack = computed(() => queue.value[currentIndex.value] ?? null);
  const progress = computed(() =>
    durationSecs.value ? positionSecs.value / durationSecs.value : 0,
  );
  const sleepTimerActive = computed(() => sleepTimerRemainingMs.value > 0);
  /**
   * Up-next entries. `orderPos` is the entry's position within `playOrder` —
   * that is what reordering acts on, since this list *is* the play order.
   */
  const upNext = computed(() =>
    playOrder.value
      .slice(playOrderPos.value + 1)
      .map((queueIndex, i) => ({
        track: queue.value[queueIndex],
        queueIndex,
        orderPos: playOrderPos.value + 1 + i,
      }))
      .filter((e) => e.track != null),
  );

  const audioEl = ref<HTMLAudioElement | null>(null);
  // FLAC seeks reload the stream, so el.currentTime restarts at 0; this holds
  // the seconds the server skipped so the wall-clock position stays right.
  let flacSeekOffset = 0;
  let _seekSeq = 0;
  let errorTimer: ReturnType<typeof setTimeout> | undefined;
  let sleepInterval: ReturnType<typeof setInterval> | undefined;
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

  function initAudio(): HTMLAudioElement {
    if (audioEl.value) return audioEl.value;
    const el = new Audio();
    el.addEventListener("timeupdate", () => {
      positionSecs.value = el.currentTime + flacSeekOffset;
    });
    el.addEventListener("durationchange", () => {
      if (Number.isFinite(el.duration)) {
        durationSecs.value = el.duration + flacSeekOffset;
      } else {
        durationSecs.value = currentTrack.value?.duration_secs ?? 0;
      }
    });
    el.addEventListener("ended", () => {
      isPlaying.value = false;
      positionSecs.value = 0;
      if (repeatMode.value === "one") {
        void playCurrent(0);
        return;
      }
      advance(true);
    });
    el.addEventListener("play", () => (isPlaying.value = true));
    el.addEventListener("pause", () => (isPlaying.value = false));
    el.volume = volume.value;
    // Must be in the DOM for the iOS Now Playing widget to register.
    el.style.display = "none";
    document.body.appendChild(el);
    audioEl.value = el;
    setupMediaSession();
    return el;
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

  async function playCurrent(startSecs = 0): Promise<void> {
    const track = currentTrack.value;
    if (!track) return;
    const seq = ++_seekSeq;
    try {
      const token = await issueStreamToken(track.id);
      if (seq !== _seekSeq) return;
      const el = initAudio();
      flacSeekOffset = track.format === "flac" ? startSecs : 0;
      el.src = streamUrl(track.id, token, startSecs);
      positionSecs.value = startSecs;
      durationSecs.value = track.duration_secs ?? 0;
      await el.play();
      recordPlay(track.id);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  /** Steps to the next entry in `playOrder`, honouring repeat-all wraparound. */
  function advance(auto: boolean): void {
    if (playOrderPos.value + 1 < playOrder.value.length) {
      playOrderPos.value++;
      void playCurrent(0);
      return;
    }
    if (repeatMode.value === "all" && playOrder.value.length > 0) {
      playOrderPos.value = 0;
      void playCurrent(0);
      return;
    }
    if (auto) isPlaying.value = false;
  }

  async function playTrack(track: CatalogTrack, newQueue: CatalogTrack[]): Promise<void> {
    shuffleAllPool = [];
    shuffleAllActive.value = false;
    queue.value = [...newQueue];
    rebuildPlayOrder(newQueue.findIndex((t) => t.id === track.id));
    await playCurrent(0);
  }

  function playPause(): void {
    const el = audioEl.value;
    if (!el) return;
    if (el.paused) el.play().catch(() => null);
    else el.pause();
  }

  function skipNext(): void {
    advance(false);
  }

  function skipPrevious(): void {
    if (positionSecs.value > RESTART_THRESHOLD_SECS) {
      void seekTo(0);
      return;
    }
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

    if (track.format === "flac") {
      // FLAC: the server transcodes from ?start=, so the stream must reload.
      // Issue a fresh token — the original expires 60s after track start.
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
      // MP3: the server serves Range requests, so the browser seeks natively.
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

  function addTracksToQueue(tracks: CatalogTrack[]): void {
    if (tracks.length === 0) return;
    appendToQueue(tracks);
    showToast("Added to queue");
  }

  function addToQueue(track: CatalogTrack): void {
    addTracksToQueue([track]);
  }

  function removeFromQueue(track: CatalogTrack): void {
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

    if (wasCurrent) {
      if (currentTrack.value) void playCurrent(0);
      else {
        audioEl.value?.pause();
        positionSecs.value = 0;
      }
    }
  }

  /** Drops everything after the current track; the current one keeps playing. */
  function clearQueue(): void {
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

  async function saveMetadata(track: CatalogTrack, update: MetadataUpdate): Promise<void> {
    const current = track as unknown as Record<string, string | number | null>;
    const changed: Record<string, string | number | null> = {};
    for (const [key, raw] of Object.entries(update)) {
      const value = typeof raw === "string" ? (raw.trim() === "" ? null : raw.trim()) : raw ?? null;
      if (value !== (current[key] ?? null)) changed[key] = value;
    }
    if (Object.keys(changed).length === 0) {
      showToast("No changes to save");
      return;
    }
    try {
      await patchMetadata(track.id, changed as MetadataUpdate, false);
      await useLibraryStore().loadLibrary();
    } catch (e) {
      showToast(`Failed to save: ${(e as Error).message}`);
    }
  }

  watch(
    () => settings.continuousPlayback,
    (on) => {
      repeatMode.value = on ? "all" : "off";
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
  });

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
  watch(currentTrack, (t) => {
    if (t?.has_cover) useLibraryStore().requestCover(t.id);
  });

  /** Stops playback and drops all session state (logout). */
  function reset(): void {
    const el = audioEl.value;
    if (el) {
      el.pause();
      el.src = "";
    }
    cancelSleepTimer();
    clearTimeout(errorTimer);
    errorTimer = undefined;
    shuffleAllPool = [];
    shuffleAllActive.value = false;
    queue.value = [];
    playOrder.value = [];
    playOrderPos.value = -1;
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
    playTrack,
    playPause,
    skipNext,
    skipPrevious,
    seekTo,
    toggleShuffle,
    cycleRepeatMode,
    skipTo,
    addToQueue,
    addTracksToQueue,
    removeFromQueue,
    clearQueue,
    reorderQueue,
    startShuffleAll,
    toggleFavorite,
    startSleepTimer,
    cancelSleepTimer,
    saveMetadata,
    reset,
  };
});

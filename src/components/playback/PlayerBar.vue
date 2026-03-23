<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { useCastStore } from "../../stores/cast";
import { usePlaylistStore } from "../../stores/playlists";
import { usePlaylistAdd } from "../../composables/usePlaylistAdd";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import StarRating from "../shared/StarRating.vue";
import VolumeControl from "./VolumeControl.vue";
import FeatherIcon from "../shared/FeatherIcon.vue";
import CastButton from "./CastButton.vue";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type { CatalogTrack } from "../../types";

const emit = defineEmits<{
  (e: "expand"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const castStore = useCastStore();
const { isCasting } = storeToRefs(castStore);
const { selectedTracks, tableOrderedTracks, queueTracks } = storeToRefs(store);
const {
  autoplayOnSelect,
  continuousPlayback,
  shuffle,
  repeat,
  playbarShowAlbumInMarquee,
  playbarDisableMarquee,
  volume,
} = storeToRefs(settingsStore);

/** List used for next/previous: queue when filled and shuffle off, else table. */
const playbackList = computed(() => {
  if (shuffle.value) return tableOrderedTracks.value;
  if (queueTracks.value.length > 0) return queueTracks.value;
  return tableOrderedTracks.value;
});

function getNextTrack(forAutoAdvance = false): CatalogTrack | null {
  const current = singleTrack.value;
  const list = playbackList.value;
  if (!current || !list.length) return null;
  if (forAutoAdvance && repeat.value === "one") return current;
  if (shuffle.value) {
    const others = list.filter((t) => t.id !== current.id);
    return others.length > 0 ? others[Math.floor(Math.random() * others.length)] : null;
  }
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx >= 0 && idx + 1 < list.length) return list[idx + 1];
  if (idx < 0 && list.length > 0) return list[0];
  if (forAutoAdvance && repeat.value === "all") return list[0];
  if (idx >= 0 && idx === list.length - 1 && continuousPlayback.value) {
    const table = tableOrderedTracks.value;
    if (table.length === 0) return null;
    const tableIdx = table.findIndex((t) => t.id === current.id);
    if (tableIdx >= 0 && tableIdx + 1 < table.length) return table[tableIdx + 1];
    return table[0];
  }
  return null;
}

function getPreviousTrack(): CatalogTrack | null {
  const current = singleTrack.value;
  const list = playbackList.value;
  if (!current || !list.length) return null;
  if (shuffle.value) {
    const idx = list.findIndex((t) => t.id === current.id);
    if (idx <= 0) return null;
    return list[idx - 1];
  }
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx > 0) return list[idx - 1];
  return null;
}

const audioRef = ref<HTMLAudioElement | null>(null);
const isPlaying = ref(false);
const audioSrc = ref("");
const currentTime = ref(0);
const duration = ref(0);
const isSeeking = ref(false);
let shouldAutoplayNextSelection = false;
const hasInitializedAutoplay = ref(false);
let unlistenCastTrackEnded: (() => void) | null = null;

/** Small cache of preloaded audio blobs (next track, maybe a couple more). Keyed by track path. */
const audioCache = new Map<string, string>();

const marqueeContainerRef = ref<HTMLDivElement | null>(null);
const shouldScrollMarquee = ref(false);
const marqueeDistance = ref(0);

const titlePopover = ref<{ text: string; x: number; y: number } | null>(null);
let titlePopoverHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTitlePopover(text: string, e: MouseEvent) {
  if (!playbarDisableMarquee.value) return;
  if (titlePopoverHideTimeout) clearTimeout(titlePopoverHideTimeout);
  titlePopoverHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  titlePopover.value = { text, x: rect.left + rect.width / 2, y: rect.top - 8 };
}

function scheduleHideTitlePopover() {
  if (!playbarDisableMarquee.value) return;
  titlePopoverHideTimeout = setTimeout(() => {
    titlePopover.value = null;
    titlePopoverHideTimeout = null;
  }, 80);
}

function cancelHideTitlePopover() {
  if (titlePopoverHideTimeout) clearTimeout(titlePopoverHideTimeout);
  titlePopoverHideTimeout = null;
}

function hideTitlePopover() {
  titlePopover.value = null;
  if (titlePopoverHideTimeout) clearTimeout(titlePopoverHideTimeout);
  titlePopoverHideTimeout = null;
}

const playlistStore = usePlaylistStore();
const { playlists } = storeToRefs(playlistStore);
const { tryAddToPlaylist } = usePlaylistAdd();

const contextMenu = ref<{ x: number; y: number } | null>(null);
const contextMenuRef = ref<HTMLElement | null>(null);
const playlistSubmenuOpen = ref(false);
const playlistBtnContainerRef = ref<HTMLElement | null>(null);
const playlistSubmenuFlipUp = computed(() => {
  if (!playlistBtnContainerRef.value) return false;
  const rect = playlistBtnContainerRef.value.getBoundingClientRect();
  return rect.bottom + 220 > window.innerHeight;
});

function onAlbumArtContextMenu(e: MouseEvent) {
  e.preventDefault();
  if (!singleTrack.value) return;
  contextMenu.value = { x: e.clientX, y: e.clientY };
}

function closeContextMenu() {
  contextMenu.value = null;
  playlistSubmenuOpen.value = false;
}

watch(contextMenu, (menu) => {
  if (!menu) return;
  const onOutside = (e: MouseEvent) => {
    if (contextMenuRef.value?.contains(e.target as Node)) return;
    closeContextMenu();
    document.removeEventListener("click", onOutside);
    document.removeEventListener("keydown", onEscapeMenu);
  };
  const onEscapeMenu = (e: KeyboardEvent) => {
    if (e.key === "Escape") {
      closeContextMenu();
      document.removeEventListener("click", onOutside);
      document.removeEventListener("keydown", onEscapeMenu);
    }
  };
  nextTick(() => setTimeout(() => {
    document.addEventListener("click", onOutside);
    document.addEventListener("keydown", onEscapeMenu);
  }, 0));
});

function viewSong() {
  const track = singleTrack.value;
  if (!track) return;
  store.setRevealTrackId(track.id);
  closeContextMenu();
}


async function setRating(rating: number | null) {
  const track = singleTrack.value;
  if (!track) return;
  closeContextMenu();
  await store.setRating([track.path], rating);
}

function addToQueue() {
  const track = singleTrack.value;
  if (!track) return;
  store.addToQueue([track.id]);
  closeContextMenu();
}

async function addToPlaylist(playlistId: number) {
  const track = singleTrack.value;
  if (!track) return;
  const playlist = playlists.value.find((p) => p.id === playlistId);
  closeContextMenu();
  await tryAddToPlaylist(playlistId, [track.id], playlist?.name ?? "");
}

function formatTime(secs: number): string {
  if (!Number.isFinite(secs) || secs < 0) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

let positionSaveTimer: ReturnType<typeof setTimeout> | null = null;

function schedulePositionSave(positionSecs: number) {
  if (positionSaveTimer) clearTimeout(positionSaveTimer);
  positionSaveTimer = setTimeout(() => {
    positionSaveTimer = null;
    settingsStore.setSessionState(
      store.queueTrackIds,
      store.currentPlayingTrackId,
      positionSecs,
    );
  }, 5000);
}

function onTimeUpdate() {
  if (isSeeking.value) return;
  const el = audioRef.value;
  if (!el) return;
  currentTime.value = el.currentTime;
  if ("mediaSession" in navigator && Number.isFinite(el.duration) && el.duration > 0) {
    navigator.mediaSession.setPositionState({
      duration: el.duration,
      position: el.currentTime,
      playbackRate: el.playbackRate,
    });
  }
  // Save position every ~5 s (timer resets on each tick, so only fires after a 5 s idle)
  schedulePositionSave(el.currentTime);
}

function onDurationChange() {
  const el = audioRef.value;
  if (el) duration.value = Number.isFinite(el.duration) ? el.duration : 0;
}

function seekTo(secs: number) {
  const el = audioRef.value;
  currentTime.value = secs;

  if (isCasting.value) {
    // Pause local audio, seek both, wait for cast to confirm playing before resuming
    const wasPlaying = el ? !el.paused : false;
    if (el && wasPlaying) {
      el.pause();
    }
    if (el) el.currentTime = secs;
    if (wasPlaying) {
      castStore.setPendingCastResume(true);
      // Fallback: resume after 15s in case cast never responds
      setTimeout(() => {
        if (castStore.pendingCastResume) {
          castStore.setPendingCastResume(false);
          audioRef.value?.play().catch(console.error);
        }
      }, 15000);
    }
    invoke("cast_seek", { positionSecs: secs }).catch(console.error);
  } else if (el) {
    el.currentTime = secs;
  }
}

function onSeekInput(e: Event) {
  seekTo(parseFloat((e.target as HTMLInputElement).value));
}

const PROGRESS_THUMB_HALF = 6;

function onProgressBarClick(e: MouseEvent) {
  const input = e.currentTarget as HTMLInputElement;
  const rect = input.getBoundingClientRect();
  const trackWidth = rect.width - 2 * PROGRESS_THUMB_HALF;
  if (trackWidth <= 0) return;
  const x = e.clientX - rect.left - PROGRESS_THUMB_HALF;
  const ratio = Math.max(0, Math.min(1, x / trackWidth));
  const d = displayDuration.value;
  if (!d || !Number.isFinite(d)) return;
  seekTo(ratio * d);
}

function onSeekMouseDown() {
  isSeeking.value = true;
}

function onSeekMouseUp() {
  isSeeking.value = false;
}

const singleTrack = computed(() => {
  const tracks = selectedTracks.value;
  return tracks.length === 1 ? tracks[0] : null;
});

const marqueeTitle = computed(() => {
  const t = singleTrack.value;
  if (!t) return "";
  const parts: string[] = [];
  const baseTitle = t.title || t.path.split(/[/\\]/).pop() || "Track";
  if (baseTitle) parts.push(baseTitle);
  if (playbarShowAlbumInMarquee.value && t.album) parts.push(t.album);
  if (t.artist) parts.push(t.artist);
  return parts.join(" · ");
});

const displayDuration = computed(() => {
  const fromTrack = singleTrack.value?.duration_secs;
  if (fromTrack != null && Number.isFinite(fromTrack) && fromTrack >= 0) return fromTrack;
  return duration.value;
});

const progressPercent = computed(() => {
  const d = displayDuration.value;
  if (!d || !Number.isFinite(d)) return 0;
  return Math.min(100, (currentTime.value / d) * 100);
});

function recomputeMarquee() {
  nextTick(() => {
    const el = marqueeContainerRef.value;
    if (!el) {
      shouldScrollMarquee.value = false;
      marqueeDistance.value = 0;
      return;
    }
    const diff = el.scrollWidth - el.clientWidth;
    if (diff > 4) {
      shouldScrollMarquee.value = true;
      marqueeDistance.value = diff;
    } else {
      shouldScrollMarquee.value = false;
      marqueeDistance.value = 0;
    }
  });
}

watch(playbarDisableMarquee, () => {
  hideTitlePopover();
  recomputeMarquee();
});

function revokeUrl(url: string | null) {
  if (!url) return;
  try {
    URL.revokeObjectURL(url);
  } catch {
    // ignore
  }
}

function evictOldCacheEntries(maxEntries = 3) {
  while (audioCache.size > maxEntries) {
    const firstKey = audioCache.keys().next().value as string | undefined;
    if (!firstKey) break;
    const url = audioCache.get(firstKey) ?? null;
    audioCache.delete(firstKey);
    revokeUrl(url);
  }
}

async function updateMediaSession(track: CatalogTrack | null) {
  if (!("mediaSession" in navigator)) return;
  if (!track) {
    navigator.mediaSession.metadata = null;
    navigator.mediaSession.playbackState = "none";
    return;
  }
  await store.fetchCover(track.path);
  const coverUrl = store.getCoverDataUrl(track.path);
  const artwork: MediaImage[] = coverUrl ? [{ src: coverUrl }] : [];
  navigator.mediaSession.metadata = new MediaMetadata({
    title: track.title || track.path.split(/[/\\]/).pop() || "Track",
    artist: track.artist || "",
    album: track.album || "",
    artwork,
  });
}

async function loadAudioBlobForCurrent(track: CatalogTrack) {
  const path = track.path;
  const cached = audioCache.get(path);

  // Stop current playback and reset state.
  if (audioRef.value) {
    audioRef.value.pause();
  }
  if (audioSrc.value && audioSrc.value !== cached) {
    // Only revoke if it's not the cached URL we are about to reuse.
    revokeUrl(audioSrc.value);
  }
  audioSrc.value = "";
  isPlaying.value = false;
  currentTime.value = 0;
  duration.value = 0;

  if (cached) {
    // Fast path: reuse preloaded blob URL.
    audioSrc.value = cached;
    audioCache.delete(path);
    return;
  }

  // Slow path: read from disk, then set src.
  try {
    const base64 = await invoke<string>("read_audio_file", { path });
    const binary = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const ext = path.toLowerCase().endsWith(".flac") ? "flac" : "mpeg";
    const blob = new Blob([binary], { type: `audio/${ext}` });
    audioSrc.value = URL.createObjectURL(blob);
  } catch {
    audioSrc.value = "";
  }
}

async function preloadTrack(track: CatalogTrack | null) {
  if (!track) return;
  const path = track.path;
  if (audioCache.has(path) || path === singleTrack.value?.path) return;
  try {
    const base64 = await invoke<string>("read_audio_file", { path });
    const binary = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const ext = path.toLowerCase().endsWith(".flac") ? "flac" : "mpeg";
    const blob = new Blob([binary], { type: `audio/${ext}` });
    const url = URL.createObjectURL(blob);
    audioCache.set(path, url);
    evictOldCacheEntries();
  } catch {
    // ignore preload failures; playback path will try again.
  }
}

async function preloadNextTrack() {
  const next = getNextTrack();
  if (!next) return;
  await preloadTrack(next);
}

watch(
  singleTrack,
  (track, oldTrack) => {
    if (!track) {
      if (audioSrc.value) {
        revokeUrl(audioSrc.value);
        audioSrc.value = "";
      }
      isPlaying.value = false;
      store.setCurrentPlaying(null);
      shouldScrollMarquee.value = false;
      marqueeDistance.value = 0;
      updateMediaSession(null);
      return;
    }
    store.setCurrentPlaying(track.id);
    updateMediaSession(track);
    recomputeMarquee();

    // Same file path means only metadata changed (e.g. after a metadata save + catalog
    // reload). Audio is already loaded and possibly playing — don't interrupt it.
    if (track.path === oldTrack?.path) return;

    // If a cast device is configured, load the new track on it and hold local
    // audio until the cast confirms it's playing (pendingCastResume).
    const castDeviceId = castStore.connectedDeviceId;
    if (castDeviceId) {
      castStore.setPendingCastResume(true);
      setTimeout(() => {
        if (castStore.pendingCastResume) {
          castStore.setPendingCastResume(false);
          audioRef.value?.play().catch(console.error);
        }
      }, 15000);
      invoke("cast_play", { deviceId: castDeviceId, trackPath: track.path }).catch(
        (e: unknown) => {
          console.error("[Cast] cast_play on track change:", e);
          castStore.setPendingCastResume(false);
        },
      );
    }

    loadAudioBlobForCurrent(track).then(() => {
      if (!hasInitializedAutoplay.value) {
        hasInitializedAutoplay.value = true;
        // On startup, restore saved position if this is the previously playing track.
        const savedId = settingsStore.sessionCurrentTrackId;
        const savedPos = settingsStore.sessionCurrentPositionSecs;
        if (savedId === track.id && savedPos > 0) {
          nextTick(() => {
            const el = audioRef.value;
            if (el) el.currentTime = savedPos;
          });
        }
        return;
      }
      const shouldAutoplay =
        autoplayOnSelect.value || shouldAutoplayNextSelection || store.playRequestTrackId === track.id;
      if (store.playRequestTrackId === track.id) store.setPlayRequestTrackId(null);
      if (!shouldAutoplay) return;
      shouldAutoplayNextSelection = false;
      nextTick(() => {
        const el = audioRef.value;
        if (!el) return;
        if (castStore.pendingCastResume) return; // cast will trigger play when it's ready
        el.play().catch(() => {});
      });
      // Once the current track is ready, start preloading the upcoming one in the background.
      preloadNextTrack();
    });
  },
  { immediate: true }
);

// Mute local audio while casting — it keeps playing for position tracking
watch(isCasting, (casting) => {
  const el = audioRef.value;
  if (el) el.muted = casting;
});

// Sync local audio state with cast device state changes
watch(() => castStore.castStatus.status, (status) => {
  if (castStore.pendingCastResume && status === "playing") {
    // Cast confirmed playing after a seek or track change — resume local tracker
    castStore.setPendingCastResume(false);
    audioRef.value?.play().catch(console.error);
  } else if (status === "paused" && !castStore.pendingCastResume) {
    // Device was paused externally (e.g. "hey Google, pause/stop") — mirror to local
    const el = audioRef.value;
    if (el && !el.paused) el.pause();
    // Sync position so the progress bar stays accurate after an external pause
    const s = castStore.castStatus;
    if (el && s.status === "paused" && s.positionSecs != null) {
      el.currentTime = s.positionSecs;
    }
  }
});

function togglePlay() {
  const el = audioRef.value;
  if (!el) return;
  if (el.paused) {
    el.play().catch(() => {});
    isPlaying.value = true;
    if (isCasting.value) invoke("cast_resume").catch(console.error);
  } else {
    el.pause();
    isPlaying.value = false;
    if (isCasting.value) invoke("cast_pause").catch(console.error);
  }
}

function onAudioPlay() {
  isPlaying.value = true;
  // Cast commands are only sent from explicit user actions (togglePlay/seekTo), not audio events.
  // Audio elements fire play/pause for buffering, loading, and seeking — mirroring those
  // to the cast device causes intermittent pause/resume storms.
  if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "playing";
}

function onAudioPause() {
  isPlaying.value = false;
  if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "paused";
  // Flush position save immediately on pause
  if (positionSaveTimer) {
    clearTimeout(positionSaveTimer);
    positionSaveTimer = null;
  }
  settingsStore.setSessionState(
    store.queueTrackIds,
    store.currentPlayingTrackId,
    audioRef.value?.currentTime ?? 0,
  );
}

function onAudioEnded() {
  isPlaying.value = false;
  if (playbackList.value === tableOrderedTracks.value && !continuousPlayback.value && repeat.value === "none") return;
  const next = getNextTrack(true);
  if (!next) return;
  if (next.id !== singleTrack.value?.id) {
    const queueIdx = store.queueTrackIds.indexOf(next.id);
    if (queueIdx >= 0) store.removeFromQueueAtIndex(queueIdx);
    store.clearSelection();
    store.toggleSelection(next.id);
  } else {
    // repeat one: restart in place
    seekTo(0);
    nextTick(() => audioRef.value?.play().catch(() => {}));
    return;
  }
  shouldAutoplayNextSelection = true;
}

function playNext() {
  const next = getNextTrack();
  if (!next) return;
  const queueIdx = store.queueTrackIds.indexOf(next.id);
  if (queueIdx >= 0) store.removeFromQueueAtIndex(queueIdx);
  shouldAutoplayNextSelection = true;
  store.clearSelection();
  store.toggleSelection(next.id);
}

function playPrevious() {
  const prev = getPreviousTrack();
  if (!prev) return;
  shouldAutoplayNextSelection = true;
  store.clearSelection();
  store.toggleSelection(prev.id);
}

function restart() {
  seekTo(0);
  const el = audioRef.value;
  if (el && isPlaying.value) el.play().catch(() => {});
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key !== "Enter" || !singleTrack.value) return;
  const target = e.target as HTMLElement;
  const tag = target.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable) return;
  e.preventDefault();
  togglePlay();
}

onMounted(async () => {
  document.addEventListener("keydown", onGlobalKeydown);
  recomputeMarquee();
  window.addEventListener("resize", recomputeMarquee);
  nextTick(() => {
    const el = audioRef.value;
    if (!el) return;
    const v = Math.min(1, Math.max(0, volume.value ?? 0.25));
    el.volume = v;
    el.muted = isCasting.value;
  });

  // When cast finishes the track naturally, advance the queue just like onAudioEnded
  unlistenCastTrackEnded = await listen("cast:track-ended", () => {
    onAudioEnded();
  });
  if ("mediaSession" in navigator) {
    navigator.mediaSession.setActionHandler("play", () => audioRef.value?.play().catch(() => {}));
    navigator.mediaSession.setActionHandler("pause", () => audioRef.value?.pause());
    navigator.mediaSession.setActionHandler("previoustrack", () => playPrevious());
    navigator.mediaSession.setActionHandler("nexttrack", () => playNext());
    navigator.mediaSession.setActionHandler("seekto", (details) => {
      if (details.seekTime != null && audioRef.value) {
        audioRef.value.currentTime = details.seekTime;
        currentTime.value = details.seekTime;
      }
    });
  }
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
  window.removeEventListener("resize", recomputeMarquee);
  if (unlistenCastTrackEnded) unlistenCastTrackEnded();
  // Cleanup any cached object URLs to avoid leaks.
  revokeUrl(audioSrc.value);
  audioSrc.value = "";
  for (const url of audioCache.values()) {
    revokeUrl(url);
  }
  audioCache.clear();
});
</script>

<template>
  <div
    v-if="singleTrack"
    class="flex shrink-0 flex-col items-center gap-2 border-t border-stone-700 px-3 py-2"
  >
    <audio
      ref="audioRef"
      :src="audioSrc"
      class="hidden"
      data-muorg-player="true"
      @play="onAudioPlay"
      @pause="onAudioPause"
      @ended="onAudioEnded"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onDurationChange"
      @durationchange="onDurationChange"
    />
    <div class="flex min-w-0 w-full items-center gap-3">
      <div class="flex min-w-0 max-w-[260px] shrink-0 items-center gap-2 rounded px-1 py-0.5 transition-colors hover:bg-stone-600/50 cursor-context-menu" @contextmenu="onAlbumArtContextMenu">
        <TrackAlbumArt v-if="singleTrack" :path="singleTrack.path" size="medium" />
        <div class="min-w-0 flex-1">
          <div
            ref="marqueeContainerRef"
            class="text-xs font-medium"
            :class="playbarDisableMarquee ? 'truncate' : 'metadata-marquee w-48'"
          >
            <template v-if="playbarDisableMarquee">
              <span
                class="text-stone-200"
                @mouseenter="showTitlePopover(marqueeTitle, $event)"
                @mouseleave="scheduleHideTitlePopover"
              >
                {{ marqueeTitle }}
              </span>
            </template>
            <template v-else>
              <template v-if="!shouldScrollMarquee">
                <span class="text-stone-200">
                  {{ marqueeTitle }}
                </span>
              </template>
              <div
                v-else
                class="metadata-marquee-inner"
                :style="{ '--marquee-distance': marqueeDistance + 'px' }"
              >
                <span class="text-stone-200">
                  {{ marqueeTitle }}
                </span>
              </div>
            </template>
          </div>
        </div>
      </div>
      <div class="flex min-w-0 flex-1 items-center gap-3">
        <div class="flex shrink-0 items-center gap-0.5">
          <button
            type="button"
            class="flex items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-40"
            aria-label="Previous track"
            @click="playPrevious"
            :disabled="!singleTrack || !getPreviousTrack()"
          >
            <FeatherIcon name="skip-back" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="flex items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Restart from beginning"
            @click="restart"
          >
            <FeatherIcon name="square" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="player-play-btn flex items-center justify-center rounded bg-[#5b7c32] p-1.5 text-stone-50 hover:bg-[#6d8f3d]"
            :aria-label="isPlaying ? 'Pause' : 'Play'"
            @click="togglePlay"
          >
            <FeatherIcon v-if="!isPlaying" name="play" class="h-4 w-4" />
            <FeatherIcon v-else name="pause" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="flex items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-40"
            aria-label="Next track"
            @click="playNext"
            :disabled="!singleTrack || !getNextTrack()"
          >
            <FeatherIcon name="skip-forward" class="h-4 w-4" />
          </button>
        </div>
        <div class="flex min-w-0 flex-1 items-center gap-2">
          <span class="shrink-0 w-8 text-right text-xs text-stone-500 tabular-nums">{{ formatTime(currentTime) }}</span>
          <input
            type="range"
            min="0"
            :max="displayDuration > 0 ? displayDuration : 0.01"
            step="0.1"
            :value="currentTime"
            class="player-progress-slider h-1.5 min-w-0 flex-1 cursor-pointer appearance-none rounded-full bg-stone-600 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full"
            :style="{ '--progress-percent': progressPercent + '%' }"
            aria-label="Seek"
            @click="onProgressBarClick"
            @input="onSeekInput"
            @mousedown="onSeekMouseDown"
            @mouseup="onSeekMouseUp"
            @mouseleave="onSeekMouseUp"
          />
          <span class="shrink-0 w-8 text-left text-xs text-stone-500 tabular-nums">{{ formatTime(displayDuration) }}</span>
          <button
            type="button"
            class="flex shrink-0 items-center justify-center rounded p-1.5 hover:bg-stone-600 hover:text-stone-200"
            :class="shuffle ? 'shuffle-active-bg text-stone-50' : 'text-stone-400'"
            aria-label="Shuffle next track"
            :aria-pressed="shuffle"
            @click="settingsStore.setShuffle(!shuffle)"
          >
            <FeatherIcon name="shuffle" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="relative flex shrink-0 items-center justify-center rounded p-1.5 hover:bg-stone-600 hover:text-stone-200"
            :class="repeat !== 'none' ? 'shuffle-active-bg text-stone-50' : 'text-stone-400'"
            :aria-label="repeat === 'none' ? 'Repeat off' : repeat === 'one' ? 'Repeat one' : 'Repeat all'"
            @click="settingsStore.setRepeat(repeat === 'none' ? 'all' : repeat === 'all' ? 'one' : 'none')"
          >
            <FeatherIcon name="repeat" class="h-4 w-4" />
            <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
          </button>
        </div>
      </div>
      <div class="ml-3 flex w-44 shrink-0 items-center justify-end gap-1.5">
        <VolumeControl mode="metadata" />
        <CastButton />
        <button
          type="button"
          class="flex items-center justify-center rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-100"
          aria-label="Expand player"
          title="Expand player"
          @click="emit('expand')"
        >
          <FeatherIcon name="maximize-2" class="h-4 w-4" />
        </button>
      </div>
    </div>
    <Teleport to="body">
      <div
        v-if="titlePopover"
        class="fixed z-[250] max-w-[420px] whitespace-pre-line rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        :style="{ left: titlePopover.x + 'px', top: titlePopover.y + 'px', transform: 'translate(-50%, -100%)' }"
        @mouseenter="cancelHideTitlePopover"
        @mouseleave="hideTitlePopover"
      >
        {{ titlePopover.text }}
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="contextMenu"
        ref="contextMenuRef"
        class="fixed z-[301] min-w-[160px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px', transform: 'translateY(-100%)' }"
        @click.stop
      >
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
          @click="viewSong"
        >
          <FeatherIcon name="list" class="h-4 w-4 shrink-0 text-stone-400" />
          View song
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
          @click="addToQueue"
        >
          <FeatherIcon name="clock" class="h-4 w-4 shrink-0 text-stone-400" />
          Add to queue
        </button>
        <div class="my-1 border-t border-stone-700" />
        <div
          ref="playlistBtnContainerRef"
          class="relative"
          @mouseenter="playlistSubmenuOpen = true"
          @mouseleave="playlistSubmenuOpen = false"
        >
          <button
            type="button"
            class="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
            @click="playlistSubmenuOpen = !playlistSubmenuOpen"
          >
            <span class="flex items-center gap-2">
              <FeatherIcon name="plus" class="h-4 w-4 shrink-0 text-stone-400" />
              Add to playlist
            </span>
            <FeatherIcon name="chevron-right" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
          </button>
          <!-- Transparent bridge covering the gap between button and submenu -->
          <div class="absolute right-0 top-0 h-full w-2 translate-x-full" />
          <div
            v-if="playlistSubmenuOpen && playlists.length"
            class="absolute left-full z-[310] min-w-[160px] max-w-[220px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
            :class="playlistSubmenuFlipUp ? 'bottom-0' : 'top-0'"
            style="margin-left: 2px"
          >
            <button
              v-for="pl in playlists"
              :key="pl.id"
              type="button"
              class="flex w-full min-w-0 items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
              @click="addToPlaylist(pl.id)"
            >
              <span v-if="pl.icon" class="shrink-0 text-sm leading-none">{{ pl.icon }}</span>
              <FeatherIcon v-else name="list" class="h-3.5 w-3.5 shrink-0 text-stone-400" />
              <span class="min-w-0 truncate">{{ pl.name }}</span>
            </button>
          </div>
          <div
            v-else-if="playlistSubmenuOpen && !playlists.length"
            class="absolute left-full z-[310] min-w-[160px] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 shadow-xl text-xs text-stone-500"
            :class="playlistSubmenuFlipUp ? 'bottom-0' : 'top-0'"
            style="margin-left: 2px"
          >
            No playlists yet.
          </div>
        </div>
        <div class="my-1 border-t border-stone-700" />
        <div class="flex items-center justify-center px-3 py-2">
          <StarRating
            :model-value="singleTrack?.rating ?? null"
            @update:model-value="setRating"
          />
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.player-volume-slider,
.player-progress-slider {
  accent-color: #5b7c32;
}
.player-volume-slider::-webkit-slider-thumb,
.player-progress-slider::-webkit-slider-thumb {
  background: #5b7c32;
}
.player-progress-slider {
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--progress-percent, 0%),
    rgb(87 83 78) var(--progress-percent, 0%),
    rgb(87 83 78) 100%
  ) !important;
  border-radius: 9999px;
}
.player-progress-slider::-webkit-slider-runnable-track {
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--progress-percent, 0%),
    rgb(87 83 78) var(--progress-percent, 0%),
    rgb(87 83 78) 100%
  );
  border-radius: 9999px;
}
.player-progress-slider::-moz-range-progress {
  background: #5b7c32;
  border-radius: 9999px;
}
.player-progress-slider::-moz-range-track {
  background: rgb(87 83 78);
  border-radius: 9999px;
}
.metadata-marquee {
  position: relative;
  overflow: hidden;
  white-space: nowrap;
}
.metadata-marquee-inner {
  display: inline-block;
  will-change: transform;
  animation: metadata-marquee-bounce 4s ease-in-out infinite alternate;
}
@keyframes metadata-marquee-bounce {
  0%,
  15% {
    transform: translateX(0);
  }
  85%,
  100% {
    transform: translateX(calc(-1 * var(--marquee-distance)));
  }
}
</style>



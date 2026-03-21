<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import type { CatalogTrack } from "../../types";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { useCastStore } from "../../stores/cast";
import { usePlaylistStore } from "../../stores/playlists";
import { usePlaylistAdd } from "../../composables/usePlaylistAdd";
import { invoke } from "@tauri-apps/api/core";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import VolumeControl from "./VolumeControl.vue";
import FeatherIcon from "../shared/FeatherIcon.vue";
import CastButton from "./CastButton.vue";
import StarRating from "../shared/StarRating.vue";

const props = withDefaults(
  defineProps<{
    /** When true, hide the expand (fullscreen) button (e.g. when already in overlay). */
    hideExpand?: boolean;
    /** When true, use fullscreen layout: massive centered art, gradient background, controls at bottom. */
    expandedLayout?: boolean;
    /** CSS vars for accent hues (--player-accent, --player-accent-play, etc.). Bland => primary; vivid => album variants. */
    accentStyle?: Record<string, string>;
    /** When set (Player/Queue tab), bottom panel height in px; used to scale album art with panel. */
    panelHeightPx?: number;
  }>(),
  { panelHeightPx: undefined }
);

const emit = defineEmits<{
  (e: "expand"): void;
  (e: "minimize"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const castStore = useCastStore();
const { selectedTracks, tableOrderedTracks, queueTracks } = storeToRefs(store);
const { shuffle, repeat, continuousPlayback, playbarShowAlbumInMarquee, playbarShowRatingInMaximized } = storeToRefs(settingsStore);
const { isCasting } = storeToRefs(castStore);

/** Album art size in px when panel height is provided; scales with panel up to full available height. */
const playbarAlbumArtSizePx = computed(() => {
  const h = props.panelHeightPx;
  if (h == null || h <= 0) return undefined;
  const reserved = 156; // handle(4) + padding(24) + gaps(16) + title(20) + controls(60) + progress(32)
  return Math.max(128, h - reserved);
});

/** List used for next/previous: queue when filled and shuffle off, else table. */
const playbackList = computed(() => {
  if (shuffle.value) return tableOrderedTracks.value;
  if (queueTracks.value.length > 0) return queueTracks.value;
  return tableOrderedTracks.value;
});

function getNextTrack(): CatalogTrack | null {
  const current = singleTrack.value;
  const list = playbackList.value;
  if (!current || !list.length) return null;
  if (shuffle.value) {
    const others = list.filter((t) => t.id !== current.id);
    return others.length > 0 ? others[Math.floor(Math.random() * others.length)] : null;
  }
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx >= 0 && idx + 1 < list.length) return list[idx + 1];
  if (idx < 0 && list.length > 0) return list[0];
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

const isPlaying = ref(false);
const currentTime = ref(0);
const duration = ref(0);
const isSeeking = ref(false);

function getAudio(): HTMLAudioElement | null {
  return document.querySelector('audio[data-muorg-player="true"]') as HTMLAudioElement | null;
}

function formatTime(secs: number): string {
  if (!Number.isFinite(secs) || secs < 0) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function syncFromAudio() {
  const el = getAudio();
  if (!el) return;
  if (!isSeeking.value) currentTime.value = el.currentTime;
  duration.value = Number.isFinite(el.duration) ? el.duration : duration.value;
  isPlaying.value = !el.paused;
}

function seekTo(secs: number) {
  const el = getAudio();
  currentTime.value = secs;
  if (isCasting.value) {
    const wasPlaying = el ? !el.paused : false;
    if (el && wasPlaying) el.pause();
    if (el) el.currentTime = secs;
    if (wasPlaying) {
      castStore.setPendingCastResume(true);
      setTimeout(() => {
        if (castStore.pendingCastResume) {
          castStore.setPendingCastResume(false);
          getAudio()?.play().catch(console.error);
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

const playbarTitleLine = computed(() => {
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

function attachAudioListeners() {
  const el = getAudio();
  if (!el) return;
  // Remove first to avoid duplicates if the element was recreated.
  el.removeEventListener("timeupdate", syncFromAudio);
  el.removeEventListener("play", syncFromAudio);
  el.removeEventListener("pause", syncFromAudio);
  el.removeEventListener("loadedmetadata", syncFromAudio);
  el.removeEventListener("durationchange", syncFromAudio);
  el.removeEventListener("volumechange", syncFromAudio);
  el.removeEventListener("ended", onAudioEnded);
  el.addEventListener("timeupdate", syncFromAudio);
  el.addEventListener("play", syncFromAudio);
  el.addEventListener("pause", syncFromAudio);
  el.addEventListener("loadedmetadata", syncFromAudio);
  el.addEventListener("durationchange", syncFromAudio);
  el.addEventListener("volumechange", syncFromAudio);
  el.addEventListener("ended", onAudioEnded);
}

watch(
  singleTrack,
  (track) => {
    if (!track) return;
    // The audio element lives inside PlayerBar's v-if="singleTrack". When the selection
    // changes the element may be destroyed and recreated, so we must re-attach listeners
    // after Vue has flushed the DOM update.
    nextTick(() => {
      attachAudioListeners();
      syncFromAudio();
    });
  },
  { immediate: true },
);

function togglePlay() {
  const el = getAudio();
  if (!el) return;
  if (el.paused) {
    el.play().catch(() => {});
    if (isCasting.value) invoke("cast_resume").catch(console.error);
  } else {
    el.pause();
    if (isCasting.value) invoke("cast_pause").catch(console.error);
  }
  isPlaying.value = !el.paused;
}

function onAudioEnded() {
  isPlaying.value = false;
}

function playNext() {
  const next = getNextTrack();
  if (!next) return;
  const queueIdx = store.queueTrackIds.indexOf(next.id);
  if (queueIdx >= 0) store.removeFromQueueAtIndex(queueIdx);
  store.clearSelection();
  store.toggleSelection(next.id);
}

function playPrevious() {
  const prev = getPreviousTrack();
  if (!prev) return;
  store.clearSelection();
  store.toggleSelection(prev.id);
}

function restart() {
  const el = getAudio();
  if (!el) return;
  el.currentTime = 0;
  if (isPlaying.value) el.play().catch(() => {});
}

async function setRating(rating: number | null) {
  const track = singleTrack.value;
  if (!track) return;
  closeContextMenu();
  await store.setRating([track.path], rating);
}

// Context menu
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

function onTitleContextMenu(e: MouseEvent) {
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

type TooltipPosition = "below" | "top-left";
const tooltipPopover = ref<{ text: string; x: number; y: number; position?: TooltipPosition } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent, position: TooltipPosition = "below") {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (position === "top-left") {
    tooltipPopover.value = { text, x: rect.left, y: rect.top, position: "top-left" };
  } else {
    tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6, position: "below" };
  }
}

function scheduleHideTooltip() {
  tooltipHideTimeout = setTimeout(() => {
    tooltipPopover.value = null;
    tooltipHideTimeout = null;
  }, 100);
}

function cancelHideTooltip() {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function hideTooltip() {
  tooltipPopover.value = null;
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key !== "Enter" || !singleTrack.value) return;
  const target = e.target as HTMLElement;
  const tag = target.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable) return;
  e.preventDefault();
  togglePlay();
}

onMounted(() => {
  document.addEventListener("keydown", onGlobalKeydown);
  nextTick(() => {
    attachAudioListeners();
    syncFromAudio();
  });
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
  const el = getAudio();
  if (el) {
    el.removeEventListener("timeupdate", syncFromAudio);
    el.removeEventListener("play", syncFromAudio);
    el.removeEventListener("pause", syncFromAudio);
    el.removeEventListener("loadedmetadata", syncFromAudio);
    el.removeEventListener("durationchange", syncFromAudio);
    el.removeEventListener("volumechange", syncFromAudio);
    el.removeEventListener("ended", onAudioEnded);
  }
});
</script>

<template>
  <!-- Expanded fullscreen layout: uses mpx-* classes only; all styling in #maximized-player-overlay section (dark theme) -->
  <div
    v-if="singleTrack && expandedLayout"
    class="mpx-root"
    :style="accentStyle"
  >
    <div class="mpx-content">
      <TrackAlbumArt v-if="singleTrack" :path="singleTrack.path" size="xlarge" class="mpx-art" />
      <div class="mpx-title" :title="playbarTitleLine" @contextmenu.prevent="onTitleContextMenu">
        {{ playbarTitleLine }}
      </div>
    </div>
    <div class="mpx-bar">
      <div class="mpx-bar-inner">
        <div class="mpx-actions">
          <span class="inline-flex" @mouseenter="showTooltip('Previous track', $event)" @mouseleave="scheduleHideTooltip">
            <button type="button" class="mpx-nav-btn" aria-label="Previous track" @click="playPrevious" :disabled="!singleTrack || !getPreviousTrack()">
              <FeatherIcon name="skip-back" class="h-6 w-6" />
            </button>
          </span>
          <span class="inline-flex" @mouseenter="showTooltip(isPlaying ? 'Pause' : 'Play', $event)" @mouseleave="scheduleHideTooltip">
            <button type="button" class="mpx-play-btn" :aria-label="isPlaying ? 'Pause' : 'Play'" @click="togglePlay">
              <FeatherIcon v-if="!isPlaying" name="play" class="h-7 w-7" />
              <FeatherIcon v-else name="pause" class="h-7 w-7" />
            </button>
          </span>
          <span class="inline-flex" @mouseenter="showTooltip('Next track', $event)" @mouseleave="scheduleHideTooltip">
            <button type="button" class="mpx-nav-btn" aria-label="Next track" @click="playNext" :disabled="!singleTrack || !getNextTrack()">
              <FeatherIcon name="skip-forward" class="h-6 w-6" />
            </button>
          </span>
        </div>
        <div v-if="playbarShowRatingInMaximized" class="mpx-rating-row">
          <StarRating :model-value="singleTrack?.rating ?? null" @update:model-value="setRating" />
        </div>
        <div class="mpx-row">
          <span class="inline-flex" @mouseenter="showTooltip('Restart from beginning', $event)" @mouseleave="scheduleHideTooltip">
            <button type="button" class="mpx-nav-btn mpx-nav-btn--sm" aria-label="Restart from beginning" @click="restart">
              <FeatherIcon name="square" class="h-4 w-4" />
            </button>
          </span>
          <span class="mpx-time mpx-time-current">{{ formatTime(currentTime) }}</span>
          <input type="range" min="0" :max="displayDuration > 0 ? displayDuration : 0.01" step="0.1" :value="currentTime" class="mpx-progress" :style="{ '--progress-percent': progressPercent + '%' }" aria-label="Seek" @click="onProgressBarClick" @input="onSeekInput" @mousedown="onSeekMouseDown" @mouseup="onSeekMouseUp" @mouseleave="onSeekMouseUp" />
          <span class="mpx-time mpx-time-total">{{ formatTime(displayDuration) }}</span>
          <button type="button" class="mpx-shuffle-btn" :class="{ 'mpx-shuffle-btn--active': shuffle }" aria-label="Shuffle" :aria-pressed="shuffle" @click="settingsStore.setShuffle(!shuffle)">
            <FeatherIcon name="shuffle" class="h-4 w-4" />
          </button>
          <button type="button" class="mpx-shuffle-btn relative" :class="{ 'mpx-shuffle-btn--active': repeat !== 'none' }" :aria-label="repeat === 'none' ? 'Repeat off' : repeat === 'one' ? 'Repeat one' : 'Repeat all'" @click="settingsStore.setRepeat(repeat === 'none' ? 'all' : repeat === 'all' ? 'one' : 'none')">
            <FeatherIcon name="repeat" class="h-4 w-4" />
            <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
          </button>
          <div class="mpx-actions-right">
            <div class="mpx-volume-wrap">
              <VolumeControl mode="playscreen" />
            </div>
            <CastButton />
            <button type="button" class="mpx-nav-btn mpx-nav-btn--sm" aria-label="Minimize player" title="Minimize player" @click="emit('minimize')">
              <FeatherIcon name="minimize-2" class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Default bottom-panel layout -->
  <div
    v-else-if="singleTrack"
    class="flex shrink-0 flex-col items-center gap-1 border-t border-stone-700 bg-stone-900/95 px-4 py-3"
  >
    <div class="flex w-full flex-col items-center gap-1">
      <TrackAlbumArt v-if="singleTrack" :path="singleTrack.path" size="large" :size-px="playbarAlbumArtSizePx" />
      <div
        class="max-w-2xl cursor-context-menu truncate rounded px-2 py-0.5 text-center text-sm font-semibold text-stone-100 transition-colors hover:bg-stone-700/50"
        :title="playbarTitleLine"
        @contextmenu.prevent="onTitleContextMenu"
      >
        {{ playbarTitleLine }}
      </div>
      <div class="flex items-center justify-center gap-2">
        <button
          type="button"
          class="flex items-center justify-center rounded-full bg-stone-800/80 p-3 text-stone-300 hover:bg-stone-700 hover:text-stone-100 disabled:opacity-40"
          aria-label="Previous track"
          @click="playPrevious"
          :disabled="!singleTrack || !getPreviousTrack()"
        >
          <FeatherIcon name="skip-back" class="h-6 w-6" />
        </button>
        <button
          type="button"
          class="player-play-btn flex items-center justify-center rounded-full bg-[#5b7c32] p-4 text-stone-50 shadow-lg hover:bg-[#6d8f3d]"
          :aria-label="isPlaying ? 'Pause' : 'Play'"
          @click="togglePlay"
        >
          <FeatherIcon v-if="!isPlaying" name="play" class="h-7 w-7" />
          <FeatherIcon v-else name="pause" class="h-7 w-7" />
        </button>
        <button
          type="button"
          class="flex items-center justify-center rounded-full bg-stone-800/80 p-3 text-stone-300 hover:bg-stone-700 hover:text-stone-100 disabled:opacity-40"
          aria-label="Next track"
          @click="playNext"
          :disabled="!singleTrack || !getNextTrack()"
        >
          <FeatherIcon name="skip-forward" class="h-6 w-6" />
        </button>
      </div>
      <div class="mt-1 flex w-full items-center gap-3">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <button
            type="button"
            class="flex items-center justify-center rounded-full bg-stone-800/80 p-2 text-stone-300 hover:bg-stone-700 hover:text-stone-100"
            aria-label="Restart from beginning"
            @click="restart"
          >
            <FeatherIcon name="square" class="h-4 w-4" />
          </button>
          <span class="w-10 shrink-0 text-right text-xs text-stone-400 tabular-nums">
            {{ formatTime(currentTime) }}
          </span>
          <input
            type="range"
            min="0"
            :max="displayDuration > 0 ? displayDuration : 0.01"
            step="0.1"
            :value="currentTime"
            class="player-progress-slider h-1.5 min-w-0 flex-1 cursor-pointer appearance-none rounded-full bg-stone-600 [&::-webkit-slider-thumb]:h-3.5 [&::-webkit-slider-thumb]:w-3.5 [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full"
            :style="{ '--progress-percent': progressPercent + '%' }"
            aria-label="Seek"
            @click="onProgressBarClick"
            @input="onSeekInput"
            @mousedown="onSeekMouseDown"
            @mouseup="onSeekMouseUp"
            @mouseleave="onSeekMouseUp"
          />
          <span class="w-10 shrink-0 text-left text-xs text-stone-400 tabular-nums">
            {{ formatTime(displayDuration) }}
          </span>
          <button
            type="button"
            class="flex shrink-0 items-center justify-center rounded p-2 hover:bg-stone-700 hover:text-stone-200"
            :class="shuffle ? 'shuffle-active-bg text-stone-50' : 'text-stone-400'"
            aria-label="Shuffle next track"
            :aria-pressed="shuffle"
            @click="settingsStore.setShuffle(!shuffle)"
          >
            <FeatherIcon name="shuffle" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="relative flex shrink-0 items-center justify-center rounded p-2 hover:bg-stone-700 hover:text-stone-200"
            :class="repeat !== 'none' ? 'shuffle-active-bg text-stone-50' : 'text-stone-400'"
            :aria-label="repeat === 'none' ? 'Repeat off' : repeat === 'one' ? 'Repeat one' : 'Repeat all'"
            @click="settingsStore.setRepeat(repeat === 'none' ? 'all' : repeat === 'all' ? 'one' : 'none')"
          >
            <FeatherIcon name="repeat" class="h-4 w-4" />
            <span v-if="repeat === 'one'" class="absolute bottom-0.5 right-0.5 text-[8px] font-bold leading-none">1</span>
          </button>
        </div>
        <div class="ml-auto flex shrink-0 items-center justify-end gap-2">
          <VolumeControl mode="playscreen" />
          <CastButton />
          <button
            v-if="!hideExpand"
            type="button"
            class="flex items-center justify-center rounded p-2 text-stone-400 hover:bg-stone-600 hover:text-stone-100"
            aria-label="Expand player"
            @click="emit('expand')"
          >
            <FeatherIcon name="maximize-2" class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
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
              <FeatherIcon name="list" class="h-3.5 w-3.5 shrink-0 text-stone-400" />
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
  <Teleport to="body">
    <div
      v-if="tooltipPopover"
      class="fixed z-[200] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
      :style="tooltipPopover.position === 'top-left'
        ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateY(-100%)' }
        : { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }"
      @mouseenter="cancelHideTooltip"
      @mouseleave="hideTooltip"
    >
      {{ tooltipPopover.text }}
    </div>
  </Teleport>
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

</style>


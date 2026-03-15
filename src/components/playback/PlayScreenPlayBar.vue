<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import type { CatalogTrack } from "../../types";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import VolumeControl from "./VolumeControl.vue";
import FeatherIcon from "../shared/FeatherIcon.vue";

defineProps<{
  /** When true, hide the expand (fullscreen) button (e.g. when already in overlay). */
  hideExpand?: boolean;
  /** When true, use fullscreen layout: massive centered art, gradient background, controls at bottom. */
  expandedLayout?: boolean;
  /** CSS vars for accent hues (--player-accent, --player-accent-play, etc.). Bland => primary; vivid => album variants. */
  accentStyle?: Record<string, string>;
}>();

const emit = defineEmits<{
  (e: "expand"): void;
  (e: "minimize"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { selectedTracks, tableOrderedTracks, queueTracks } = storeToRefs(store);
const { shuffle, continuousPlayback, playbarShowAlbumInMarquee } = storeToRefs(settingsStore);

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

function onSeekInput(e: Event) {
  const val = parseFloat((e.target as HTMLInputElement).value);
  currentTime.value = val;
  const el = getAudio();
  if (el) el.currentTime = val;
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
  const newTime = ratio * d;
  currentTime.value = newTime;
  const el = getAudio();
  if (el) el.currentTime = newTime;
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

watch(
  singleTrack,
  () => {
    syncFromAudio();
  },
  { immediate: true },
);

function togglePlay() {
  const el = getAudio();
  if (!el) return;
  if (el.paused) {
    el.play().catch(() => {});
  } else {
    el.pause();
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
  const el = getAudio();
  if (el) {
    isPlaying.value = !el.paused;
    duration.value = Number.isFinite(el.duration) ? el.duration : duration.value;
    currentTime.value = el.currentTime;
    el.addEventListener("timeupdate", syncFromAudio);
    el.addEventListener("play", syncFromAudio);
    el.addEventListener("pause", syncFromAudio);
    el.addEventListener("loadedmetadata", syncFromAudio);
    el.addEventListener("durationchange", syncFromAudio);
    el.addEventListener("volumechange", syncFromAudio);
    el.addEventListener("ended", onAudioEnded);
  }
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
      <div class="mpx-title" :title="playbarTitleLine">
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
        <div class="mpx-row">
          <span class="inline-flex" @mouseenter="showTooltip('Restart from beginning', $event)" @mouseleave="scheduleHideTooltip">
            <button type="button" class="mpx-nav-btn mpx-nav-btn--sm" aria-label="Restart from beginning" @click="restart">
              <FeatherIcon name="rotate-ccw" class="h-4 w-4" />
            </button>
          </span>
          <span class="mpx-time mpx-time-current">{{ formatTime(currentTime) }}</span>
          <input type="range" min="0" :max="displayDuration > 0 ? displayDuration : 0.01" step="0.1" :value="currentTime" class="mpx-progress" :style="{ '--progress-percent': progressPercent + '%' }" aria-label="Seek" @click="onProgressBarClick" @input="onSeekInput" @mousedown="onSeekMouseDown" @mouseup="onSeekMouseUp" @mouseleave="onSeekMouseUp" />
          <span class="mpx-time mpx-time-total">{{ formatTime(displayDuration) }}</span>
          <button type="button" class="mpx-shuffle-btn" :class="{ 'mpx-shuffle-btn--active': shuffle }" aria-label="Shuffle" :aria-pressed="shuffle" @click="settingsStore.setShuffle(!shuffle)">
            <FeatherIcon name="shuffle" class="h-4 w-4" />
          </button>
          <div class="mpx-actions-right">
            <div class="mpx-volume-wrap">
              <VolumeControl mode="playscreen" />
            </div>
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
    class="flex shrink-0 flex-col items-center gap-1 border-t border-stone-700 bg-stone-900/95 px-4 py-2"
  >
    <div class="flex w-full flex-col items-center gap-1">
      <TrackAlbumArt v-if="singleTrack" :path="singleTrack.path" size="large" />
      <div
        class="max-w-2xl truncate text-center text-sm font-semibold text-stone-100"
        :title="playbarTitleLine"
      >
        {{ playbarTitleLine }}
      </div>
      <div class="flex items-center justify-center gap-2">
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Previous track', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="flex items-center justify-center rounded-full bg-stone-800/80 p-3 text-stone-300 hover:bg-stone-700 hover:text-stone-100 disabled:opacity-40"
            aria-label="Previous track"
            @click="playPrevious"
            :disabled="!singleTrack || !getPreviousTrack()"
          >
            <FeatherIcon name="skip-back" class="h-6 w-6" />
          </button>
        </span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip(isPlaying ? 'Pause' : 'Play', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="player-play-btn flex items-center justify-center rounded-full bg-[#5b7c32] p-4 text-stone-50 shadow-lg hover:bg-[#6d8f3d]"
            :aria-label="isPlaying ? 'Pause' : 'Play'"
            @click="togglePlay"
          >
            <FeatherIcon v-if="!isPlaying" name="play" class="h-7 w-7" />
            <FeatherIcon v-else name="pause" class="h-7 w-7" />
          </button>
        </span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Next track', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="flex items-center justify-center rounded-full bg-stone-800/80 p-3 text-stone-300 hover:bg-stone-700 hover:text-stone-100 disabled:opacity-40"
            aria-label="Next track"
            @click="playNext"
            :disabled="!singleTrack || !getNextTrack()"
          >
            <FeatherIcon name="skip-forward" class="h-6 w-6" />
          </button>
        </span>
      </div>
      <div class="mt-1 flex w-full items-center gap-3">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <span
            class="inline-flex"
            @mouseenter="showTooltip('Restart from beginning', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="flex items-center justify-center rounded-full bg-stone-800/80 p-2 text-stone-300 hover:bg-stone-700 hover:text-stone-100"
              aria-label="Restart from beginning"
              @click="restart"
            >
              <FeatherIcon name="rotate-ccw" class="h-4 w-4" />
            </button>
          </span>
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
        </div>
        <div class="ml-auto flex shrink-0 items-center justify-end gap-2">
          <VolumeControl mode="playscreen" />
          <span
            v-if="!hideExpand"
            class="inline-flex"
            @mouseenter="showTooltip('Expand player', $event, 'top-left')"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="flex items-center justify-center rounded p-2 text-stone-400 hover:bg-stone-600 hover:text-stone-100"
              aria-label="Expand player"
              @click="emit('expand')"
            >
              <FeatherIcon name="maximize-2" class="h-4 w-4" />
            </button>
          </span>
        </div>
      </div>
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

</style>


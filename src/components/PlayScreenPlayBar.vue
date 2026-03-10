<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import TrackAlbumArt from "./TrackAlbumArt.vue";
import VolumeControl from "./VolumeControl.vue";

const store = useCatalogStore();
const { selectedTracks, filteredTracks } = storeToRefs(store);

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
  const current = singleTrack.value;
  const list = filteredTracks.value;
  if (!current || !list.length) return;
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx < 0 || idx + 1 >= list.length) return;
  const next = list[idx + 1];
  store.clearSelection();
  store.toggleSelection(next.id);
}

function playPrevious() {
  const current = singleTrack.value;
  const list = filteredTracks.value;
  if (!current || !list.length) return;
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx <= 0) return;
  const prev = list[idx - 1];
  store.clearSelection();
  store.toggleSelection(prev.id);
}

function restart() {
  const el = getAudio();
  if (!el) return;
  el.currentTime = 0;
  if (isPlaying.value) el.play().catch(() => {});
}

const tooltipPopover = ref<{ text: string; x: number; y: number } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent) {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6 };
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
  <div
    v-if="singleTrack"
    class="flex shrink-0 flex-col items-center gap-4 border-t border-stone-700 bg-stone-900/95 px-4 py-4"
  >
    <div class="flex w-full flex-col items-center gap-4">
      <TrackAlbumArt v-if="singleTrack" :path="singleTrack.path" size="large" />
      <div class="mt-2 flex w-full flex-col items-center text-center">
        <div
          class="max-w-2xl truncate text-sm font-semibold text-stone-100"
          :title="singleTrack?.title || singleTrack?.path"
        >
          {{ singleTrack?.title || singleTrack?.path.split(/[/\\]/).pop() || "Track" }}
        </div>
        <div
          v-if="singleTrack?.artist"
          class="mt-1 max-w-2xl truncate text-xs text-stone-400"
        >
          {{ singleTrack.artist }}
        </div>
      </div>
      <div class="flex items-center justify-center gap-4">
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Previous track', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded-full bg-stone-800/80 p-3 text-stone-300 hover:bg-stone-700 hover:text-stone-100 disabled:opacity-40"
            aria-label="Previous track"
            @click="playPrevious"
            :disabled="!singleTrack || filteredTracks.findIndex((t) => t.id === singleTrack.id) <= 0"
          >
            <svg class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7 5v14m2-7l10 7V5L9 12z" />
            </svg>
          </button>
        </span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip(isPlaying ? 'Pause' : 'Play', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded-full bg-[#5b7c32] p-4 text-stone-50 shadow-lg hover:bg-[#6d8f3d]"
            :aria-label="isPlaying ? 'Pause' : 'Play'"
            @click="togglePlay"
          >
            <svg
              v-if="!isPlaying"
              class="h-7 w-7"
              fill="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path d="M8 5v14l11-7z" />
            </svg>
            <svg
              v-else
              class="h-7 w-7"
              fill="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
            </svg>
          </button>
        </span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Next track', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded-full bg-stone-800/80 p-3 text-stone-300 hover:bg-stone-700 hover:text-stone-100 disabled:opacity-40"
            aria-label="Next track"
            @click="playNext"
            :disabled="!singleTrack || (() => { const list = filteredTracks; const current = singleTrack; const idx = list.findIndex((t) => t.id === current.id); return idx < 0 || idx + 1 >= list.length; })()"
          >
            <svg class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M17 5v14m-2-7L5 19V5l10 7z" />
            </svg>
          </button>
        </span>
      </div>
      <div class="mt-2 flex w-full items-center gap-4">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <span
            class="inline-flex"
            @mouseenter="showTooltip('Restart from beginning', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="rounded-full bg-stone-800/80 p-2 text-stone-300 hover:bg-stone-700 hover:text-stone-100"
              aria-label="Restart from beginning"
              @click="restart"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
              </svg>
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
        </div>
        <div class="ml-auto flex shrink-0 items-center justify-end gap-2">
          <VolumeControl mode="playscreen" />
        </div>
      </div>
    </div>
    <Teleport to="body">
      <div
        v-if="tooltipPopover"
        class="fixed z-[200] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        :style="{ left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }"
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
}
.player-progress-slider::-webkit-slider-runnable-track {
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--progress-percent, 0%),
    rgb(87 83 78) var(--progress-percent, 0%),
    rgb(87 83 78) 100%
  );
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


<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import { invoke } from "@tauri-apps/api/core";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { selectedTracks, filteredTracks } = storeToRefs(store);
const { autoplayOnSelect, continuousPlayback } = storeToRefs(settingsStore);

const audioRef = ref<HTMLAudioElement | null>(null);
const isPlaying = ref(false);
const volume = ref(0.25);
const volumeBeforeMute = ref(0.25);
const audioSrc = ref("");
const currentTime = ref(0);
const duration = ref(0);
const isSeeking = ref(false);
let shouldAutoplayNextSelection = false;

function onVolumeInput(e: Event) {
  const v = parseFloat((e.target as HTMLInputElement).value);
  volume.value = v;
  if (audioRef.value) audioRef.value.volume = v;
  if (v > 0) volumeBeforeMute.value = v;
}

function toggleMute() {
  if (volume.value > 0) {
    volumeBeforeMute.value = volume.value;
    volume.value = 0;
    if (audioRef.value) audioRef.value.volume = 0;
  } else {
    const v = volumeBeforeMute.value || 0.25;
    volume.value = v;
    if (audioRef.value) audioRef.value.volume = v;
  }
}

function formatTime(secs: number): string {
  if (!Number.isFinite(secs) || secs < 0) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function onTimeUpdate() {
  if (isSeeking.value) return;
  const el = audioRef.value;
  if (el) currentTime.value = el.currentTime;
}

function onDurationChange() {
  const el = audioRef.value;
  if (el) duration.value = Number.isFinite(el.duration) ? el.duration : 0;
}

function onSeekInput(e: Event) {
  const val = parseFloat((e.target as HTMLInputElement).value);
  currentTime.value = val;
  if (audioRef.value) audioRef.value.currentTime = val;
}

/** Inset (px) so click math matches the visible track (thumb is 12px so half on each end). */
const PROGRESS_THUMB_HALF = 6;

/** Seek to the position under the click; use same inset as track so thumb ends exactly to the left of the click. */
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
  if (audioRef.value) audioRef.value.currentTime = newTime;
}

function onSeekMouseDown() {
  isSeeking.value = true;
}

function onSeekMouseUp() {
  isSeeking.value = false;
}

watch(audioRef, (el) => {
  if (el) el.volume = volume.value;
});

const singleTrack = computed(() => {
  const tracks = selectedTracks.value;
  return tracks.length === 1 ? tracks[0] : null;
});

/** Display duration: prefer catalog value (reliable); fall back to audio element when loaded. */
const displayDuration = computed(() => {
  const fromTrack = singleTrack.value?.duration_secs;
  if (fromTrack != null && Number.isFinite(fromTrack) && fromTrack >= 0) return fromTrack;
  return duration.value;
});

/** Progress 0–100 for filling the progress bar (elapsed portion). */
const progressPercent = computed(() => {
  const d = displayDuration.value;
  if (!d || !Number.isFinite(d)) return 0;
  return Math.min(100, (currentTime.value / d) * 100);
});


async function loadAudioBlob(path: string) {
  if (audioSrc.value) {
    URL.revokeObjectURL(audioSrc.value);
    audioSrc.value = "";
  }
  isPlaying.value = false;
  currentTime.value = 0;
  duration.value = 0;
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

watch(
  singleTrack,
  (track) => {
    if (!track) {
      if (audioSrc.value) {
        URL.revokeObjectURL(audioSrc.value);
        audioSrc.value = "";
      }
      isPlaying.value = false;
      store.setCurrentPlaying(null);
      return;
    }
    store.setCurrentPlaying(track.id);
    loadAudioBlob(track.path).then(() => {
      const shouldAutoplay = autoplayOnSelect.value || shouldAutoplayNextSelection;
      if (!shouldAutoplay) return;
      shouldAutoplayNextSelection = false;
      nextTick(() => {
        const el = audioRef.value;
        if (!el) return;
        el.play().catch(() => {});
      });
    });
  },
  { immediate: true }
);

function togglePlay() {
  const el = audioRef.value;
  if (!el) return;
  if (el.paused) {
    el.play().catch(() => {});
    isPlaying.value = true;
  } else {
    el.pause();
    isPlaying.value = false;
  }
}

function onAudioPlay() {
  isPlaying.value = true;
}

function onAudioPause() {
  isPlaying.value = false;
}

function onAudioEnded() {
  isPlaying.value = false;
  if (!continuousPlayback.value) return;
  const current = singleTrack.value;
  const list = filteredTracks.value;
  if (!current || !list.length) return;
  const idx = list.findIndex((t) => t.id === current.id);
  if (idx < 0 || idx + 1 >= list.length) return;
  const next = list[idx + 1];
  shouldAutoplayNextSelection = true;
  store.clearSelection();
  store.toggleSelection(next.id);
}

function restart() {
  const el = audioRef.value;
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
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
});
</script>

<template>
  <div
    v-if="singleTrack"
    class="flex shrink-0 flex-col gap-2 border-t border-stone-700 px-3 py-2"
  >
    <audio
      ref="audioRef"
      :src="audioSrc"
      class="hidden"
      @play="onAudioPlay"
      @pause="onAudioPause"
      @ended="onAudioEnded"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onDurationChange"
      @durationchange="onDurationChange"
    />
    <div class="flex min-w-0 items-center gap-3">
      <!-- Track title (left) -->
      <span class="min-w-0 max-w-[180px] shrink-0 truncate text-xs font-medium text-stone-300" :title="singleTrack?.title || singleTrack?.path">
        {{ singleTrack?.title || singleTrack?.path.split(/[/\\]/).pop() || "Track" }}
      </span>
      <!-- Playback controls -->
      <div class="flex shrink-0 items-center gap-0.5">
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Restart from beginning', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Restart from beginning"
            @click="restart"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
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
            class="rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            :aria-label="isPlaying ? 'Pause' : 'Play'"
            @click="togglePlay"
          >
            <svg v-if="!isPlaying" class="h-4 w-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M8 5v14l11-7z" />
            </svg>
            <svg v-else class="h-4 w-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
            </svg>
          </button>
        </span>
      </div>
      <!-- Progress bar + times (center: time left, bar, time right) -->
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
      </div>
      <!-- Volume (right) -->
      <div class="flex w-24 shrink-0 items-center gap-2">
        <button
          type="button"
          class="flex shrink-0 rounded p-1 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
          :aria-label="volume === 0 ? 'Unmute' : 'Mute'"
          :title="volume === 0 ? 'Unmute' : 'Mute'"
          @click="toggleMute"
        >
          <svg v-if="volume === 0" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M11 5L6 9H2v6h4l5 4V5z" />
            <path d="M23 9l-6 6" />
            <path d="M17 9l6 6" />
          </svg>
          <svg v-else class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M11 5L6 9H2v6h4l5 4V5z" />
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
          </svg>
        </button>
        <label class="sr-only" for="player-volume">Volume</label>
        <input
          id="player-volume"
          type="range"
          min="0"
          max="1"
          step="0.05"
          :value="volume"
          class="player-volume-slider h-1.5 w-full min-w-0 cursor-pointer appearance-none rounded-full bg-stone-600 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full"
          title="Volume"
          @input="onVolumeInput"
        />
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
/* Progress bar: fill the elapsed (left) portion; fill ends at thumb so click position matches. */
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
/* Firefox: filled portion before thumb */
.player-progress-slider::-moz-range-progress {
  background: #5b7c32;
  border-radius: 9999px;
}
.player-progress-slider::-moz-range-track {
  background: rgb(87 83 78);
  border-radius: 9999px;
}
</style>

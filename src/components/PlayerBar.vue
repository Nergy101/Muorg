<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import TrackAlbumArt from "./TrackAlbumArt.vue";
import VolumeControl from "./VolumeControl.vue";
import { invoke } from "@tauri-apps/api/core";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { selectedTracks, filteredTracks } = storeToRefs(store);
const {
  autoplayOnSelect,
  continuousPlayback,
  shuffle,
  playbarShowAlbumInMarquee,
  playbarDisableMarquee,
} = storeToRefs(settingsStore);

const audioRef = ref<HTMLAudioElement | null>(null);
const isPlaying = ref(false);
const audioSrc = ref("");
const currentTime = ref(0);
const duration = ref(0);
const isSeeking = ref(false);
let shouldAutoplayNextSelection = false;

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

function recomputeMarquee() {
  nextTick(() => {
    if (playbarDisableMarquee.value) {
      shouldScrollMarquee.value = false;
      marqueeDistance.value = 0;
      return;
    }
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
      shouldScrollMarquee.value = false;
      marqueeDistance.value = 0;
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
    recomputeMarquee();
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
  let next: typeof list[0];
  if (shuffle.value) {
    const others = list.filter((t) => t.id !== current.id);
    if (others.length === 0) return;
    next = others[Math.floor(Math.random() * others.length)];
  } else {
    const idx = list.findIndex((t) => t.id === current.id);
    if (idx < 0 || idx + 1 >= list.length) return;
    next = list[idx + 1];
  }
  shouldAutoplayNextSelection = true;
  store.clearSelection();
  store.toggleSelection(next.id);
}

function playNext() {
  const current = singleTrack.value;
  const list = filteredTracks.value;
  if (!current || !list.length) return;
  let next: typeof list[0];
  if (shuffle.value) {
    const others = list.filter((t) => t.id !== current.id);
    if (others.length === 0) return;
    next = others[Math.floor(Math.random() * others.length)];
  } else {
    const idx = list.findIndex((t) => t.id === current.id);
    if (idx < 0 || idx + 1 >= list.length) return;
    next = list[idx + 1];
  }
  shouldAutoplayNextSelection = true;
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
  shouldAutoplayNextSelection = true;
  store.clearSelection();
  store.toggleSelection(prev.id);
}

function restart() {
  const el = audioRef.value;
  if (!el) return;
  el.currentTime = 0;
  if (isPlaying.value) el.play().catch(() => {});
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
  recomputeMarquee();
  window.addEventListener("resize", recomputeMarquee);
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
  window.removeEventListener("resize", recomputeMarquee);
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
      <!-- Track artwork + title/artist (left) -->
      <div class="flex min-w-0 max-w-[260px] shrink-0 items-center gap-2">
        <TrackAlbumArt v-if="singleTrack" :path="singleTrack.path" />
        <div class="min-w-0 flex-1">
          <div
            ref="marqueeContainerRef"
            class="metadata-marquee text-xs font-medium"
            :class="playbarDisableMarquee ? 'truncate' : ''"
            @mouseenter="showTitlePopover(marqueeTitle, $event)"
            @mouseleave="scheduleHideTitlePopover"
          >
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
          </div>
        </div>
      </div>
      <!-- Center: playback controls + progress -->
      <div class="flex min-w-0 flex-1 items-center gap-3">
        <!-- Playback controls -->
        <div class="flex shrink-0 items-center gap-0.5">
          <button
            type="button"
            class="rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-40"
            aria-label="Previous track"
            @click="playPrevious"
            :disabled="!singleTrack || filteredTracks.findIndex((t) => t.id === singleTrack?.id) <= 0"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7 5v14m2-7l10 7V5L9 12z" />
            </svg>
          </button>
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
          <button
            type="button"
            class="player-play-btn rounded bg-[#5b7c32] p-1.5 text-stone-50 hover:bg-[#6d8f3d]"
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
          <button
            type="button"
            class="rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-40"
            aria-label="Next track"
            @click="playNext"
            :disabled="!singleTrack || (() => { const list = filteredTracks; const current = singleTrack; const idx = list.findIndex((t) => t.id === current.id); return idx < 0 || idx + 1 >= list.length; })()"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M17 5v14m-2-7L5 19V5l10 7z" />
            </svg>
          </button>
        </div>
        <!-- Progress bar + times -->
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
            class="shrink-0 rounded p-1.5 hover:bg-stone-600 hover:text-stone-200"
            :class="shuffle ? 'shuffle-active-bg text-stone-50' : 'text-stone-400'"
            aria-label="Shuffle next track"
            :aria-pressed="shuffle"
            @click="settingsStore.setShuffle(!shuffle)"
          >
            <svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
              <path fill="currentColor" d="M2 16.25a.75.75 0 0 0 0 1.5zm8.748-2.163l-.643-.386zm2.504-4.174l.643.386zM22 7l.53.53a.75.75 0 0 0 0-1.06zm-2.53 1.47a.75.75 0 0 0 1.06 1.06zm1.06-4a.75.75 0 1 0-1.06 1.06zm-5.31 2.92l-.369-.653zM2 17.75h3.603v-1.5H2zm9.39-3.277l2.505-4.174l-1.286-.772l-2.504 4.174zm7.007-6.723H22v-1.5h-3.603zm3.073-1.28l-2 2l1.06 1.06l2-2zm1.06 0l-2-2l-1.06 1.06l2 2zm-8.635 3.829c.434-.724.734-1.22 1.006-1.589c.263-.355.468-.543.689-.668l-.739-1.305c-.467.264-.82.627-1.155 1.08c-.326.44-.668 1.011-1.087 1.71zm4.502-4.049c-.815 0-1.48 0-2.025.052c-.562.055-1.054.17-1.521.435l.739 1.305c.22-.125.487-.204.927-.247c.456-.044 1.036-.045 1.88-.045zM5.603 17.75c.815 0 1.48 0 2.025-.052c.562-.055 1.054-.17 1.521-.435l-.739-1.305c-.22.125-.487.204-.927.247c-.456.044-1.036.045-1.88.045zm4.502-4.049c-.435.724-.734 1.22-1.006 1.589c-.263.355-.468.543-.689.668l.74 1.305c.466-.264.819-.627 1.154-1.08c.326-.44.668-1.011 1.087-1.71zM2 6.25a.75.75 0 0 0 0 1.5zM22 17l.53.53a.75.75 0 0 0 0-1.06zm-1.47-2.53a.75.75 0 1 0-1.06 1.06zm-1.06 4a.75.75 0 1 0 1.06 1.06zm-3.345-1.525l.144-.736zm-1.682-2.33a.75.75 0 1 0-1.286.77zm.025 1.391l.558-.501zm-6.593-8.95l.143-.737zm1.682 2.33a.75.75 0 0 0 1.286-.772zm-.025-1.393l-.558.502zM2 7.75h4.668v-1.5H2zm15.332 10H22v-1.5h-4.668zm5.198-1.28l-2-2l-1.06 1.06l2 2zm-1.06 0l-2 2l1.06 1.06l2-2zm-4.138-.22c-.645 0-.867-.003-1.063-.041l-.287 1.472c.372.072.765.069 1.35.069zm-4.175-.864c.3.502.5.84.754 1.122l1.115-1.003c-.134-.149-.25-.337-.583-.89zm3.112.823a2.25 2.25 0 0 1-1.243-.704l-1.115 1.003a3.75 3.75 0 0 0 2.071 1.173zM6.668 7.75c.645 0 .867.003 1.063.041l.287-1.472c-.372-.072-.765-.069-1.35-.069zm4.175.864c-.3-.502-.5-.84-.754-1.122L8.974 8.495c.134.149.25.337.583.89zm-3.112-.823c.48.094.916.34 1.243.704l1.115-1.003a3.75 3.75 0 0 0-2.071-1.173z" />
            </svg>
          </button>
        </div>
      </div>
      <!-- Volume (right) -->
      <div class="ml-3 flex w-32 shrink-0 items-center justify-end">
        <VolumeControl mode="metadata" />
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
/* Progress bar: fill the elapsed (left) portion; fill ends at thumb so click position matches. Rounded start/end. */
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
/* Firefox: filled portion before thumb */
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

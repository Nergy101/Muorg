<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";

const props = defineProps<{
  mode?: "metadata" | "playscreen";
}>();

const volume = ref(0.25);
const volumeBeforeMute = ref(0.25);

function getAudio(): HTMLAudioElement | null {
  return document.querySelector('audio[data-muorg-player="true"]') as HTMLAudioElement | null;
}

function syncFromAudio() {
  const el = getAudio();
  if (!el) return;
  volume.value = el.volume;
  if (el.volume > 0) volumeBeforeMute.value = el.volume;
}

function onVolumeInput(e: Event) {
  const input = e.target as HTMLInputElement;
  const raw = input.valueAsNumber;
  if (!Number.isFinite(raw)) return;
  const v = Math.min(1, Math.max(0, raw));
  volume.value = v;
  const el = getAudio();
  if (el) el.volume = v;
  if (v > 0) volumeBeforeMute.value = v;
}

function toggleMute() {
  const el = getAudio();
  if (!el) return;
  if (volume.value > 0) {
    volumeBeforeMute.value = volume.value;
    volume.value = 0;
    el.volume = 0;
  } else {
    const v = volumeBeforeMute.value || 0.25;
    volume.value = v;
    el.volume = v;
  }
}

onMounted(() => {
  const el = getAudio();
  if (el) {
    volume.value = el.volume;
    if (el.volume > 0) volumeBeforeMute.value = el.volume;
    el.addEventListener("volumechange", syncFromAudio);
  }
});

onUnmounted(() => {
  const el = getAudio();
  if (el) {
    el.removeEventListener("volumechange", syncFromAudio);
  }
});
</script>

<template>
  <div class="flex items-center gap-2">
    <button
      type="button"
      class="flex shrink-0 rounded p-1 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
      :aria-label="volume === 0 ? 'Unmute' : 'Mute'"
      :title="volume === 0 ? 'Unmute' : 'Mute'"
      @click="toggleMute"
    >
      <svg
        v-if="volume === 0"
        class="h-4 w-4"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M11 5L6 9H2v6h4l5 4V5z" />
        <path d="M23 9l-6 6" />
        <path d="M17 9l6 6" />
      </svg>
      <svg
        v-else
        class="h-4 w-4"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M11 5L6 9H2v6h4l5 4V5z" />
        <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
      </svg>
    </button>
    <input
      type="range"
      min="0"
      max="1"
      step="0.05"
      :value="volume"
      class="player-volume-slider h-1.5 cursor-pointer appearance-none rounded-full bg-stone-600 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full"
      :class="props.mode === 'playscreen' ? 'w-32' : 'w-full min-w-0'"
      title="Volume"
      @input="onVolumeInput"
    />
  </div>
</template>

<style scoped>
.player-volume-slider {
  accent-color: #5b7c32;
}
.player-volume-slider::-webkit-slider-thumb {
  background: #5b7c32;
}
.player-volume-slider::-moz-range-thumb {
  background: #5b7c32;
}
</style>



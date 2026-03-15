<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useSettingsStore } from "../../stores/settings";
import FeatherIcon from "../shared/FeatherIcon.vue";

const props = defineProps<{
  mode?: "metadata" | "playscreen";
}>();

const settingsStore = useSettingsStore();
const volume = ref(settingsStore.volume ?? 0.25);
const volumeBeforeMute = ref(volume.value);

/** Icon by level: volume-x (muted), volume-1 (low), volume-2 (medium/high). */
const volumeIconName = computed(() => {
  const v = volume.value;
  if (v <= 0) return "volume-x";
  if (v < 0.5) return "volume-1";
  return "volume-2";
});

function getAudio(): HTMLAudioElement | null {
  return document.querySelector('audio[data-muorg-player="true"]') as HTMLAudioElement | null;
}

function syncFromAudio() {
  const el = getAudio();
  if (!el) return;
  volume.value = el.volume;
  if (el.volume > 0) volumeBeforeMute.value = el.volume;
  settingsStore.setVolume(el.volume);
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
  settingsStore.setVolume(v);
}

function toggleMute() {
  const el = getAudio();
  if (!el) return;
  if (volume.value > 0) {
    volumeBeforeMute.value = volume.value;
    volume.value = 0;
    el.volume = 0;
    settingsStore.setVolume(0);
  } else {
    const v = volumeBeforeMute.value || settingsStore.volume || 0.25;
    volume.value = v;
    el.volume = v;
    settingsStore.setVolume(v);
  }
}

onMounted(() => {
  const el = getAudio();
  if (el) {
    const initial = settingsStore.volume ?? el.volume ?? 0.25;
    const v = Math.min(1, Math.max(0, initial));
    el.volume = v;
    volume.value = v;
    if (v > 0) volumeBeforeMute.value = v;
    settingsStore.setVolume(v);
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
      class="flex shrink-0 items-center justify-center rounded p-1 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
      :aria-label="volume === 0 ? 'Unmute' : 'Mute'"
      :title="volume === 0 ? 'Unmute' : 'Mute'"
      @click="toggleMute"
    >
      <FeatherIcon :name="volumeIconName" class="h-4 w-4" />
    </button>
    <input
      type="range"
      min="0"
      max="1"
      step="0.05"
      :value="volume"
      class="player-volume-slider h-1.5 cursor-pointer appearance-none rounded-full bg-stone-600 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full"
      :class="props.mode === 'playscreen' ? 'w-32' : 'w-full min-w-0'"
      :style="{ '--volume-percent': volume * 100 + '%' }"
      title="Volume"
      @input="onVolumeInput"
    />
  </div>
</template>

<style scoped>
.player-volume-slider {
  accent-color: #5b7c32;
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--volume-percent, 0%),
    rgb(87 83 78) var(--volume-percent, 0%),
    rgb(87 83 78) 100%
  ) !important;
  border-radius: 9999px;
}
.player-volume-slider::-webkit-slider-runnable-track {
  background: linear-gradient(
    to right,
    #5b7c32 0%,
    #5b7c32 var(--volume-percent, 0%),
    rgb(87 83 78) var(--volume-percent, 0%),
    rgb(87 83 78) 100%
  );
  border-radius: 9999px;
}
.player-volume-slider::-moz-range-progress {
  background: #5b7c32;
  border-radius: 9999px;
}
.player-volume-slider::-moz-range-track {
  background: rgb(87 83 78);
  border-radius: 9999px;
}
.player-volume-slider::-webkit-slider-thumb {
  background: #5b7c32;
}
.player-volume-slider::-moz-range-thumb {
  background: #5b7c32;
}
</style>


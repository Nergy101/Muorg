<script setup lang="ts">
/**
 * Chromecast device picker.
 *
 * mDNS discovery costs the server a background sweep, so it only runs while
 * this sheet is open — opened on mount, stopped on close.
 */
import { onUnmounted, watch } from "vue";
import { useCastStore } from "../stores/cast";
import { usePlayerStore } from "../stores/player";
import BottomSheet from "./BottomSheet.vue";
import MageIcon from "./MageIcon.vue";
import type { CastDevice } from "@shared/api";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ (e: "close"): void }>();

const cast = useCastStore();
const player = usePlayerStore();

watch(
  () => props.open,
  (open) => {
    if (open) void cast.startDiscovery();
    else void cast.stopDiscovery();
  },
  { immediate: true },
);

onUnmounted(() => void cast.stopDiscovery());

async function select(device: CastDevice) {
  const track = player.currentTrack;
  if (!track) return;
  // The server streams the audio itself, so the browser's own playback has to
  // get out of the way rather than play the same track twice.
  player.pauseForCast();
  await cast.play(device, track.id);
  emit("close");
}

async function disconnect() {
  await cast.stop();
  emit("close");
}
</script>

<template>
  <BottomSheet :open="open" @close="emit('close')">
    <div class="flex items-center gap-2 px-6 pb-2">
      <MageIcon name="screencast" class="h-5 w-5 text-on-surface-variant" />
      <span class="text-title-md text-on-surface">Cast to</span>
    </div>

    <p v-if="cast.error" class="px-6 pb-2 text-body-md text-error">
      {{ cast.error }}
    </p>

    <button
      v-if="cast.isCasting && cast.device"
      type="button"
      class="flex h-14 w-full items-center gap-3 px-6 text-left"
      @click="disconnect"
    >
      <MageIcon name="screencast" class="h-5 w-5 shrink-0 text-primary" />
      <span class="min-w-0 flex-1 truncate text-body-lg text-primary">
        {{ cast.device.name }}
      </span>
      <span class="shrink-0 text-label-md text-on-surface-variant">Disconnect</span>
    </button>

    <div v-if="cast.isCasting" class="my-1 border-t border-outline/30" />

    <button
      v-for="d in cast.devices"
      :key="d.id"
      type="button"
      class="flex h-14 w-full items-center gap-3 px-6 text-left disabled:opacity-40"
      :disabled="!player.currentTrack || cast.device?.id === d.id"
      @click="select(d)"
    >
      <MageIcon name="screencast" class="h-5 w-5 shrink-0 text-on-surface-variant" />
      <span class="min-w-0 flex-1 truncate text-body-lg text-on-surface">{{ d.name }}</span>
    </button>

    <p
      v-if="cast.devices.length === 0"
      class="px-6 py-6 text-center text-body-md text-on-surface-variant"
    >
      {{ cast.discovering ? "Looking for devices…" : "No devices found." }}
    </p>

    <p
      v-else-if="!player.currentTrack"
      class="px-6 pb-4 text-center text-body-md text-on-surface-variant"
    >
      Start a track first, then pick a device.
    </p>

    <div class="h-2" />
  </BottomSheet>
</template>

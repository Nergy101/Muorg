<script setup lang="ts">
import { computed, onMounted, onUnmounted } from "vue";
import { storeToRefs } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import { useCastStore } from "../../stores/cast";
import { useCatalogStore } from "../../stores/catalog";
import FeatherIcon from "../shared/FeatherIcon.vue";
import type { CastDevice } from "../../stores/cast";

const props = defineProps<{
  anchorX: number;
  anchorY: number;
  isActive: boolean;
}>();

const emit = defineEmits<{ close: [] }>();

const castStore = useCastStore();
const catalogStore = useCatalogStore();
const { discoveredDevices, connectedDeviceName } = storeToRefs(castStore);

/** Position: right-aligned to anchorX, above anchorY */
const style = computed(() => ({
  position: "fixed" as const,
  right: `${window.innerWidth - props.anchorX}px`,
  top: `${props.anchorY - 8}px`,
  transform: "translateY(-100%)",
  zIndex: 250,
}));

onMounted(async () => {
  if (!props.isActive) {
    await invoke("cast_start_discovery");
  }
});

onUnmounted(async () => {
  if (!props.isActive) {
    await invoke("cast_stop_discovery");
  }
});

async function selectDevice(device: CastDevice) {
  const ids = catalogStore.selectedTrackIds;
  if (ids.length !== 1) return;
  const track = catalogStore.tracks.find((t) => t.id === ids[0]);
  if (!track) return;

  castStore.setConnectedDevice(device.id, device.name);
  try {
    await invoke("cast_play", { deviceId: device.id, trackPath: track.path });
  } catch (e) {
    console.error("[Cast] cast_play error:", e);
  }
  emit("close");
}

function disconnect() {
  castStore.stopCast();
  emit("close");
}
</script>

<template>
  <div
    :style="style"
    class="w-52 rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
  >
    <!-- Active cast: show connected device + disconnect -->
    <template v-if="isActive">
      <div class="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-stone-500">
        Now casting
      </div>
      <div class="flex items-center gap-2 px-3 py-2 text-sm text-stone-300">
        <FeatherIcon name="cast" class="h-3.5 w-3.5 shrink-0 text-green-400" />
        <span class="truncate">{{ connectedDeviceName ?? "Device" }}</span>
      </div>
      <div class="mt-1 border-t border-stone-700 pt-1">
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-red-400 hover:bg-stone-700 hover:text-red-300"
          @click="disconnect"
        >
          <FeatherIcon name="x-circle" class="h-3.5 w-3.5 shrink-0" />
          Disconnect
        </button>
      </div>
    </template>

    <!-- Idle: show device list -->
    <template v-else>
      <div class="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-stone-500">
        Cast to device
      </div>

      <div v-if="discoveredDevices.length === 0" class="px-3 py-2 text-xs text-stone-500">
        Searching for devices…
      </div>

      <button
        v-for="device in discoveredDevices"
        :key="device.id"
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700"
        @click="selectDevice(device)"
      >
        <FeatherIcon name="cast" class="h-3.5 w-3.5 shrink-0 text-stone-400" />
        <span class="truncate">{{ device.name }}</span>
      </button>

      <div class="mt-1 border-t border-stone-700 pt-1">
        <button
          type="button"
          class="w-full px-3 py-1.5 text-left text-xs text-stone-500 hover:text-stone-300"
          @click="emit('close')"
        >
          Dismiss
        </button>
      </div>
    </template>
  </div>
</template>

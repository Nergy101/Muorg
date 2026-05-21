<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCastStore } from "../../stores/cast";
import { useCatalogStore } from "../../stores/catalog";
import CastDevicePicker from "./CastDevicePicker.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const props = defineProps<{ size?: 'sm' | 'md' }>();

const castStore = useCastStore();
const catalogStore = useCatalogStore();
const { isCasting, castStatus } = storeToRefs(castStore);

const pickerOpen = ref(false);
const buttonRef = ref<HTMLButtonElement | null>(null);
const pickerRef = ref<HTMLElement | null>(null);
const pickerPos = ref({ x: 0, y: 0 });

// Close picker on click outside
watch(pickerOpen, (open) => {
  if (!open) return;
  function onMousedown(e: MouseEvent) {
    const target = e.target as Node;
    if (buttonRef.value?.contains(target)) return;
    if (pickerRef.value?.contains(target)) return;
    pickerOpen.value = false;
  }
  // Use setTimeout so this listener doesn't fire on the same click that opened the picker
  setTimeout(() => document.addEventListener("mousedown", onMousedown, { capture: true, once: true }), 0);
});

const currentTrack = computed(() => {
  const ids = catalogStore.selectedTrackIds;
  if (ids.length !== 1) return null;
  return catalogStore.tracks.find((t) => t.id === ids[0]) ?? null;
});

const isConnecting = computed(() => castStatus.value.status === "connecting");
const isTranscoding = computed(() => castStatus.value.status === "transcoding");
const isActive = computed(() => isCasting.value || isTranscoding.value || isConnecting.value);
const isDisabled = computed(() => !currentTrack.value && !isActive.value);

const title = computed(() => {
  if (isActive.value) return `Casting to ${castStore.connectedDeviceName ?? "device"}`;
  if (!currentTrack.value) return "No track selected";
  return "Cast to device";
});

function openPicker() {
  if (buttonRef.value) {
    const rect = buttonRef.value.getBoundingClientRect();
    pickerPos.value = { x: rect.right, y: rect.top };
  }
  pickerOpen.value = true;
}

function handleClick() {
  if (isConnecting.value) return;
  if (pickerOpen.value) { pickerOpen.value = false; return; }
  openPicker();
}
</script>

<template>
  <button
    ref="buttonRef"
    type="button"
    :disabled="isDisabled"
    :title="title"
    :class="[
      props.size === 'md' ? 'flex items-center justify-center rounded p-2 transition-colors' : 'flex items-center justify-center rounded p-1.5 transition-colors',
      isCasting
        ? 'text-green-400 hover:bg-stone-600'
        : isTranscoding
          ? 'text-amber-400 hover:bg-stone-600'
          : isConnecting
            ? 'text-stone-300'
            : isDisabled
              ? 'cursor-not-allowed text-stone-600'
              : 'text-stone-400 hover:bg-stone-600 hover:text-stone-100',
    ]"
    @click="handleClick"
  >
    <FeatherIcon name="cast" class="h-4 w-4" />
  </button>

  <Teleport to="body">
    <CastDevicePicker
      v-if="pickerOpen"
      :ref="(el) => (pickerRef = el as HTMLElement | null)"
      :anchor-x="pickerPos.x"
      :anchor-y="pickerPos.y"
      :is-active="isActive"
      @close="pickerOpen = false"
    />
  </Teleport>
</template>

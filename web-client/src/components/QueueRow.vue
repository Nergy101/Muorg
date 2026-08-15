<template>
  <div class="relative h-14 overflow-hidden" data-queue-row>
    <!-- Swipe-to-remove backdrop -->
    <div
      v-if="swipe.offsetX.value < 0"
      class="absolute inset-0 flex items-center justify-end bg-error-container pr-6"
    >
      <MageIcon name="trash" class="h-5 w-5 text-on-error-container" />
    </div>

    <!-- Foreground row -->
    <div
      class="relative flex h-14 items-center bg-background"
      :class="swipe.swiping.value ? '' : 'transition-transform duration-200'"
      :style="{ transform: `translateX(${swipe.offsetX.value}px)` }"
      style="touch-action: pan-y"
      @pointerdown="swipe.onPointerdown"
    >
      <button
        type="button"
        class="flex h-[52px] w-10 shrink-0 items-center justify-center text-on-surface-variant"
        style="touch-action: none"
        aria-label="Reorder"
        @pointerdown.stop="emit('drag-start', $event)"
      >
        <MageIcon name="dash-menu" class="h-5 w-5" />
      </button>

      <!-- Format and duration are dropped here: the queue is narrower than the
           other lists (drag handle + remove button), so the title gets the room. -->
      <TrackListRow
        class="min-w-0 flex-1"
        :track="track"
        :show-format-badge="false"
        :show-duration="false"
        @play="emit('play')"
        @actions="emit('actions')"
      />

      <button
        type="button"
        class="-ml-1 mr-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant"
        aria-label="Remove from queue"
        @click="emit('remove')"
      >
        <MageIcon name="multiply" class="h-5 w-5" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import MageIcon from "./MageIcon.vue";
import TrackListRow from "./TrackListRow.vue";
import { useSwipeToRemove } from "../composables/useSwipeToRemove";
import type { CatalogTrack } from "../types";

defineProps<{ track: CatalogTrack; index: number }>();
const emit = defineEmits<{
  play: [];
  actions: [];
  remove: [];
  "drag-start": [PointerEvent];
}>();

const swipe = useSwipeToRemove(() => emit("remove"));
</script>

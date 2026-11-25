<script setup lang="ts">
import { computed } from "vue";

/**
 * Artist mosaic: 1 album art = full bleed, 2 or 3 = vertical slices side by
 * side, 4 = 2x2 grid. Falls back to initials when the artist has no art.
 */
const props = defineProps<{
  /** Cover URLs, up to 4. */
  covers: string[];
  /** Initials shown when there is no art at all. */
  fallback?: string;
}>();

const cells = computed(() => props.covers.slice(0, 4));
const cols = computed(() => {
  const n = cells.value.length;
  if (n === 3) return 3;
  if (n === 4) return 2;
  return Math.max(1, n);
});
const rows = computed(() => (cells.value.length === 4 ? 2 : 1));
/** The 2x2 grid is stitched together: no gap, and cells overlap by 1px so
 *  fractional grid rounding cannot leave hairline seams. */
const gap = computed(() => (cells.value.length === 4 ? "0" : "2px"));
const stitched = computed(() => cells.value.length === 4);
</script>

<template>
  <div
    v-if="cells.length"
    class="grid h-full w-full"
    :class="{ 'mosaic-stitched': stitched }"
    :style="{ gridTemplateColumns: `repeat(${cols}, 1fr)`, gridTemplateRows: `repeat(${rows}, 1fr)`, gap }"
  >
    <template v-for="(c, i) in cells" :key="i">
      <img :src="c" alt="" class="h-full w-full object-cover" draggable="false" />
    </template>
  </div>
  <div v-else class="flex h-full w-full items-center justify-center text-on-surface-variant">
    <span class="text-lg font-extrabold">{{ (fallback ?? "?").slice(0, 2).toUpperCase() }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { CatalogTrack } from "@/types";

const props = defineProps<{ tracks: CatalogTrack[] }>();

const BIN_SECS = 30;

const bins = computed(() => {
  const durations = props.tracks
    .map((t) => t.duration_secs ?? 0)
    .filter((d) => d > 0);
  if (durations.length === 0) return null;

  const max = Math.max(...durations);
  const binCount = Math.min(Math.ceil(max / BIN_SECS) + 1, 24);
  const counts = new Array<number>(binCount).fill(0);
  for (const d of durations) {
    counts[Math.min(Math.floor(d / BIN_SECS), binCount - 1)]++;
  }
  const maxCount = Math.max(...counts, 1);
  return counts.map((count, i) => ({
    label: `${Math.floor((i * BIN_SECS) / 60)}:${String((i * BIN_SECS) % 60).padStart(2, "0")}`,
    count,
    pct: (count / maxCount) * 100,
  }));
});

function fmtClock(secs: number): string {
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}
</script>

<template>
  <div v-if="!bins" class="py-4 text-center text-xs text-[color:var(--color-on-surface-variant,#78716c)]">No duration data.</div>
  <div v-else>
    <div class="flex h-28 items-end gap-1">
      <div
        v-for="(bin, i) in bins"
        :key="i"
        class="group relative min-w-0 flex-1 rounded-t-sm bg-[color:var(--color-primary,#5b7c32)]/80 transition-all duration-300 hover:bg-[color:var(--color-primary,#5b7c32)]"
        :style="{ height: Math.max(bin.pct, bin.count > 0 ? 4 : 0) + '%' }"
        :title="`${fmtClock(i * BIN_SECS)} – ${fmtClock((i + 1) * BIN_SECS)}: ${bin.count} tracks`"
      />
    </div>
    <div class="mt-1 flex justify-between text-[10px] tabular-nums text-[color:var(--color-on-surface-variant,#78716c)]">
      <span>{{ bins[0]?.label }}</span>
      <span>{{ bins[bins.length - 1]?.label }}+</span>
    </div>
  </div>
</template>

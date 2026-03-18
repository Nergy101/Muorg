<script setup lang="ts">
import { computed } from "vue";
import type { CatalogTrack } from "../../types";

const props = defineProps<{ tracks: CatalogTrack[] }>();

const MAX_SHOWN = 12;

const rows = computed(() => {
  const counts = new Map<string, number>();
  for (const t of props.tracks) {
    const artist = t.artist?.trim() || "Unknown Artist";
    counts.set(artist, (counts.get(artist) ?? 0) + 1);
  }

  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const top = sorted.slice(0, MAX_SHOWN);
  const max = top[0]?.[1] ?? 1;

  return top.map(([label, count]) => ({
    label,
    count,
    pct: (count / max) * 100,
    trackPct: ((count / props.tracks.length) * 100).toFixed(1),
  }));
});
</script>

<template>
  <div v-if="!tracks.length" class="py-4 text-center text-xs text-stone-500">No tracks.</div>
  <div v-else class="space-y-1.5">
    <div
      v-for="row in rows"
      :key="row.label"
      class="group flex items-center gap-3"
    >
      <!-- Artist name -->
      <span class="w-36 shrink-0 truncate text-right text-xs text-stone-400 group-hover:text-stone-200 transition-colors">
        {{ row.label }}
      </span>
      <!-- Bar -->
      <div class="relative h-5 min-w-0 flex-1 overflow-hidden rounded-sm bg-stone-700/60">
        <div
          class="h-full rounded-sm bg-[#5b7c32] transition-all duration-300"
          :style="{ width: row.pct + '%' }"
        />
      </div>
      <!-- Count -->
      <span class="w-14 shrink-0 text-right text-xs tabular-nums text-stone-500">
        {{ row.count }} <span class="text-stone-600">({{ row.trackPct }}%)</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { CatalogTrack } from "@/types";

const props = defineProps<{ tracks: CatalogTrack[] }>();

const hovered = ref<number | null>(null);

const bars = computed(() => {
  const counts = [0, 0, 0, 0, 0, 0];
  for (const t of props.tracks) {
    if (t.rating == null) counts[0]++;
    else counts[Math.min(5, Math.max(1, t.rating))]++;
  }
  const max = Math.max(...counts, 1);
  const total = props.tracks.length;
  return [
    { label: "—", stars: 0, count: counts[0], heightPct: (counts[0] / max) * 100, pct: total ? ((counts[0] / total) * 100).toFixed(1) : "0.0" },
    { label: "★", stars: 1, count: counts[1], heightPct: (counts[1] / max) * 100, pct: total ? ((counts[1] / total) * 100).toFixed(1) : "0.0" },
    { label: "★★", stars: 2, count: counts[2], heightPct: (counts[2] / max) * 100, pct: total ? ((counts[2] / total) * 100).toFixed(1) : "0.0" },
    { label: "★★★", stars: 3, count: counts[3], heightPct: (counts[3] / max) * 100, pct: total ? ((counts[3] / total) * 100).toFixed(1) : "0.0" },
    { label: "★★★★", stars: 4, count: counts[4], heightPct: (counts[4] / max) * 100, pct: total ? ((counts[4] / total) * 100).toFixed(1) : "0.0" },
    { label: "★★★★★", stars: 5, count: counts[5], heightPct: (counts[5] / max) * 100, pct: total ? ((counts[5] / total) * 100).toFixed(1) : "0.0" },
  ];
});
</script>

<template>
  <div v-if="!tracks.length" class="py-4 text-center text-xs text-stone-500">No tracks.</div>
  <div v-else class="flex flex-col gap-3">
    <div class="flex h-40 items-end gap-2">
      <div
        v-for="bar in bars"
        :key="bar.stars"
        class="group flex min-w-0 flex-1 flex-col items-center gap-1 self-stretch"
        @mouseenter="hovered = bar.stars"
        @mouseleave="hovered = null"
      >
        <div class="relative min-w-0 w-full flex-1 flex flex-col justify-end">
          <div
            class="w-full rounded-t-sm transition-all duration-200"
            :class="bar.stars === 0 ? (hovered === bar.stars ? 'bg-stone-500' : 'bg-stone-600') : (hovered === bar.stars ? 'bg-amber-400' : 'bg-amber-500')"
            :style="{ height: bar.heightPct + '%', minHeight: bar.count > 0 ? '2px' : '0' }"
          />
        </div>
      </div>
    </div>
    <div class="flex items-start gap-2">
      <div
        v-for="bar in bars"
        :key="bar.stars"
        class="min-w-0 flex-1 text-center leading-tight"
        :class="[bar.stars === 0 ? 'text-[9px] text-stone-500' : 'text-[10px] text-amber-500/70', hovered === bar.stars ? (bar.stars === 0 ? '!text-stone-300' : '!text-amber-400') : '']"
      >
        {{ bar.label }}
      </div>
    </div>
    <p class="text-center text-xs text-stone-400 transition-opacity duration-100" :class="hovered !== null ? 'opacity-100' : 'opacity-0'">
      <template v-if="hovered === 0">
        <span class="font-medium text-stone-200">Unrated</span> — {{ bars[0].count }} tracks ({{ bars[0].pct }}%)
      </template>
      <template v-else-if="hovered !== null">
        <span class="font-medium text-amber-400">{{ hovered }}★</span> — {{ bars.find(b => b.stars === hovered)?.count }} tracks ({{ bars.find(b => b.stars === hovered)?.pct }}%)
      </template>
      <template v-else>&nbsp;</template>
    </p>
  </div>
</template>

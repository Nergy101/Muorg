<script setup lang="ts">
import { computed, ref } from "vue";
import type { CatalogTrack } from "../../types";

const props = defineProps<{ tracks: CatalogTrack[] }>();

const hovered = ref<string | null>(null);

const bars = computed(() => {
  const counts = new Map<string, number>();

  for (const t of props.tracks) {
    const key = t.year != null ? `${Math.floor(t.year / 10) * 10}s` : "Unknown";
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }

  // Sort decades chronologically; push Unknown to end
  const sorted = [...counts.entries()].sort(([a], [b]) => {
    if (a === "Unknown") return 1;
    if (b === "Unknown") return -1;
    return parseInt(a) - parseInt(b);
  });

  const max = Math.max(...sorted.map(([, c]) => c), 1);
  const total = props.tracks.length;

  return sorted.map(([label, count]) => ({
    label,
    count,
    heightPct: (count / max) * 100,
    trackPct: ((count / total) * 100).toFixed(1),
  }));
});

</script>

<template>
  <div v-if="!tracks.length" class="py-4 text-center text-xs text-stone-500">No tracks.</div>
  <div v-else class="flex flex-col gap-3">
    <!-- Bar chart -->
    <div class="flex h-40 items-end gap-1.5">
      <div
        v-for="bar in bars"
        :key="bar.label"
        class="group flex min-w-0 flex-1 flex-col items-center gap-1"
        @mouseenter="hovered = bar.label"
        @mouseleave="hovered = null"
      >
        <!-- Count label on hover -->
        <span
          class="text-[10px] tabular-nums transition-opacity duration-100"
          :class="hovered === bar.label ? 'text-stone-300 opacity-100' : 'opacity-0'"
        >
          {{ bar.count }}
        </span>
        <!-- Bar -->
        <div class="w-full min-w-0 flex-1 flex items-end">
          <div
            class="w-full rounded-t-sm transition-all duration-200"
            :class="hovered === bar.label ? 'bg-[#6d9a3a]' : 'bg-[#5b7c32]'"
            :style="{ height: bar.heightPct + '%' }"
          />
        </div>
      </div>
    </div>
    <!-- X-axis labels -->
    <div class="flex items-start gap-1.5">
      <div
        v-for="bar in bars"
        :key="bar.label"
        class="min-w-0 flex-1 text-center text-[9px] leading-tight text-stone-500"
        :class="{ 'text-stone-300': hovered === bar.label }"
      >
        {{ bar.label }}
      </div>
    </div>
    <!-- Hovered detail -->
    <p v-if="hovered" class="text-center text-xs text-stone-400">
      <span class="font-medium text-stone-200">{{ hovered }}</span>
      — {{ bars.find((b) => b.label === hovered)?.count }} tracks
      ({{ bars.find((b) => b.label === hovered)?.trackPct }}%)
    </p>
  </div>
</template>

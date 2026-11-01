<script setup lang="ts">
import { computed } from "vue";
import type { CatalogTrack } from "@/types";

const props = defineProps<{ tracks: CatalogTrack[] }>();

const MAX_SHOWN = 10;

const rows = computed(() => {
  const played = props.tracks.filter((t) => t.play_count > 0);
  const sorted = [...played].sort((a, b) => b.play_count - a.play_count).slice(0, MAX_SHOWN);
  const max = sorted[0]?.play_count ?? 1;
  return sorted.map((t) => ({
    id: t.id,
    label: `${t.title?.trim() || "Unknown Title"} · ${t.artist?.trim() || "Unknown Artist"}`,
    plays: t.play_count,
    pct: (t.play_count / max) * 100,
  }));
});
</script>

<template>
  <div v-if="!rows.length" class="py-4 text-center text-xs text-[color:var(--color-on-surface-variant,#78716c)]">Nothing played yet.</div>
  <div v-else class="space-y-1.5">
    <div v-for="row in rows" :key="row.id" class="group flex items-center gap-3">
      <span class="w-44 shrink-0 truncate text-right text-xs text-[color:var(--color-on-surface-variant,#a8a29e)] group-hover:text-[color:var(--color-on-surface,#e7e5e4)] transition-colors">
        {{ row.label }}
      </span>
      <div class="relative h-5 min-w-0 flex-1 overflow-hidden rounded-sm bg-[color:var(--color-surface-variant,#44403c)]/60">
        <div
          class="h-full rounded-sm bg-[color:var(--color-primary,#5b7c32)] transition-all duration-300"
          :style="{ width: row.pct + '%' }"
        />
      </div>
      <span class="w-12 shrink-0 text-right text-xs tabular-nums text-[color:var(--color-on-surface-variant,#78716c)]">
        ×{{ row.plays }}
      </span>
    </div>
  </div>
</template>

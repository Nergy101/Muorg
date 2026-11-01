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
  <div v-else class="space-y-3">
    <div v-for="row in rows" :key="row.id" class="group">
      <div class="mb-1 flex items-baseline justify-between gap-3">
        <span class="min-w-0 truncate text-xs text-[color:var(--color-on-surface-variant,#a8a29e)] group-hover:text-[color:var(--color-on-surface,#e7e5e4)] transition-colors">
          {{ row.label }}
        </span>
        <span class="shrink-0 text-xs tabular-nums text-[color:var(--color-on-surface-variant,#78716c)]">
          ×{{ row.plays }}
        </span>
      </div>
      <div class="relative h-5 w-full overflow-hidden rounded-sm bg-[color:var(--color-surface-variant,#44403c)]/60">
        <div
          class="h-full rounded-sm bg-[color:var(--color-primary,#5b7c32)] transition-all duration-300"
          :style="{ width: row.pct + '%' }"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { CatalogTrack } from "@/types";
import type { MissingMetadataField } from "../../reports";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const props = defineProps<{ tracks: CatalogTrack[] }>();
const emit = defineEmits<{ "view-field": [field: MissingMetadataField] }>();

const fields = computed(() => {
  const n = props.tracks.length;
  if (n === 0) return [];

  const check = (fn: (t: CatalogTrack) => boolean, field: MissingMetadataField, label: string) => {
    const have = props.tracks.filter(fn).length;
    const missing = n - have;
    const pct = Math.round((have / n) * 100);
    return { field, label, missing, pct };
  };

  return [
    check((t) => !!t.title?.trim(),        "title",        "Title"),
    check((t) => !!t.artist?.trim(),       "artist",       "Artist"),
    check((t) => !!t.album?.trim(),        "album",        "Album"),
    check((t) => t.year != null,           "year",         "Year"),
    check((t) => !!t.genre?.trim(),        "genre",        "Genre"),
    check((t) => t.has_cover,             "has_cover",    "Cover art"),
    check((t) => t.track_number != null,   "track_number", "Track #"),
    check((t) => t.rating != null,         "rating",       "Rating"),
  ];
});

function pctColor(pct: number) {
  if (pct >= 100) return "text-[color:var(--color-primary,#5b7c32)]";
  if (pct >= 80)  return "text-[color:var(--color-on-surface,#e7e5e4)]";
  if (pct >= 40)  return "text-[color:var(--color-on-surface-variant,#d6d3d1)]";
  return "text-[color:var(--color-error,#f87171)]";
}

function cardBorder(pct: number) {
  if (pct >= 100) return "border-[color:var(--color-primary,#5b7c32)] bg-[color:var(--color-surface,#292524)]";
  if (pct >= 80)  return "border-[color:var(--color-outline,#57534e)] bg-[color:var(--color-surface,#292524)]/60";
  if (pct >= 40)  return "border-[color:var(--color-outline,#57534e)] bg-[color:var(--color-surface,#292524)]/60";
  return "border-[color:var(--color-error,#b91c1c)]/40 bg-[color:var(--color-surface,#292524)]/60";
}
</script>

<template>
  <div v-if="!tracks.length" class="py-4 text-center text-xs text-[color:var(--color-on-surface-variant,#78716c)]">No tracks.</div>
  <div v-else class="grid grid-cols-2 gap-2 sm:grid-cols-4">
    <button
      v-for="f in fields"
      :key="f.field"
      type="button"
      class="group flex flex-col items-center gap-1 rounded-lg border px-3 py-3 text-center transition"
      :class="[
        cardBorder(f.pct),
        f.pct < 100
          ? 'cursor-pointer hover:brightness-125'
          : 'cursor-default',
      ]"
      :disabled="f.pct >= 100"
      @click="f.pct < 100 && emit('view-field', f.field)"
    >
      <span class="text-[11px] font-medium text-[color:var(--color-on-surface-variant,#a8a29e)]">{{ f.label }}</span>

      <!-- 100% state -->
      <template v-if="f.pct >= 100">
        <FeatherIcon name="check-circle" class="mt-1 h-5 w-5 text-[color:var(--color-primary,#5b7c32)]" />
        <span class="text-sm font-bold text-[color:var(--color-primary,#5b7c32)]">100%</span>
      </template>

      <!-- Incomplete state -->
      <template v-else>
        <span class="mt-1 text-2xl font-bold tabular-nums leading-none" :class="pctColor(f.pct)">
          {{ f.pct }}%
        </span>
        <span class="text-[10px] text-[color:var(--color-on-surface-variant,#78716c)]">{{ f.missing }} missing</span>
        <span
          class="mt-0.5 inline-flex items-center gap-1 text-[10px] text-[color:var(--color-on-surface-variant,#78716c)] opacity-0 transition-opacity group-hover:opacity-100 group-hover:text-[color:var(--color-on-surface,#e7e5e4)]"
        >
          View tracks
          <FeatherIcon name="arrow-right" class="h-3 w-3" />
        </span>
      </template>
    </button>
  </div>
</template>

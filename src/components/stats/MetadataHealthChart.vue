<script setup lang="ts">
import { computed } from "vue";
import type { CatalogTrack } from "../../types";
import type { MissingMetadataField } from "../../stores/settings";
import FeatherIcon from "../shared/FeatherIcon.vue";

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
  if (pct >= 100) return "text-emerald-400";
  if (pct >= 80)  return "text-stone-200";
  if (pct >= 40)  return "text-amber-400";
  return "text-red-400";
}

function cardBorder(pct: number) {
  if (pct >= 100) return "border-emerald-700/50 bg-emerald-950/30";
  if (pct >= 80)  return "border-stone-600/70 bg-stone-900/60";
  if (pct >= 40)  return "border-amber-700/40 bg-amber-950/20";
  return "border-red-700/40 bg-red-950/20";
}
</script>

<template>
  <div v-if="!tracks.length" class="py-4 text-center text-xs text-stone-500">No tracks.</div>
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
      <span class="text-[11px] font-medium text-stone-400">{{ f.label }}</span>

      <!-- 100% state -->
      <template v-if="f.pct >= 100">
        <FeatherIcon name="check-circle" class="mt-1 h-5 w-5 text-emerald-500" />
        <span class="text-sm font-bold text-emerald-400">100%</span>
      </template>

      <!-- Incomplete state -->
      <template v-else>
        <span class="mt-1 text-2xl font-bold tabular-nums leading-none" :class="pctColor(f.pct)">
          {{ f.pct }}%
        </span>
        <span class="text-[10px] text-stone-500">{{ f.missing }} missing</span>
        <span
          class="mt-0.5 inline-flex items-center gap-1 text-[10px] text-stone-600 opacity-0 transition-opacity group-hover:opacity-100 group-hover:text-stone-300"
        >
          View tracks
          <FeatherIcon name="arrow-right" class="h-3 w-3" />
        </span>
      </template>
    </button>
  </div>
</template>

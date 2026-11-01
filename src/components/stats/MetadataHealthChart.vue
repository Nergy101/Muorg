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

/** Score colour on a red → orange → green scale, so a good score never
 *  renders white/neutral. Fixed hexes read well on both dark and light. */
const SCORE_COLORS = [
  { min: 90, text: "text-[#4ade80]", border: "border-[#4ade80]/50" }, // green
  { min: 70, text: "text-[#a3e635]", border: "border-[#a3e635]/50" }, // lime
  { min: 50, text: "text-[#fb923c]", border: "border-[#fb923c]/50" }, // orange
  { min: 30, text: "text-[#f97316]", border: "border-[#f97316]/50" }, // deep orange
  { min: 0,  text: "text-[#ef4444]", border: "border-[#ef4444]/50" }, // red
] as const;

function scoreColor(pct: number) {
  return SCORE_COLORS.find((s) => pct >= s.min) ?? SCORE_COLORS[SCORE_COLORS.length - 1];
}

// 100% is plain green like a checkmark, not the theme's primary accent.
const PERFECT = { text: "text-[#4ade80]", border: "border-[#4ade80]/50" };

function pctColor(pct: number) {
  if (pct >= 100) return PERFECT.text;
  return scoreColor(pct).text;
}

function cardBorder(pct: number) {
  const bg = "bg-[color:var(--color-surface,#292524)]";
  if (pct >= 100) return `${PERFECT.border} ${bg}`;
  return `${scoreColor(pct).border} ${bg}/60`;
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
        <FeatherIcon name="check-circle" class="mt-1 h-5 w-5 text-[#4ade80]" />
        <span class="text-sm font-bold text-[#4ade80]">100%</span>
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

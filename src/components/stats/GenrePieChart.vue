<script setup lang="ts">
import { computed, ref } from "vue";
import type { CatalogTrack } from "@/types";

const props = defineProps<{
  tracks: CatalogTrack[];
}>();

// ── Palette ──────────────────────────────────────────────────────────────────
// Muted, earthy colours that read well on stone-800/stone-900 backgrounds.
const PALETTE = [
  "#5b7c32", // green (app accent)
  "#4a7fa5", // steel blue
  "#b06a2a", // amber brown
  "#7a4d9e", // muted purple
  "#3a9a7a", // teal
  "#b04848", // dusty red
  "#3a7aaa", // mid blue
  "#9a7a2a", // gold
  "#5a8a5a", // sage
  "#8a4a6a", // rose
  "#4a6a9a", // slate blue
  "#7a6a3a", // olive
];

// ── Data computation ──────────────────────────────────────────────────────────
const MIN_SLICE_PERCENT = 1.5; // slices below this are folded into "Other"

const slices = computed(() => {
  const counts = new Map<string, number>();
  for (const t of props.tracks) {
    const genre = t.genre?.trim() || "Unknown";
    counts.set(genre, (counts.get(genre) ?? 0) + 1);
  }

  const total = props.tracks.length;
  if (total === 0) return [];

  // Sort descending by count
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);

  const main: { label: string; count: number; pct: number; color: string }[] = [];
  let otherCount = 0;

  sorted.forEach(([label, count], i) => {
    const pct = (count / total) * 100;
    if (pct < MIN_SLICE_PERCENT && i >= 5) {
      otherCount += count;
    } else {
      main.push({ label, count, pct, color: PALETTE[main.length % PALETTE.length] });
    }
  });

  if (otherCount > 0) {
    main.push({
      label: "Other",
      count: otherCount,
      pct: (otherCount / total) * 100,
      color: "#57534e", // stone-600
    });
  }

  return main;
});

const total = computed(() => props.tracks.length);

// ── SVG pie path generation ───────────────────────────────────────────────────
const CX = 100;
const CY = 100;
const R = 80;
const R_INNER = 38; // donut hole

function polarToXY(angleDeg: number, r: number) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return { x: CX + r * Math.cos(rad), y: CY + r * Math.sin(rad) };
}

/** Build an SVG path for a donut slice from startDeg to endDeg. */
function slicePath(startDeg: number, endDeg: number): string {
  // Clamp to avoid degenerate arc at exactly 360°
  const sweep = Math.min(endDeg - startDeg, 359.9999);
  const large = sweep > 180 ? 1 : 0;
  const o = polarToXY(startDeg, R);
  const i1 = polarToXY(startDeg, R_INNER);
  const o2 = polarToXY(startDeg + sweep, R);
  const i2 = polarToXY(startDeg + sweep, R_INNER);
  return [
    `M ${o.x} ${o.y}`,
    `A ${R} ${R} 0 ${large} 1 ${o2.x} ${o2.y}`,
    `L ${i2.x} ${i2.y}`,
    `A ${R_INNER} ${R_INNER} 0 ${large} 0 ${i1.x} ${i1.y}`,
    "Z",
  ].join(" ");
}

interface SliceSvg {
  path: string;
  color: string;
  label: string;
  pct: number;
  count: number;
  midAngle: number;
}

const svgSlices = computed((): SliceSvg[] => {
  let cursor = 0;
  return slices.value.map((s) => {
    const span = (s.pct / 100) * 360;
    const mid = cursor + span / 2;
    const path = slicePath(cursor, cursor + span);
    cursor += span;
    return { path, color: s.color, label: s.label, pct: s.pct, count: s.count, midAngle: mid };
  });
});

// ── Hover state ───────────────────────────────────────────────────────────────
const hovered = ref<number | null>(null);
</script>

<template>
  <div v-if="total === 0" class="py-8 text-center text-xs text-stone-500">
    No tracks in library yet.
  </div>

  <div v-else class="flex flex-col items-center gap-3">
    <!-- SVG donut chart -->
    <div class="relative shrink-0">
      <svg viewBox="0 0 200 200" class="h-44 w-44 drop-shadow-sm">
        <g>
          <path
            v-for="(s, i) in svgSlices"
            :key="s.label"
            :d="s.path"
            :fill="s.color"
            :opacity="hovered === null || hovered === i ? 1 : 0.35"
            class="cursor-pointer transition-opacity duration-150"
            @mouseenter="hovered = i"
            @mouseleave="hovered = null"
          />
        </g>

        <!-- Centre label -->
        <text
          x="100"
          y="96"
          text-anchor="middle"
          dominant-baseline="middle"
          class="select-none"
          fill="#d6d3d1"
          font-size="13"
          font-weight="600"
        >
          {{ hovered !== null ? svgSlices[hovered].pct.toFixed(1) + "%" : total }}
        </text>
        <text
          x="100"
          y="111"
          text-anchor="middle"
          dominant-baseline="middle"
          class="select-none"
          fill="#78716c"
          font-size="8"
        >
          {{ hovered !== null ? svgSlices[hovered].label : "tracks" }}
        </text>
      </svg>
    </div>

    <!-- Legend -->
    <div class="w-full flex flex-col gap-1">
      <div
        v-for="(s, i) in svgSlices"
        :key="s.label"
        class="flex cursor-default items-center gap-2 rounded px-1 py-0.5 transition-colors"
        :class="hovered === i ? 'bg-stone-700' : 'hover:bg-stone-700/50'"
        @mouseenter="hovered = i"
        @mouseleave="hovered = null"
      >
        <span
          class="h-2.5 w-2.5 shrink-0 rounded-sm"
          :style="{ background: s.color }"
        />
        <span class="min-w-0 flex-1 truncate text-xs text-stone-300">{{ s.label }}</span>
        <span class="shrink-0 text-[11px] tabular-nums text-stone-500">{{ s.count }}</span>
        <span class="w-10 shrink-0 text-right text-[11px] tabular-nums text-stone-400">
          {{ s.pct.toFixed(1) }}%
        </span>
      </div>
    </div>
  </div>
</template>

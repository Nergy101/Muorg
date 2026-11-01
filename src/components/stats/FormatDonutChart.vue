<script setup lang="ts">
import { computed, ref } from "vue";
import type { CatalogTrack } from "@/types";

const props = defineProps<{ tracks: CatalogTrack[] }>();

// Muted palette that reads well on dark surfaces; first entry is the app accent.
const PALETTE = [
  "#5b7c32", // green (app accent)
  "#4a7fa5", // steel blue
  "#b06a2a", // amber brown
  "#7a4d9e", // muted purple
  "#3a9a7a", // teal
  "#b04848", // dusty red
  "#9a7a2a", // gold
  "#8a4a6a", // rose
];

const slices = computed(() => {
  const counts = new Map<string, number>();
  for (const t of props.tracks) {
    const fmt = (t.format ?? "").trim().toUpperCase() || "Unknown";
    counts.set(fmt, (counts.get(fmt) ?? 0) + 1);
  }
  const total = props.tracks.length;
  if (total === 0) return [];
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  return sorted.map(([label, count], i) => ({
    label,
    count,
    pct: (count / total) * 100,
    color: PALETTE[i % PALETTE.length],
  }));
});

const total = computed(() => props.tracks.length);

// ── SVG donut geometry ────────────────────────────────────────────────────────
const CX = 100;
const CY = 100;
const R = 80;
const R_INNER = 38;

function polarToXY(angleDeg: number, r: number) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return { x: CX + r * Math.cos(rad), y: CY + r * Math.sin(rad) };
}

function slicePath(startDeg: number, endDeg: number): string {
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
}

const svgSlices = computed((): SliceSvg[] => {
  let cursor = 0;
  return slices.value.map((s) => {
    const span = (s.pct / 100) * 360;
    const path = slicePath(cursor, cursor + span);
    cursor += span;
    return { path, color: s.color, label: s.label, pct: s.pct, count: s.count };
  });
});

const hovered = ref<number | null>(null);
</script>

<template>
  <div v-if="total === 0" class="py-8 text-center text-xs text-[color:var(--color-on-surface-variant,#78716c)]">
    No tracks in library yet.
  </div>

  <div v-else class="flex flex-col items-center gap-3">
    <div class="relative shrink-0">
      <svg viewBox="0 0 200 200" class="h-44 w-44 drop-shadow-sm">
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
        <text
          x="100"
          y="96"
          text-anchor="middle"
          dominant-baseline="middle"
          class="select-none"
          fill="var(--color-on-surface,#d6d3d1)"
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
          fill="var(--color-on-surface-variant,#78716c)"
          font-size="8"
        >
          {{ hovered !== null ? svgSlices[hovered].label : "tracks" }}
        </text>
      </svg>
    </div>

    <div class="flex w-full flex-wrap justify-center gap-x-4 gap-y-1">
      <div
        v-for="(s, i) in svgSlices"
        :key="s.label"
        class="flex cursor-default items-center gap-1.5 rounded px-1 py-0.5 transition-colors"
        :class="hovered === i ? 'bg-[color:var(--color-surface-variant,#44403c)]' : 'hover:bg-[color:var(--color-surface-variant,#44403c)]/50'"
        @mouseenter="hovered = i"
        @mouseleave="hovered = null"
      >
        <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="{ background: s.color }" />
        <span class="text-xs font-mono text-[color:var(--color-on-surface,#d6d3d1)]">{{ s.label }}</span>
        <span class="text-[11px] tabular-nums text-[color:var(--color-on-surface-variant,#78716c)]">
          {{ s.count }} ({{ s.pct.toFixed(1) }}%)
        </span>
      </div>
    </div>
  </div>
</template>

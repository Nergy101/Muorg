<script setup lang="ts">
import { computed, ref } from "vue";
import type { CatalogTrack } from "../../types";

const props = defineProps<{ tracks: CatalogTrack[] }>();

const hovered = ref<number | null>(null); // hovered year index

// ── Data ─────────────────────────────────────────────────────────────────────
const chartData = computed(() => {
  const datedTracks = props.tracks.filter((t) => t.year != null && t.year > 1900 && t.year <= new Date().getFullYear() + 1);
  if (datedTracks.length === 0) return null;

  const countByYear = new Map<number, number>();
  for (const t of datedTracks) {
    const y = t.year!;
    countByYear.set(y, (countByYear.get(y) ?? 0) + 1);
  }

  const minYear = Math.min(...countByYear.keys());
  const maxYear = Math.max(...countByYear.keys());

  // Fill every year in range (gaps get 0)
  const years: number[] = [];
  for (let y = minYear; y <= maxYear; y++) years.push(y);

  const counts = years.map((y) => countByYear.get(y) ?? 0);
  const maxCount = Math.max(...counts, 1);

  return { years, counts, maxCount, datedTracks: datedTracks.length };
});

// ── SVG geometry ──────────────────────────────────────────────────────────────
const W = 400;
const H = 120;
const PAD_L = 28;
const PAD_R = 8;
const PAD_T = 12;
const PAD_B = 20;

const svgData = computed(() => {
  const d = chartData.value;
  if (!d) return null;

  const { years, counts, maxCount } = d;
  const n = years.length;
  const chartW = W - PAD_L - PAD_R;
  const chartH = H - PAD_T - PAD_B;

  const xOf = (i: number) => PAD_L + (i / Math.max(n - 1, 1)) * chartW;
  const yOf = (v: number) => PAD_T + chartH - (v / maxCount) * chartH;

  // Area path
  const pts = years.map((_, i) => `${xOf(i)},${yOf(counts[i])}`).join(" L ");
  const areaPath = `M ${xOf(0)},${yOf(0)} L ${pts} L ${xOf(n - 1)},${yOf(0)} Z`;
  const linePath = `M ${pts}`;

  // Y-axis tick labels (0 and max)
  const yTicks = [
    { y: yOf(0), label: "0" },
    { y: yOf(maxCount), label: String(maxCount) },
  ];

  // X-axis: show ~5 evenly spaced year labels
  const step = Math.max(1, Math.floor(n / 5));
  const xTicks = years
    .map((yr, i) => ({ i, yr }))
    .filter(({ i }) => i % step === 0 || i === n - 1);

  // Hover points
  const points = years.map((yr, i) => ({
    x: xOf(i),
    y: yOf(counts[i]),
    year: yr,
    count: counts[i],
    i,
  }));

  return { areaPath, linePath, yTicks, xTicks, points, xOf, yOf, chartH };
});
</script>

<template>
  <div v-if="!tracks.length || !chartData" class="py-4 text-center text-xs text-stone-500">
    No tracks with release year data.
  </div>
  <div v-else class="flex flex-col gap-1">
    <p class="text-[10px] text-stone-600">
      {{ chartData.datedTracks }} of {{ tracks.length }} tracks have a release year
    </p>

    <svg
      :viewBox="`0 0 ${W} ${H}`"
      class="w-full"
      @mouseleave="hovered = null"
    >
      <defs>
        <linearGradient id="area-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#5b7c32" stop-opacity="0.45" />
          <stop offset="100%" stop-color="#5b7c32" stop-opacity="0.03" />
        </linearGradient>
      </defs>

      <template v-if="svgData">
        <!-- Area fill -->
        <path :d="svgData.areaPath" fill="url(#area-grad)" />

        <!-- Line -->
        <path
          :d="svgData.linePath"
          fill="none"
          stroke="#5b7c32"
          stroke-width="1.5"
          stroke-linejoin="round"
          stroke-linecap="round"
        />

        <!-- Y-axis ticks -->
        <text
          v-for="tick in svgData.yTicks"
          :key="tick.label"
          :x="PAD_L - 4"
          :y="tick.y + 3"
          text-anchor="end"
          font-size="7"
          fill="#57534e"
        >{{ tick.label }}</text>

        <!-- X-axis ticks -->
        <text
          v-for="tick in svgData.xTicks"
          :key="tick.yr"
          :x="svgData.points[tick.i].x"
          :y="H - 4"
          text-anchor="middle"
          font-size="7"
          fill="#57534e"
        >{{ tick.yr }}</text>

        <!-- Hover hit areas (transparent rects per year column) -->
        <rect
          v-for="pt in svgData.points"
          :key="pt.i"
          :x="pt.x - (W - PAD_L - PAD_R) / Math.max(chartData!.years.length - 1, 1) / 2"
          :y="PAD_T"
          :width="(W - PAD_L - PAD_R) / Math.max(chartData!.years.length - 1, 1)"
          :height="svgData.chartH"
          fill="transparent"
          class="cursor-crosshair"
          @mouseenter="hovered = pt.i"
        />

        <!-- Hover indicator -->
        <template v-if="hovered !== null && svgData.points[hovered]">
          <line
            :x1="svgData.points[hovered].x"
            :x2="svgData.points[hovered].x"
            :y1="PAD_T"
            :y2="PAD_T + svgData.chartH"
            stroke="#78716c"
            stroke-width="1"
            stroke-dasharray="3 2"
          />
          <circle
            :cx="svgData.points[hovered].x"
            :cy="svgData.points[hovered].y"
            r="3"
            fill="#5b7c32"
            stroke="#1c1917"
            stroke-width="1.5"
          />
          <!-- Tooltip -->
          <g>
            <rect
              :x="Math.min(svgData.points[hovered].x + 6, W - 60)"
              :y="svgData.points[hovered].y - 20"
              width="54"
              height="18"
              rx="3"
              fill="#292524"
              stroke="#44403c"
              stroke-width="0.5"
            />
            <text
              :x="Math.min(svgData.points[hovered].x + 6, W - 60) + 27"
              :y="svgData.points[hovered].y - 7"
              text-anchor="middle"
              font-size="8"
              fill="#d6d3d1"
            >{{ svgData.points[hovered].year }}: {{ svgData.points[hovered].count }}</text>
          </g>
        </template>
      </template>
    </svg>
  </div>
</template>

<template>
  <!-- List mode -->
  <div
    v-if="mode === 'list'"
    :data-album-key="item.key"
    class="flex h-14 w-full select-none items-center gap-3 px-4 text-left transition-colors [-webkit-touch-callout:none] lg:cursor-pointer lg:hover:bg-surface-container/70"
    :class="isActive && !activeColor ? 'bg-secondary/[0.08]' : ''"
    :style="listActiveStyle"
    @click="emit('open')"
    @touchstart.passive="lp.onTouchstart"
    @touchmove.passive="lp.onTouchmove"
    @touchend="lp.onTouchend"
    @contextmenu.prevent="emit('actions')"
  >
    <div class="h-12 w-12 shrink-0 overflow-hidden rounded-md bg-surface-variant">
      <img
        v-if="coverUrl"
        :src="coverUrl"
        :alt="item.album"
        class="h-full w-full object-cover [-webkit-touch-callout:none]"
        draggable="false"
        decoding="async"
      />
      <div v-else class="flex h-full w-full items-center justify-center">
        <MageIcon name="music" class="h-5 w-5 text-on-surface-variant/60" />
      </div>
    </div>

    <div class="min-w-0 flex-1">
      <MarqueeText
        :text="item.album"
        :class="listTitleClass"
        :style="listTitleStyle"
      />
      <MarqueeText :text="item.albumArtist" class="text-body-sm text-on-surface-variant" />
    </div>

    <div class="flex shrink-0 items-center gap-1 text-on-surface-variant">
      <MageIcon name="music" class="h-3.5 w-3.5" />
      <span class="text-label-md tabular-nums">{{ item.trackCount }}</span>
    </div>
  </div>

  <!-- Grid mode -->
  <button
    v-else-if="ready"
    :data-album-key="item.key"
    type="button"
    class="relative aspect-square w-full select-none overflow-hidden rounded-xl bg-surface-variant shadow-[0_12px_30px_-8px_rgba(0,0,0,0.55),0_2px_6px_rgba(0,0,0,0.35),inset_0_0_0_1px_rgba(255,255,255,0.06)] ring-1 ring-white/10 text-left transition-transform duration-150 [-webkit-touch-callout:none] active:scale-95 lg:hover:scale-[1.03] lg:hover:shadow-[0_16px_36px_-8px_rgba(0,0,0,0.6)]"
    @click="emit('open')"
    @touchstart.passive="lp.onTouchstart"
    @touchmove.passive="lp.onTouchmove"
    @touchend="lp.onTouchend"
    @contextmenu.prevent="emit('actions')"
  >
    <img
      :src="coverUrl!"
      :alt="item.album"
      class="absolute inset-0 h-full w-full object-cover [-webkit-touch-callout:none]"
      draggable="false"
      decoding="async"
    />

    <!-- Secondary playing indicator: the little 3-bar equalizer in the
         top-right corner, frozen while paused. -->
    <div
      v-if="isActive"
      class="absolute right-2 top-2 z-20 flex h-7 items-center rounded-full bg-black/45 px-2.5 backdrop-blur-sm"
      :style="activeColor ? { color: `rgb(${activeColor})` } : undefined"
      :class="activeColor ? '' : 'text-primary'"
    >
      <EqualizerBars :paused="!player.isPlaying" />
    </div>

    <!-- Playing indicator on the active album: a single round line centered
         over the card that spikes outwards — an SVG ring with animated
         radial spikes, frozen while paused. Tinted with the album's own
         color when one is available. -->
    <div
      v-if="isActive"
      class="absolute inset-0 z-10 flex items-center justify-center bg-black/40"
      :style="activeColor ? { color: `rgb(${activeColor})` } : undefined"
      :class="activeColor ? '' : 'text-primary'"
    >
      <div class="relative h-32 w-32" aria-hidden="true">
        <svg viewBox="0 0 128 128" class="absolute inset-0 h-full w-full">
          <path
            :d="ringPath"
            fill="none"
            stroke="currentColor"
            stroke-width="2.5"
            stroke-linecap="round"
          />
        </svg>
      </div>
    </div>

    <!-- Frosted scrim so the caption stays legible over any artwork -->
    <div
      class="glass-scrim pointer-events-none absolute inset-x-0 bottom-0 h-[44px]"
      aria-hidden="true"
    />

    <!-- Caption, vertically centred within the frosted panel -->
    <div class="scrim-label absolute inset-x-0 bottom-0 flex h-[44px] items-center gap-2 px-2.5">
      <div class="min-w-0 flex-1">
        <MarqueeText :text="item.album" class="text-label-lg font-semibold text-on-surface" />
        <div class="truncate text-label-sm text-on-surface-variant">{{ item.albumArtist }}</div>
      </div>
      <div class="flex shrink-0 items-center gap-1 text-on-surface-variant">
        <MageIcon name="music" class="h-3 w-3" />
        <span class="text-label-sm tabular-nums">{{ item.trackCount }}</span>
      </div>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import MarqueeText from "./MarqueeText.vue";
import EqualizerBars from "@shared/components/EqualizerBars.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useLongPress } from "../composables/useLongPress";
import { rawMainColor, useDominantColor, useMostCommonColor, useProminentHueColor } from "../composables/useDominantColor";
import type { AlbumGridItem } from "../types";

/** Active-highlight usability: unlike isColorBland (built for vivid player
 *  accents), muted warm colors like #7C6C4D are perfectly good highlight
 *  colors — only reject near-white, near-black and low-saturation gray.
 *  `minSat` lets the first-choice average demand more saturation than the
 *  fallback tiers, so a muddy gray average doesn't mask a vivid accent. */
function isUsableHighlightColor(rgb: string, minSat: number): boolean {
  const parts = rgb.split(",").map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) return false;
  const [r, g, b] = parts;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  const lum = (r * 0.299 + g * 0.587 + b * 0.114) / 255;
  const sat = max === 0 ? 0 : (max - min) / max;
  if (lum > 0.85 || lum < 0.06) return false;
  return sat >= minSat;
}

const props = defineProps<{
  item: AlbumGridItem;
  mode: "grid" | "list";
  isActive?: boolean;
}>();

const emit = defineEmits<{ open: []; actions: [] }>();

const lib = useLibraryStore();
const player = usePlayerStore();
const lp = useLongPress(() => emit("actions"));

// Radial visualizer: one closed ring path whose radius bulges outward per
// angle. Animated with rAF while the track plays; frozen (last frame kept)
// when paused.
const RING_POINTS = 48;
const RING_BASE_R = 48;
const RING_MAX_SPIKE = 15;
const ringPath = ref("");
let ringRaf = 0;
// Smoothed per-angle amplitudes, lerped toward the live spectrum each frame
// so the ring flows instead of jumping.
const ringAmps = new Array<number>(RING_POINTS).fill(0);

function ringFrame(t: number): void {
  const spectrum = player.getFrequencyData();
  // Treat an all-zero spectrum (suspended context, silent captured stream)
  // as "no data" so the ring never sits dead while music plays.
  let level = 0;
  if (spectrum) for (let i = 0; i < spectrum.length; i++) level += spectrum[i];
  const usable = spectrum && spectrum.length > 0 && level > spectrum.length * 2;
  const pts: [number, number][] = [];
  for (let i = 0; i < RING_POINTS; i++) {
    const a = (i / RING_POINTS) * Math.PI * 2;
    // Map the angle to a frequency bin (low bins = outer ring); fall back to
    // a gentle time-based wobble when no analyser data is available.
    let target: number;
    if (usable) {
      // Auto-gain: normalize by this frame's max bin, otherwise quiet
      // passages render as a near-flat ring. Log-scale angle→bin mapping
      // spreads the (bass-heavy) spectrum across the whole circle.
      let max = 1;
      for (let b = 0; b < spectrum.length; b++) if (spectrum[b] > max) max = spectrum[b];
      const bin = Math.floor(Math.pow(i / RING_POINTS, 1.8) * spectrum.length * 0.6);
      target = (spectrum[bin] / max) * RING_MAX_SPIKE;
      // Peak-hold with fast decay: spikes jump with the beat, then fall.
      ringAmps[i] = Math.max(target, ringAmps[i] * 0.8);
    } else {
      target =
        RING_MAX_SPIKE *
        (0.5 + 0.5 * Math.sin(t * 0.003 + i * 0.7)) *
        (0.55 + 0.45 * Math.sin(t * 0.0013 + i * 1.7));
      ringAmps[i] += (target - ringAmps[i]) * 0.35;
    }
    const r = RING_BASE_R + ringAmps[i];
    pts.push([64 + r * Math.cos(a), 64 + r * Math.sin(a)]);
  }
  // Smooth closed curve through the points (quadratic via midpoints).
  const mid = (p: [number, number], q: [number, number]): [number, number] => [
    (p[0] + q[0]) / 2,
    (p[1] + q[1]) / 2,
  ];
  let d = `M ${mid(pts[RING_POINTS - 1], pts[0]).join(" ")}`;
  for (let i = 0; i < RING_POINTS; i++) {
    const m = mid(pts[i], pts[(i + 1) % RING_POINTS]);
    d += ` Q ${pts[i].join(" ")} ${m.join(" ")}`;
  }
  ringPath.value = d + " Z";
}

function ringLoop(t: number): void {
  ringFrame(t);
  ringRaf = requestAnimationFrame(ringLoop);
}

watch(
  () => props.isActive && player.isPlaying,
  (active) => {
    if (active) {
      ringRaf = requestAnimationFrame(ringLoop);
    } else {
      cancelAnimationFrame(ringRaf);
      if (props.isActive) ringFrame(0); // paused: freeze on a static frame
    }
  },
  { immediate: true },
);

onUnmounted(() => cancelAnimationFrame(ringRaf));

const coverUrl = computed(() => {
  if (!props.item.hasCover || props.item.coverTrackId === null) return null;
  return lib.coverCache.get(props.item.coverTrackId) ?? null;
});

/** Grid cards render only once their cover has actually loaded — no placeholder. */
const ready = computed(() => props.item.hasCover && coverUrl.value !== null);

// Active-card highlight color: the album's own dominant cover color, sampled
// only while the card is the playing one (the URL is null otherwise, so
// inactive cards never pay the image-decode cost). Algorithm order:
// 1) saturation-weighted average, 2) peak saturated hue (catches e.g. red
// text on a black/white cover), 3) most common (modal) color. If all come
// out unusable there is no color and the theme secondary (green) is used.
const activeCoverUrl = computed(() => (props.isActive ? coverUrl.value : null));
const activeGlowRgb = useDominantColor(activeCoverUrl);
const activeHueRgb = useProminentHueColor(activeCoverUrl);
const activeModeRgb = useMostCommonColor(activeCoverUrl);
const activeColor = computed<string | null>(() => {
  if (!props.isActive) return null;
  // Tier 1 (the average) must be clearly saturated — a muddy gray-brown
  // average on a mostly-monochrome cover should not mask the vivid accent
  // the hue tier finds. Fallback tiers accept muted colors (e.g. #7C6C4D).
  const avg = rawMainColor(activeGlowRgb.value);
  if (isUsableHighlightColor(avg, 0.22)) return avg;
  const hue = activeHueRgb.value;
  if (isUsableHighlightColor(hue, 0.08)) return hue;
  const mode = activeModeRgb.value;
  if (isUsableHighlightColor(mode, 0.08)) return mode;
  return null;
});

const listActiveStyle = computed<Record<string, string> | undefined>(() => {
  if (!props.isActive || !activeColor.value) return undefined;
  return { backgroundColor: `rgba(${activeColor.value},0.08)` };
});
const listTitleClass = computed(() => {
  if (!props.isActive) return "text-body-md font-semibold text-on-surface";
  return activeColor.value
    ? "text-body-md font-semibold"
    : "text-body-md font-semibold text-secondary";
});
const listTitleStyle = computed<Record<string, string> | undefined>(() => {
  if (!props.isActive || !activeColor.value) return undefined;
  return { color: `rgb(${activeColor.value})` };
});

// Fetch the cover as soon as the card mounts (and whenever the cover track
// changes) so it resolves in the background and the card appears whole. No
// IntersectionObserver here: a ready-gated card isn't in the DOM until loaded,
// so an observer could never fire to trigger the fetch.
watch(
  () => props.item.coverTrackId,
  (id) => {
    if (id !== null && !lib.coverCache.has(id)) lib.requestCover(id);
  },
  { immediate: true },
);
</script>

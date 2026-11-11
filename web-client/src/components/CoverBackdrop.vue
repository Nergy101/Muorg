<template>
  <!-- The dominant-colour backdrop shared by the maximized player and the
       album view: a darkened dominant colour as the base, then the cover
       stretched and blurred across the pane with masked edges that fade into
       the base. Paints its own background so the parent's theme background
       stays fully covered. -->
  <div
    class="pointer-events-none absolute inset-0 z-0 overflow-hidden"
    :style="baseStyle"
    aria-hidden="true"
  >
    <div
      v-if="coverUrl"
      class="edge-blur-art absolute"
      :class="{
        'edge-art-sharp': sharp,
        'edge-blur-art-player': variant === 'player',
        'edge-blur-art-album': variant === 'album',
      }"
      :style="artStyle"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { isColorBland, useDominantColor } from "../composables/useDominantColor";
import { useImageSize } from "../composables/useImageSize";
import { useSettingsStore } from "../stores/settings";

const props = withDefaults(
  defineProps<{
    coverUrl: string | null;
    /**
     * "player" (default) keeps a dark dominant-colour wash in the centre.
     * Both variants fade to the theme background (#1C1917) at the edges, so
     * the masked art blends into the sidebar/background instead of showing a
     * hard seam at the pane's left edge on desktop.
     */
    variant?: "player" | "album";
    /** Unblur the art and drop the edge mask so it fills the pane sharply. */
    sharp?: boolean;
  }>(),
  { variant: "player", sharp: false },
);

const coverUrlRef = computed(() => props.coverUrl);
const glowRgb = useDominantColor(coverUrlRef);
const coverSize = useImageSize(coverUrlRef);
const settings = useSettingsStore();

// lg breakpoint matches the CSS media queries that set the FS blur (12px at
// lg+, 6px below) — the resolution-based blur is capped at those amounts.
const lgQuery = window.matchMedia("(min-width: 64rem)");
const isLg = ref(lgQuery.matches);
const onLgChange = (e: MediaQueryListEvent) => { isLg.value = e.matches; };
lgQuery.addEventListener("change", onLgChange);
onBeforeUnmount(() => lgQuery.removeEventListener("change", onLgChange));

/**
 * FS-mode blur scaled by the cover's resolution: high-res art gets less blur
 * (down to MIN_BLUR), low-res art gets the full current amount (12px at lg,
 * 6px below), interpolated logarithmically on total megapixels between
 * LOW_MP and HIGH_MP. Calibrated against Harakiri for the Sky's "Arson"
 * cover (1200x1200 = 1.44MP), which reads best at 6px: with LOW=0.25MP and
 * HIGH=2.6MP that lands at t=0.75 → 6px on desktop. Unknown resolution falls
 * back to the CSS default (no override).
 */
const MIN_BLUR = 4;
const LOW_MP = 0.25;
const HIGH_MP = 2.6;
const sharpBlurPx = computed(() => {
  // Explicit user override (sidebar blur slider) wins over the
  // resolution-based default.
  if (settings.fsBackdropBlur !== null) return settings.fsBackdropBlur;
  const s = coverSize.value;
  if (!s || s.width <= 0 || s.height <= 0) return null;
  const mp = (s.width * s.height) / 1_000_000;
  const maxBlur = isLg.value ? 12 : 6;
  const t = Math.min(1, Math.max(0, (Math.log(mp) - Math.log(LOW_MP)) / (Math.log(HIGH_MP) - Math.log(LOW_MP))));
  return Math.round(maxBlur - t * (maxBlur - MIN_BLUR));
});

const artStyle = computed(() => ({
  backgroundImage: `url(${props.coverUrl})`,
  opacity: props.sharp ? 1 : 0.9,
  ...(props.sharp && sharpBlurPx.value !== null
    ? { filter: `blur(${sharpBlurPx.value}px)` }
    : {}),
}));

const baseStyle = computed(() => {
  // Both variants end at the theme background (#1C1917) at the edges, so the
  // masked art fades into the sidebar/background colour — on desktop the
  // player sheet sits beside the sidebar, and a near-black base there showed
  // as a hard vertical seam on its left edge. The player keeps a dark
  // dominant-colour wash in the centre; the album stays flat #1C1917.
  if (props.variant === "album") return { backgroundColor: "#1C1917" };
  const parts = glowRgb.value.split(",").map(Number);
  const dark =
    parts.length !== 3 || isColorBland(glowRgb.value)
      ? "#0c0a09"
      : `rgb(${Math.round(parts[0] * 0.08)},${Math.round(parts[1] * 0.08)},${Math.round(parts[2] * 0.08)})`;
  return {
    backgroundColor: "#1C1917",
    backgroundImage: `radial-gradient(closest-side at center, ${dark} 30%, #1C1917 100%)`,
  };
});
</script>

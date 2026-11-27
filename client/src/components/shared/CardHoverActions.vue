<script setup lang="ts">
import { computed } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useCatalogStore } from "../../stores/catalog";
import { useDominantColor, useMostCommonColor } from "../../composables/useDominantColor";
import { accentHexFromRgb } from "../../composables/useDynamicAccent";

/**
 * Hover actions on album/playlist cards: Shuffle + Play. The Play button is
 * tinted with the accent color this cover art would give (dominant color),
 * falling back to the theme accent.
 */
const props = defineProps<{
  coverPath?: string;
  /** Explicit accent color for the Play button (e.g. a mix's lucide icon color).
   *  When set, it overrides the cover-art-derived color. */
  accentColor?: string;
}>();
const emit = defineEmits<{
  (e: "play"): void;
  (e: "shuffle"): void;
}>();

const store = useCatalogStore();
const coverUrl = computed(() => (props.coverPath ? store.getCoverDataUrl(props.coverPath) : null));
const modeRgb = useMostCommonColor(coverUrl);
const dominantRgb = useDominantColor(coverUrl);
// Mode first (cover background); for light/low-saturation covers the mode is
// gray, so fall back to the saturation-weighted average which catches the tint.
const playColor = computed(
  () => props.accentColor ?? accentHexFromRgb(modeRgb.value) ?? accentHexFromRgb(dominantRgb.value) ?? "var(--primary)",
);

/** Perceived brightness (0..1) of a CSS color — hex/rgb(), resolving var() refs. */
function brightnessOf(color: string): number {
  let c = color.trim();
  const varMatch = c.match(/var\(\s*(--[a-zA-Z0-9-]+)/);
  if (varMatch) {
    c = getComputedStyle(document.documentElement).getPropertyValue(varMatch[1]).trim() || c;
  }
  const hex = c.match(/^#([0-9a-fA-F]{6})$/);
  const rgb = c.match(/^rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)/);
  let r = 0, g = 0, b = 0;
  if (hex) {
    const n = parseInt(hex[1], 16);
    r = (n >> 16) & 255; g = (n >> 8) & 255; b = n & 255;
  } else if (rgb) {
    r = Number(rgb[1]); g = Number(rgb[2]); b = Number(rgb[3]);
  } else {
    return 0.5;
  }
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255;
}

/** A white icon on a light accent is unreadable — flip to black when the accent is light. */
const playIconColor = computed(() => (brightnessOf(playColor.value) > 0.65 ? "#000" : "#fff"));
</script>

<template>
  <div class="card-actions">
    <button
      type="button"
      class="card-action-btn card-action-shuffle"
      aria-label="Shuffle"
      title="Shuffle"
      @click.stop="emit('shuffle')"
    >
      <FeatherIcon name="shuffle" class="h-4 w-4" />
    </button>
    <button
      type="button"
      class="card-action-btn card-action-play"
      :style="{
        background: playColor,
        color: playIconColor,
        boxShadow: `0 6px 16px color-mix(in srgb, ${playColor} 40%, transparent)`,
      }"
      aria-label="Play"
      title="Play"
      @click.stop="emit('play')"
    >
      <FeatherIcon name="play" class="ml-0.5 h-4 w-4" />
    </button>
  </div>
</template>

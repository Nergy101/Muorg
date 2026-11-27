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

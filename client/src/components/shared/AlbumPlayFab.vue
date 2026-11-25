<script setup lang="ts">
import { computed } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useCatalogStore } from "../../stores/catalog";
import { useDominantColor, isColorBland } from "../../composables/useDominantColor";

/**
 * Hover play button for album cards, tinted with the accent color that this
 * album's own cover art would give (dominant color), falling back to the
 * theme accent when the art is missing or gray.
 */
const props = defineProps<{ coverPath: string }>();
const emit = defineEmits<{ (e: "play"): void }>();

const store = useCatalogStore();
const coverUrl = computed(() => store.getCoverDataUrl(props.coverPath));
const glowRgb = useDominantColor(coverUrl);
const fabColor = computed(() =>
  glowRgb.value && !isColorBland(glowRgb.value) ? `rgb(${glowRgb.value})` : "var(--primary)",
);
</script>

<template>
  <span
    class="play-fab"
    :style="{
      background: fabColor,
      color: '#fff',
      boxShadow: `0 6px 16px color-mix(in srgb, ${fabColor} 40%, transparent)`,
    }"
    role="button"
    aria-label="Play"
    @click.stop="emit('play')"
  >
    <FeatherIcon name="play" class="ml-0.5 h-4 w-4" />
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { useDominantColor, useEdgeColors, PRIMARY_RGB } from "../../composables/useDominantColor";
import type { AlbumGridItem } from "./AlbumGridView.vue";

const props = defineProps<{
  album: AlbumGridItem;
  isPlaying?: boolean;
}>();

const emit = defineEmits<{
  (e: "openAlbum", albumKey: string): void;
}>();

const store = useCatalogStore();
const settings = useSettingsStore();
store.fetchCover(props.album.coverPath);
const coverDataUrl = computed(() => store.getCoverDataUrl(props.album.coverPath));
const glowRgb = useDominantColor(coverDataUrl);
const edgeColors = useEdgeColors(coverDataUrl);

const isLightTheme = computed(() => {
  const t = settings.theme;
  if (t === "light") return true;
  if (t === "auto" && typeof window !== "undefined") {
    return !window.matchMedia("(prefers-color-scheme: dark)").matches;
  }
  return false;
});

const textPanelStyle = computed(() => {
  const color = edgeColors.value?.colors?.[0] ?? glowRgb.value;
  if (isLightTheme.value) {
    return {
      backgroundImage: `linear-gradient(160deg, rgba(${color},0.42) 0%, rgba(${color},0.18) 100%)`,
      backgroundColor: `rgba(248,247,245,0.92)`,
      borderTop: `1px solid rgba(${color},0.40)`,
    };
  }
  return {
    backgroundImage: `linear-gradient(160deg, rgba(${color},0.60) 0%, rgba(${color},0.28) 100%)`,
    backgroundColor: `rgba(16,14,12,0.90)`,
    borderTop: `1px solid rgba(${color},0.45)`,
  };
});
</script>

<template>
  <button
    type="button"
    class="flex w-full min-h-[220px] flex-col overflow-hidden rounded bg-stone-900 text-center transition-colors hover:border-primary"
    :class="isPlaying ? 'border-[2px] border-primary' : 'border border-stone-700'"
    :style="isPlaying ? { boxShadow: `0 0 0 2px rgba(${PRIMARY_RGB},0.5), 0 0 20px 6px rgba(${PRIMARY_RGB},0.45), 0 0 40px 10px rgba(${PRIMARY_RGB},0.20)` } : undefined"
    @click="emit('openAlbum', album.key)"
  >
    <!-- Album art: square crop, uniform across all cards -->
    <div class="h-40 w-full shrink-0 overflow-hidden bg-stone-800">
      <img
        v-if="coverDataUrl"
        :src="coverDataUrl"
        alt=""
        class="h-full w-full object-cover"
      />
      <div v-else class="flex h-full w-full items-center justify-center text-stone-400">
        <span class="inline-flex h-12 w-12 items-center justify-center rounded-full border border-stone-500 text-xl">♪</span>
      </div>
    </div>
    <!-- Frosted glass text panel, colored from album art -->
    <div
      class="w-full px-3 pb-3 pt-2.5 backdrop-blur-sm"
      :style="textPanelStyle"
    >
      <div class="truncate text-sm font-semibold text-stone-100">{{ album.album }}</div>
      <div class="mt-0.5 truncate text-xs text-stone-300">{{ album.albumArtist }}</div>
    </div>
  </button>
</template>

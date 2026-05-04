<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { useDominantColor, useEdgeColors, PRIMARY_RGB } from "../../composables/useDominantColor";
import type { AlbumGridItem } from "./AlbumGridView.vue";
import { setTracksDragGhost } from "../../utils/dragGhost";

const props = defineProps<{
  album: AlbumGridItem;
  isPlaying?: boolean;
}>();

const emit = defineEmits<{
  (e: "openAlbum", albumKey: string): void;
  (e: "albumContextMenu", event: MouseEvent, albumKey: string): void;
}>();

const store = useCatalogStore();
const settings = useSettingsStore();

function onDragStart(e: DragEvent) {
  if (!e.dataTransfer) return;
  setTracksDragGhost(e, props.album.album, props.album.trackIds.length, coverDataUrl.value);
  e.dataTransfer.setData("application/muorg-tracks", JSON.stringify(props.album.trackIds));
  e.dataTransfer.effectAllowed = "copy";
  store.setInternalQueueDrag(true, props.album.trackIds);
}

function onDragEnd() {
  store.setInternalQueueDrag(false);
}

// Boost this album's cover to the front of the fetch queue — it's now visible.
if (props.album.hasCover) {
  store.boostCoverPriority(props.album.coverPath);
}

/** undefined = loading, null = no cover, CoverInfo = has cover */
const coverEntry = computed(() => props.album.hasCover ? store.getCover(props.album.coverPath) : null);
const coverDataUrl = computed(() => store.getCoverDataUrl(props.album.coverPath));

/**
 * imageReady tracks whether the browser has fully decoded and painted the image.
 * We keep the spinner up until @load fires, preventing the half-painted flash.
 */
const imageReady = ref(false);

// Reset imageReady whenever the data URL changes (e.g. cover updated after metadata edit).
watch(coverDataUrl, () => { imageReady.value = false; });

function onImageLoad() { imageReady.value = true; }
function onImageError() { imageReady.value = false; }

/**
 * Three display states:
 *  "spinner"     — cover is fetching OR data URL arrived but img not decoded yet
 *  "image"       — img fully loaded and painted
 *  "placeholder" — no cover (confirmed), or hasCover=false
 */
const displayState = computed(() => {
  if (!props.album.hasCover) return "placeholder";
  const e = coverEntry.value;
  if (e === undefined) return "spinner";   // still fetching from backend
  if (e === null)      return "placeholder"; // fetched, confirmed no art
  if (!imageReady.value) return "spinner"; // data arrived, browser still decoding
  return "image";
});

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
    draggable="true"
    class="flex w-full min-h-[220px] flex-col overflow-hidden rounded bg-stone-900 text-center transition-colors hover:border-primary"
    :class="isPlaying ? 'border-[2px] border-primary' : 'border border-stone-700'"
    :style="isPlaying ? { boxShadow: `0 0 0 2px rgba(${PRIMARY_RGB},0.5), 0 0 20px 6px rgba(${PRIMARY_RGB},0.45), 0 0 40px 10px rgba(${PRIMARY_RGB},0.20)` } : undefined"
    @click="emit('openAlbum', album.key)"
    @contextmenu.prevent="emit('albumContextMenu', $event, album.key)"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
  >
    <!-- Album art area -->
    <div class="relative h-40 w-full shrink-0 overflow-hidden bg-stone-800">
      <!-- Spinner: shown while fetching OR while browser is decoding the image -->
      <div
        v-if="displayState === 'spinner'"
        class="absolute inset-0 flex items-center justify-center"
      >
        <svg class="album-cover-spinner h-7 w-7 text-stone-500" viewBox="0 0 24 24" fill="none">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2.5" />
          <path class="opacity-80" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>

      <!-- No cover placeholder -->
      <div
        v-else-if="displayState === 'placeholder'"
        class="flex h-full w-full items-center justify-center text-stone-500"
      >
        <span class="inline-flex h-12 w-12 items-center justify-center rounded-full border border-stone-600 text-xl">♪</span>
      </div>

      <!--
        The img is always rendered once coverDataUrl is set so the browser can
        decode it in the background. It stays invisible until imageReady=true,
        at which point displayState switches to "image" and the spinner hides.
      -->
      <img
        v-if="coverDataUrl"
        :src="coverDataUrl"
        alt=""
        class="absolute inset-0 h-full w-full object-cover transition-opacity duration-150"
        :class="displayState === 'image' ? 'opacity-100' : 'opacity-0'"
        decoding="async"
        @load="onImageLoad"
        @error="onImageError"
      />
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

<style scoped>
@keyframes album-cover-spin {
  to { transform: rotate(360deg); }
}
.album-cover-spinner {
  animation: album-cover-spin 0.9s linear infinite;
  transform-origin: center;
}
</style>

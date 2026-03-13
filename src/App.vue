<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import Sidebar from "./components/layout/Sidebar.vue";
import LibraryTable from "./components/library/LibraryTable.vue";
import PlayerBar from "./components/playback/PlayerBar.vue";
import PlayScreenPlayBar from "./components/playback/PlayScreenPlayBar.vue";
import MetadataEditor from "./components/metadata/MetadataEditor.vue";
import {
  getGlowBlobs,
  getSimpleGlowBlobs,
  useDominantColor,
  useEdgeColors,
  isColorBland,
} from "./composables/useDominantColor";
import type { GlowBlob } from "./composables/useDominantColor";
import { useCatalogStore } from "./stores/catalog";
import { useSettingsStore } from "./stores/settings";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { invoke } from "@tauri-apps/api/core";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { playerGlowIntensity } = storeToRefs(settingsStore);
const sidebarCollapsed = ref(false);
const playExpanded = ref(false);

const expandedCoverUrl = computed(() => {
  if (!playExpanded.value) return null;
  const tracks = store.selectedTracks;
  if (tracks.length !== 1) return null;
  return store.getCoverDataUrl(tracks[0].path);
});
const glowRgb = useDominantColor(expandedCoverUrl);
const edgeColors = useEdgeColors(expandedCoverUrl);
const expandedTrack = computed(() =>
  playExpanded.value && store.selectedTracks.length === 1 ? store.selectedTracks[0] : null,
);
const trackKey = computed(
  () => expandedTrack.value?.path ?? expandedTrack.value?.id ?? "",
);

/** Edge blur when bland center + vibrant edge colors. Blobs when bland center + uniform edge (e.g. white). */
const useEdgeBlurMode = computed(
  () =>
    isColorBland(glowRgb.value) &&
    !!expandedCoverUrl.value &&
    !!edgeColors.value?.length &&
    !isColorBland(edgeColors.value[0]),
);

const currentBlobs = computed(() => {
  const key = String(trackKey.value);
  if (useEdgeBlurMode.value) return [];
  if (isColorBland(glowRgb.value)) {
    const color = edgeColors.value?.[0] ?? glowRgb.value;
    return getSimpleGlowBlobs(color, key);
  }
  return getGlowBlobs(glowRgb.value, key);
});

/** Opacity multiplier by intensity (off = 0, no blobs shown). */
const glowOpacityScale = computed(() => {
  const v = playerGlowIntensity.value;
  if (v === "off") return 0;
  if (v === "subdued") return 0.4;
  if (v === "vibrant") return 1.6;
  return 1.2;
});

/** Blobs to display, scaled by intensity. */
const displayCurrentBlobs = computed(() => {
  const scale = glowOpacityScale.value;
  if (scale <= 0 || useEdgeBlurMode.value) return [];
  const blobs = currentBlobs.value;
  return scale === 1 ? blobs : blobs.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
});

const displayOutgoingBlobs = computed(() => {
  const scale = glowOpacityScale.value;
  if (scale <= 0) return [];
  const blobs = outgoingBlobs.value;
  return scale === 1 ? blobs : blobs.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
});

/** Opacity for edge-blur layer (0.5–0.9 based on intensity). */
const edgeBlurOpacity = computed(() => {
  if (playerGlowIntensity.value === "off") return 0;
  const v = playerGlowIntensity.value;
  if (v === "subdued") return 0.45;
  if (v === "vibrant") return 0.85;
  return 0.65;
});

const showGlow = computed(() => playerGlowIntensity.value !== "off");

/** Use album color for controls only when it's not bland; otherwise use primary. */
const effectiveAccentRgb = computed(() =>
  isColorBland(glowRgb.value) ? undefined : glowRgb.value,
);

/** Very dark tint for background – from center color (vivid) or blended edge colors (bland). */
const glowBgColor = computed(() => {
  if (!showGlow.value) return undefined;
  if (isColorBland(glowRgb.value) && edgeColors.value) {
    const ec = edgeColors.value;
    const parts = ec.map((s) => s.split(",").map(Number));
    const valid = parts.filter((p) => p.length === 3);
    if (valid.length === 0) return undefined;
    const r = Math.round(valid.reduce((a, p) => a + p[0], 0) / valid.length * 0.06);
    const g = Math.round(valid.reduce((a, p) => a + p[1], 0) / valid.length * 0.06);
    const b = Math.round(valid.reduce((a, p) => a + p[2], 0) / valid.length * 0.06);
    return `rgb(${r},${g},${b})`;
  }
  if (!isColorBland(glowRgb.value)) {
    const parts = glowRgb.value.split(",").map(Number);
    if (parts.length !== 3) return undefined;
    const [r, g, b] = parts;
    return `rgb(${Math.round(r * 0.08)},${Math.round(g * 0.08)},${Math.round(b * 0.08)})`;
  }
  return undefined;
});

/** Previous blobs for transitions. */
const lastBlobs = ref<GlowBlob[]>([]);
/** Outgoing layer (crossfade when color changes). */
const outgoingBlobs = ref<GlowBlob[]>([]);
/** Outgoing cover URL for edge-blur crossfade (previous album). */
const outgoingCoverUrl = ref<string | null>(null);
const outgoingGlowOpacity = ref(0);
const currentGlowOpacity = ref(1);
const currentGlowRef = ref<HTMLElement | null>(null);
const outgoingGlowRef = ref<HTMLElement | null>(null);
/** When true, crossfading: blob positions snap, only opacity animates. */
const isCrossfading = ref(false);

const GLOW_TRANSITION_MS = 1800;
const BLOB_MORPH_MS = 800;

// On track change: morph (same album) or crossfade (different album). Must run before sync watch.
watch(
  expandedTrack,
  (newTrack, oldTrack) => {
    const newKey = newTrack?.path ?? newTrack?.id ?? null;
    const oldKey = oldTrack?.path ?? oldTrack?.id ?? null;
    if (!playExpanded.value || !newKey || !oldKey || newKey === oldKey || !showGlow.value) return;
    const prevBlobs = lastBlobs.value;
    const sameAlbum = (newTrack?.album ?? "") === (oldTrack?.album ?? "");

    if (sameAlbum) {
      // Same album art: blobs just move, no crossfade. Vue updates currentBlobs,
      // CSS transition on blob positions animates the morph.
      lastBlobs.value = currentBlobs.value;
    } else {
      // Different album: crossfade (only opacity, no blob morph)
      isCrossfading.value = true;
      outgoingBlobs.value = prevBlobs;
      outgoingCoverUrl.value = store.getCoverDataUrl(oldTrack.path) ?? null;
      outgoingGlowOpacity.value = 1;
      currentGlowOpacity.value = 0;
      lastBlobs.value = currentBlobs.value;
      nextTick(() => {
        const currentEl = currentGlowRef.value;
        const outgoingEl = outgoingGlowRef.value;
        if (!currentEl || !outgoingEl) return;
        const opts: KeyframeAnimationOptions = {
          duration: GLOW_TRANSITION_MS,
          fill: "forwards",
        };
        currentEl.animate([{ opacity: 0 }, { opacity: 1 }], { ...opts, easing: "ease-in" });
        outgoingEl.animate([{ opacity: 1 }, { opacity: 0 }], { ...opts, easing: "ease-out" });
        setTimeout(() => {
          currentGlowOpacity.value = 1;
          outgoingGlowOpacity.value = 0;
          outgoingCoverUrl.value = null;
          isCrossfading.value = false;
        }, GLOW_TRANSITION_MS);
      });
    }
  },
  { flush: "post" },
);

// Keep lastBlobs in sync (runs after track-change watch so prevBlobs is correct)
watch(
  currentBlobs,
  (blobs) => {
    lastBlobs.value = blobs;
  },
  { immediate: true },
);

const showEditor = computed(() => store.selectedTrackIds.length > 0);
const isDropTarget = ref(false);
const activeTab = ref<"library" | "metadata" | "play">(
  (settingsStore.defaultBottomPanel as "library" | "metadata" | "play") ?? "library",
);
let unlistenDragDrop: (() => void) | null = null;

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key !== "Escape" || !playExpanded.value) return;
  const target = e.target as HTMLElement;
  if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) return;
  e.preventDefault();
  playExpanded.value = false;
}

onMounted(async () => {
  document.addEventListener("keydown", onGlobalKeydown);
  try {
    unlistenDragDrop = await getCurrentWindow().onDragDropEvent((event) => {
      if (event.payload.type === "enter" || event.payload.type === "over") {
        isDropTarget.value = true;
      } else if (event.payload.type === "leave") {
        isDropTarget.value = false;
      } else if (event.payload.type === "drop") {
        isDropTarget.value = false;
        const paths = event.payload.paths ?? [];
        if (paths.length === 0) return;
        (async () => {
          const folders = new Set<string>();
          for (const p of paths) {
            try {
              const folder = await invoke<string>("path_to_folder", { path: p });
              folders.add(folder);
            } catch {
              // path_to_folder can fail for invalid paths; skip
            }
          }
          for (const folder of folders) {
            try {
              await store.addFolder(folder);
            } catch {
              // error shown in store
            }
          }
        })();
      }
    });
  } catch {
    // not in Tauri or API unavailable
  }
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
  unlistenDragDrop?.();
});
</script>

<template>
  <div
    class="relative grid h-screen grid-rows-[minmax(0,1fr)_minmax(72px,auto)] grid-cols-[auto,1fr] overflow-hidden"
    :class="{ 'ring-2 ring-amber-500/80 ring-inset bg-amber-950/20': isDropTarget }"
  >
    <!-- Top row: sidebar + table view -->
    <div class="row-start-1 row-end-2 col-start-1 col-end-2 h-full overflow-hidden">
      <Sidebar :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = !sidebarCollapsed" />
    </div>
    <main
      class="row-start-1 row-end-2 col-start-2 col-end-3 flex min-w-0 flex-col overflow-hidden transition-colors duration-150"
    >
      <LibraryTable v-model:activeTab="activeTab" />
    </main>

    <!-- Bottom row: metadata / player bar spanning full width -->
    <div
      class="row-start-2 row-end-3 col-span-2 flex shrink-0 flex-col min-w-0 border-t border-stone-700 bg-stone-900/95"
    >
      <PlayerBar
        :class="activeTab === 'play' ? 'sr-only h-0 overflow-hidden' : ''"
        @expand="() => { playExpanded = true; sidebarCollapsed = true; activeTab = 'play'; }"
      />
      <PlayScreenPlayBar
        v-if="activeTab === 'play' && !playExpanded"
        @expand="() => { playExpanded = true; sidebarCollapsed = true; }"
      />
      <MetadataEditor
        v-if="showEditor && activeTab === 'metadata'"
        :key="store.selectedTrackIds.join(',')"
      />
    </div>

    <!-- Full-window player overlay ("focus" mode) -->
    <Teleport to="body">
      <div
        v-if="playExpanded"
        class="fixed inset-0 z-[350] flex h-screen w-screen flex-col overflow-visible"
        :style="{ backgroundColor: glowBgColor ?? '#000' }"
      >
        <!-- Glow: edge-blur (blurred album art) when bland, or procedural blobs when vivid. -->
        <div
          v-if="showGlow"
          ref="currentGlowRef"
          class="glow-layer pointer-events-none fixed inset-0 z-0 flex items-center justify-center overflow-visible"
          :style="{ opacity: currentGlowOpacity, backgroundColor: glowBgColor ?? '#000' }"
          aria-hidden="true"
        >
          <!-- Edge blur: every pixel at the edge bleeds outward via blur -->
          <template v-if="useEdgeBlurMode && expandedCoverUrl && glowOpacityScale > 0">
            <div
              class="edge-blur-art absolute"
              :style="{
                backgroundImage: `url(${expandedCoverUrl})`,
                opacity: edgeBlurOpacity,
              }"
            />
          </template>
          <template v-else>
            <div
              v-for="(blob, i) in displayCurrentBlobs"
              :key="`current-${i}`"
              class="glow-blob absolute inset-0 origin-top-left"
            :style="{
              background: `radial-gradient(ellipse at center, rgba(${blob.rgb},${blob.opacity.toFixed(2)}) 0%, rgba(${blob.rgb},${(blob.opacity * 0.6).toFixed(2)}) 25%, rgba(${blob.rgb},${(blob.opacity * 0.2).toFixed(2)}) 45%, rgba(${blob.rgb},0.04) 70%, transparent 90%)`,
              transform: `translate(${blob.cx * 100}%, ${blob.cy * 100}%) translate(-50%, -50%) scale(${blob.rx}, ${blob.ry})`,
              filter: 'blur(24px)',
              transition: isCrossfading ? 'none' : `transform ${BLOB_MORPH_MS}ms ease-in-out`,
              willChange: isCrossfading ? 'auto' : 'transform',
            }"
            />
          </template>
        </div>
        <div
          v-if="showGlow"
          ref="outgoingGlowRef"
          class="glow-layer pointer-events-none fixed inset-0 z-0 flex items-center justify-center overflow-visible"
          :style="{ opacity: outgoingGlowOpacity, backgroundColor: glowBgColor ?? '#000' }"
          aria-hidden="true"
        >
          <!-- Outgoing edge blur only when previous track was in edge-blur mode (had no blobs) -->
          <template v-if="outgoingCoverUrl && glowOpacityScale > 0 && outgoingBlobs.length === 0">
            <div
              class="edge-blur-art absolute"
              :style="{
                backgroundImage: `url(${outgoingCoverUrl})`,
                opacity: edgeBlurOpacity,
              }"
            />
          </template>
          <template v-else>
            <div
              v-for="(blob, i) in displayOutgoingBlobs"
              :key="`outgoing-${i}`"
              class="glow-blob absolute inset-0 origin-top-left"
            :style="{
              background: `radial-gradient(ellipse at center, rgba(${blob.rgb},${blob.opacity.toFixed(2)}) 0%, rgba(${blob.rgb},${(blob.opacity * 0.6).toFixed(2)}) 25%, rgba(${blob.rgb},${(blob.opacity * 0.2).toFixed(2)}) 45%, rgba(${blob.rgb},0.04) 70%, transparent 90%)`,
              transform: `translate(${blob.cx * 100}%, ${blob.cy * 100}%) translate(-50%, -50%) scale(${blob.rx}, ${blob.ry})`,
              filter: 'blur(24px)',
            }"
            />
          </template>
        </div>
        <div class="relative z-[1] flex min-h-0 flex-1 flex-col">
          <PlayScreenPlayBar :hide-expand="true" :expanded-layout="true" :accent-rgb="effectiveAccentRgb" @minimize="playExpanded = false" />
        </div>
      </div>
    </Teleport>

    <!-- Global drag-and-drop overlay -->
    <div
      v-if="isDropTarget"
      class="pointer-events-none absolute inset-0 z-40 flex items-center justify-center"
    >
      <div
        class="rounded-lg border-2 border-dashed border-amber-500/80 bg-stone-900/90 px-6 py-4 text-center text-sm font-medium text-amber-200 shadow-lg"
      >
        Drop folder(s) to add to library
      </div>
    </div>

    <div
      v-if="store.loading"
      class="absolute inset-0 z-50 flex items-center justify-center bg-stone-900/70"
      aria-live="polite"
      aria-busy="true"
    >
      <div
        class="h-10 w-10 rounded-full border-2 border-stone-600 border-t-stone-300 animate-spin"
        role="status"
        aria-label="Loading"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import Sidebar from "./components/layout/Sidebar.vue";
import LibraryTable from "./components/library/LibraryTable.vue";
import PlayerBar from "./components/playback/PlayerBar.vue";
import PlayScreenPlayBar from "./components/playback/PlayScreenPlayBar.vue";
import QueueList from "./components/playback/QueueList.vue";
import MetadataEditor from "./components/metadata/MetadataEditor.vue";
import {
  getGlowBlobs,
  getSimpleGlowBlobs,
  useDominantColor,
  useEdgeColors,
  isColorBland,
  hasOpposingEdgeColors,
} from "./composables/useDominantColor";
import type { GlowBlob } from "./composables/useDominantColor";
import { useCatalogStore } from "./stores/catalog";
import { useSettingsStore } from "./stores/settings";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { invoke } from "@tauri-apps/api/core";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { playerGlowIntensity, queuePanelWidthFraction, bottomPanelHeightPx } = storeToRefs(settingsStore);
const queueBarContainerRef = ref<HTMLElement | null>(null);
const isDraggingQueueDivider = ref(false);
const isDraggingPanelDivider = ref(false);
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

/** Edge blur when bland center + vibrant edge colors, or when 2 opposing colors split the cover. */
const useEdgeBlurMode = computed(() => {
  if (!expandedCoverUrl.value || !edgeColors.value?.colors?.length) return false;
  const blandCenterVibrantEdges =
    isColorBland(glowRgb.value) && !isColorBland(edgeColors.value.colors[0]);
  const opposingColors = hasOpposingEdgeColors(edgeColors.value.bySide);
  return blandCenterVibrantEdges || opposingColors;
});

const currentBlobs = computed(() => {
  const key = String(trackKey.value);
  if (useEdgeBlurMode.value) return [];
  if (isColorBland(glowRgb.value)) {
    const color = edgeColors.value?.colors?.[0] ?? glowRgb.value;
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
  return blobs.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
});

const displayOutgoingBlobs = computed(() => {
  const scale = glowOpacityScale.value;
  if (scale <= 0) return [];
  const blobs = outgoingBlobs.value;
  return blobs.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
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

/** Use album color for controls. When opposing edge colors (split design), use first hard edge color. */
const effectiveAccentRgb = computed(() => {
  if (hasOpposingEdgeColors(edgeColors.value?.bySide ?? null) && edgeColors.value?.colors?.length) {
    const first = edgeColors.value.colors[0];
    if (!isColorBland(first)) return first;
  }
  return isColorBland(glowRgb.value) ? undefined : glowRgb.value;
});

/** Derive hue variants from base RGB "r,g,b". Returns CSS-ready rgb() strings. */
function accentHueVariants(rgb: string): Record<string, string> {
  const [r, g, b] = rgb.split(",").map(Number);
  if (!Number.isFinite(r + g + b)) return {};

  function toHsl(rr: number, gg: number, bb: number): [number, number, number] {
    rr /= 255; gg /= 255; bb /= 255;
    const max = Math.max(rr, gg, bb), min = Math.min(rr, gg, bb);
    let h = 0, s = 0, l = (max + min) / 2;
    if (max !== min) {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      if (max === rr) h = ((gg - bb) / d + (gg < bb ? 6 : 0)) / 6;
      else if (max === gg) h = ((bb - rr) / d + 2) / 6;
      else h = ((rr - gg) / d + 4) / 6;
    }
    return [h * 360, s * 100, l * 100];
  }
  function fromHsl(h: number, s: number, l: number): string {
    h = ((h % 360) + 360) % 360;
    s /= 100; l /= 100;
    const a = s * Math.min(l, 1 - l);
    const f = (n: number) => {
      const k = (n + h / 30) % 12;
      return l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1));
    };
    const rr = Math.round(f(0) * 255);
    const gg = Math.round(f(8) * 255);
    const bb = Math.round(f(4) * 255);
    return `rgb(${rr},${gg},${bb})`;
  }

  const [h, s, l] = toHsl(r, g, b);
  const playL = Math.min(100, l + 12);
  return {
    accent: fromHsl(h, s, l),
    accentPlay: fromHsl(h, s, playL),
    accentProgress: fromHsl(h, s, l),
    accentVolume: fromHsl(h, Math.min(100, s + 10), Math.min(100, playL + 6)),
    accentShuffle: fromHsl((h - 12 + 360) % 360, s, l),
    accentNav: fromHsl((h + 8) % 360, Math.max(0, s - 8), Math.min(100, l + 6)),
  };
}

/** Accent CSS vars for maximized player. Vivid album => picked color; bland cover => simple dark (neutral gray). */
const NEUTRAL_ACCENT_RGB = "rgb(87 83 78)"; /* stone-600, no hue */
const expandedAccentStyle = computed(() => {
  const rgb = effectiveAccentRgb.value;
  if (!rgb) {
    return {
      "--player-accent": NEUTRAL_ACCENT_RGB,
      "--player-accent-play": NEUTRAL_ACCENT_RGB,
      "--player-accent-progress": NEUTRAL_ACCENT_RGB,
      "--player-accent-volume": NEUTRAL_ACCENT_RGB,
      "--player-accent-shuffle": NEUTRAL_ACCENT_RGB,
      "--player-accent-nav": NEUTRAL_ACCENT_RGB,
    };
  }
  const v = accentHueVariants(rgb);
  return {
    "--player-accent": v.accent,
    "--player-accent-play": v.accentPlay,
    "--player-accent-progress": v.accentProgress,
    "--player-accent-volume": v.accentVolume,
    "--player-accent-shuffle": v.accentShuffle,
    "--player-accent-nav": v.accentNav,
  };
});

/** Very dark tint for background – from center color (vivid) or blended edge colors (bland). */
const glowBgColor = computed(() => {
  if (!showGlow.value) return undefined;
  if (isColorBland(glowRgb.value) && edgeColors.value?.colors) {
    const ec = edgeColors.value.colors;
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
    } else if (oldTrack) {
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
const activeTab = ref<"library" | "metadata" | "play" | "queue">(
  (settingsStore.defaultBottomPanel as "library" | "metadata" | "play" | "queue") ?? "library",
);
/** Tab to restore when minimizing the fullscreen player. */
const activeTabBeforeExpand = ref<"library" | "metadata" | "play" | "queue">("library");
let unlistenDragDrop: (() => void) | null = null;

function expandPlayer() {
  activeTabBeforeExpand.value = activeTab.value;
  playExpanded.value = true;
  sidebarCollapsed.value = true;
  activeTab.value = "play";
}

function minimizePlayer() {
  playExpanded.value = false;
  activeTab.value = activeTabBeforeExpand.value;
}

function onQueueDividerMouseDown() {
  isDraggingQueueDivider.value = true;
  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";
  document.addEventListener("mousemove", onQueueDividerMouseMove);
  document.addEventListener("mouseup", onQueueDividerMouseUp);
}

function onQueueDividerMouseMove(e: MouseEvent) {
  const el = queueBarContainerRef.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const queueWidth = rect.right - e.clientX - 4;
  const fraction = Math.min(0.6, Math.max(0.15, queueWidth / rect.width));
  settingsStore.setQueuePanelWidthFraction(fraction);
}

function onQueueDividerMouseUp() {
  isDraggingQueueDivider.value = false;
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
  document.removeEventListener("mousemove", onQueueDividerMouseMove);
  document.removeEventListener("mouseup", onQueueDividerMouseUp);
}

const bottomPanelResizable = computed(() => activeTab.value === "play" || activeTab.value === "queue");

function onPanelDividerMouseDown() {
  isDraggingPanelDivider.value = true;
  document.body.style.cursor = "row-resize";
  document.body.style.userSelect = "none";
  document.addEventListener("mousemove", onPanelDividerMouseMove);
  document.addEventListener("mouseup", onPanelDividerMouseUp);
}

function onPanelDividerMouseMove(e: MouseEvent) {
  const el = queueBarContainerRef.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const heightPx = rect.bottom - e.clientY;
  settingsStore.setBottomPanelHeightPx(heightPx);
}

function onPanelDividerMouseUp() {
  isDraggingPanelDivider.value = false;
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
  document.removeEventListener("mousemove", onPanelDividerMouseMove);
  document.removeEventListener("mouseup", onPanelDividerMouseUp);
}

function onGlobalKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement;
  const isEditable =
    target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable;

  if (e.key === "Escape" && playExpanded.value) {
    if (!isEditable) {
      e.preventDefault();
      minimizePlayer();
    }
    return;
  }

  if ((e.metaKey || e.ctrlKey) && e.key === "s") {
    e.preventDefault();
    if (!isEditable && store.selectedTracks.length >= 1 && !playExpanded.value) {
      expandPlayer();
    }
  }
}

onMounted(async () => {
  sidebarCollapsed.value = settingsStore.sidebarClosedOnStartup;
  document.addEventListener("keydown", onGlobalKeydown);
  try {
    unlistenDragDrop = await getCurrentWindow().onDragDropEvent((event) => {
      if (event.payload.type === "enter" || event.payload.type === "over") {
        if (!store.isInternalQueueDrag) isDropTarget.value = true;
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
  document.removeEventListener("mousemove", onQueueDividerMouseMove);
  document.removeEventListener("mouseup", onQueueDividerMouseUp);
  document.removeEventListener("mousemove", onPanelDividerMouseMove);
  document.removeEventListener("mouseup", onPanelDividerMouseUp);
  unlistenDragDrop?.();
});
</script>

<template>
  <div
    class="relative grid h-screen w-full max-w-[100vw] grid-rows-[minmax(0,1fr)_minmax(72px,auto)] grid-cols-[auto,1fr] overflow-hidden"
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

    <!-- Bottom row: metadata / player bar spanning full width (or resizable when Queue tab). Resizable height when Player or Queue tab. -->
    <div
      ref="queueBarContainerRef"
      class="bottom-panel-bar row-start-2 row-end-3 col-span-2 flex shrink-0 min-w-0 flex-col border-t bg-stone-900/95 overflow-hidden"
      :style="{
        ...(bottomPanelResizable ? { height: `${bottomPanelHeightPx}px` } : {}),
      }"
    >
      <!-- Resize handle at top when Player or Queue tab -->
      <div
        v-if="bottomPanelResizable"
        role="separator"
        aria-orientation="horizontal"
        :aria-valuenow="bottomPanelHeightPx"
        class="panel-height-divider shrink-0 h-1 min-w-0 cursor-row-resize border-b-2 border-primary bg-stone-600/50 hover:bg-stone-500/60"
        @mousedown.prevent="onPanelDividerMouseDown"
      />
      <!-- Content: flex-col for play, grid for queue, flex-col for library/metadata -->
      <div
        class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
        :class="activeTab === 'queue' ? 'grid items-stretch' : ''"
        :style="activeTab === 'queue' ? { gridTemplateColumns: `${1 - queuePanelWidthFraction}fr 4px ${queuePanelWidthFraction}fr` } : undefined"
      >
        <!-- Single player slot: flex-1 so it gets height when content div is flex (Play tab); overflow-y-auto so bar can scroll if taller than panel. -->
        <div
          class="flex min-w-0 min-h-0 flex-col overflow-hidden"
          :class="{
            'flex-1 min-h-0 overflow-y-auto': activeTab === 'play' || activeTab === 'queue',
          }"
        >
          <PlayerBar
            :class="activeTab === 'play' || activeTab === 'queue' ? 'sr-only h-0 overflow-hidden' : ''"
            @expand="expandPlayer"
          />
          <PlayScreenPlayBar
            v-if="(activeTab === 'play' || activeTab === 'queue') && !playExpanded"
            :panel-height-px="bottomPanelResizable ? bottomPanelHeightPx : undefined"
            @expand="expandPlayer"
          />
        </div>
        <template v-if="activeTab === 'queue'">
          <div
            role="separator"
            aria-orientation="vertical"
            :aria-valuenow="Math.round(queuePanelWidthFraction * 100)"
            class="queue-divider shrink-0 w-1 min-h-0 cursor-col-resize border-x-2 border-primary bg-stone-600/50"
            @mousedown.prevent="onQueueDividerMouseDown"
          />
          <div class="flex min-h-0 min-w-0 flex-col overflow-hidden">
            <QueueList />
          </div>
        </template>
        <MetadataEditor
          v-if="showEditor && activeTab === 'metadata'"
          :key="store.selectedTrackIds.join(',')"
        />
      </div>
    </div>

    <!-- Full-window player overlay ("focus" mode) -->
    <Teleport to="body">
      <div
        v-if="playExpanded"
        id="maximized-player-overlay"
        class="maximized-player-overlay fixed inset-0 z-[350] flex h-screen w-screen flex-col overflow-visible"
        data-theme="dark"
        :style="{ backgroundColor: glowBgColor ?? '#000', colorScheme: 'dark' }"
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
          <PlayScreenPlayBar :hide-expand="true" :expanded-layout="true" :accent-style="expandedAccentStyle" @minimize="minimizePlayer" />
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

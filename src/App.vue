<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import Sidebar from "./components/layout/Sidebar.vue";
import LibraryTable from "./components/library/LibraryTable.vue";
import PlayerBar from "./components/playback/PlayerBar.vue";
import PlayScreenPlayBar from "./components/playback/PlayScreenPlayBar.vue";
import MetadataEditor from "./components/metadata/MetadataEditor.vue";
import { buildProceduralGlow, useDominantColor } from "./composables/useDominantColor";
import { useCatalogStore } from "./stores/catalog";
import { useSettingsStore } from "./stores/settings";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { invoke } from "@tauri-apps/api/core";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const sidebarCollapsed = ref(false);
const playExpanded = ref(false);

const expandedCoverUrl = computed(() => {
  if (!playExpanded.value) return null;
  const tracks = store.selectedTracks;
  if (tracks.length !== 1) return null;
  return store.getCoverDataUrl(tracks[0].path);
});
const glowRgb = useDominantColor(expandedCoverUrl);
const expandedTrack = computed(() =>
  playExpanded.value && store.selectedTracks.length === 1 ? store.selectedTracks[0] : null,
);
const proceduralGlowBackground = computed(() =>
  buildProceduralGlow(glowRgb.value, expandedTrack.value?.path ?? expandedTrack.value?.id ?? ""),
);

/** Previous glow (so we can show it on the outgoing layer when track changes). */
const lastGlowBackground = ref("");
/** Outgoing glow layer: shows previous song's background and fades out. */
const outgoingGlowBackground = ref("");
const outgoingGlowOpacity = ref(0);

const GLOW_TRANSITION_MS = 1800;

watch(
  proceduralGlowBackground,
  (newBackground) => {
    if (lastGlowBackground.value && lastGlowBackground.value !== newBackground) {
      outgoingGlowBackground.value = lastGlowBackground.value;
      outgoingGlowOpacity.value = 1;
      nextTick(() => {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            outgoingGlowOpacity.value = 0;
          });
        });
      });
    }
    lastGlowBackground.value = newBackground;
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
    class="relative grid h-screen grid-rows-[minmax(0,1fr)_auto] grid-cols-[auto,1fr] overflow-hidden"
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
      <MetadataEditor v-if="showEditor && activeTab === 'metadata'" />
    </div>

    <!-- Full-window player overlay ("focus" mode) -->
    <Teleport to="body">
      <div
        v-if="playExpanded"
        class="fixed inset-0 z-[350] flex h-screen w-screen flex-col bg-black"
      >
        <!-- Procedural glow: current (bottom) + outgoing (top, fades out on track change) -->
        <div
          class="pointer-events-none fixed inset-0 z-0"
          :style="{ background: proceduralGlowBackground }"
          aria-hidden="true"
        />
        <div
          class="pointer-events-none fixed inset-0 z-0 transition-opacity ease-out"
          :style="{
            background: outgoingGlowBackground,
            opacity: outgoingGlowOpacity,
            transitionDuration: `${GLOW_TRANSITION_MS}ms`,
          }"
          aria-hidden="true"
        />
        <div class="relative z-[1] flex min-h-0 flex-1 flex-col">
          <PlayScreenPlayBar :hide-expand="true" :expanded-layout="true" :accent-rgb="glowRgb" @minimize="playExpanded = false" />
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

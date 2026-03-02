<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import Sidebar from "./components/Sidebar.vue";
import LibraryTable from "./components/LibraryTable.vue";
import PlayerBar from "./components/PlayerBar.vue";
import MetadataEditor from "./components/MetadataEditor.vue";
import { useCatalogStore } from "./stores/catalog";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { invoke } from "@tauri-apps/api/core";

const store = useCatalogStore();
const showEditor = computed(() => store.selectedTrackIds.length > 0);
const isDropTarget = ref(false);
let unlistenDragDrop: (() => void) | null = null;

onMounted(async () => {
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
  unlistenDragDrop?.();
});
</script>

<template>
  <div class="flex h-screen overflow-hidden relative">
    <Sidebar />
    <main
      class="flex flex-1 flex-col min-w-0 transition-colors duration-150"
      :class="{ 'ring-2 ring-amber-500/80 ring-inset bg-amber-950/20': isDropTarget }"
    >
      <div
        v-if="isDropTarget"
        class="absolute inset-0 z-40 flex items-center justify-center pointer-events-none"
      >
        <div class="rounded-lg border-2 border-dashed border-amber-500/80 bg-stone-900/90 px-6 py-4 text-center text-sm font-medium text-amber-200 shadow-lg">
          Drop folder(s) to add to library
        </div>
      </div>
      <LibraryTable />
      <PlayerBar />
      <MetadataEditor v-if="showEditor" />
    </main>
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

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import SidebarFolders from "./SidebarFolders.vue";
import SidebarReports from "./SidebarReports.vue";
import SidebarPlaylists from "./SidebarPlaylists.vue";

const emit = defineEmits<{ toggle: [] }>();

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { error } = storeToRefs(store);
const { hideReportsSection } = storeToRefs(settingsStore);

type SidebarTabId = "folders" | "reports" | "playlists";
const activeSidebarTab = ref<SidebarTabId>(
  (settingsStore.sidebarDefaultTab as SidebarTabId) ?? "folders",
);
let sidebarDataLoadedOnce = false;

onMounted(async () => {
  if (!sidebarDataLoadedOnce) {
    await store.loadRoots();
    await store.loadTracks();
    await playlistStore.loadPlaylists();
    // Restore saved session queue (silently — no auto-play)
    if (settingsStore.sessionQueueTrackIds.length > 0) {
      store.restoreSession(settingsStore.sessionQueueTrackIds);
    }
    sidebarDataLoadedOnce = true;
  }
});
</script>

<template>
  <aside class="flex h-full min-h-0 w-full flex-col overflow-hidden bg-stone-800/80">
    <!-- Expanded: tab bar + scrollable content -->
    <div class="flex min-h-0 flex-1 flex-col">
      <!-- Tab bar -->
      <div class="flex min-w-0 shrink-0 items-center gap-1 border-b border-stone-700 px-2 py-1.5">
        <div class="flex min-w-0 flex-1 items-center gap-1">
          <button
            type="button"
            class="rounded px-2 py-1 text-xs font-medium"
            :class="activeSidebarTab === 'folders'
              ? 'bg-stone-700 text-stone-100'
              : 'text-stone-300 hover:bg-stone-700/60 hover:text-stone-100'"
            @click="activeSidebarTab = 'folders'"
          >
            Folders
          </button>
          <button
            v-if="!hideReportsSection"
            type="button"
            class="rounded px-2 py-1 text-xs font-medium"
            :class="activeSidebarTab === 'reports'
              ? 'bg-stone-700 text-stone-100'
              : 'text-stone-300 hover:bg-stone-700/60 hover:text-stone-100'"
            @click="activeSidebarTab = 'reports'"
          >
            Reports
          </button>
          <button
            type="button"
            class="rounded px-2 py-1 text-xs font-medium"
            :class="activeSidebarTab === 'playlists'
              ? 'bg-stone-700 text-stone-100'
              : 'text-stone-300 hover:bg-stone-700/60 hover:text-stone-100'"
            @click="activeSidebarTab = 'playlists'"
          >
            Playlists
          </button>
        </div>
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Collapse library"
          @click="emit('toggle')"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18m7-6l-3-3l3-3"/></svg>
        </button>
      </div>

      <!-- Scrollable tab content -->
      <div
        class="table-scroll-container min-h-0 flex-1 overflow-y-auto p-2"
        data-overlayscrollbars-initialize
      >
        <SidebarFolders v-if="activeSidebarTab === 'folders'" />
        <SidebarReports v-else-if="activeSidebarTab === 'reports'" />
        <SidebarPlaylists v-else />
      </div>

      <div v-if="error" class="mt-2 border-t border-stone-700 pt-2 text-xs text-red-400">
        {{ error }}
      </div>
    </div>
  </aside>
</template>


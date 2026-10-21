<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import SidebarFolders from "./SidebarFolders.vue";
import SidebarReports from "./SidebarReports.vue";
import SidebarPlaylists from "./SidebarPlaylists.vue";
import SidebarStats from "./SidebarStats.vue";
import SidebarMixes from "./SidebarMixes.vue";

const emit = defineEmits<{ toggle: [] }>();

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { error } = storeToRefs(store);
const { hideReportsSection, backendMode, localServerReady } = storeToRefs(settingsStore);

type SidebarTabId = "folders" | "reports" | "playlists" | "mixes" | "stats";
const activeSidebarTab = ref<SidebarTabId>(
  (settingsStore.sidebarDefaultTab as SidebarTabId) ?? "folders",
);

const tabs = computed<{ id: SidebarTabId; label: string }[]>(() => [
  { id: "folders", label: "Folders" },
  { id: "stats", label: "Stats" },
  ...(hideReportsSection.value ? [] : [{ id: "reports" as const, label: "Reports" }]),
  { id: "playlists", label: "Playlists" },
  { id: "mixes", label: "Mixes" },
]);

// When the sidebar is narrow the tabs keep their natural width and the bar
// scrolls horizontally instead of squeezing/wrapping them.
const tabBarRef = ref<HTMLElement | null>(null);

/** Vertical wheel over the bar scrolls it sideways (no scrollbar is shown). */
function onTabBarWheel(e: WheelEvent) {
  const el = tabBarRef.value;
  if (!el || el.scrollWidth <= el.clientWidth) return;
  if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return;
  el.scrollLeft += e.deltaY;
  e.preventDefault();
}

/** Keep the active tab visible; scrolls only the bar, never an ancestor. */
function revealActiveTab() {
  const el = tabBarRef.value;
  const btn = el?.querySelector<HTMLElement>(`[data-tab="${activeSidebarTab.value}"]`);
  if (!el || !btn) return;
  const left = btn.offsetLeft;
  const right = left + btn.offsetWidth;
  if (left < el.scrollLeft) el.scrollLeft = left;
  else if (right > el.scrollLeft + el.clientWidth) el.scrollLeft = right - el.clientWidth;
}

watch(activeSidebarTab, () => nextTick(revealActiveTab));
onMounted(revealActiveTab);
let sidebarDataLoadedOnce = false;

onMounted(async () => {
  // In local mode, ServerStartupModal handles the initial load once the sidecar is ready.
  if (backendMode.value === "local") return;
  if (!sidebarDataLoadedOnce) {
    await store.loadRoots();
    await store.loadTracks();
    await playlistStore.loadPlaylists();
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
        <div
          ref="tabBarRef"
          class="relative flex min-w-0 flex-1 items-center gap-1 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          @wheel="onTabBarWheel"
        >
          <button
            v-for="tab in tabs"
            :key="tab.id"
            type="button"
            :data-tab="tab.id"
            class="shrink-0 whitespace-nowrap rounded px-2 py-1 text-xs font-medium"
            :class="activeSidebarTab === tab.id
              ? 'bg-stone-700 text-stone-100'
              : 'text-stone-300 hover:bg-stone-700/60 hover:text-stone-100'"
            @click="activeSidebarTab = tab.id"
          >
            {{ tab.label }}
          </button>
        </div>
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Collapse library"
          @click="emit('toggle')"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" /><path d="M9 3v18m7-6l-3-3l3-3" /></svg>
        </button>
      </div>

      <!-- Scrollable tab content -->
      <div
        class="table-scroll-container min-h-0 flex-1 overflow-y-auto p-2"
        data-overlayscrollbars-initialize
      >
        <SidebarFolders v-if="activeSidebarTab === 'folders'" />
        <SidebarReports v-else-if="activeSidebarTab === 'reports'" />
        <SidebarPlaylists v-else-if="activeSidebarTab === 'playlists'" />
        <SidebarMixes v-else-if="activeSidebarTab === 'mixes'" />
        <SidebarStats v-else />
      </div>

      <div v-if="error && (backendMode !== 'local' || localServerReady)" class="mt-2 border-t border-stone-700 pt-2 text-xs text-red-400">
        {{ error }}
      </div>
    </div>
  </aside>
</template>


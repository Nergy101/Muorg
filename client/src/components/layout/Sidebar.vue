<script setup lang="ts">
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import logoUrl from "../../assets/muorg-logo.svg";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore, FAVORITES_PLAYLIST_NAME } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";
import VolumeControl from "../playback/VolumeControl.vue";
import { currentView, navigate, sidebarCollapsed, type ViewName } from "../../state/navigation";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { tracks, totalTracks } = storeToRefs(store);
const { sortedPlaylists } = storeToRefs(playlistStore);

/** Favorites on top, then the pinned/server order. */
const navPlaylists = computed(() => {
  const fav = sortedPlaylists.value.filter((p) => p.name === FAVORITES_PLAYLIST_NAME);
  const rest = sortedPlaylists.value.filter((p) => p.name !== FAVORITES_PLAYLIST_NAME);
  return [...fav, ...rest];
});
const { localServerReady, backendMode } = storeToRefs(settingsStore);

const NAV: { id: ViewName; label: string; icon: string }[] = [
  { id: "home", label: "Home", icon: "home" },
  { id: "search", label: "Search", icon: "search" },
  { id: "albums", label: "Library", icon: "disc" },
  { id: "playlists", label: "Playlists", icon: "music" },
  { id: "queue", label: "Queue", icon: "layers" },
  { id: "reports", label: "Reports", icon: "file-text" },
  { id: "stats", label: "Stats", icon: "pie-chart" },
  { id: "settings", label: "Settings", icon: "settings" },
];

// The Albums/List/Artists display switch and album detail pages are all
// subpages of Library — the Library nav item stays active on all of them.
const LIBRARY_VIEWS = new Set(["albums", "list", "artists", "album"]);
const activeName = computed(() =>
  LIBRARY_VIEWS.has(currentView.value.name) ? "albums" : currentView.value.name,
);

function go(id: ViewName) {
  navigate({ name: id });
}

function openPlaylist(id: number) {
  navigate({ name: "playlist", id });
}

const online = computed(() => backendMode.value !== "local" || localServerReady.value);
const trackCount = computed(() => totalTracks.value || tracks.value.length);

let loadedOnce = false;
onMounted(async () => {
  if (backendMode.value === "local") return; // ServerStartupModal drives the initial load
  if (!loadedOnce) {
    await store.loadRoots();
    await store.loadTracks();
    await playlistStore.loadPlaylists();
    if (settingsStore.sessionQueueTrackIds.length > 0) {
      store.restoreSession(settingsStore.sessionQueueTrackIds);
    }
    loadedOnce = true;
  }
});
</script>

<template>
  <aside class="flex h-full min-h-0 w-full flex-col">
    <!-- Brand row: logo centered when collapsed; click logo to expand -->
    <div class="flex h-14 shrink-0 items-center gap-2.5 px-5 pt-2" :class="{ '!px-0 !justify-center': sidebarCollapsed }">
      <button
        type="button"
        class="shrink-0 cursor-pointer rounded-lg transition-colors"
        :class="sidebarCollapsed ? 'p-1 hover:bg-surface-container' : 'cursor-default'"
        :aria-label="sidebarCollapsed ? 'Expand sidebar' : 'Muorg'"
        :title="sidebarCollapsed ? 'Expand sidebar' : 'Muorg'"
        @click="sidebarCollapsed = false"
      >
        <img :src="logoUrl" alt="Muorg" class="h-[34px] w-[34px]" />
      </button>
      <span v-if="!sidebarCollapsed" class="text-[17px] font-bold tracking-tight text-on-surface">Muorg</span>
      <button
        v-if="!sidebarCollapsed"
        type="button"
        class="ml-auto rounded p-0.5 text-on-surface-variant/70 hover:bg-surface-container hover:text-on-surface"
        aria-label="Collapse sidebar"
        title="Collapse sidebar"
        @click="sidebarCollapsed = true"
      >
        <FeatherIcon name="chevron-left" class="h-4 w-4" />
      </button>
    </div>

    <!-- Nav pills (icon-only when collapsed) -->
    <nav class="flex shrink-0 flex-col gap-0.5 px-3 py-2" :class="{ '!px-2 !items-center': sidebarCollapsed }">
      <button
        v-for="item in NAV"
        :key="item.id"
        type="button"
        class="nav-pill"
        :class="{ active: activeName === item.id, '!justify-center !px-0 !w-10': sidebarCollapsed }"
        :title="item.label"
        @click="go(item.id)"
      >
        <FeatherIcon :name="item.icon" />
        <span v-if="!sidebarCollapsed">{{ item.label }}</span>
      </button>
    </nav>

    <!-- Playlists -->
    <div v-if="!sidebarCollapsed" class="flex min-h-0 flex-1 flex-col border-t border-outline/20">
      <div class="shrink-0 px-5 pb-1 pt-3 text-[11px] font-bold uppercase tracking-wider text-on-surface-variant">
        Playlists
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        <button
          v-for="pl in navPlaylists"
          :key="pl.id"
          type="button"
          class="pl-item"
          :class="{ active: currentView.name === 'playlist' && currentView.id === pl.id }"
          @click="openPlaylist(pl.id)"
        >
          <span class="pl-icon pl-icon-glass">
            <PlaylistIcon :icon="pl.icon" />
          </span>
          <span class="truncate">{{ pl.name }}</span>
        </button>
      </div>
    </div>

    <!-- Volume: slider when expanded, centered mute/unmute only when collapsed -->
    <div class="shrink-0 border-t border-outline/20 px-3 py-2" :class="{ '!px-0 !flex !justify-center': sidebarCollapsed }">
      <div v-if="!sidebarCollapsed" class="px-2 pb-1.5 text-[11px] font-bold uppercase tracking-wider text-on-surface-variant">
        Volume
      </div>
      <div class="sidebar-volume" :class="{ 'sidebar-volume-collapsed': sidebarCollapsed }">
        <VolumeControl mode="metadata" />
      </div>
    </div>

    <!-- Online footer -->
    <div class="shrink-0 px-5 py-3" :class="{ '!px-0 !flex !justify-center': sidebarCollapsed }">
      <div class="flex items-center gap-2 rounded-full bg-surface-container px-3 py-1.5 w-fit" :class="{ '!px-2': sidebarCollapsed }">
        <span
          class="h-2 w-2 rounded-full"
          :style="{ background: 'var(--primary)', boxShadow: '0 0 6px var(--primary)' }"
        />
        <span v-if="!sidebarCollapsed" class="text-xs text-on-surface-variant">
          {{ online ? "Online" : "Offline" }}, {{ trackCount }} tracks
        </span>
      </div>
    </div>
  </aside>
</template>

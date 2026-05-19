<template>
  <div class="flex h-full flex-col">
    <!-- Connection screen -->
    <ConnectionScreen v-if="!connected" @connected="onConnected" />

    <!-- Main app -->
    <template v-else>
      <LibraryHeader @disconnect="disconnect" @toggle-sidebar="sidebarOpen = !sidebarOpen" />

      <div class="flex min-h-0 flex-1 overflow-hidden">
        <!-- Playlist sidebar -->
        <PlaylistSidebar :open="sidebarOpen" />

        <!-- Mobile overlay backdrop -->
        <div
          v-if="sidebarOpen"
          class="fixed inset-0 z-30 bg-black/50 md:hidden"
          @click="sidebarOpen = false"
        />

        <!-- Main content -->
        <main class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
          <LibraryStats
            :stats="lib.stats ?? emptyStats"
            :playlist-name="activePlaylistName"
            @clear-playlist="playlistStore.selectPlaylist(null)"
          />

          <AlbumGrid v-if="lib.viewMode === 'grid'" />
          <TrackTable v-else />
        </main>
      </div>

      <PlayerBar />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import ConnectionScreen from "./components/ConnectionScreen.vue";
import LibraryHeader from "./components/LibraryHeader.vue";
import LibraryStats from "./components/LibraryStats.vue";
import PlaylistSidebar from "./components/PlaylistSidebar.vue";
import AlbumGrid from "./components/AlbumGrid.vue";
import TrackTable from "./components/TrackTable.vue";
import PlayerBar from "./components/PlayerBar.vue";
import { isConnected, disconnect as apiDisconnect } from "./api/client";
import { useLibraryStore } from "./stores/library";
import { usePlaylistStore } from "./stores/playlists";
import type { LibraryStats as LibraryStatsType } from "./types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const connected = ref(isConnected());
const sidebarOpen = ref(window.innerWidth >= 768);

const emptyStats: LibraryStatsType = {
  total_tracks: 0,
  total_artists: 0,
  total_albums: 0,
  total_duration_secs: 0,
  total_size_bytes: 0,
};

const activePlaylistName = computed(() => {
  if (playlistStore.activePlaylistId === null) return undefined;
  return playlistStore.playlists.find((p) => p.id === playlistStore.activePlaylistId)?.name;
});

onMounted(() => {
  // Apply saved theme
  const theme = localStorage.getItem("muorg-web-theme") ?? "dark";
  document.documentElement.setAttribute("data-theme", theme);

  if (connected.value) {
    loadData();
  }
});

function onConnected(): void {
  connected.value = true;
  loadData();
}

async function loadData(): Promise<void> {
  await Promise.all([lib.loadLibrary(), playlistStore.loadPlaylists()]);
}

function disconnect(): void {
  apiDisconnect();
  connected.value = false;
  lib.$reset();
  playlistStore.$reset();
}
</script>

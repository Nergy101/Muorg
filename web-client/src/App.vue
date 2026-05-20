<template>
  <div class="flex h-full flex-col">
    <!-- Connection screen -->
    <ConnectionScreen v-if="!connected" @connected="onConnected" />

    <!-- Main app -->
    <template v-else>
      <LibraryHeader
        :show-back="!!openAlbum"
        @disconnect="disconnect"
        @toggle-sidebar="sidebarOpen = !sidebarOpen"
        @back="openAlbum = null"
        @open-stats="showStats = true"
      />

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
          <AlbumDetailView v-if="openAlbum" :item="openAlbum" />
          <AlbumGrid v-else-if="lib.viewMode === 'grid'" @open-album="openAlbum = $event" />
          <TrackTable v-else />
        </main>
      </div>

      <PlayerBar />
      <StatsModal :open="showStats" @close="showStats = false" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import ConnectionScreen from "./components/ConnectionScreen.vue";
import LibraryHeader from "./components/LibraryHeader.vue";
import PlaylistSidebar from "./components/PlaylistSidebar.vue";
import AlbumGrid from "./components/AlbumGrid.vue";
import AlbumDetailView from "./components/AlbumDetailView.vue";
import TrackTable from "./components/TrackTable.vue";
import PlayerBar from "./components/PlayerBar.vue";
import StatsModal from "./components/StatsModal.vue";
import { isConnected, disconnect as apiDisconnect } from "./api/client";
import { useLibraryStore } from "./stores/library";
import { usePlaylistStore } from "./stores/playlists";
import type { AlbumGridItem } from "./types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const connected = ref(isConnected());
const sidebarOpen = ref(window.innerWidth >= 768);
const openAlbum = ref<AlbumGridItem | null>(null);
const showStats = ref(false);

onMounted(() => {
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
  openAlbum.value = null;
  lib.$reset();
  playlistStore.$reset();
}
</script>

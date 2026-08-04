<template>
  <div class="flex h-full flex-col">
    <!-- Connection screen -->
    <ConnectionScreen v-if="!connected" @connected="onConnected" />

    <!-- Main app -->
    <template v-else>
      <OfflineBanner />
      <LibraryHeader
        :show-back="!!openAlbum"
        @disconnect="disconnect"
        @toggle-sidebar="sidebarOpen = !sidebarOpen"
        @back="openAlbum = null"
        @open-stats="showStats = true"
        @open-mobile-search="mobileSearchOpen = true"
      />

      <div class="flex min-h-0 flex-1 overflow-hidden">
        <!-- Playlist sidebar -->
        <PlaylistSidebar
          :open="sidebarOpen"
          :home-active="section === 'home'"
          @go-home="goHome"
          @go-library="goLibrary"
        />

        <!-- Mobile overlay backdrop -->
        <div
          v-if="sidebarOpen"
          class="fixed inset-0 z-30 bg-black/50 md:hidden"
          @click="sidebarOpen = false"
        />

        <!-- Main content -->
        <main class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
          <AlbumDetailView v-if="openAlbum" :item="openAlbum" />
          <PlaylistView v-else-if="playlistStore.activePlaylistId !== null" />
          <HomeView v-else-if="section === 'home'" />
          <AlbumGrid v-else-if="lib.viewMode === 'grid'" @open-album="openAlbum = $event" />
          <TrackTable v-else />
        </main>
      </div>

      <PlayerBar :overlay-open="playerOverlayOpen" @update:overlayOpen="playerOverlayOpen = $event" />
      <MobileTabBar :active="activeMobileTab" @select="onMobileTabSelect" />
      <StatsModal :open="showStats" @close="showStats = false" />
      <MobileSearchScreen :open="mobileSearchOpen" @close="mobileSearchOpen = false" @open-album="openAlbum = $event" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watchEffect, computed, watch } from "vue";
import ConnectionScreen from "./components/ConnectionScreen.vue";
import OfflineBanner from "./components/OfflineBanner.vue";
import LibraryHeader from "./components/LibraryHeader.vue";
import PlaylistSidebar from "./components/PlaylistSidebar.vue";
import AlbumGrid from "./components/AlbumGrid.vue";
import AlbumDetailView from "./components/AlbumDetailView.vue";
import TrackTable from "./components/TrackTable.vue";
import PlaylistView from "./components/PlaylistView.vue";
import HomeView from "./components/HomeView.vue";
import PlayerBar from "./components/PlayerBar.vue";
import StatsModal from "./components/StatsModal.vue";
import MobileTabBar, { type MobileTab } from "./components/MobileTabBar.vue";
import MobileSearchScreen from "./components/MobileSearchScreen.vue";
import { isConnected, disconnect as apiDisconnect } from "./api/client";
import { useLibraryStore } from "./stores/library";
import { usePlaylistStore } from "./stores/playlists";
import type { AlbumGridItem } from "./types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const connected = ref(isConnected());

watchEffect(() => {
  const t = lib.nowPlaying;
  if (t) {
    const artist = t.artist ?? t.album_artist;
    document.title = artist ? `${t.title} - ${artist} | Muorg` : `${t.title} | Muorg`;
  } else {
    document.title = "Muorg Web";
  }
});
const sidebarOpen = ref(window.innerWidth >= 768);
const openAlbum = ref<AlbumGridItem | null>(null);
const showStats = ref(false);
const playerOverlayOpen = ref(false);
const mobileSearchOpen = ref(false);
// 'home' | 'library' — what the main area shows (library = grid/table per viewMode)
const section = ref<"home" | "library">("home");
// Which mobile tab the user is on (music = full library, albums = grid)
const mobileSection = ref<"home" | "music" | "albums">("home");

function goHome(): void {
  section.value = "home";
  openAlbum.value = null;
  if (window.innerWidth < 768) sidebarOpen.value = false;
  mobileSection.value = "home";
}

function goLibrary(): void {
  section.value = "library";
  openAlbum.value = null;
  if (window.innerWidth < 768) sidebarOpen.value = false;
}

// Keep the now-playing overlay in sync with the tab bar
const activeMobileTab = computed<MobileTab>(() => {
  if (playerOverlayOpen.value) return "now-playing";
  if (sidebarOpen.value) return "playlists";
  if (openAlbum.value) return "albums";
  if (playlistStore.activePlaylistId !== null) return "playlists";
  if (section.value === "home") return "home";
  return mobileSection.value;
});

// Switching view mode from Home (header grid/table icons) jumps to the library
watch(() => lib.viewMode, () => {
  if (section.value === "home") section.value = "library";
});

// Selecting a playlist navigates to it: close album detail and (on mobile) the drawer
watch(() => playlistStore.activePlaylistId, (id) => {
  if (id !== null) openAlbum.value = null;
  if (window.innerWidth < 768) sidebarOpen.value = false;
});

function onMobileTabSelect(tab: MobileTab): void {
  switch (tab) {
    case "home":
      sidebarOpen.value = false;
      openAlbum.value = null;
      section.value = "home";
      mobileSection.value = "home";
      break;
    case "music":
      sidebarOpen.value = false;
      openAlbum.value = null;
      section.value = "library";
      mobileSection.value = "music";
      break;
    case "albums":
      sidebarOpen.value = false;
      openAlbum.value = null;
      section.value = "library";
      lib.setViewMode("grid");
      mobileSection.value = "albums";
      break;
    case "playlists":
      sidebarOpen.value = true;
      break;
    case "now-playing":
      if (!lib.nowPlaying) {
        // Nothing playing — the tab still behaves as a shortcut to the player
        sidebarOpen.value = false;
        openAlbum.value = null;
        return;
      }
      playerOverlayOpen.value = true;
      break;
  }
}

// Theme-color meta updates with the active theme (notch / status bar tint)
function applyThemeColor(): void {
  const theme = localStorage.getItem("muorg-web-theme") ?? "dark";
  let meta = document.querySelector('meta[name="theme-color"]');
  if (!meta) {
    meta = document.createElement("meta");
    meta.setAttribute("name", "theme-color");
    document.head.appendChild(meta);
  }
  meta.setAttribute("content", theme === "light" ? "#fafaf9" : "#1c1917");
}

onMounted(() => {
  const theme = localStorage.getItem("muorg-web-theme") ?? "dark";
  document.documentElement.setAttribute("data-theme", theme);
  applyThemeColor();

  if (connected.value) {
    loadData();
  }
});

// Keep theme-color in sync if the theme changes at runtime
const themeObserver = new MutationObserver(() => applyThemeColor());
themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

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
  playerOverlayOpen.value = false;
  mobileSearchOpen.value = false;
  lib.$reset();
  playlistStore.$reset();
}
</script>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import Sidebar from "./components/layout/Sidebar.vue";
import TopBar from "./components/layout/TopBar.vue";
import PlayerBar from "./components/playback/PlayerBar.vue";
import PlayScreenPlayBar from "./components/playback/PlayScreenPlayBar.vue";
import ServerStartupModal from "./components/modals/ServerStartupModal.vue";
import Toast from "./components/shared/Toast.vue";
import MetadataEditor from "./components/metadata/MetadataEditor.vue";
import HomeView from "./views/HomeView.vue";
import AlbumsView from "./views/AlbumsView.vue";
import ListView from "./views/ListView.vue";
import ArtistsView from "./views/ArtistsView.vue";
import ArtistDetailView from "./views/ArtistDetailView.vue";
import PlaylistsView from "./views/PlaylistsView.vue";
import PlaylistDetailView from "./views/PlaylistDetailView.vue";
import MixDetailView from "./views/MixDetailView.vue";
import QueueView from "./views/QueueView.vue";
import ReportsView from "./views/ReportsView.vue";
import StatsView from "./views/StatsView.vue";
import SettingsView from "./views/SettingsView.vue";
import AlbumDetailHost from "./views/AlbumDetailHost.vue";
import SearchView from "./components/library/SearchView.vue";
import { useCatalogStore } from "./stores/catalog";
import { useSettingsStore } from "./stores/settings";
import { useProfilesStore } from "./stores/profiles";
import { useCastEvents } from "./composables/useCastEvents";
import { useDynamicAccent } from "./composables/useDynamicAccent";
import { useDominantColor, useMostCommonColor } from "./composables/useDominantColor";
import { accentHexFromRgb } from "./composables/useDynamicAccent";
import { currentView, navigate, metadataEditorOpen, sidebarCollapsed } from "./state/navigation";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import { albumArtistOf } from "./composables/useAlbums";
import { getCurrentWindow } from "@tauri-apps/api/window";

const store = useCatalogStore();
const settingsStore = useSettingsStore();

// NER-326: listening profiles. Re-read the per-context (local/server) choice
// and load the current library's profiles; refresh again when the backend
// mode flips, since local and server each have their own profiles.
const profilesStore = useProfilesStore();
profilesStore.bindContext();
void profilesStore.refresh();
watch(() => settingsStore.backendMode, () => {
  profilesStore.bindContext();
  void profilesStore.refresh();
});

useCastEvents();
// Dynamic accent: recolors the UI (and the frosted player bar) to the playing album.
useDynamicAccent();

// Persist queue whenever it changes (debounced — write at most once per second)
let sessionSaveTimer: ReturnType<typeof setTimeout> | null = null;
function scheduleSessionQueueSave() {
  if (sessionSaveTimer) clearTimeout(sessionSaveTimer);
  sessionSaveTimer = setTimeout(() => {
    sessionSaveTimer = null;
    settingsStore.setSessionState(
      store.queueTrackIds,
      store.currentPlayingTrackId,
      settingsStore.sessionCurrentPositionSecs,
    );
  }, 800);
}
watch(() => store.queueTrackIds, scheduleSessionQueueSave, { deep: true });

// The 401 handler (main.ts) and other legacy entry points ask for the settings
// modal; in the redesigned shell that means navigating to the Settings view.
watch(
  () => settingsStore.settingsModalOpen,
  (open) => {
    if (!open) return;
    settingsStore.settingsModalOpen = false;
    navigate({ name: "settings" });
  },
);

/** Maximized ("fullscreen") player overlay, opened from the player bar's now-playing widget. */
const playExpanded = ref(false);
function expandPlayer() {
  playExpanded.value = true;
}
function minimizePlayer() {
  playExpanded.value = false;
}

/** Album art for the album page, shown as a translucent backdrop behind the
 *  whole island (including the integrated topbar). */
const albumBackdropUrl = computed(() => {
  const v = currentView.value;
  if (v.name !== "album" || !v.key) return null;
  const track = store.tracks.find((t) => albumIdentityKey(t.album, albumArtistOf(t)) === v.key && t.has_cover)
    ?? store.tracks.find((t) => albumIdentityKey(t.album, albumArtistOf(t)) === v.key);
  return track ? store.getCoverDataUrl(track.path) : null;
});

/** Accent vars for the maximized player, taken from the playing album art. */
const playingCoverUrl = computed(() => {
  const id = store.currentPlayingTrackId;
  if (id == null) return null;
  const track = store.tracks.find((t) => t.id === id);
  return track ? store.getCoverDataUrl(track.path) : null;
});
const glowRgb = useDominantColor(playingCoverUrl);
/** Flip the close-chevron to dark when the backdrop art is light. */
const closeChevronDark = computed(() => {
  const parts = (glowRgb.value ?? "").split(",").map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) return false;
  const lum = (0.299 * parts[0] + 0.587 * parts[1] + 0.114 * parts[2]) / 255;
  return lum > 0.6;
});

/** Album-page backdrop luminance: topbar controls flip to dark on light art. */
const albumBackdropRgb = useDominantColor(albumBackdropUrl);
const albumBackdropLight = computed(() => {
  if (currentView.value.name !== "album") return false;
  const parts = (albumBackdropRgb.value ?? "").split(",").map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) return false;
  return (0.299 * parts[0] + 0.587 * parts[1] + 0.114 * parts[2]) / 255 > 0.6;
});
const modeRgb = useMostCommonColor(playingCoverUrl);
const expandedAccentStyle = computed(() => {
  // Same accent-picking as the dynamic UI accent (mode color preferred).
  const hex = accentHexFromRgb(modeRgb.value) ?? accentHexFromRgb(glowRgb.value) ?? "#5b7c32";
  const color = hex.startsWith("#") ? hex : `rgb(${hex})`;
  return {
    "--player-accent": color,
    "--player-accent-play": color,
    "--player-accent-progress": color,
    "--player-accent-volume": color,
    "--player-accent-shuffle": color,
    "--player-accent-nav": color,
  };
});

/** Metadata editor opens as an overlay when a track row asks for it or the
 *  album page's 3-dots menu requests it. */
const editorOpen = ref(false);
watch(metadataEditorOpen, (open) => {
  if (open) {
    metadataEditorOpen.value = false;
    editorOpen.value = true;
  }
});
function openMetadataEditor() {
  editorOpen.value = true;
}
function closeMetadataEditor() {
  editorOpen.value = false;
  store.clearSelection();
}
const showEditor = computed(() => editorOpen.value && store.selectedTrackIds.length > 0);

const isDropTarget = ref(false);
let unlistenDragDrop: (() => void) | null = null;

function isImagePath(p: string): boolean {
  const ext = p.split(".").pop()?.toLowerCase() ?? "";
  return ext === "jpg" || ext === "jpeg" || ext === "png" || ext === "webp" || ext === "gif" || ext === "bmp";
}

function onGlobalKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement;
  const isEditable =
    target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable;
  if (e.key === "Escape" && playExpanded.value && !isEditable) {
    e.preventDefault();
    minimizePlayer();
    return;
  }
  if (e.key === " " && !isEditable) {
    e.preventDefault();
    // Space toggles play via the player bar's audio element state.
    const audio = document.querySelector<HTMLAudioElement>("audio[data-muorg-player]");
    if (audio) {
      if (audio.paused) void audio.play();
      else audio.pause();
    }
  }
}

onMounted(async () => {
  document.addEventListener("keydown", onGlobalKeydown);
  try {
    unlistenDragDrop = await getCurrentWindow().onDragDropEvent((event) => {
      if (event.payload.type === "enter" || event.payload.type === "over") {
        const paths = "paths" in event.payload ? event.payload.paths : [];
        if (paths.length > 0 && !paths.every(isImagePath)) isDropTarget.value = true;
      } else if (event.payload.type === "leave") {
        isDropTarget.value = false;
      } else if (event.payload.type === "drop") {
        isDropTarget.value = false;
        const paths = "paths" in event.payload ? event.payload.paths : [];
        if (paths.length === 0) return;
        const imagePaths = paths.filter(isImagePath);
        if (imagePaths.length > 0 && showEditor.value) {
          store.setPendingCoverImagePath(imagePaths[0]);
          return;
        }
        (async () => {
          const folders = new Set<string>();
          for (const p of paths) {
            const sep = p.includes("/") ? "/" : "\\";
            const parts = p.split(sep);
            parts.pop();
            const folder = parts.join(sep) || sep;
            if (folder) folders.add(folder);
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
    class="app-shell"
    :class="{ 'ring-2 ring-amber-500/80 ring-inset': isDropTarget, 'on-light-backdrop': albumBackdropLight }"
    :style="{ gridTemplateColumns: sidebarCollapsed ? '64px 1fr' : '248px 1fr' }"
  >
    <!-- Sidebar -->
    <div class="app-sidebar">
      <Sidebar />
    </div>

    <!-- Body column: one island — topbar controls at the top inside the main panel -->
    <div class="app-body">
      <main class="app-main flex min-h-0 flex-1 flex-col">
        <!-- Album art backdrop spans the whole island, so the transparent topbar
             shows the same accent fade translucently over the art -->
        <div v-if="albumBackdropUrl" class="pointer-events-none absolute inset-0 z-0 overflow-hidden" aria-hidden="true">
          <img
            :src="albumBackdropUrl"
            alt=""
            class="h-full w-full scale-150 object-cover opacity-80"
            style="filter: blur(8px) saturate(1.4)"
          />
          <div
            class="absolute inset-0"
            style="background: linear-gradient(180deg, color-mix(in srgb, var(--background) 25%, transparent) 0%, color-mix(in srgb, var(--background) 55%, transparent) 45%, var(--background) 100%)"
          />
        </div>
        <TopBar />
        <div class="relative z-[1] flex min-h-0 flex-1 flex-col">
          <HomeView v-if="currentView.name === 'home'" />
          <AlbumsView v-else-if="currentView.name === 'albums'" />
          <ListView v-else-if="currentView.name === 'list'" @open-metadata="openMetadataEditor" />
          <ArtistsView v-else-if="currentView.name === 'artists'" />
          <ArtistDetailView v-else-if="currentView.name === 'artist'" :artist="currentView.key ?? ''" />
          <PlaylistsView v-else-if="currentView.name === 'playlists'" />
          <PlaylistDetailView v-else-if="currentView.name === 'playlist'" :id="currentView.id ?? 0" />
          <MixDetailView v-else-if="currentView.name === 'mix'" />
          <QueueView v-else-if="currentView.name === 'queue'" />
          <ReportsView v-else-if="currentView.name === 'reports'" />
          <StatsView v-else-if="currentView.name === 'stats'" />
          <SettingsView v-else-if="currentView.name === 'settings'" />
          <AlbumDetailHost v-else-if="currentView.name === 'album'" :album-key="currentView.key ?? ''" />
          <SearchView
            v-else-if="currentView.name === 'search'"
            @back="navigate({ name: 'list' })"
            @open-album="(key: string) => navigate({ name: 'album', key })"
          />
        </div>

        <!-- Frosted accent-glass player bar, floating over the scrolling content -->
        <PlayerBar @expand="expandPlayer" @open-queue="navigate({ name: 'queue' })" />
      </main>
    </div>

    <!-- Maximized player overlay (web-client art-fullscreen look: full-bleed cover backdrop) -->
    <Teleport to="body">
      <div
        v-if="playExpanded"
        id="maximized-player-overlay"
        class="fixed inset-0 z-[350] flex h-screen w-screen flex-col overflow-hidden bg-[#0c0a09]"
        :class="{ 'mpx-close-dark': closeChevronDark }"
        :style="{ colorScheme: 'dark' }"
      >
        <!-- Full-bleed album art backdrop, like the web-client's CoverBackdrop (sharp) -->
        <div v-if="playingCoverUrl" class="pointer-events-none absolute inset-0 z-0" aria-hidden="true">
          <img
            :src="playingCoverUrl"
            alt=""
            class="h-full w-full scale-105 object-cover opacity-75"
            style="filter: blur(28px) saturate(1.2) brightness(0.85)"
          />
          <div class="absolute inset-0 bg-gradient-to-b from-black/20 via-transparent to-black/45" />
        </div>
        <div class="relative z-[1] flex min-h-0 flex-1 flex-col">
          <PlayScreenPlayBar
            :hide-expand="true"
            :expanded-layout="true"
            :accent-style="expandedAccentStyle"
            @minimize="minimizePlayer"
          />
        </div>
      </div>
    </Teleport>

    <!-- Global drag-and-drop overlay -->
    <div
      v-if="isDropTarget"
      class="pointer-events-none fixed inset-0 z-40 flex items-center justify-center"
    >
      <div
        class="rounded-lg border-2 border-dashed border-amber-500/80 bg-surface/90 px-6 py-4 text-center text-sm font-medium text-amber-200 shadow-lg"
      >
        Drop folder(s) to add to library
      </div>
    </div>

    <div
      v-if="store.loading"
      class="fixed inset-0 z-50 flex items-center justify-center bg-surface/70"
      aria-live="polite"
      aria-busy="true"
    >
      <div
        class="h-10 w-10 rounded-full border-2 border-outline border-t-outline animate-spin"
        role="status"
        aria-label="Loading"
      />
    </div>

    <div
      v-if="store.loadingMore"
      class="pointer-events-none fixed bottom-24 right-4 z-40 flex items-center gap-2 rounded-full border border-outline bg-surface/90 px-3 py-1.5 text-xs text-on-surface shadow-lg"
      aria-live="polite"
    >
      <div class="h-3 w-3 rounded-full border-2 border-outline border-t-outline animate-spin" role="status" />
      Loading library — {{ store.tracks.length }} of {{ store.totalTracks }}
    </div>

    <Teleport to="body">
      <div
        v-if="store.bulkProgress"
        class="fixed inset-0 z-[500] flex items-center justify-center bg-black/80"
        aria-live="polite"
        aria-busy="true"
      >
        <div class="flex flex-col items-center gap-4">
          <div class="h-10 w-10 rounded-full border-2 border-outline border-t-outline animate-spin" role="status" aria-label="Processing" />
          <div class="text-sm font-medium text-on-surface">
            {{ store.bulkProgress.current }} / {{ store.bulkProgress.total }}
            <span class="ml-1.5 text-on-surface-variant">({{ Math.round(store.bulkProgress.current / store.bulkProgress.total * 100) }}%)</span>
          </div>
          <button
            type="button"
            class="rounded border border-outline px-3 py-1 text-xs text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
            @click="store.setBulkCancelled(true)"
          >
            Cancel
          </button>
        </div>
      </div>
    </Teleport>

    <!-- Metadata editor overlay -->
    <Teleport to="body">
      <div
        v-if="showEditor"
        class="fixed inset-0 z-[400] flex items-center justify-center bg-black/60"
        @click.self="closeMetadataEditor"
      >
        <div class="flex max-h-[90vh] w-[720px] max-w-[92vw] flex-col overflow-hidden rounded-2xl bg-surface shadow-2xl">
          <div class="flex items-center justify-between border-b border-outline px-4 py-2">
            <span class="text-sm font-semibold text-on-surface">Edit metadata</span>
            <button
              type="button"
              class="topbar-pill-btn !h-7 !min-w-7"
              aria-label="Close"
              @click="closeMetadataEditor"
            >
              ✕
            </button>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto">
            <MetadataEditor :key="store.selectedTrackIds.join(',')" />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
  <ServerStartupModal />
  <Toast />
</template>

<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <!-- Search field: pinned, not part of the scrolling body -->
    <div class="shrink-0 px-4 pb-2 pt-3">
      <div class="flex h-11 items-center gap-2 rounded-full bg-surface px-4">
        <FeatherIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
        <input
          v-model="query"
          type="text"
          placeholder="Search albums, artists…"
          class="flex-1 bg-transparent text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
          @keyup.enter="onSearchEnter"
        />
        <button
          v-if="query"
          type="button"
          class="shrink-0 text-on-surface-variant"
          aria-label="Clear search"
          @click="clearSearch"
        >
          <FeatherIcon name="x" class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto">
      <!-- Search history -->
      <div v-if="!query && settings.searchHistory.length > 0" class="px-4 pb-2">
        <div class="flex items-center justify-between py-1">
          <div class="flex items-center gap-1.5 text-label-lg text-on-surface-variant">
            <FeatherIcon name="clock" class="h-4 w-4" />
            <span>Recent</span>
          </div>
          <button
            type="button"
            class="text-label-lg text-primary"
            @click="settings.clearSearchHistory()"
          >
            Clear all
          </button>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="h in settings.searchHistory"
            :key="h"
            type="button"
            class="rounded-full bg-surface px-3 py-1.5 text-label-lg text-on-surface"
            @click="selectHistoryChip(h)"
          >
            {{ h }}
          </button>
        </div>
      </div>

      <!-- Artist filter chip -->
      <div v-if="artistLabel" class="px-4 pb-2">
        <div class="inline-flex items-center gap-2 rounded-full bg-surface px-3 py-1.5">
          <FeatherIcon name="user" class="h-4 w-4 text-on-surface-variant" />
          <span class="text-label-lg text-on-surface">{{ artistLabel }}</span>
          <button
            type="button"
            class="text-on-surface-variant"
            aria-label="Clear artist filter"
            @click="clearArtistFilter"
          >
            <FeatherIcon name="x" class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      <!-- Toolbar -->
      <div class="flex items-center gap-1 px-4 py-2">
        <div ref="sortMenuRef" class="relative">
          <button
            type="button"
            class="text-label-lg text-primary"
            @click="sortMenuOpen = !sortMenuOpen"
          >
            Sort: {{ sortLabel }}
          </button>
          <div
            v-if="sortMenuOpen"
            class="absolute left-0 top-full z-20 mt-1 w-32 rounded-xl bg-surface-container py-1 shadow-xl"
          >
            <button
              v-for="opt in SORT_OPTIONS"
              :key="opt.value"
              type="button"
              class="flex w-full items-center justify-between px-3 py-2 text-label-lg text-on-surface"
              @click="selectSort(opt.value)"
            >
              <span>{{ opt.label }}</span>
              <FeatherIcon v-if="settings.sortMode === opt.value" name="check" class="h-4 w-4 text-primary" />
            </button>
          </div>
        </div>

        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
          :aria-label="settings.sortAscending ? 'Sort descending' : 'Sort ascending'"
          @click="settings.setSortAscending(!settings.sortAscending)"
        >
          <FeatherIcon :name="settings.sortAscending ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
        </button>

        <div class="flex-1" />

        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
          aria-label="Change layout"
          @click="settings.cycleAlbumViewStyle()"
        >
          <FeatherIcon :name="viewStyleIcon" class="h-4 w-4" />
        </button>

        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full transition-colors"
          :class="player.shuffleAllActive ? 'text-primary' : 'text-on-surface'"
          aria-label="Shuffle all"
          :aria-pressed="player.shuffleAllActive"
          @click="player.startShuffleAll(lib.filteredTracks)"
        >
          <FeatherIcon name="shuffle" class="h-4 w-4" />
        </button>
      </div>

      <!-- Content -->
      <div v-if="lib.loading" class="flex items-center justify-center py-12">
        <FeatherIcon name="refresh-cw" class="h-7 w-7 animate-spin text-on-surface-variant" />
      </div>

      <div v-else-if="lib.error" class="px-6 py-12 text-center text-body-md text-error">
        {{ lib.error }}
      </div>

      <div v-else-if="showEmptyState" class="flex flex-col items-center gap-2 px-6 py-12 text-center">
        <FeatherIcon name="music" class="h-12 w-12 text-on-surface-variant/40" />
        <span class="text-body-md text-on-surface-variant">{{ emptyMessage }}</span>
      </div>

      <div v-else-if="showTrackList" class="pb-4">
        <TrackListRow
          v-for="track in lib.filteredTracks"
          :key="track.id"
          :track="track"
          :is-playing="player.currentTrack?.id === track.id"
          @play="player.playTrack(track, lib.filteredTracks)"
          @actions="sheetTrack = track"
        />
      </div>

      <div v-else-if="settings.albumViewStyle === 'list'" class="pb-4">
        <AlbumCard
          v-for="item in lib.albumGridItems"
          :key="item.key"
          :item="item"
          mode="list"
          :is-active="isAlbumActive(item)"
          @open="openAlbum(item)"
          @actions="openAlbumPicker(item)"
        />
      </div>

      <div v-else class="grid grid-cols-2 gap-3 px-4 pb-4">
        <AlbumCard
          v-for="item in lib.albumGridItems"
          :key="item.key"
          :item="item"
          mode="grid"
          :is-active="isAlbumActive(item)"
          @open="openAlbum(item)"
          @actions="openAlbumPicker(item)"
        />
      </div>
    </div>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      @close="sheetTrack = null"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />

    <PlaylistPickerSheet
      :open="pickerAlbum !== null"
      :playlists="playlistStore.playlists"
      :membership-ids="membershipIds"
      :partial-membership-ids="partialMembershipIds"
      @add="onPickerAdd"
      @remove="onPickerRemove"
      @create="onPickerCreate"
      @close="pickerAlbum = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import PlaylistPickerSheet from "../components/PlaylistPickerSheet.vue";
import { albumKeyFor, useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { usePlaylistStore } from "../stores/playlists";
import { scrollToActiveSignal } from "../composables/useScrollSignal";
import { showToast } from "../composables/useToast";
import type { AlbumGridItem, CatalogTrack, Playlist, SortMode } from "../types";

const route = useRoute();
const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const playlistStore = usePlaylistStore();

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: "album", label: "Album" },
  { value: "artist", label: "Artist" },
  { value: "year", label: "Year" },
];

// --- Search field --------------------------------------------------------

const query = ref(lib.searchQuery);
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

function commitSearch(value: string): void {
  lib.searchQuery = value;
  const trimmed = value.trim();
  if (trimmed.length > 0) settings.addSearch(trimmed);
}

watch(query, (value) => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => commitSearch(value), 300);
});

function onSearchEnter(): void {
  clearTimeout(debounceTimer);
  commitSearch(query.value);
}

function clearSearch(): void {
  clearTimeout(debounceTimer);
  query.value = "";
  commitSearch("");
}

function selectHistoryChip(entry: string): void {
  clearTimeout(debounceTimer);
  query.value = entry;
  commitSearch(entry);
}

onUnmounted(() => clearTimeout(debounceTimer));

// --- Artist filter ---------------------------------------------------------

const artistLabel = computed(() => (typeof route.query.artist === "string" ? route.query.artist : null));

watch(
  () => route.query.artist,
  (value) => {
    lib.artistFilter = (value as string) ?? null;
  },
  { immediate: true },
);

onUnmounted(() => {
  lib.artistFilter = null;
});

function clearArtistFilter(): void {
  void router.replace({ name: "library" });
}

// --- Toolbar: sort dropdown -------------------------------------------------

const sortMenuOpen = ref(false);
const sortMenuRef = ref<HTMLElement | null>(null);

const sortLabel = computed(
  () => SORT_OPTIONS.find((o) => o.value === settings.sortMode)?.label ?? "Album",
);

const viewStyleIcon = computed(() => {
  if (settings.albumViewStyle === "list") return "list";
  if (settings.albumViewStyle === "tracks") return "music";
  return "grid";
});

function selectSort(mode: SortMode): void {
  settings.setSortMode(mode);
  sortMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  if (!sortMenuOpen.value) return;
  const el = sortMenuRef.value;
  if (el && !el.contains(e.target as Node)) sortMenuOpen.value = false;
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));

// --- Content -----------------------------------------------------------

const isSearching = computed(() => lib.searchQuery.trim().length > 0);
const showTrackList = computed(() => isSearching.value || settings.albumViewStyle === "tracks");
const showEmptyState = computed(() =>
  showTrackList.value ? lib.filteredTracks.length === 0 : lib.albumGridItems.length === 0,
);
const emptyMessage = computed(() =>
  isSearching.value ? `No results for "${lib.searchQuery}"` : "No tracks in library",
);

function isAlbumActive(item: AlbumGridItem): boolean {
  return player.currentTrack != null && item.trackIds.includes(player.currentTrack.id);
}

function openAlbum(item: AlbumGridItem): void {
  void router.push({ name: "album", params: { albumKey: item.key } });
}

// --- Track actions sheet -------------------------------------------------

const sheetTrack = ref<CatalogTrack | null>(null);

function onViewArtist(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "library", query: { artist: t.artist ?? t.album_artist } });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: albumKeyFor(t) } });
}

// --- Album playlist picker -------------------------------------------------

const pickerAlbum = ref<AlbumGridItem | null>(null);

async function openAlbumPicker(item: AlbumGridItem): Promise<void> {
  await playlistStore.loadAllTrackIds();
  pickerAlbum.value = item;
}

const membershipIds = computed(() => {
  const album = pickerAlbum.value;
  const result = new Set<number>();
  if (!album) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (set && album.trackIds.every((id) => set.has(id))) result.add(p.id);
  }
  return result;
});

const partialMembershipIds = computed(() => {
  const album = pickerAlbum.value;
  const result = new Set<number>();
  if (!album) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (!set) continue;
    const hasSome = album.trackIds.some((id) => set.has(id));
    const hasAll = album.trackIds.every((id) => set.has(id));
    if (hasSome && !hasAll) result.add(p.id);
  }
  return result;
});

async function onPickerAdd(p: Playlist): Promise<void> {
  const album = pickerAlbum.value;
  if (!album) return;
  const existing = playlistStore.trackIdSets.get(p.id) ?? new Set<number>();
  const toAdd = album.trackIds.filter((id) => !existing.has(id));
  if (toAdd.length > 0) await playlistStore.addTracks(p.id, toAdd);
  showToast(`Added to ${p.name}`);
}

async function onPickerRemove(p: Playlist): Promise<void> {
  const album = pickerAlbum.value;
  if (!album) return;
  await playlistStore.removeTracks(p.id, album.trackIds);
  showToast(`Removed from ${p.name}`);
}

async function onPickerCreate(name: string): Promise<void> {
  const album = pickerAlbum.value;
  if (!album) return;
  const p = await playlistStore.createPlaylist(name, "🎵");
  await playlistStore.addTracks(p.id, album.trackIds);
  showToast(`Added to ${p.name}`);
}

// --- Scroll-to-active (re-tapping the active Library tab) ------------------

watch(scrollToActiveSignal, () => {
  const t = player.currentTrack;
  if (!t) return;
  const key = albumKeyFor(t);
  const el = document.querySelector(`[data-album-key="${CSS.escape(key)}"]`);
  el?.scrollIntoView({ behavior: "smooth", block: "center" });
});
</script>

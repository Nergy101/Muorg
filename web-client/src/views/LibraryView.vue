<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center justify-between px-4">
      <div class="flex min-w-0 items-center gap-2">
        <MageIcon name="compact-disk-fill" class="h-5 w-5 text-primary" />
        <span class="truncate text-title-lg text-on-surface">Library</span>
      </div>
    </div>

    <!-- Search field: pinned, not part of the scrolling body -->
    <div class="content-col shrink-0 px-4 pb-2 pt-3">
      <div class="flex h-11 items-center gap-2 rounded-full bg-surface px-4">
        <MageIcon name="search" class="h-4 w-4 shrink-0 text-on-surface-variant" />
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
          <MageIcon name="multiply" class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto">
      <!-- Search history -->
      <div v-if="!query && settings.searchHistory.length > 0" class="content-col px-4 pb-2">
        <div class="flex items-center justify-between py-1">
          <div class="flex items-center gap-1.5 text-label-lg text-on-surface-variant">
            <MageIcon name="clock" class="h-4 w-4" />
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
      <div v-if="artistLabel" class="content-col px-4 pb-2">
        <div class="inline-flex items-center gap-2 rounded-full bg-surface px-3 py-1.5">
          <MageIcon name="user" class="h-4 w-4 text-on-surface-variant" />
          <span class="text-label-lg text-on-surface">{{ artistLabel }}</span>
          <button
            type="button"
            class="text-on-surface-variant"
            aria-label="Clear artist filter"
            @click="clearArtistFilter"
          >
            <MageIcon name="multiply" class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      <!-- Toolbar -->
      <div class="content-col flex items-center gap-1 px-4 py-2">
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
              <MageIcon v-if="settings.sortMode === opt.value" name="check" class="h-4 w-4 text-primary" />
            </button>
          </div>
        </div>

        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
          :aria-label="settings.sortAscending ? 'Sort descending' : 'Sort ascending'"
          @click="settings.setSortAscending(!settings.sortAscending)"
        >
          <MageIcon :name="settings.sortAscending ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
        </button>

        <div ref="genreMenuRef" class="relative">
          <button
            type="button"
            class="flex max-w-28 items-center gap-1 text-label-lg text-primary"
            @click="genreMenuOpen = !genreMenuOpen"
          >
            <span class="truncate">Genre: {{ genreLabel }}</span>
            <MageIcon :name="genreMenuOpen ? 'chevron-up' : 'chevron-down'" class="h-4 w-4 shrink-0" />
          </button>
          <div
            v-if="genreMenuOpen"
            class="absolute left-0 top-full z-20 mt-1 max-h-72 w-48 overflow-y-auto rounded-xl bg-surface-container py-1 shadow-xl"
          >
            <button
              type="button"
              class="flex w-full items-center justify-between px-3 py-2 text-label-lg text-on-surface"
              @click="selectGenre(null)"
            >
              <span>All genres</span>
              <MageIcon v-if="!lib.genreFilter" name="check" class="h-4 w-4 text-primary" />
            </button>
            <button
              v-for="g in lib.genres"
              :key="g.value"
              type="button"
              class="flex w-full items-center justify-between gap-2 px-3 py-2 text-label-lg text-on-surface"
              @click="selectGenre(g.value)"
            >
              <span class="truncate">{{ g.label }}</span>
              <MageIcon v-if="lib.genreFilter === g.value" name="check" class="h-4 w-4 shrink-0 text-primary" />
            </button>
          </div>
        </div>

        <div class="flex-1" />

        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
          aria-label="Change layout"
          @click="settings.cycleAlbumViewStyle()"
        >
          <MageIcon :name="viewStyleIcon" class="h-4 w-4" />
        </button>

        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full transition-colors"
          :class="player.shuffleAllActive ? 'text-primary' : 'text-on-surface'"
          aria-label="Shuffle all"
          :aria-pressed="player.shuffleAllActive"
          @click="player.startShuffleAll(lib.filteredTracks)"
        >
          <MageIcon name="exchange" class="h-4 w-4" />
        </button>
      </div>

      <!-- Content -->
      <div v-if="lib.loading" class="flex items-center justify-center py-12">
        <MageIcon name="refresh" class="h-7 w-7 animate-spin text-on-surface-variant" />
      </div>

      <div v-else-if="lib.error" class="content-col px-6 py-12 text-center text-body-md text-error">
        {{ lib.error }}
      </div>

      <div
        v-else-if="showEmptyState"
        class="content-col flex flex-col items-center gap-2 px-6 py-12 text-center"
      >
        <MageIcon name="music" class="h-12 w-12 text-on-surface-variant/40" />
        <span class="text-body-md text-on-surface-variant">{{ emptyMessage }}</span>
      </div>

      <div v-else-if="showTrackList" class="content-col">
        <div
          ref="tracksAnchor"
          class="pb-4"
          :style="{ height: `${tracksList.totalHeight.value + 16}px` }"
        >
          <div :style="{ transform: `translateY(${tracksList.offsetTop.value}px)` }">
            <TrackListRow
              v-for="track in visibleTracks"
              :key="track.id"
              :track="track"
              :is-playing="player.currentTrack?.id === track.id"
              @play="player.playTrack(track, lib.filteredTracks)"
              @actions="sheetTrack = track"
            />
          </div>
        </div>
      </div>

      <div v-else-if="settings.albumViewStyle === 'list'" class="content-col">
        <div
          ref="albumsListAnchor"
          class="pb-4"
          :style="{ height: `${albumsList.totalHeight.value + 16}px` }"
        >
          <div :style="{ transform: `translateY(${albumsList.offsetTop.value}px)` }">
            <AlbumCard
              v-for="item in visibleAlbumListItems"
              :key="item.key"
              :item="item"
              mode="list"
              :is-active="isAlbumActive(item)"
              @open="openAlbum(item)"
              @actions="openAlbumPicker(item)"
            />
          </div>
        </div>
      </div>

      <!-- Deliberately not content-col: the grid is the one thing that should use
           the whole shell. auto-fill keeps tiles ~200-240px at any width, where a
           fixed 4-column rule would balloon them to ~590px at 2400px.
           Virtualized: gridCols mirrors those breakpoints in JS so the spacer
           height and the rendered window always agree. -->
      <div
        v-else
        ref="albumsAnchor"
        class="px-4 pb-4"
        :style="{ height: `${albumGrid.totalHeight.value + 16}px` }"
      >
        <div
          class="grid gap-3"
          :style="{
            transform: `translateY(${albumGrid.offsetTop.value}px)`,
            gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
          }"
        >
          <AlbumCard
            v-for="item in visibleGridItems"
            :key="item.key"
            :item="item"
            mode="grid"
            :is-active="isAlbumActive(item)"
            @open="openAlbum(item)"
            @actions="openAlbumPicker(item)"
          />
        </div>
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
import MageIcon from "../components/MageIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import PlaylistPickerSheet from "../components/PlaylistPickerSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { usePlaylistStore } from "../stores/playlists";
import { scrollToActiveSignal } from "../composables/useScrollSignal";
import { useScrollMemory } from "../composables/useScrollMemory";
import { useVirtualList } from "../composables/useVirtualList";
import { useGridColumns } from "../composables/useGridColumns";
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

/** Applies the query to the live library filter (debounced while typing). */
function commitSearch(value: string): void {
  lib.searchQuery = value;
}

/** Records a committed search in the history — Enter or tapping a history
 *  chip only, never intermediate keystrokes (the debounced watcher just
 *  filters live). */
function recordSearch(value: string): void {
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
  recordSearch(query.value);
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
  recordSearch(entry);
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
  if (settings.albumViewStyle === "list") return "arrowlist";
  if (settings.albumViewStyle === "tracks") return "music";
  return "layout-grid";
});

function selectSort(mode: SortMode): void {
  settings.setSortMode(mode);
  sortMenuOpen.value = false;
}

// --- Toolbar: genre filter dropdown ------------------------------------------

const genreMenuOpen = ref(false);
const genreMenuRef = ref<HTMLElement | null>(null);

const genreLabel = computed(
  () => lib.genres.find((g) => g.value === lib.genreFilter)?.label ?? "All",
);

function selectGenre(value: string | null): void {
  lib.genreFilter = value;
  genreMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  if (!sortMenuOpen.value && !genreMenuOpen.value) return;
  const target = e.target as Node;
  if (sortMenuOpen.value && sortMenuRef.value && !sortMenuRef.value.contains(target)) {
    sortMenuOpen.value = false;
  }
  if (genreMenuOpen.value && genreMenuRef.value && !genreMenuRef.value.contains(target)) {
    genreMenuOpen.value = false;
  }
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
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
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

// --- Virtualized lists -----------------------------------------------------
// 3000+ tracks / 273 albums is too many DOM rows to mount at once: each row
// carries MarqueeText animations, touch handlers and a cover fetch. Only the
// rows near the viewport are rendered; the anchor reserves the full height.

const scroller = ref<HTMLElement | null>(null);
const tracksAnchor = ref<HTMLElement | null>(null);
const albumsListAnchor = ref<HTMLElement | null>(null);
const albumsAnchor = ref<HTMLElement | null>(null);

const TRACK_ROW_HEIGHT = 56; // h-14, matches REORDER_ROW_HEIGHT

const tracksList = useVirtualList({
  scroller,
  anchor: tracksAnchor,
  count: () => lib.filteredTracks.length,
  rowHeight: () => TRACK_ROW_HEIGHT,
  columns: () => 1,
});

const albumsList = useVirtualList({
  scroller,
  anchor: albumsListAnchor,
  count: () => lib.albumGridItems.length,
  rowHeight: () => TRACK_ROW_HEIGHT,
  columns: () => 1,
});

// Grid column count mirrors the CSS breakpoints the static grid used
// (grid-cols-2 / md:grid-cols-3 / lg:auto-fill minmax(200px, 1fr)). Kept in a
// ref so the template's gridTemplateColumns and the spacer height stay in sync.
const gridCols = useGridColumns(scroller);

/** Tile height (aspect-square) + gap; the grid's row pitch. */
function gridPitch(): number {
  const w = scroller.value?.clientWidth ?? 0;
  const c = gridCols.value;
  const tile = w > 0 ? (w - 32 - (c - 1) * 12) / c : 200;
  return Math.max(1, tile + 12);
}

const albumGrid = useVirtualList({
  scroller,
  anchor: albumsAnchor,
  count: () => lib.albumGridItems.length,
  rowHeight: gridPitch,
  columns: () => gridCols.value,
});

const visibleTracks = computed(() =>
  lib.filteredTracks.slice(tracksList.start.value, tracksList.end.value),
);
const visibleAlbumListItems = computed(() =>
  lib.albumGridItems.slice(albumsList.start.value, albumsList.end.value),
);
const visibleGridItems = computed(() =>
  lib.albumGridItems.slice(albumGrid.start.value, albumGrid.end.value),
);

// --- Scroll-to-active (re-tapping the active Library tab) ------------------

watch(scrollToActiveSignal, () => {
  const t = player.currentTrack;
  if (!t) return;
  if (showTrackList.value) {
    const i = lib.filteredTracks.findIndex((tr) => tr.id === t.id);
    if (i >= 0) tracksList.scrollToIndex(i);
    return;
  }
  const i = lib.albumGridItems.findIndex((item) => item.key === lib.keyForTrack(t));
  if (i < 0) return;
  (settings.albumViewStyle === "list" ? albumsList : albumGrid).scrollToIndex(i);
});

// --- Scroll memory ---------------------------------------------------------
// Restores the list offset when coming back from an album. Cannot collide with
// the scroll-to-active above: BottomNav only bumps that signal when Library is
// already the active route, so a re-tap never triggers an activation and an
// activation never triggers a re-tap.
useScrollMemory(scroller);
</script>

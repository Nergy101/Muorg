<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <!-- Desktop-class library table (>=1024px, tracks layout). Mounted
         instead of the mobile scroller so it can own its own virtualization
         and keyboard focus; the mobile list below is unaffected. -->
    <LibraryTrackTable v-if="showDesktopTable" />

    <div v-else ref="scroller" class="scrollbar-overlay min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
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
              :selectable="selection.selecting"
              :selected="selection.has(track.id)"
              @play="player.playTrack(track, lib.filteredTracks)"
              @select="(mods) => onSelect(track, mods)"
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
              @actions="albumActions = item"
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
        class="px-4 pb-4 pt-3"
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
            @actions="albumActions = item"
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

    <AlbumActionsHost :item="albumActions" @close="albumActions = null" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import AlbumActionsHost from "../components/AlbumActionsHost.vue";
import LibraryTrackTable from "../components/library/LibraryTrackTable.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useSelectionStore, type SelectModifiers } from "../stores/selection";
import { provideVisibleIds } from "../composables/useSelectableList";
import { scrollToActiveSignal } from "../composables/useScrollSignal";
import { useScrollMemory } from "../composables/useScrollMemory";
import { useVirtualList } from "../composables/useVirtualList";
import { useGridColumns } from "../composables/useGridColumns";
import type { AlbumGridItem, CatalogTrack } from "../types";

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const selection = useSelectionStore();

// --- Content -----------------------------------------------------------

const isSearching = computed(() => lib.searchQuery.trim().length > 0);
const showTrackList = computed(() => isSearching.value || settings.albumViewStyle === "tracks");
const showEmptyState = computed(() =>
  showTrackList.value ? lib.filteredTracks.length === 0 : lib.albumGridItems.length === 0,
);
const emptyMessage = computed(() =>
  isSearching.value ? `No results for "${lib.searchQuery}"` : "No tracks in library",
);

/** The desktop-class table replaces the mobile flat list at >=1024px, only
 *  for the "tracks" layout (grid/list keep their album views; a search still
 *  falls back to the familiar flat list regardless of viewport). */
const isDesktop = ref(false);
let desktopMq: MediaQueryList | null = null;
function onDesktopMqChange(): void {
  isDesktop.value = desktopMq?.matches ?? false;
}
onMounted(() => {
  if (typeof window.matchMedia !== "function") return;
  desktopMq = window.matchMedia("(min-width: 64rem)");
  onDesktopMqChange();
  desktopMq.addEventListener("change", onDesktopMqChange);
});
onBeforeUnmount(() => {
  desktopMq?.removeEventListener("change", onDesktopMqChange);
  desktopMq = null;
});
const showDesktopTable = computed(() => isDesktop.value && settings.albumViewStyle === "tracks");

/** Mobile/search flat list selection — Ctrl/Shift-click or an active
 *  selection toggles; a plain tap plays as before. */
function onSelect(track: CatalogTrack, mods: SelectModifiers): void {
  const consumed = selection.click(
    track.id,
    lib.filteredTracks.map((t) => t.id),
    mods,
  );
  if (!consumed) void player.playTrack(track, lib.filteredTracks);
}

provideVisibleIds(() => lib.filteredTracks.map((t) => t.id));

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
  const name = t.artist ?? t.album_artist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}

// --- Album context menu ---------------------------------------------------
// Right-click / "more" on an album card. The menu and the add-to-playlist
// picker live in AlbumActionsHost, shared with every other screen that shows
// album cards.

const albumActions = ref<AlbumGridItem | null>(null);

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
  // The desktop table owns its own viewport and has no scroll-to-active hook
  // yet; nothing to drive here while it's showing.
  if (showDesktopTable.value) return;
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

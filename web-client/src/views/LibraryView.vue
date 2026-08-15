<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div ref="scroller" class="scrollbar-overlay min-h-0 flex-1 overflow-y-auto">
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

    <AlbumActionsSheet
      :open="albumActions !== null"
      :item="albumActions"
      @close="albumActions = null"
      @play="playAlbumFromSheet"
      @add-to-playlist="onAlbumAddToPlaylist"
      @view-artist="onAlbumViewArtist"
      @view-album="onAlbumViewAlbum"
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
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import AlbumActionsSheet from "../components/AlbumActionsSheet.vue";
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
import type { AlbumGridItem, CatalogTrack, Playlist } from "../types";

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const playlistStore = usePlaylistStore();

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
  const name = t.artist ?? t.album_artist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}

// --- Album actions sheet --------------------------------------------------

const albumActions = ref<AlbumGridItem | null>(null);

function playAlbumFromSheet(): void {
  const album = albumActions.value;
  if (!album) return;
  const tracks = lib.tracksForAlbum(album.key);
  albumActions.value = null;
  if (tracks.length === 0) return;
  void player.playTrack(tracks[0], tracks);
}

function onAlbumAddToPlaylist(): void {
  const album = albumActions.value;
  if (!album) return;
  albumActions.value = null;
  void openAlbumPicker(album);
}

function onAlbumViewArtist(): void {
  const album = albumActions.value;
  if (!album) return;
  albumActions.value = null;
  const name = album.albumArtist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onAlbumViewAlbum(): void {
  const album = albumActions.value;
  if (!album) return;
  albumActions.value = null;
  void router.push({ name: "album", params: { albumKey: album.key } });
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

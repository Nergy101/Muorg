<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <template v-if="!playlist">
      <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:rounded-full lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
          aria-label="Back"
          @click="router.back()"
        >
          <MageIcon name="chevron-left" class="h-5 w-5" />
          <span class="hidden lg:inline">Back</span>
        </button>
      </div>
      <div class="flex min-h-0 flex-1 items-center justify-center">
        <p class="text-body-md text-on-surface-variant">Playlist not found</p>
      </div>
    </template>

    <template v-else>
      <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:rounded-full lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
          aria-label="Back"
          @click="router.back()"
        >
          <MageIcon name="chevron-left" class="h-5 w-5" />
          <span class="hidden lg:inline">Back</span>
        </button>

        <span class="min-w-0 flex-1 truncate text-title-md text-on-surface">
          {{ playlist.icon ?? "🎵" }} {{ playlist.name }}
        </span>

        <button
          v-if="viewStyle === 'tracks' && !isSmart && !isMix"
          type="button"
          class="flex h-9 shrink-0 items-center gap-1 rounded-full px-2"
          :class="hasUnsavedOrder ? 'text-primary' : 'text-on-surface-variant'"
          @click="commitOrder"
        >
          <MageIcon :name="hasUnsavedOrder ? 'save-floppy' : 'dash-menu'" class="h-5 w-5" />
          <span class="text-label-md">{{ hasUnsavedOrder ? "save" : "reorder" }}</span>
        </button>

        <button
          v-if="isSmart"
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-primary"
          aria-label="Edit rules"
          @click="openSmartEditor"
        >
          <MageIcon name="zap" class="h-5 w-5" />
        </button>

        <button
          v-if="isMix"
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-primary"
          aria-label="Save mix as playlist"
          @click="saveMixOpen = true"
        >
          <MageIcon name="save-floppy" class="h-5 w-5" />
        </button>

        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant"
          aria-label="Change layout"
          @click="toggleViewStyle"
        >
          <MageIcon :name="viewIcon" class="h-5 w-5" />
        </button>
      </div>

      <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto">
        <div v-if="tracks.length === 0" class="content-col flex justify-center py-12">
          <p class="text-body-md text-on-surface-variant">No tracks in this playlist</p>
        </div>

        <div v-else-if="viewStyle === 'tracks'" class="content-col">
          <div
            ref="tracksAnchor"
            :style="{ height: `${tracksList.totalHeight.value}px` }"
          >
            <div :style="{ transform: `translateY(${tracksList.offsetTop.value}px)` }">
              <div
                v-for="entry in visibleTrackEntries"
                :key="entry.id"
                class="relative flex h-14 items-center"
                :class="rowClass(entry.index)"
                :style="rowStyle(entry.index)"
              >
                <div
                  v-if="!isSmart && !isMix"
                  class="flex h-14 w-10 shrink-0 items-center justify-center text-on-surface-variant"
                  style="touch-action: none"
                  @pointerdown="drag.start(entry.index, $event)"
                >
                  <MageIcon name="dash-menu" class="h-5 w-5" />
                </div>
                <TrackListRow
                  v-if="trackById.get(entry.id)"
                  class="min-w-0 flex-1"
                  :track="trackById.get(entry.id)!"
                  :is-playing="player.currentTrack?.id === entry.id"
                  @play="player.playTrack(trackById.get(entry.id)!, tracks)"
                  @actions="openActions(trackById.get(entry.id)!)"
                />
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="viewStyle === 'list'" class="content-col">
          <div
            ref="albumsListAnchor"
            :style="{ height: `${albumsList.totalHeight.value}px` }"
          >
            <div :style="{ transform: `translateY(${albumsList.offsetTop.value}px)` }">
              <AlbumCard
                v-for="item in visibleAlbumListItems"
                :key="item.key"
                :item="item"
                mode="list"
                :is-active="isAlbumActive(item)"
                @open="openAlbum(item)"
                @actions="openAlbumActions(item)"
              />
            </div>
          </div>
        </div>

        <!-- Full-bleed by design; see LibraryView for the auto-fill rationale. -->
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
              @actions="openAlbumActions(item)"
            />
          </div>
        </div>
      </div>
    </template>

    <SmartPlaylistDialog
      :open="smartEditorOpen"
      is-editing
      :initial-name="playlist?.name"
      :initial-icon="playlist?.icon ?? undefined"
      :initial-rules="smartEditorRules"
      :genres="genres"
      @confirm="onSmartEdited"
      @cancel="smartEditorOpen = false"
    />

    <PlaylistFormDialog
      :open="saveMixOpen"
      title="New playlist"
      confirm-label="Save"
      :initial-name="mix?.name"
      :initial-icon="mix?.emoji"
      @confirm="onSaveMix"
      @cancel="saveMixOpen = false"
    />

    <TrackActionsSheet
      :open="actionsTrack !== null"
      :track="actionsTrack"
      @close="closeActions"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import TrackListRow from "../components/TrackListRow.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import SmartPlaylistDialog from "../components/SmartPlaylistDialog.vue";
import PlaylistFormDialog from "../components/PlaylistFormDialog.vue";
import { useDragReorder, REORDER_ROW_HEIGHT } from "../composables/useDragReorder";
import { useScrollMemory } from "../composables/useScrollMemory";
import { useVirtualList } from "../composables/useVirtualList";
import { useGridColumns } from "../composables/useGridColumns";
import { showToast } from "../composables/useToast";
import { usePlaylistStore, rulesToSmartJson, parseSmartRules } from "../stores/playlists";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useLibraryStore } from "../stores/library";
import { findMix } from "../composables/useMixes";
import type { AlbumGridItem, AlbumViewStyle, CatalogTrack, Playlist, SmartRule } from "../types";

const props = defineProps<{ id: string }>();

const route = useRoute();
const router = useRouter();
const playlistStore = usePlaylistStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const lib = useLibraryStore();

const playlistId = computed(() => Number(props.id));

/** True when this route renders an ephemeral client-side mix, not a server playlist. */
const isMix = computed(() => route.name === "mix");
const mix = computed(() => (isMix.value ? findMix(playlistId.value) : null));

const playlist = computed<Playlist | null>(() => {
  if (mix.value) {
    return {
      id: mix.value.id,
      name: mix.value.name,
      track_count: mix.value.trackIds.length,
      icon: mix.value.emoji,
      smart_rules: null,
    };
  }
  return playlistStore.playlists.find((p) => p.id === playlistId.value) ?? null;
});

/** Smart playlists are rule-driven: no manual reorder, rules editable in place. */
const isSmart = computed(() => playlist.value?.smart_rules != null);

const genres = computed(() =>
  [...new Set(lib.tracks.map((t) => t.genre).filter((g): g is string => g != null))].sort(),
);

const smartEditorOpen = ref(false);
const smartEditorRules = ref<SmartRule[]>([]);

function openSmartEditor(): void {
  smartEditorRules.value = parseSmartRules(playlist.value?.smart_rules ?? null);
  smartEditorOpen.value = true;
}

async function onSmartEdited(_name: string, _icon: string, rules: SmartRule[]): Promise<void> {
  if (!playlist.value) return;
  await playlistStore.updateSmartPlaylistRules(playlist.value.id, rulesToSmartJson(rules));
  smartEditorOpen.value = false;
  await loadOrder();
}

const viewIcon = computed(
  () => ({ grid: "layout-grid", list: "arrowlist", tracks: "music" })[viewStyle.value],
);

// --- View style ---
// Mixes open as track rows by default, independent of the global album-view
// preference (which keeps applying to real playlists). The in-header toggle
// cycles the mix's own style with the same grid → list → tracks → grid order.
const mixViewStyle = ref<AlbumViewStyle>("tracks");
const viewStyle = computed<AlbumViewStyle>(() =>
  isMix.value ? mixViewStyle.value : settings.albumViewStyle,
);

watch(
  () => (isMix.value ? playlistId.value : null),
  (id) => {
    if (id != null) mixViewStyle.value = "tracks";
  },
  { immediate: true },
);

function toggleViewStyle(): void {
  if (isMix.value) {
    mixViewStyle.value =
      mixViewStyle.value === "grid" ? "list" : mixViewStyle.value === "list" ? "tracks" : "grid";
    return;
  }
  settings.cycleAlbumViewStyle();
}

const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

// Ordered ids loaded from the server; `reorderedIds` mirrors it and only
// diverges while a drag is staged but not yet saved.
const orderedIds = ref<number[]>([]);
const reorderedIds = ref<number[]>([]);
const hasUnsavedOrder = ref(false);

watch(
  orderedIds,
  (ids) => {
    reorderedIds.value = [...ids];
    hasUnsavedOrder.value = false;
  },
  { immediate: true },
);

async function loadOrder(): Promise<void> {
  if (mix.value) {
    orderedIds.value = [...mix.value.trackIds];
    return;
  }
  orderedIds.value = await playlistStore.loadTrackOrderForPlaylist(playlistId.value);
}

/** Save the current mix as a real playlist: New-playlist modal, then the mix's
 *  tracks are added to the freshly created playlist. */
const saveMixOpen = ref(false);

async function onSaveMix(name: string, icon: string): Promise<void> {
  if (!mix.value) return;
  const created = await playlistStore.createPlaylist(name, icon);
  await playlistStore.addTracks(created.id, mix.value.trackIds);
  saveMixOpen.value = false;
  showToast("Mix saved as playlist");
}

onMounted(loadOrder);
watch(playlistId, loadOrder);

const tracks = computed<CatalogTrack[]>(() =>
  orderedIds.value
    .map((id) => trackById.value.get(id))
    .filter((t): t is CatalogTrack => t != null),
);

async function commitOrder(): Promise<void> {
  if (!hasUnsavedOrder.value) return;
  await playlistStore.reorderTracks(playlistId.value, reorderedIds.value);
  orderedIds.value = [...reorderedIds.value];
  showToast("Order saved");
}

const drag = useDragReorder({
  itemCount: () => reorderedIds.value.length,
  rowHeight: REORDER_ROW_HEIGHT,
  immediate: false,
  onCommit: (from, to) => {
    const ids = [...reorderedIds.value];
    const [moved] = ids.splice(from, 1);
    ids.splice(to, 0, moved);
    reorderedIds.value = ids;
    hasUnsavedOrder.value = true;
  },
});

function rowClass(i: number): string {
  return i === drag.draggingIndex.value
    ? "z-10 rounded-lg border border-primary"
    : "transition-transform duration-150";
}

function rowStyle(i: number): Record<string, string> {
  if (i === drag.draggingIndex.value) {
    return { transform: `translateY(${drag.offsetY.value}px)` };
  }
  const from = drag.draggingIndex.value;
  const to = drag.dropIndex.value;
  if (from === null || to === null || from === to) return {};
  if (from < to && i > from && i <= to) return { transform: `translateY(-${REORDER_ROW_HEIGHT}px)` };
  if (from > to && i < from && i >= to) return { transform: `translateY(${REORDER_ROW_HEIGHT}px)` };
  return {};
}

// Grid/list modes group the playlist's tracks into albums locally — the
// library store's grouping only ever sees the whole catalog.
function buildAlbumItems(source: CatalogTrack[]): AlbumGridItem[] {
  const map = new Map<string, AlbumGridItem>();
  for (const t of source) {
    const key = lib.keyForTrack(t);
    let item = map.get(key);
    if (!item) {
      // Seed display fields from the full-catalog album so the playlist grid
      // shows the same album identity (artist, year) as the library grid.
      const seed = lib.albumByKey(key);
      item = seed
        ? { ...seed, trackCount: 0, totalDurationSecs: 0, coverTrackId: null, hasCover: false, trackIds: [] }
        : {
            key,
            album: t.album ?? "Unknown Album",
            albumArtist: t.album_artist ?? t.artist ?? "Unknown Artist",
            year: null,
            trackCount: 0,
            totalDurationSecs: 0,
            coverTrackId: t.has_cover ? t.id : null,
            hasCover: t.has_cover,
            trackIds: [],
          };
      map.set(key, item);
    }
    item.trackCount++;
    item.totalDurationSecs += t.duration_secs ?? 0;
    item.trackIds.push(t.id);
    if (t.year && (item.year === null || t.year < item.year)) item.year = t.year;
    if (t.has_cover && !item.hasCover) {
      item.hasCover = true;
      item.coverTrackId = t.id;
    }
  }
  return [...map.values()].sort((a, b) => a.album.localeCompare(b.album));
}

const albumItems = computed(() => buildAlbumItems(tracks.value));

function isAlbumActive(item: AlbumGridItem): boolean {
  const current = player.currentTrack;
  return current != null && item.trackIds.includes(current.id);
}

function openAlbum(item: AlbumGridItem): void {
  router.push({
    name: "album",
    params: { albumKey: item.key },
    // Mixes aren't server playlists; a playlistId query would try to resolve
    // one and filter the album to nothing.
    ...(isMix.value ? {} : { query: { playlistId: String(playlistId.value) } }),
  });
}

function openAlbumActions(item: AlbumGridItem): void {
  const track = trackById.value.get(item.trackIds[0]);
  if (track) openActions(track);
}

const actionsTrack = ref<CatalogTrack | null>(null);

function openActions(track: CatalogTrack): void {
  actionsTrack.value = track;
}

function closeActions(): void {
  actionsTrack.value = null;
}

function onViewArtist(): void {
  const track = actionsTrack.value;
  if (track) {
    router.push({ name: "library", query: { artist: track.artist ?? track.album_artist ?? undefined } });
  }
  closeActions();
}

function onViewAlbum(): void {
  const track = actionsTrack.value;
  if (track) {
    router.push({ name: "album", params: { albumKey: lib.keyForTrack(track) } });
  }
  closeActions();
}

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);

// --- Virtualized lists -----------------------------------------------------
// A playlist can be thousands of tracks; only the rows near the viewport are
// mounted (each row carries MarqueeText animations, touch handlers and a cover
// fetch). The anchors reserve the full heights. Grid column count mirrors the
// static grid's breakpoints (grid-cols-2 / md:grid-cols-3 / lg:auto-fill).
const tracksAnchor = ref<HTMLElement | null>(null);
const albumsListAnchor = ref<HTMLElement | null>(null);
const albumsAnchor = ref<HTMLElement | null>(null);
const gridCols = useGridColumns(scroller);

const tracksList = useVirtualList({
  scroller,
  anchor: tracksAnchor,
  count: () => reorderedIds.value.length,
  rowHeight: () => REORDER_ROW_HEIGHT,
  columns: () => 1,
});

const albumsList = useVirtualList({
  scroller,
  anchor: albumsListAnchor,
  count: () => albumItems.value.length,
  rowHeight: () => REORDER_ROW_HEIGHT,
  columns: () => 1,
});

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
  count: () => albumItems.value.length,
  rowHeight: gridPitch,
  columns: () => gridCols.value,
});

/** Visible track rows with their absolute indices (drag reorder needs them). */
const visibleTrackEntries = computed(() => {
  const s = tracksList.start.value;
  const e = tracksList.end.value;
  return reorderedIds.value.slice(s, e).map((id, i) => ({ id, index: s + i }));
});

const visibleAlbumListItems = computed(() =>
  albumItems.value.slice(albumsList.start.value, albumsList.end.value),
);
const visibleGridItems = computed(() =>
  albumItems.value.slice(albumGrid.start.value, albumGrid.end.value),
);
</script>

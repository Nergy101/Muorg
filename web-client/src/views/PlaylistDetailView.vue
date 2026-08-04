<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <template v-if="!playlist">
      <div class="flex h-14 shrink-0 items-center gap-1 px-2">
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-full text-on-surface"
          aria-label="Back"
          @click="router.back()"
        >
          <FeatherIcon name="chevron-left" class="h-5 w-5" />
        </button>
      </div>
      <div class="flex min-h-0 flex-1 items-center justify-center">
        <p class="text-body-md text-on-surface-variant">Playlist not found</p>
      </div>
    </template>

    <template v-else>
      <div class="flex h-14 shrink-0 items-center gap-1 px-2">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface"
          aria-label="Back"
          @click="router.back()"
        >
          <FeatherIcon name="chevron-left" class="h-5 w-5" />
        </button>

        <span class="min-w-0 flex-1 truncate text-title-md text-on-surface">
          {{ playlist.icon ?? "🎵" }} {{ playlist.name }}
        </span>

        <button
          v-if="settings.albumViewStyle === 'tracks'"
          type="button"
          class="flex h-9 shrink-0 items-center gap-1 rounded-full px-2"
          :class="hasUnsavedOrder ? 'text-primary' : 'text-on-surface-variant'"
          @click="commitOrder"
        >
          <FeatherIcon :name="hasUnsavedOrder ? 'save' : 'menu'" class="h-5 w-5" />
          <span class="text-label-md">{{ hasUnsavedOrder ? "save" : "reorder" }}</span>
        </button>

        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant"
          aria-label="Change layout"
          @click="settings.cycleAlbumViewStyle()"
        >
          <FeatherIcon :name="viewIcon" class="h-5 w-5" />
        </button>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto">
        <div v-if="tracks.length === 0" class="flex justify-center py-12">
          <p class="text-body-md text-on-surface-variant">No tracks in this playlist</p>
        </div>

        <div v-else-if="settings.albumViewStyle === 'tracks'">
          <div
            v-for="(id, i) in reorderedIds"
            :key="id"
            class="relative flex h-14 items-center"
            :class="rowClass(i)"
            :style="rowStyle(i)"
          >
            <div
              class="flex h-14 w-10 shrink-0 items-center justify-center text-on-surface-variant"
              style="touch-action: none"
              @pointerdown="drag.start(i, $event)"
            >
              <FeatherIcon name="menu" class="h-5 w-5" />
            </div>
            <TrackListRow
              v-if="trackById.get(id)"
              class="min-w-0 flex-1"
              :track="trackById.get(id)!"
              :is-playing="player.currentTrack?.id === id"
              @play="player.playTrack(trackById.get(id)!, tracks)"
              @actions="openActions(trackById.get(id)!)"
            />
          </div>
        </div>

        <div v-else-if="settings.albumViewStyle === 'list'">
          <AlbumCard
            v-for="item in albumItems"
            :key="item.key"
            :item="item"
            mode="list"
            :is-active="isAlbumActive(item)"
            @open="openAlbum(item)"
            @actions="openAlbumActions(item)"
          />
        </div>

        <div v-else class="grid grid-cols-2 gap-3 px-4 pb-4">
          <AlbumCard
            v-for="item in albumItems"
            :key="item.key"
            :item="item"
            mode="grid"
            :is-active="isAlbumActive(item)"
            @open="openAlbum(item)"
            @actions="openAlbumActions(item)"
          />
        </div>
      </div>
    </template>

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
import { useRouter } from "vue-router";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import TrackListRow from "../components/TrackListRow.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { useDragReorder, REORDER_ROW_HEIGHT } from "../composables/useDragReorder";
import { showToast } from "../composables/useToast";
import { usePlaylistStore } from "../stores/playlists";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import type { AlbumGridItem, CatalogTrack } from "../types";

const props = defineProps<{ id: string }>();

const router = useRouter();
const playlistStore = usePlaylistStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const lib = useLibraryStore();

const playlistId = computed(() => Number(props.id));
const playlist = computed(() => playlistStore.playlists.find((p) => p.id === playlistId.value) ?? null);

const viewIcon = computed(
  () => ({ grid: "grid", list: "list", tracks: "music" })[settings.albumViewStyle],
);

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
  orderedIds.value = await playlistStore.loadTrackOrderForPlaylist(playlistId.value);
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
    const key = albumKeyFor(t);
    let item = map.get(key);
    if (!item) {
      item = {
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
    query: { playlistId: String(playlistId.value) },
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
    router.push({ name: "album", params: { albumKey: albumKeyFor(track) } });
  }
  closeActions();
}
</script>

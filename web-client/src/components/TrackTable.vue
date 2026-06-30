<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="flex-1 overflow-auto">
      <table class="w-full border-collapse text-left sm:min-w-[400px]">
        <!-- Sticky header -->
        <thead class="sticky top-0 z-10 bg-stone-900 text-xs uppercase tracking-wide text-stone-500">
          <tr class="border-b border-stone-700">
            <th :class="lib.tableArtSize === 'large' ? 'w-28 py-1 pl-3 pr-2' : 'w-px py-1 pl-1.5 pr-1 sm:w-10 sm:pl-3 sm:pr-2'">
              <div class="flex items-center gap-0.5">
                <label
                  class="flex h-4 w-4 cursor-pointer items-center justify-center rounded border border-stone-600 hover:border-stone-400 sm:h-5 sm:w-5"
                  :class="allSelected ? 'bg-accent border-accent' : ''"
                  @click.stop="toggleSelectAll"
                >
                  <svg
                    v-if="allSelected"
                    width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3"
                  >
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                  <svg
                    v-else-if="someSelected"
                    width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"
                  >
                    <line x1="5" y1="12" x2="19" y2="12" />
                  </svg>
                </label>
              </div>
            </th>
            <th
              class="w-[35%] cursor-pointer py-2 pr-4 hover:text-stone-300 select-none"
              @click="lib.setTableSort('title')"
            >
              Title <span v-if="lib.tableSortCol === 'title'" class="ml-0.5 inline-block text-accent">{{ lib.tableSortDir === 'asc' ? '↑' : '↓' }}</span>
            </th>
            <th
              class="hidden w-[25%] cursor-pointer py-2 pr-4 hover:text-stone-300 select-none sm:table-cell"
              @click="lib.setTableSort('artist')"
            >
              Artist <span v-if="lib.tableSortCol === 'artist'" class="ml-0.5 inline-block text-accent">{{ lib.tableSortDir === 'asc' ? '↑' : '↓' }}</span>
            </th>
            <th
              class="hidden w-[25%] cursor-pointer py-2 pr-4 hover:text-stone-300 select-none md:table-cell"
              @click="lib.setTableSort('album')"
            >
              Album <span v-if="lib.tableSortCol === 'album'" class="ml-0.5 inline-block text-accent">{{ lib.tableSortDir === 'asc' ? '↑' : '↓' }}</span>
            </th>
            <th
              class="hidden w-16 cursor-pointer py-2 pr-4 hover:text-stone-300 select-none lg:table-cell"
              @click="lib.setTableSort('year')"
            >
              Year <span v-if="lib.tableSortCol === 'year'" class="ml-0.5 inline-block text-accent">{{ lib.tableSortDir === 'asc' ? '↑' : '↓' }}</span>
            </th>
            <th class="w-14 py-2 pr-3" />
            <th
              class="w-16 cursor-pointer py-2 pr-3 text-right hover:text-stone-300 select-none"
              @click="lib.setTableSort('duration')"
            >
              <span v-if="lib.tableSortCol === 'duration'" class="mr-0.5 inline-block text-accent">{{ lib.tableSortDir === 'asc' ? '↑' : '↓' }}</span>Time
            </th>
          </tr>
        </thead>

        <tbody>
          <template v-if="lib.loading">
            <tr>
              <td colspan="7" class="py-8 text-center text-sm text-stone-500">Loading library…</td>
            </tr>
          </template>
          <template v-else-if="lib.error">
            <tr>
              <td colspan="7" class="py-8 text-center text-sm">
                <span class="text-red-400">Failed to load library: {{ lib.error }}</span>
                <button class="ml-2 text-xs text-stone-400 underline hover:text-stone-200" @click="lib.loadLibrary()">Retry</button>
              </td>
            </tr>
          </template>
          <template v-else-if="rows.length === 0">
            <tr>
              <td colspan="7" class="py-8 text-center text-sm text-stone-500">No tracks found.</td>
            </tr>
          </template>
          <template v-else>
            <template v-for="row in rows" :key="rowKey(row)">
              <GroupHeader
                v-if="row.type === 'group'"
                :row="row"
                :active="row.key === nowPlayingGroupKey"
                @toggle="lib.toggleGroup(row.key)"
                @contextmenu="openGroupCtx($event, row.key)"
              />
              <TrackRow
                v-else
                :track="row.track"
                @play="lib.playTrack(row.track)"
                @contextmenu="openTrackCtx($event, row.track)"
              />
            </template>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Multi-select action bar -->
    <Transition
      enter-active-class="transition-transform duration-150"
      enter-from-class="translate-y-full"
      leave-active-class="transition-transform duration-150"
      leave-to-class="translate-y-full"
    >
      <div
        v-if="selectedCount > 1"
        class="flex flex-wrap items-center gap-3 border-t border-stone-700 bg-stone-800 px-4 py-2 text-sm"
        style="padding-bottom: max(env(safe-area-inset-bottom, 0px), 0.5rem);"
      >
        <span class="text-stone-300">{{ selectedCount }} selected</span>
        <button
          class="rounded-lg bg-accent px-3 py-1 text-xs font-medium text-white hover:bg-[var(--accent-hover)]"
          @click="pickerRef?.open()"
        >
          Add to playlist
        </button>
        <div class="relative">
          <button
            class="rounded-lg border border-stone-600 px-3 py-1 text-xs font-medium text-stone-200 hover:bg-stone-700"
            @click="showRatingPicker = !showRatingPicker"
          >
            Set rating…
          </button>
          <div
            v-if="showRatingPicker"
            class="absolute bottom-full left-0 z-20 mb-2 flex gap-1 rounded-lg border border-stone-600 bg-stone-800 p-2 shadow-xl"
          >
            <button
              v-for="r in 5"
              :key="r"
              class="flex h-7 w-7 items-center justify-center rounded text-sm hover:bg-stone-700"
              :class="r <= hoverRating ? 'text-yellow-400' : 'text-stone-500'"
              @mouseenter="hoverRating = r"
              @mouseleave="hoverRating = 0"
              @click="batchSetRating(r)"
            >★</button>
            <button
              class="flex h-7 w-7 items-center justify-center rounded text-xs text-stone-500 hover:bg-stone-700"
              @click="batchSetRating(null)"
              title="Clear rating"
            >✕</button>
          </div>
        </div>
        <button
          class="rounded-lg border border-stone-600 px-3 py-1 text-xs font-medium text-stone-200 hover:bg-stone-700"
          @click="batchAddToQueue"
        >
          Add to queue
        </button>
        <button class="ml-auto text-xs text-stone-500 hover:text-stone-300" @click="lib.clearSelection()">
          Clear
        </button>
      </div>
    </Transition>

    <!-- Track context menu -->
    <TrackContextMenu
      ref="trackCtxRef"
      :track-id="ctxTrack?.id ?? null"
      @play="ctxTrack && lib.playTrack(ctxTrack)"
      @edit="editingTrack = ctxTrack"
      @add-to-playlist="id => ctxTrack && playlistStore.addTracks(id, [ctxTrack!.id])"
      @remove-from-playlist="id => ctxTrack && playlistStore.removeTracks(id, [ctxTrack!.id])"
      @new-playlist="newPlaylistForTrack"
    />

    <MetadataEditorModal
      v-if="editingTrack"
      :track="editingTrack"
      @close="editingTrack = null"
      @saved="lib.loadLibrary()"
    />

    <!-- Playlist picker for multi-select -->
    <PlaylistPicker
      ref="pickerRef"
      :playlists="playlistStore.playlists"
      :track-count="selectedCount"
      @pick="addSelectedToPlaylist"
      @new-playlist="newPlaylistForSelected"
    />

    <PlaylistModal
      v-model="showNewPlaylist"
      title="New Playlist"
      confirm-label="Create"
      @confirm="confirmNewPlaylist"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from "vue";
import GroupHeader from "./GroupHeader.vue";
import TrackRow from "./TrackRow.vue";
import TrackContextMenu from "./TrackContextMenu.vue";
import MetadataEditorModal from "./MetadataEditorModal.vue";
import PlaylistPicker from "./PlaylistPicker.vue";
import PlaylistModal from "./PlaylistModal.vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import type { CatalogTrack, TableRow } from "../types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const rows = computed(() => lib.tableRows);
const selectedCount = computed(() => lib.selectedTrackIds.size);

// Select all / some
const allTracks = computed(() =>
  rows.value.filter((r): r is import("../types").TableTrackRow => r.type === "track").map((r) => r.track),
);
const allSelected = computed(
  () => allTracks.value.length > 0 && allTracks.value.every((t) => lib.selectedTrackIds.has(t.id)),
);
const someSelected = computed(
  () => lib.selectedTrackIds.size > 0 && !allSelected.value,
);

function toggleSelectAll(): void {
  if (allSelected.value) {
    lib.clearSelection();
  } else {
    lib.selectedTrackIds = new Set(allTracks.value.map((t) => t.id));
  }
}

// Rating batch
const showRatingPicker = ref(false);
const hoverRating = ref(0);

async function batchSetRating(rating: number | null): Promise<void> {
  showRatingPicker.value = false;
  hoverRating.value = 0;
  const ids = [...lib.selectedTrackIds];
  await lib.batchSetRating(ids, rating);
}

// Queue batch
function batchAddToQueue(): void {
  const ids = lib.selectedTrackIds;
  const tracks = lib.tracks.filter((t) => ids.has(t.id));
  lib.addMultipleToQueue(tracks);
  lib.clearSelection();
}

watch(() => lib.revealTrackId, (id) => {
  if (id === null) return;
  nextTick(() => {
    const el = document.querySelector(`[data-track-id="${id}"]`);
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
    lib.revealTrackId = null;
  });
});

const trackCtxRef = ref<InstanceType<typeof TrackContextMenu> | null>(null);
const pickerRef = ref<InstanceType<typeof PlaylistPicker> | null>(null);
const ctxTrack = ref<CatalogTrack | null>(null);

const showNewPlaylist = ref(false);
const editingTrack = ref<CatalogTrack | null>(null);
let pendingNewPlaylistTrackIds: number[] = [];

// Compute the group key directly from the playing track's metadata so it
// stays correct even when the group is collapsed (track rows are absent then).
const nowPlayingGroupKey = computed(() => {
  const t = lib.nowPlaying;
  if (!t || lib.groupBy === "none") return null;
  if (lib.groupBy === "album") {
    const album = (t.album ?? "Unknown Album").toLowerCase();
    const artist = (t.album_artist ?? t.artist ?? "Unknown Artist").toLowerCase();
    return `${album}|||${artist}`;
  }
  return (t.artist ?? t.album_artist ?? "Unknown Artist").toLowerCase();
});

function rowKey(row: TableRow): string {
  return row.type === "group" ? `g:${row.key}` : `t:${row.track.id}`;
}


function openTrackCtx(event: { clientX: number; clientY: number }, track: CatalogTrack): void {
  ctxTrack.value = track;
  trackCtxRef.value?.open(event);
}

function openGroupCtx(event: { clientX: number; clientY: number }, groupKey: string): void {
  const track = lib.tableRows
    .find((r): r is import("../types").TableTrackRow => r.type === "track" && r.groupKey === groupKey)
    ?.track ?? null;
  if (!track) return;
  ctxTrack.value = track;
  trackCtxRef.value?.open(event);
}


async function addSelectedToPlaylist(playlistId: number): Promise<void> {
  await playlistStore.addTracks(playlistId, [...lib.selectedTrackIds]);
  lib.clearSelection();
}

function newPlaylistForSelected(): void {
  pendingNewPlaylistTrackIds = [...lib.selectedTrackIds];
  showNewPlaylist.value = true;
}

function newPlaylistForTrack(): void {
  if (!ctxTrack.value) return;
  pendingNewPlaylistTrackIds = [ctxTrack.value.id];
  showNewPlaylist.value = true;
}

async function confirmNewPlaylist(name: string, icon: string | null): Promise<void> {
  await playlistStore.createPlaylist(name, icon ?? undefined);
  const newPlaylist = playlistStore.playlists.at(-1);
  if (newPlaylist && pendingNewPlaylistTrackIds.length > 0) {
    await playlistStore.addTracks(newPlaylist.id, pendingNewPlaylistTrackIds);
  }
  lib.clearSelection();
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div ref="scrollEl" class="relative flex-1 overflow-auto overscroll-contain" @scroll="onScroll">
      <!-- Pull-to-refresh indicator -->
      <div class="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-center" :style="indicatorStyle">
        <div class="mt-2 flex h-9 w-9 items-center justify-center rounded-full border border-stone-600 bg-stone-800 shadow-lg">
          <FeatherIcon name="refresh-cw" class="h-4 w-4 text-accent" :class="ptr.refreshing ? 'animate-spin' : ''" />
        </div>
      </div>

      <div :style="{ transform: contentTransform }">
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
            <!-- Skeleton loading state -->
            <template v-if="lib.loading">
              <tr v-for="i in 8" :key="i" class="border-b border-stone-800/50">
                <td class="py-1.5 pl-1.5 pr-1 sm:w-10 sm:pl-3 sm:pr-2">
                  <div class="skeleton h-4 w-4" />
                </td>
                <td class="max-w-0 py-1.5 pr-4">
                  <div class="skeleton h-4 w-3/4" />
                </td>
                <td class="hidden py-1.5 pr-4 sm:table-cell">
                  <div class="skeleton h-4 w-1/2" />
                </td>
                <td class="hidden py-1.5 pr-4 md:table-cell">
                  <div class="skeleton h-4 w-2/5" />
                </td>
                <td class="hidden py-1.5 pr-4 lg:table-cell">
                  <div class="skeleton h-4 w-8" />
                </td>
                <td class="py-1.5 pr-3"><div class="skeleton h-4 w-9" /></td>
                <td class="py-1.5 pr-3 text-right"><div class="skeleton ml-auto h-4 w-10" /></td>
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
            <!-- Virtualized rows -->
            <template v-else>
              <tr v-if="topPad > 0" aria-hidden="true"><td :colspan="7" :style="{ height: topPad + 'px' }" /></tr>
              <template v-for="row in visibleRows" :key="rowKey(row)">
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
                  @add-to-playlist="openPickerForTrack"
                />
              </template>
              <tr v-if="bottomPad > 0" aria-hidden="true"><td :colspan="7" :style="{ height: bottomPad + 'px' }" /></tr>
            </template>
          </tbody>
        </table>
      </div>
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

    <!-- Playlist picker for multi-select and swipe actions -->
    <PlaylistPicker
      ref="pickerRef"
      :playlists="playlistStore.playlists"
      :track-count="pickerTrackCount"
      @pick="onPickerPick"
      @new-playlist="onPickerNewPlaylist"
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
import { computed, ref, watch, nextTick, onMounted, onUnmounted } from "vue";
import GroupHeader from "./GroupHeader.vue";
import TrackRow from "./TrackRow.vue";
import TrackContextMenu from "./TrackContextMenu.vue";
import MetadataEditorModal from "./MetadataEditorModal.vue";
import PlaylistPicker from "./PlaylistPicker.vue";
import PlaylistModal from "./PlaylistModal.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { usePullToRefresh } from "../composables/usePullToRefresh";
import type { CatalogTrack, TableRow } from "../types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const rows = computed(() => lib.tableRows);
const selectedCount = computed(() => lib.selectedTrackIds.size);

const scrollEl = ref<HTMLElement | null>(null);
const ptr = usePullToRefresh(scrollEl, async () => {
  await Promise.all([lib.loadLibrary(), playlistStore.loadPlaylists()]);
});
const { indicatorStyle, contentTransform } = ptr;

// ── Virtualization (fixed estimated row heights) ─────────────────────────
const TRACK_H = 46; // track row incl. border
const GROUP_SMALL_H = 45;
const GROUP_LARGE_H = 101;
const WINDOW_BUFFER = 12;

const positions = computed<number[]>(() => {
  const out = new Array<number>(rows.value.length + 1);
  out[0] = 0;
  for (let i = 0; i < rows.value.length; i++) {
    const row = rows.value[i];
    const h =
      row.type === "track"
        ? TRACK_H
        : lib.tableArtSize === "large"
          ? GROUP_LARGE_H
          : GROUP_SMALL_H;
    out[i + 1] = out[i] + h;
  }
  return out;
});

const totalHeight = computed(() => positions.value[rows.value.length] ?? 0);

const startIdx = ref(0);
const endIdx = ref(0);
let scrollRaf = 0;

function updateWindow(): void {
  const el = scrollEl.value;
  if (!el) return;
  const st = el.scrollTop;
  const ch = el.clientHeight;
  const pos = positions.value;
  const n = rows.value.length;
  if (n === 0) {
    startIdx.value = 0;
    endIdx.value = 0;
    return;
  }
  // Binary search: first row whose end offset > st
  let lo = 0;
  let hi = n;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (pos[mid + 1] <= st) lo = mid + 1;
    else hi = mid;
  }
  const start = Math.max(0, lo - WINDOW_BUFFER);
  let end = Math.min(n, lo);
  const floor = st + ch;
  while (end < n && pos[end + 1] < floor) end++;
  end = Math.min(n, end + WINDOW_BUFFER);
  startIdx.value = start;
  endIdx.value = end;
}

function onScroll(): void {
  if (scrollRaf) return;
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0;
    updateWindow();
  });
}

const visibleRows = computed(() => rows.value.slice(startIdx.value, endIdx.value));
const topPad = computed(() => positions.value[startIdx.value] ?? 0);
const bottomPad = computed(() => totalHeight.value - (positions.value[endIdx.value] ?? totalHeight.value));

// Recompute the window when rows or art size change
watch([rows, () => lib.tableArtSize], () => {
  nextTick(updateWindow);
});

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  updateWindow();
  if (scrollEl.value) {
    resizeObserver = new ResizeObserver(() => updateWindow());
    resizeObserver.observe(scrollEl.value);
  }
});

onUnmounted(() => {
  resizeObserver?.disconnect();
  if (scrollRaf) cancelAnimationFrame(scrollRaf);
});

// ── Selection helpers ────────────────────────────────────────────────────
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
    // Virtualized: scroll the container to the row's estimated offset first
    const rowIdx = rows.value.findIndex((r) => r.type === "track" && r.track.id === id);
    if (rowIdx >= 0 && scrollEl.value) {
      const offset = positions.value[rowIdx] ?? 0;
      const el = scrollEl.value;
      const target = Math.max(0, offset - el.clientHeight / 2);
      el.scrollTop = target;
      updateWindow();
    }
    const el = document.querySelector(`[data-track-id="${id}"]`);
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
    lib.revealTrackId = null;
  });
});

// ── Context menus ────────────────────────────────────────────────────────
const trackCtxRef = ref<InstanceType<typeof TrackContextMenu> | null>(null);
const pickerRef = ref<InstanceType<typeof PlaylistPicker> | null>(null);
const ctxTrack = ref<CatalogTrack | null>(null);

const showNewPlaylist = ref(false);
const editingTrack = ref<CatalogTrack | null>(null);
let pendingNewPlaylistTrackIds: number[] = [];

// Playlist picker target: multi-select vs single track (swipe action)
const pickerTrackCount = ref(1);

function openPickerForTrack(track: CatalogTrack): void {
  pickerTargetId.value = track.id;
  pickerTrackCount.value = 1;
  pickerRef.value?.open();
}
const pickerTargetId = ref<number | null>(null);

function onPickerPick(playlistId: number): void {
  if (pickerTargetId.value !== null) {
    playlistStore.addTracks(playlistId, [pickerTargetId.value]);
    pickerTargetId.value = null;
    return;
  }
  playlistStore.addTracks(playlistId, [...lib.selectedTrackIds]);
  lib.clearSelection();
}

function onPickerNewPlaylist(): void {
  if (pickerTargetId.value !== null) {
    pendingNewPlaylistTrackIds = [pickerTargetId.value];
  } else {
    pendingNewPlaylistTrackIds = [...lib.selectedTrackIds];
  }
  showNewPlaylist.value = true;
}

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
  pickerTargetId.value = null;
}
</script>

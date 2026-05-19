<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="flex-1 overflow-auto">
      <table class="w-full min-w-[400px] border-collapse text-left">
        <!-- Sticky header -->
        <thead class="sticky top-0 z-10 bg-stone-900 text-xs uppercase tracking-wide text-stone-500">
          <tr class="border-b border-stone-700">
            <th class="w-8 py-2 pl-3 pr-2">#</th>
            <th
              class="cursor-pointer py-2 pr-4 hover:text-stone-300 select-none"
              @click="lib.setTableSort('title')"
            >
              Title <SortArrow col="title" />
            </th>
            <th
              class="hidden cursor-pointer py-2 pr-4 hover:text-stone-300 select-none sm:table-cell"
              @click="lib.setTableSort('artist')"
            >
              Artist <SortArrow col="artist" />
            </th>
            <th
              class="hidden cursor-pointer py-2 pr-4 hover:text-stone-300 select-none md:table-cell"
              @click="lib.setTableSort('album')"
            >
              Album <SortArrow col="album" />
            </th>
            <th
              class="hidden cursor-pointer py-2 pr-4 hover:text-stone-300 select-none lg:table-cell"
              @click="lib.setTableSort('year')"
            >
              Year <SortArrow col="year" />
            </th>
            <th
              class="cursor-pointer py-2 pr-3 text-right hover:text-stone-300 select-none"
              @click="lib.setTableSort('duration')"
            >
              <SortArrow col="duration" />Time
            </th>
          </tr>
        </thead>

        <tbody>
          <template v-if="lib.loading">
            <tr>
              <td colspan="6" class="py-8 text-center text-sm text-stone-500">Loading library…</td>
            </tr>
          </template>
          <template v-else-if="rows.length === 0">
            <tr>
              <td colspan="6" class="py-8 text-center text-sm text-stone-500">No tracks found.</td>
            </tr>
          </template>
          <template v-else>
            <template v-for="row in rows" :key="rowKey(row)">
              <GroupHeader
                v-if="row.type === 'group'"
                :row="row"
                @toggle="lib.toggleGroup(row.key)"
                @add-to-playlist="openGroupPlaylistPicker(row)"
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
        v-if="selectedCount > 0"
        class="flex items-center gap-3 border-t border-stone-700 bg-stone-800 px-4 py-2 text-sm"
        style="padding-bottom: max(env(safe-area-inset-bottom, 0px), 0.5rem);"
      >
        <span class="text-stone-300">{{ selectedCount }} selected</span>
        <button
          class="rounded-lg bg-accent px-3 py-1 text-xs font-medium text-white hover:bg-[var(--accent-hover)]"
          @click="pickerRef?.open()"
        >
          Add to playlist
        </button>
        <button class="ml-auto text-xs text-stone-500 hover:text-stone-300" @click="lib.clearSelection()">
          Clear
        </button>
      </div>
    </Transition>

    <!-- Track context menu -->
    <TrackContextMenu
      ref="trackCtxRef"
      :playlists="playlistStore.playlists"
      @play="ctxTrack && lib.playTrack(ctxTrack)"
      @add-to-playlist="id => ctxTrack && playlistStore.addTracks(id, [ctxTrack!.id])"
      @new-playlist="newPlaylistForTrack"
    />

    <!-- Playlist picker for multi-select -->
    <PlaylistPicker
      ref="pickerRef"
      :playlists="playlistStore.playlists"
      :track-count="selectedCount"
      @pick="addSelectedToPlaylist"
      @new-playlist="newPlaylistForSelected"
    />

    <!-- New playlist modal -->
    <Teleport to="body">
      <div
        v-if="showNewPlaylist"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
        @click.self="showNewPlaylist = false"
      >
        <div class="w-full max-w-xs rounded-xl border border-stone-700 bg-stone-900 p-6 shadow-2xl">
          <h3 class="mb-3 text-sm font-semibold text-stone-100">New Playlist</h3>
          <input
            ref="newPlaylistInputEl"
            v-model="newPlaylistName"
            type="text"
            placeholder="Playlist name"
            class="w-full rounded border border-stone-600 bg-stone-800 px-3 py-2 text-sm text-stone-200 placeholder-stone-500 focus:border-accent focus:outline-none"
            maxlength="80"
            @keydown.enter="confirmNewPlaylist"
            @keydown.esc="showNewPlaylist = false"
          />
          <div class="mt-4 flex justify-end gap-2">
            <button
              class="rounded px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-800"
              @click="showNewPlaylist = false"
            >
              Cancel
            </button>
            <button
              class="rounded bg-accent px-3 py-1.5 text-sm text-white hover:bg-[var(--accent-hover)]"
              @click="confirmNewPlaylist"
            >
              Create
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick } from "vue";
import GroupHeader from "./GroupHeader.vue";
import TrackRow from "./TrackRow.vue";
import TrackContextMenu from "./TrackContextMenu.vue";
import PlaylistPicker from "./PlaylistPicker.vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import type { CatalogTrack, TableRow, TableGroupRow } from "../types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const rows = computed(() => lib.tableRows);
const selectedCount = computed(() => lib.selectedTrackIds.size);

const trackCtxRef = ref<InstanceType<typeof TrackContextMenu> | null>(null);
const pickerRef = ref<InstanceType<typeof PlaylistPicker> | null>(null);
const ctxTrack = ref<CatalogTrack | null>(null);

const showNewPlaylist = ref(false);
const newPlaylistName = ref("");
const newPlaylistInputEl = ref<HTMLInputElement | null>(null);
let pendingNewPlaylistTrackIds: number[] = [];

function rowKey(row: TableRow): string {
  return row.type === "group" ? `g:${row.key}` : `t:${row.track.id}`;
}

// Sort arrow component inline
const SortArrow = {
  props: ["col"],
  setup(props: { col: string }) {
    return () => {
      const active = lib.tableSortCol === props.col;
      if (!active) return null;
      return active
        ? {
            type: "span",
            props: { class: "ml-0.5 inline-block text-accent" },
            children: lib.tableSortDir === "asc" ? "↑" : "↓",
          }
        : null;
    };
  },
};

function openTrackCtx(event: MouseEvent, track: CatalogTrack): void {
  ctxTrack.value = track;
  trackCtxRef.value?.open(event);
}

function openGroupPlaylistPicker(row: TableGroupRow): void {
  const trackIds = lib.tableRows
    .filter((r) => r.type === "track" && r.groupKey === row.key)
    .map((r) => (r as { type: "track"; track: CatalogTrack; groupKey: string }).track.id);
  pendingNewPlaylistTrackIds = trackIds;
  pickerRef.value?.open();
}

async function addSelectedToPlaylist(playlistId: number): Promise<void> {
  await playlistStore.addTracks(playlistId, [...lib.selectedTrackIds]);
  lib.clearSelection();
}

function newPlaylistForSelected(): void {
  pendingNewPlaylistTrackIds = [...lib.selectedTrackIds];
  showNewPlaylist.value = true;
  nextTick(() => newPlaylistInputEl.value?.focus());
}

function newPlaylistForTrack(): void {
  if (!ctxTrack.value) return;
  pendingNewPlaylistTrackIds = [ctxTrack.value.id];
  showNewPlaylist.value = true;
  nextTick(() => newPlaylistInputEl.value?.focus());
}

async function confirmNewPlaylist(): Promise<void> {
  const name = newPlaylistName.value.trim();
  showNewPlaylist.value = false;
  newPlaylistName.value = "";
  if (!name) return;
  await playlistStore.createPlaylist(name);
  const newPlaylist = playlistStore.playlists.at(-1);
  if (newPlaylist && pendingNewPlaylistTrackIds.length > 0) {
    await playlistStore.addTracks(newPlaylist.id, pendingNewPlaylistTrackIds);
  }
  lib.clearSelection();
}
</script>

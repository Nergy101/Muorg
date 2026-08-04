<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <!-- Header -->
    <div class="flex shrink-0 items-center gap-3 border-b border-stone-800 px-4 py-3">
      <div class="min-w-0 flex-1">
        <h2 class="truncate text-base font-semibold text-stone-100">{{ playlistName }}</h2>
        <p class="text-xs text-stone-500">
          {{ orderedTracks.length }} track{{ orderedTracks.length !== 1 ? 's' : '' }}
          <span v-if="playlist?.smart_rules" class="ml-1 text-accent">⚡ smart</span>
        </p>
      </div>
      <button
        type="button"
        class="flex h-10 items-center gap-1.5 rounded border border-stone-600 px-3 text-xs text-stone-300 hover:bg-stone-800 active:bg-stone-700"
        @click="clearFilter"
      >
        <FeatherIcon name="x" class="h-3.5 w-3.5" />
        Back to library
      </button>
    </div>

    <!-- Tracks (playlist order) -->
    <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain">
      <div v-if="lib.loading" class="p-4 text-sm text-stone-500">Loading library…</div>
      <div v-else-if="orderedTracks.length === 0" class="p-4 text-sm text-stone-500">
        This playlist is empty.
      </div>
      <div
        v-for="(t, i) in orderedTracks"
        :key="t.id"
        :data-track-id="t.id"
        class="group flex items-center gap-2 border-b border-stone-800/50 px-2 py-1.5"
        :class="[dragOver === i ? 'bg-stone-800' : '', isPlaying(t) ? 'row-playing' : '']"
        draggable="true"
        @dragstart="onDragStart(i)"
        @dragover.prevent="onDragOver(i)"
        @drop.prevent="onDrop(i)"
        @dragend="onDragEnd"
      >
        <!-- Drag handle (desktop) -->
        <span class="hidden shrink-0 cursor-grab text-stone-600 active:cursor-grabbing md:inline-flex" title="Drag to reorder">
          <FeatherIcon name="grip-vertical" class="h-4 w-4" />
        </span>

        <!-- Index -->
        <span class="w-6 shrink-0 text-right text-xs tabular-nums text-stone-600">{{ i + 1 }}</span>

        <!-- Track info -->
        <button
          type="button"
          class="flex min-w-0 flex-1 items-center gap-2.5 rounded py-0.5 text-left"
          @click="lib.playTrack(t)"
          @contextmenu.prevent="openCtx($event, t)"
        >
          <CoverThumb :track="t" class="h-10 w-10 shrink-0 rounded" />
          <span class="min-w-0 flex-1">
            <span class="block truncate text-sm text-stone-200">{{ t.title ?? '—' }}</span>
            <span class="block truncate text-xs text-stone-500">{{ t.artist ?? t.album_artist ?? '—' }}</span>
          </span>
          <span class="shrink-0 text-xs text-stone-500 tabular-nums">{{ durationLabel(t) }}</span>
        </button>

        <!-- Mobile reorder: up/down -->
        <span class="flex shrink-0 flex-col md:hidden">
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded text-stone-500 active:bg-stone-700"
            :disabled="i === 0"
            aria-label="Move up"
            @click="move(i, -1)"
          >
            <FeatherIcon name="chevron-up" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded text-stone-500 active:bg-stone-700"
            :disabled="i === orderedTracks.length - 1"
            aria-label="Move down"
            @click="move(i, 1)"
          >
            <FeatherIcon name="chevron-down" class="h-4 w-4" />
          </button>
        </span>

        <!-- Remove -->
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-300"
          :aria-label="`Remove ${t.title ?? 'track'} from playlist`"
          @click="removeTrack(t)"
        >
          <FeatherIcon name="x" class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Track context menu -->
    <TrackContextMenu
      ref="ctxRef"
      :track-id="ctxTrack?.id ?? null"
      @play="ctxTrack && lib.playTrack(ctxTrack)"
      @add-to-queue="ctxTrack && lib.addToQueue(ctxTrack)"
      @play-next="ctxTrack && lib.playNextTrack(ctxTrack)"
      @add-to-playlist="id => ctxTrack && playlistStore.addTracks(id, [ctxTrack!.id])"
      @remove-from-playlist="id => ctxTrack && playlistStore.removeTracks(id, [ctxTrack!.id])"
      @new-playlist="newPlaylistForTrack"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import CoverThumb from "./CoverThumb.vue";
import TrackContextMenu from "./TrackContextMenu.vue";
import type { CatalogTrack } from "../types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const playlist = computed(() =>
  playlistStore.playlists.find((p) => p.id === playlistStore.activePlaylistId),
);
const playlistName = computed(() => playlist.value?.name ?? "Playlist");

// Playlist-ordered tracks (library store preserves playlist order via playlistTrackIds)
const orderedTracks = computed(() => lib.filteredTracks);

function clearFilter(): void {
  playlistStore.selectPlaylist(null);
}

function isPlaying(t: CatalogTrack): boolean {
  return lib.nowPlaying?.id === t.id;
}

function durationLabel(t: CatalogTrack): string {
  return t.duration_secs != null ? formatDuration(t.duration_secs) : "";
}

// ── Reorder (optimistic; desktop drag, mobile up/down) ───────────────────
let dragFrom = -1;
const dragOver = ref<number | null>(null);

function applyOrder(newIds: number[]): void {
  const pid = playlistStore.activePlaylistId;
  if (pid === null) return;
  playlistStore.reorderTracks(pid, newIds).catch(() => {
    // store already reverted the local order on failure
  });
}

function move(i: number, dir: -1 | 1): void {
  const j = i + dir;
  if (j < 0 || j >= orderedTracks.value.length) return;
  const ids = [...(lib.playlistTrackIds ?? orderedTracks.value.map((t) => t.id))];
  const [moved] = ids.splice(i, 1);
  ids.splice(j, 0, moved);
  applyOrder(ids);
}

function onDragStart(i: number): void {
  dragFrom = i;
  dragOver.value = null;
}

function onDragOver(i: number): void {
  dragOver.value = i;
}

function onDrop(i: number): void {
  dragOver.value = null;
  if (dragFrom < 0 || dragFrom === i) return;
  const ids = [...(lib.playlistTrackIds ?? orderedTracks.value.map((t) => t.id))];
  const [moved] = ids.splice(dragFrom, 1);
  ids.splice(i, 0, moved);
  dragFrom = -1;
  applyOrder(ids);
}

function onDragEnd(): void {
  dragFrom = -1;
  dragOver.value = null;
}

function removeTrack(t: CatalogTrack): void {
  const pid = playlistStore.activePlaylistId;
  if (pid === null) return;
  playlistStore.removeTracks(pid, [t.id]);
}

// Context menu
const ctxRef = ref<InstanceType<typeof TrackContextMenu> | null>(null);
const ctxTrack = ref<CatalogTrack | null>(null);

function openCtx(event: { clientX: number; clientY: number }, track: CatalogTrack): void {
  ctxTrack.value = track;
  ctxRef.value?.open(event);
}

function newPlaylistForTrack(): void {
  // Playlist view is already inside a playlist — no new-playlist flow needed
}
</script>

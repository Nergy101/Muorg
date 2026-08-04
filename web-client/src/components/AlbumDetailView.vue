<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <!-- Album header -->
    <div class="flex shrink-0 items-center gap-4 border-b border-stone-800 px-4 py-4 md:gap-5 md:px-5">
      <!-- Cover art -->
      <div class="relative h-36 w-36 shrink-0 overflow-hidden rounded-lg bg-stone-800 md:h-28 md:w-28 md:rounded">
        <img
          v-if="coverUrl"
          :src="coverUrl"
          :alt="item.album"
          class="h-full w-full object-cover"
        />
        <div v-else class="flex h-full w-full items-center justify-center text-stone-600">
          <span class="text-3xl">♪</span>
        </div>
      </div>

      <!-- Info -->
      <div class="min-w-0 flex-1">
        <div class="truncate text-lg font-semibold text-stone-100 md:text-xl">{{ item.album }}</div>
        <div class="mt-1 truncate text-sm text-stone-300">{{ item.albumArtist }}</div>
        <div v-if="item.year" class="mt-1 text-xs text-stone-500">{{ item.year }}</div>
        <div class="mt-2 text-xs text-stone-500">{{ tracks.length }} tracks · {{ totalDuration }}</div>
      </div>

      <!-- Play button: FAB on mobile, text button on desktop -->
      <button
        type="button"
        class="ml-auto flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-accent text-white shadow-lg active:bg-[var(--accent-hover)] md:h-auto md:w-auto md:gap-2 md:rounded-lg md:px-4 md:py-2 md:text-sm md:font-medium md:shadow-none"
        @click="lib.playAlbum(item)"
      >
        <FeatherIcon name="play" class="h-6 w-6 md:h-4 md:w-4" />
        <span class="hidden md:inline">Play</span>
      </button>
    </div>

    <!-- Track list -->
    <div class="relative min-h-0 flex-1 overflow-hidden">
      <!-- Compact sticky bar (mobile, after scrolling past the hero) -->
      <div
        v-if="compactBar"
        class="absolute inset-x-0 top-0 z-20 flex items-center gap-2 border-b border-stone-800 bg-stone-900/95 px-3 py-1.5 backdrop-blur sm:hidden"
      >
        <div class="h-8 w-8 shrink-0 overflow-hidden rounded bg-stone-800">
          <img v-if="coverUrl" :src="coverUrl" :alt="item.album" class="h-full w-full object-cover" />
          <div v-else class="flex h-full w-full items-center justify-center text-stone-600">♪</div>
        </div>
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm font-medium text-stone-100">{{ item.album }}</div>
          <div class="truncate text-xs text-stone-500">{{ item.albumArtist }}</div>
        </div>
        <button
          type="button"
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent text-white active:bg-[var(--accent-hover)]"
          aria-label="Play album"
          @click="lib.playAlbum(item)"
        >
          <FeatherIcon name="play" class="h-5 w-5" />
        </button>
      </div>
      <div ref="listScrollEl" class="h-full overflow-auto" @scroll="onListScroll">
      <table class="w-full border-collapse text-left">
        <thead class="sticky top-0 z-10 bg-stone-900 text-xs uppercase tracking-wide text-stone-500">
          <tr class="border-b border-stone-700">
            <th class="w-10 py-2 pl-4 pr-2 text-right">#</th>
            <th class="py-2 pr-4">Title</th>
            <th class="hidden py-2 pr-4 sm:table-cell">Artist</th>
            <th class="w-14 py-2 pr-3" />
            <th class="w-16 py-2 pr-4 text-right">Time</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="track in tracks"
            :key="track.id"
            class="group cursor-pointer select-none border-b border-stone-800/60 hover:bg-stone-800/60"
            :class="lib.nowPlaying?.id === track.id ? 'row-playing' : ''"
            @click="lib.playTrack(track)"
            @contextmenu.prevent="openCtxMenu($event, track)"
            @touchstart.passive="(e) => onTouchStart(e, track)"
            @touchmove.passive="onTouchMove"
            @touchend="onTouchEnd"
          >
            <td class="py-2.5 pl-4 pr-2 text-right text-xs text-stone-500 md:py-2">
              {{ track.track_number ?? '—' }}
            </td>
            <td class="max-w-0 py-2.5 pr-4 md:py-2">
              <div class="flex items-center gap-2">
                <img
                  v-if="coverThumb(track)"
                  :src="coverThumb(track)"
                  :alt="track.title ?? ''"
                  class="h-9 w-9 shrink-0 rounded object-cover sm:hidden"
                />
                <div class="min-w-0">
                  <div class="truncate text-sm text-stone-200">{{ track.title ?? 'Unknown' }}</div>
                  <div class="truncate text-xs text-stone-400 sm:hidden">{{ track.artist ?? track.album_artist ?? '—' }}</div>
                </div>
              </div>
            </td>
            <td class="hidden max-w-0 py-2 pr-4 sm:table-cell">
              <div class="truncate text-xs text-stone-400">{{ track.artist ?? track.album_artist ?? '—' }}</div>
            </td>
            <td class="py-2 pr-3 whitespace-nowrap">
              <span
                class="rounded border px-1 py-0.5 font-mono text-[10px] uppercase leading-none tracking-wider"
                :class="track.format === 'flac' ? 'border-accent/70 text-accent' : 'border-stone-600 text-stone-500'"
              >{{ track.format }}</span>
            </td>
            <td class="py-2 pr-4 text-right text-xs text-stone-500 whitespace-nowrap">
              {{ track.duration_secs != null ? formatDuration(track.duration_secs) : '—' }}
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>
  </div>

  <!-- Track context menu -->
  <TrackContextMenu
    ref="trackCtxRef"
    :track-id="ctxTrack?.id ?? null"
    @play="ctxTrack && lib.playTrack(ctxTrack)"
    @add-to-playlist="id => ctxTrack && playlistStore.addTracks(id, [ctxTrack!.id])"
    @remove-from-playlist="id => ctxTrack && playlistStore.removeTracks(id, [ctxTrack!.id])"
    @new-playlist="newPlaylistForTrack"
  />

  <PlaylistModal
    v-model="showNewPlaylist"
    title="New Playlist"
    confirm-label="Create"
    @confirm="confirmNewPlaylist"
  />
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { formatDuration } from "../stores/library";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import TrackContextMenu from "./TrackContextMenu.vue";
import PlaylistModal from "./PlaylistModal.vue";
import type { AlbumGridItem, CatalogTrack } from "../types";

const props = defineProps<{ item: AlbumGridItem }>();

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const listScrollEl = ref<HTMLElement | null>(null);
const compactBar = ref(false);

function onListScroll(): void {
  compactBar.value = (listScrollEl.value?.scrollTop ?? 0) > 120;
}

const coverUrl = computed(() => {
  if (!props.item.hasCover || props.item.coverTrackId === null) return null;
  return lib.coverCache.get(props.item.coverTrackId) ?? null;
});

function coverThumb(track: CatalogTrack): string | undefined {
  if (!track.has_cover) return undefined;
  lib.requestCover(track.id);
  return lib.coverCache.get(track.id) ?? undefined;
}

const tracks = computed(() => {
  const ids = new Set(props.item.trackIds);
  return lib.tracks
    .filter((t) => ids.has(t.id))
    .sort((a, b) => {
      const discA = a.disc_number ?? 1;
      const discB = b.disc_number ?? 1;
      if (discA !== discB) return discA - discB;
      return (a.track_number ?? 0) - (b.track_number ?? 0);
    });
});

const totalDuration = computed(() => {
  const total = tracks.value.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
  return formatDuration(Math.round(total));
});

// Context menu
const trackCtxRef = ref<InstanceType<typeof TrackContextMenu> | null>(null);
const ctxTrack = ref<CatalogTrack | null>(null);

function openCtxMenu(event: { clientX: number; clientY: number }, track: CatalogTrack): void {
  ctxTrack.value = track;
  trackCtxRef.value?.open(event);
}

const LONG_PRESS_MS = 500;
let _lpTimer: ReturnType<typeof setTimeout> | null = null;
let _lpStart = { x: 0, y: 0 };
let _lpFired = false;

function onTouchStart(e: TouchEvent, track: CatalogTrack): void {
  _lpFired = false;
  const t = e.touches[0];
  _lpStart = { x: t.clientX, y: t.clientY };
  _lpTimer = setTimeout(() => {
    _lpTimer = null;
    _lpFired = true;
    navigator.vibrate?.(20);
    openCtxMenu({ clientX: t.clientX, clientY: t.clientY }, track);
  }, LONG_PRESS_MS);
}

function onTouchMove(e: TouchEvent): void {
  if (!_lpTimer) return;
  const t = e.touches[0];
  if (Math.abs(t.clientX - _lpStart.x) > 8 || Math.abs(t.clientY - _lpStart.y) > 8) {
    clearTimeout(_lpTimer);
    _lpTimer = null;
  }
}

function onTouchEnd(e: TouchEvent): void {
  if (_lpTimer) { clearTimeout(_lpTimer); _lpTimer = null; }
  if (_lpFired) { e.preventDefault(); _lpFired = false; }
}

// New playlist modal
const showNewPlaylist = ref(false);

function newPlaylistForTrack(): void {
  showNewPlaylist.value = true;
}

async function confirmNewPlaylist(name: string, icon: string | null): Promise<void> {
  if (!ctxTrack.value) return;
  await playlistStore.createPlaylist(name, icon ?? undefined);
  const newPl = playlistStore.playlists.at(-1);
  if (newPl) await playlistStore.addTracks(newPl.id, [ctxTrack.value.id]);
}
</script>

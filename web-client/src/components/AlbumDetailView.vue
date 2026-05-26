<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <!-- Album header -->
    <div class="flex shrink-0 items-center gap-5 border-b border-stone-800 px-5 py-4">
      <!-- Cover art -->
      <div class="relative h-28 w-28 shrink-0 overflow-hidden rounded bg-stone-800">
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
      <div class="min-w-0">
        <div class="truncate text-lg font-semibold text-stone-100">{{ item.album }}</div>
        <div class="mt-1 truncate text-sm text-stone-300">{{ item.albumArtist }}</div>
        <div v-if="item.year" class="mt-1 text-xs text-stone-500">{{ item.year }}</div>
        <div class="mt-2 text-xs text-stone-500">{{ tracks.length }} tracks · {{ totalDuration }}</div>
      </div>

      <!-- Play button -->
      <button
        type="button"
        class="ml-auto flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]"
        @click="lib.playAlbum(item)"
      >
        <FeatherIcon name="play" class="h-4 w-4" />
        Play
      </button>
    </div>

    <!-- Track list -->
    <div class="flex-1 overflow-auto">
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
            <td class="py-2 pl-4 pr-2 text-right text-xs text-stone-500">
              {{ track.track_number ?? '—' }}
            </td>
            <td class="max-w-0 py-2 pr-4">
              <div class="truncate text-sm text-stone-200">{{ track.title ?? 'Unknown' }}</div>
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

const coverUrl = computed(() => {
  if (!props.item.hasCover || props.item.coverTrackId === null) return null;
  return lib.coverCache.get(props.item.coverTrackId) ?? null;
});

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

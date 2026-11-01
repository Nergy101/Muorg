<template>
  <BottomSheet :open="open" @close="emit('close')">
    <template v-if="playlist">
      <!-- ─── header ──────────────────────────────────────────────────── -->
      <div class="flex items-center gap-3 px-6 pb-3">
        <div class="relative flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded bg-surface-variant">
          <div v-if="coverUrls.length > 1" class="grid h-full w-full grid-cols-2 grid-rows-2">
            <template v-for="(url, i) in coverUrls" :key="i">
              <img
                v-if="url"
                :src="url"
                :alt="playlist.name"
                class="h-full w-full object-cover"
                decoding="async"
              />
              <div v-else class="h-full w-full bg-surface-variant/50" />
            </template>
          </div>
          <img
            v-else-if="coverUrls.length === 1 && coverUrls[0]"
            :src="coverUrls[0]"
            :alt="playlist.name"
            class="absolute inset-0 h-full w-full object-cover"
            decoding="async"
          />
          <MageIcon v-else name="music" class="h-4 w-4 text-on-surface-variant/60" />
        </div>
        <div class="min-w-0 flex-1">
          <MarqueeText :text="playlist.name" class="text-title-md text-on-surface" />
          <p class="text-body-sm text-on-surface-variant">
            {{ playlist.track_count }} track{{ playlist.track_count === 1 ? "" : "s" }}
          </p>
        </div>
        <!-- Quick play / shuffle next to the name -->
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          aria-label="Play playlist"
          @click="emit('play'); emit('close')"
        >
          <MageIcon name="play" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          aria-label="Shuffle play playlist"
          @click="emit('shuffle'); emit('close')"
        >
          <MageIcon name="exchange" class="h-5 w-5" />
        </button>
      </div>

      <div class="my-1 border-t border-outline/30" />

      <button
        type="button"
        :class="ROW"
        :disabled="downloading"
        @click="downloaded ? emit('undownload') : emit('download'); emit('close')"
      >
        <span class="relative flex h-5 w-5 shrink-0 items-center justify-center">
          <svg v-if="downloading" viewBox="0 0 20 20" class="absolute inset-0 h-5 w-5 -rotate-90">
            <circle cx="10" cy="10" r="8.5" fill="none" stroke="currentColor" stroke-opacity="0.25" stroke-width="2.5" />
            <circle
              cx="10" cy="10" r="8.5" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"
              :stroke-dasharray="RING_CIRC" :stroke-dashoffset="ringOffset"
            />
          </svg>
          <MageIcon
            name="download"
            class="h-5 w-5"
            :class="downloadClass"
          />
        </span>
        <span>{{ downloaded ? "Remove offline download" : "Download for offline" }}</span>
      </button>
      <button type="button" :class="ROW" @click="emit('pin'); emit('close')">
        <MageIcon name="pin" class="h-5 w-5 shrink-0" :class="pinned ? 'text-primary' : 'text-on-surface-variant'" />
        <span>{{ pinned ? "Unpin playlist" : "Pin playlist" }}</span>
      </button>
      <button v-if="!isFavorites" type="button" :class="ROW" @click="emit('edit'); emit('close')">
        <MageIcon name="edit" class="h-5 w-5 shrink-0 text-on-surface-variant" />
        <span>Edit playlist</span>
      </button>
      <button type="button" :class="ROW" @click="emit('export'); emit('close')">
        <MageIcon name="export-list" class="h-5 w-5 shrink-0 text-on-surface-variant" />
        <span>Export as M3U</span>
      </button>
      <button
        v-if="!isFavorites"
        type="button"
        :class="[ROW, 'text-error']"
        @click="emit('delete'); emit('close')"
      >
        <MageIcon name="trash" class="h-5 w-5 shrink-0" />
        <span>Delete playlist</span>
      </button>

      <div class="h-4" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import MarqueeText from "./MarqueeText.vue";
import { usePlaylistStore } from "../stores/playlists";
import { useOfflineStore } from "../stores/offline";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import type { Playlist } from "../types";

const ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";

const props = defineProps<{ open: boolean; playlist: Playlist | null }>();
const emit = defineEmits<{
  close: [];
  play: [];
  shuffle: [];
  pin: [];
  edit: [];
  export: [];
  download: [];
  undownload: [];
  delete: [];
}>();

const playlistStore = usePlaylistStore();
const offline = useOfflineStore();
const lib = useLibraryStore();

// ── Header cover collage: the first distinct album covers in track order,
//    same 2×2 grid the playlist card shows. Loaded when the sheet opens. ──
const orderedIds = ref<number[]>([]);

const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

const coverUrls = computed<(string | null)[]>(() => {
  const seen = new Set<string>();
  const urls: (string | null)[] = [];
  for (const id of orderedIds.value) {
    const t = trackById.value.get(id);
    if (!t) continue;
    const key = albumKeyFor(t);
    if (seen.has(key)) continue;
    seen.add(key);
    const url = lib.coverCache.get(id);
    if (!url) continue;
    urls.push(url);
    if (urls.length === 4) break;
  }
  return urls;
});

watch(
  () => [props.open, props.playlist?.id] as const,
  async ([open, id]) => {
    if (!open || id == null) return;
    orderedIds.value = await playlistStore.loadTrackOrderForPlaylist(id);
    for (const trackId of orderedIds.value) {
      if (!lib.coverCache.has(trackId)) lib.requestCover(trackId);
    }
  },
  { immediate: true },
);

const pinned = computed(() => (props.playlist ? playlistStore.isPinned(props.playlist.id) : false));
const downloading = computed(() =>
  props.playlist ? offline.activePlaylistId === props.playlist.id : false,
);
const downloaded = computed(() =>
  props.playlist ? offline.isPlaylistDownloaded(props.playlist.id) : false,
);
const downloadClass = computed(() =>
  downloaded.value ? "text-primary" : "text-on-surface-variant",
);

// Circular progress ring around the download icon while this playlist downloads.
const RING_CIRC = 2 * Math.PI * 8.5;
const ringOffset = computed(() => {
  const { done, total } = offline.downloadProgress;
  const frac = total > 0 ? done / total : 0;
  return RING_CIRC * (1 - frac);
});
/** The server-owned Favorites playlist cannot be renamed or deleted (Q21). */
const isFavorites = computed(() =>
  props.playlist ? playlistStore.isFavoritesPlaylist(props.playlist) : false,
);
</script>

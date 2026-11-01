<template>
  <BottomSheet :open="open" @close="emit('close')">
    <template v-if="item">
      <!-- ─── header ──────────────────────────────────────────────────── -->
      <div class="flex items-center gap-3 px-6 pb-3">
        <div class="h-11 w-11 shrink-0 overflow-hidden rounded bg-surface-variant">
          <img
            v-if="coverUrl"
            :src="coverUrl"
            :alt="item.album"
            class="h-full w-full object-cover [-webkit-touch-callout:none]"
            draggable="false"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <MageIcon name="music" class="h-4 w-4 text-on-surface-variant/60" />
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <MarqueeText :text="item.album" class="text-title-md text-on-surface" />
          <p class="truncate text-body-sm text-on-surface-variant">{{ item.albumArtist }}</p>
          <p class="text-body-sm text-on-surface-variant">{{ metaLine }}</p>
        </div>
      </div>

      <div class="my-1 border-t border-outline/30" />

      <button type="button" :class="ROW" @click="emit('play'); emit('close')">
        <MageIcon name="play" class="h-5 w-5 shrink-0 text-primary" />
        <span>Play album</span>
      </button>

      <button type="button" :class="ROW" @click="emit('add-to-playlist'); emit('close')">
        <MageIcon name="playlist-add" class="h-5 w-5 shrink-0 text-on-surface-variant" />
        <span>Add to playlist</span>
      </button>

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
            :class="downloaded ? 'text-primary' : 'text-on-surface-variant'"
          />
        </span>
        <span>{{ downloaded ? "Remove offline download" : "Download for offline" }}</span>
      </button>

      <div class="my-1 border-t border-outline/30" />

      <button
        v-if="item.albumArtist"
        type="button"
        class="flex min-h-14 w-full items-center gap-4 px-6 py-2 text-left"
        @click="emit('view-artist'); emit('close')"
      >
        <MageIcon name="user" class="h-5 w-5 shrink-0 text-on-surface-variant" />
        <span class="min-w-0 flex-1">
          <span class="block text-body-lg text-on-surface">View artist</span>
          <span class="block truncate text-body-sm text-on-surface-variant">{{ item.albumArtist }}</span>
        </span>
      </button>

      <button
        type="button"
        class="flex min-h-14 w-full items-center gap-4 px-6 py-2 text-left"
        @click="emit('view-album'); emit('close')"
      >
        <MageIcon name="music" class="h-5 w-5 shrink-0 text-on-surface-variant" />
        <span class="min-w-0 flex-1">
          <span class="block text-body-lg text-on-surface">View album</span>
          <span class="block truncate text-body-sm text-on-surface-variant">{{ item.album }}</span>
        </span>
      </button>

      <div class="h-4" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import MarqueeText from "./MarqueeText.vue";
import { useLibraryStore } from "../stores/library";
import { useOfflineStore } from "../stores/offline";
import type { AlbumGridItem } from "../types";

const ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";

const props = defineProps<{
  open: boolean;
  item: AlbumGridItem | null;
}>();

const emit = defineEmits<{
  close: [];
  play: [];
  "add-to-playlist": [];
  download: [];
  undownload: [];
  "view-artist": [];
  "view-album": [];
}>();

const lib = useLibraryStore();
const offline = useOfflineStore();

const downloading = computed(() =>
  props.item ? offline.activeAlbumKey === props.item.key : false,
);
const downloaded = computed(() =>
  props.item ? offline.isAlbumDownloaded(props.item.key) : false,
);

// Circular progress ring around the download icon while this album downloads.
const RING_CIRC = 2 * Math.PI * 8.5;
const ringOffset = computed(() => {
  const { done, total } = offline.downloadProgress;
  const frac = total > 0 ? done / total : 0;
  return RING_CIRC * (1 - frac);
});

const coverUrl = computed(() => {
  const id = props.item?.coverTrackId;
  if (!props.item?.hasCover || id == null) return null;
  lib.requestCover(id);
  return lib.coverCache.get(id) ?? null;
});

const metaLine = computed(() => {
  const parts: string[] = [];
  if (props.item?.year) parts.push(String(props.item.year));
  const n = props.item?.trackCount ?? 0;
  parts.push(n === 1 ? "1 track" : `${n} tracks`);
  return parts.join(" · ");
});
</script>

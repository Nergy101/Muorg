<template>
  <BottomSheet :open="open" @close="emit('close')">
    <template v-if="playlist">
      <!-- ─── header ──────────────────────────────────────────────────── -->
      <div class="flex items-center gap-3 px-6 pb-3">
        <div class="flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded bg-surface-variant">
          <MageIcon name="music" class="h-4 w-4 text-on-surface-variant/60" />
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

      <button type="button" :class="ROW" @click="emit('play'); emit('close')">
        <MageIcon name="play" class="h-5 w-5 shrink-0 text-primary" />
        <span>Play playlist</span>
      </button>
      <button type="button" :class="ROW" @click="emit('shuffle'); emit('close')">
        <MageIcon name="exchange" class="h-5 w-5 shrink-0 text-on-surface-variant" />
        <span>Shuffle play</span>
      </button>

      <div class="my-1 border-t border-outline/30" />

      <button
        type="button"
        :class="ROW"
        :disabled="downloading"
        @click="emit('download'); emit('close')"
      >
        <MageIcon
          name="download"
          class="h-5 w-5 shrink-0"
          :class="downloadClass"
        />
        <span>{{ downloaded ? "Downloaded for offline" : "Download for offline" }}</span>
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
import { computed } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import MarqueeText from "./MarqueeText.vue";
import { usePlaylistStore } from "../stores/playlists";
import { useOfflineStore } from "../stores/offline";
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
  delete: [];
}>();

const playlistStore = usePlaylistStore();
const offline = useOfflineStore();

const pinned = computed(() => (props.playlist ? playlistStore.isPinned(props.playlist.id) : false));
const downloading = computed(() =>
  props.playlist ? offline.activePlaylistId === props.playlist.id : false,
);
const downloaded = computed(() =>
  props.playlist ? offline.isPlaylistDownloaded(props.playlist.id) : false,
);
const downloadClass = computed(() =>
  `${downloaded.value ? "text-primary" : "text-on-surface-variant"} ${downloading.value ? "animate-spin" : ""}`,
);
/** The server-owned Favorites playlist cannot be renamed or deleted (Q21). */
const isFavorites = computed(() =>
  props.playlist ? playlistStore.isFavoritesPlaylist(props.playlist) : false,
);
</script>

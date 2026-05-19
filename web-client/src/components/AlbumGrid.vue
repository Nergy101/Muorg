<template>
  <div class="flex-1 overflow-y-auto p-4">
    <div
      v-if="lib.loading"
      class="flex h-32 items-center justify-center text-stone-500 text-sm"
    >
      Loading library…
    </div>

    <div
      v-else-if="items.length === 0"
      class="flex h-32 items-center justify-center text-stone-500 text-sm"
    >
      No albums found.
    </div>

    <div
      v-else
      class="grid gap-3"
      style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))"
    >
      <AlbumCard
        v-for="item in items"
        :key="item.key"
        :item="item"
        :is-playing="isAlbumPlaying(item)"
        @play="playAlbum(item)"
        @contextmenu="openAlbumMenu($event, item)"
      />
    </div>

    <!-- Album context menu (simple: add first track to playlist, etc.) -->
    <Teleport to="body">
      <div v-if="ctxItem" class="fixed inset-0 z-40" @click="ctxItem = null" @contextmenu.prevent="ctxItem = null" />
      <div
        v-if="ctxItem && ctxPos"
        class="ctx-menu fixed z-50"
        :style="{ top: ctxPos.y + 'px', left: ctxPos.x + 'px' }"
      >
        <button class="ctx-menu-item" @click.stop="playAlbum(ctxItem!); ctxItem = null">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
          Play album
        </button>
        <div v-if="playlistStore.playlists.length > 0" class="ctx-menu-separator" />
        <div v-if="playlistStore.playlists.length > 0" class="px-3 py-1 text-xs text-stone-500 uppercase tracking-wide">Add to playlist</div>
        <button
          v-for="p in playlistStore.playlists"
          :key="p.id"
          class="ctx-menu-item"
          @click.stop="addAlbumToPlaylist(ctxItem!, p.id); ctxItem = null"
        >
          <span class="text-base leading-none">{{ p.icon ?? '🎵' }}</span>
          {{ p.name }}
        </button>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import AlbumCard from "./AlbumCard.vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import type { AlbumGridItem } from "../types";

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const items = computed(() => lib.albumGridItems);

const ctxItem = ref<AlbumGridItem | null>(null);
const ctxPos = ref<{ x: number; y: number } | null>(null);

function isAlbumPlaying(item: AlbumGridItem): boolean {
  return (
    !!lib.nowPlaying &&
    item.trackIds.includes(lib.nowPlaying.id)
  );
}

function playAlbum(item: AlbumGridItem): void {
  lib.playAlbum(item);
}

function openAlbumMenu(event: MouseEvent, item: AlbumGridItem): void {
  ctxItem.value = item;
  ctxPos.value = { x: event.clientX, y: event.clientY };
}

async function addAlbumToPlaylist(item: AlbumGridItem, playlistId: number): Promise<void> {
  await playlistStore.addTracks(playlistId, item.trackIds);
}
</script>

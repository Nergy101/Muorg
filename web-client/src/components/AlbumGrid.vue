<template>
  <div ref="scrollEl" class="relative flex-1 overflow-y-auto overscroll-contain">
    <!-- Pull-to-refresh indicator -->
    <div class="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-center" :style="indicatorStyle">
      <div
        class="mt-2 flex h-9 w-9 items-center justify-center rounded-full border border-stone-600 bg-stone-800 shadow-lg"
      >
        <FeatherIcon name="refresh-cw" class="h-4 w-4 text-accent" :class="ptr.refreshing ? 'animate-spin' : ''" />
      </div>
    </div>

    <div :style="{ transform: contentTransform }">
      <!-- Skeleton loading state -->
      <div v-if="lib.loading" class="grid gap-3 p-4" style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))">
        <div v-for="i in 8" :key="i" class="flex min-h-[220px] flex-col overflow-hidden rounded border border-stone-800">
          <div class="skeleton h-40 w-full rounded-none" />
          <div class="space-y-1.5 bg-stone-900/90 px-3 pb-3 pt-2.5">
            <div class="skeleton h-3.5 w-3/4" />
            <div class="skeleton h-3 w-1/2" />
          </div>
        </div>
      </div>

      <div
        v-else-if="lib.error"
        class="flex h-32 flex-col items-center justify-center gap-2 p-4 text-sm"
      >
        <span class="text-red-400">Failed to load library: {{ lib.error }}</span>
        <button class="text-xs text-stone-400 underline hover:text-stone-200" @click="lib.loadLibrary()">Retry</button>
      </div>

      <div
        v-else-if="items.length === 0"
        class="flex h-32 items-center justify-center p-4 text-sm text-stone-500"
      >
        No albums found.
      </div>

      <div
        v-else
        class="grid gap-3 p-4"
        style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))"
      >
        <AlbumCard
          v-for="item in items"
          :key="item.key"
          :item="item"
          :is-playing="isAlbumPlaying(item)"
          @play="emit('open-album', item)"
          @contextmenu="openAlbumMenu($event, item)"
        />
      </div>
    </div>

    <!-- Album context menu -->
    <Teleport to="body">
      <div v-if="ctxItem" class="fixed inset-0 z-40" @click="ctxItem = null" @contextmenu.prevent="ctxItem = null" />
      <div
        v-if="ctxItem && ctxPos"
        class="ctx-menu fixed z-50"
        :class="isMobile ? 'ctx-sheet' : ''"
        :style="{ top: ctxPos.y + 'px', left: ctxPos.x + 'px' }"
      >
        <div class="ctx-sheet-handle" />
        <button class="ctx-menu-item" @click.stop="emit('open-album', ctxItem!); ctxItem = null">
          <FeatherIcon name="disc" class="h-3.5 w-3.5" />
          Open album
        </button>
        <button class="ctx-menu-item" @click.stop="lib.playAlbum(ctxItem!); ctxItem = null">
          <FeatherIcon name="play" class="h-3.5 w-3.5" />
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
import { computed, ref, watch, nextTick } from "vue";
import AlbumCard from "./AlbumCard.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { usePullToRefresh } from "../composables/usePullToRefresh";
import type { AlbumGridItem } from "../types";

const emit = defineEmits<{ "open-album": [item: AlbumGridItem] }>();

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const items = computed(() => lib.albumGridItems);

const scrollEl = ref<HTMLElement | null>(null);
const ptr = usePullToRefresh(scrollEl, async () => {
  await Promise.all([lib.loadLibrary(), playlistStore.loadPlaylists()]);
});
const { indicatorStyle, contentTransform } = ptr;

const isMobile = computed(() => window.matchMedia("(max-width: 639px)").matches);

watch(() => lib.revealTrackId, (id) => {
  if (id === null) return;
  // Find the album that contains this track and scroll to its card
  const album = lib.albumGridItems.find((a) => a.trackIds.includes(id));
  if (!album) return;
  nextTick(() => {
    const el = document.querySelector(`[data-album-key="${CSS.escape(album.key)}"]`);
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
    lib.revealTrackId = null;
  });
});

const ctxItem = ref<AlbumGridItem | null>(null);
const ctxPos = ref<{ x: number; y: number } | null>(null);

function isAlbumPlaying(item: AlbumGridItem): boolean {
  return !!lib.nowPlaying && item.trackIds.includes(lib.nowPlaying.id);
}

function openAlbumMenu(event: { clientX: number; clientY: number }, item: AlbumGridItem): void {
  ctxItem.value = item;
  ctxPos.value = { x: event.clientX, y: event.clientY };
}

async function addAlbumToPlaylist(item: AlbumGridItem, playlistId: number): Promise<void> {
  await playlistStore.addTracks(playlistId, item.trackIds);
}
</script>

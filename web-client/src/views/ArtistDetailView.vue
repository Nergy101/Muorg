<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:rounded-full lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-5 w-5" />
        <span class="hidden lg:inline">Back</span>
      </button>

      <div class="min-w-0 flex-1">
        <h1 class="truncate text-title-md text-on-surface">{{ artistName }}</h1>
        <p class="text-body-sm text-on-surface-variant">{{ metaLine }}</p>
      </div>
    </div>

    <template v-if="tracks.length === 0">
      <div class="content-col flex min-h-0 flex-1 flex-col items-center justify-center gap-3">
        <p class="text-body-md text-on-surface-variant">No tracks by this artist</p>
        <button
          type="button"
          class="text-label-lg text-primary"
          @click="router.push({ name: 'library' })"
        >Back to library</button>
      </div>
    </template>

    <template v-else>
      <div ref="scroller" class="content-col min-h-0 flex-1 overflow-y-auto pb-4">
        <div v-if="albums.length > 0" class="flex items-center gap-1.5 pb-2 pt-2 text-label-lg font-semibold text-on-surface">
          <MageIcon name="compact-disk" class="h-4 w-4 text-primary" />
          Albums
        </div>
        <div class="-mx-4 flex flex-wrap gap-3 px-4">
          <AlbumCard
            v-for="item in albums"
            :key="item.key"
            :item="item"
            mode="grid"
            class="w-[calc(50%-6px)] sm:w-[calc(33.333%-8px)]"
            @open="openAlbum(item)"
          />
        </div>

        <div class="mt-4 flex items-center gap-1.5 pb-2 text-label-lg font-semibold text-on-surface">
          <MageIcon name="music" class="h-4 w-4 text-primary" />
          Tracks · {{ tracks.length }}
        </div>
        <TrackListRow
          v-for="track in tracks"
          :key="track.id"
          :track="track"
          :is-playing="player.currentTrack?.id === track.id"
          @play="player.playTrack(track, tracks)"
          @actions="sheetTrack = track"
        />
      </div>
    </template>

    <TrackActionsSheet
      :open="sheetTrack !== null"
      :track="sheetTrack"
      @close="sheetTrack = null"
      @view-artist="onViewArtist"
      @view-album="onViewAlbum"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import AlbumCard from "../components/AlbumCard.vue";
import TrackListRow from "../components/TrackListRow.vue";
import TrackActionsSheet from "../components/TrackActionsSheet.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useScrollMemory } from "../composables/useScrollMemory";
import type { AlbumGridItem, CatalogTrack } from "../types";

const props = defineProps<{ name: string }>();

const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();

const artistName = computed(() => props.name);
const norm = computed(() => artistName.value.toLowerCase());

/** Every track by this artist — matched on track artist OR album artist, so a
 *  featured/collab credit on an album still surfaces the album here. */
const tracks = computed<CatalogTrack[]>(() =>
  lib.tracks.filter((t) => {
    const n = norm.value;
    return (
      (t.artist?.toLowerCase() ?? "") === n || (t.album_artist?.toLowerCase() ?? "") === n
    );
  }),
);

/** Albums grouped with the library's own album identity (stable full-catalog
 *  keys), so splits/compilations match the main library exactly. */
const albums = computed<AlbumGridItem[]>(() => {
  const items = new Map<string, AlbumGridItem>();
  for (const t of tracks.value) {
    const key = lib.keyForTrack(t);
    const seed = lib.albumByKey(key);
    if (!seed) continue;
    items.set(key, seed);
  }
  return [...items.values()].sort((a, b) =>
    a.album.toLowerCase().localeCompare(b.album.toLowerCase()),
  );
});

const metaLine = computed(() => {
  const parts: string[] = [];
  const n = albums.value.length;
  parts.push(n === 1 ? "1 album" : `${n} albums`);
  const tc = tracks.value.length;
  parts.push(tc === 1 ? "1 track" : `${tc} tracks`);
  return parts.join(" · ");
});

function openAlbum(item: AlbumGridItem): void {
  void router.push({ name: "album", params: { albumKey: item.key } });
}

const sheetTrack = ref<CatalogTrack | null>(null);

function onViewArtist(): void {
  const t = sheetTrack.value;
  if (!t) return;
  const name = t.artist ?? t.album_artist;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = sheetTrack.value;
  if (!t) return;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);
</script>

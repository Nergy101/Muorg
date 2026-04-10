<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import AlbumGridCard from "./AlbumGridCard.vue";

export type AlbumGridItem = {
  key: string;
  album: string;
  albumArtist: string;
  year: number | null;
  trackCount: number;
  totalDurationSecs: number;
  coverPath: string;
};

defineProps<{
  albums: AlbumGridItem[];
}>();

const emit = defineEmits<{
  (e: "openAlbum", albumKey: string): void;
}>();

const store = useCatalogStore();
const { tracks, currentPlayingTrackId } = storeToRefs(store);

const playingAlbumKey = computed(() => {
  const id = currentPlayingTrackId.value;
  if (id == null) return null;
  const track = tracks.value.find((t) => t.id === id);
  if (!track) return null;
  const album = (track.album ?? "Unknown Album").trim() || "Unknown Album";
  return album.toLocaleLowerCase();
});

const gridRef = ref<HTMLElement | null>(null);

function scrollToAlbum(key: string) {
  const container = gridRef.value;
  const el = container?.querySelector<HTMLElement>(`[data-album-key="${CSS.escape(key)}"]`);
  if (!container || !el) return;
  // Center the card in the viewport of the scrollable container.
  const targetScrollTop = el.offsetTop - container.clientHeight / 2 + el.clientHeight / 2;
  container.scrollTop = Math.max(0, targetScrollTop);
}

defineExpose({ scrollToAlbum });
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div v-if="albums.length === 0" class="flex h-full items-center justify-center text-sm text-stone-400">
      No albums match current filters.
    </div>
    <div
      v-else
      ref="gridRef"
      class="grid min-h-0 grid-cols-[repeat(auto-fill,minmax(190px,1fr))] gap-3 overflow-y-auto p-3"
    >
      <AlbumGridCard
        v-for="album in albums"
        :key="album.key"
        :album="album"
        :is-playing="album.key === playingAlbumKey"
        :data-album-key="album.key"
        @openAlbum="emit('openAlbum', $event)"
      />
    </div>
  </div>
</template>


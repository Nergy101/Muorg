<template>
  <div
    ref="cardEl"
    :class="[
      'group flex flex-col rounded-xl overflow-hidden cursor-pointer transition-all duration-150',
      'border border-stone-800 hover:border-stone-600 bg-stone-800/50 hover:bg-stone-800',
      isPlaying ? 'ring-2 ring-accent' : '',
    ]"
    @click="emit('play')"
    @contextmenu.prevent="emit('contextmenu', $event)"
  >
    <!-- Cover -->
    <div class="relative aspect-square w-full overflow-hidden bg-stone-700">
      <img
        v-if="coverUrl"
        :src="coverUrl"
        :alt="item.album"
        class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
        loading="lazy"
      />
      <div v-else class="flex h-full w-full items-center justify-center">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" class="text-stone-600">
          <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="3" />
          <line x1="12" y1="9" x2="12" y2="2" />
        </svg>
      </div>

      <!-- Play overlay on hover -->
      <div class="absolute inset-0 flex items-center justify-center bg-black/0 transition-colors group-hover:bg-black/30">
        <div class="flex h-10 w-10 items-center justify-center rounded-full bg-accent opacity-0 shadow-lg transition-opacity group-hover:opacity-100">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="white">
            <polygon points="5 3 19 12 5 21 5 3" />
          </svg>
        </div>
      </div>
    </div>

    <!-- Info -->
    <div class="flex min-w-0 flex-col px-2.5 py-2">
      <p class="truncate text-sm font-medium text-stone-200 group-hover:text-white">
        {{ item.album }}
      </p>
      <p class="truncate text-xs text-stone-500">{{ item.albumArtist }}</p>
      <p class="mt-0.5 text-xs text-stone-600">
        {{ item.trackCount }} tracks
        <template v-if="item.year"> · {{ item.year }}</template>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useLibraryStore } from "../stores/library";
import type { AlbumGridItem } from "../types";

const props = defineProps<{
  item: AlbumGridItem;
  isPlaying: boolean;
}>();

const emit = defineEmits<{
  play: [];
  contextmenu: [event: MouseEvent];
}>();

const lib = useLibraryStore();
const cardEl = ref<HTMLElement | null>(null);

const coverUrl = computed(() => {
  if (!props.item.hasCover || props.item.coverTrackId === null) return null;
  return lib.coverCache.get(props.item.coverTrackId) ?? null;
});

let observer: IntersectionObserver | null = null;

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting && props.item.hasCover && props.item.coverTrackId !== null) {
        lib.requestCover(props.item.coverTrackId);
        observer?.disconnect();
      }
    },
    { rootMargin: "200px" },
  );
  if (cardEl.value) observer.observe(cardEl.value);
});

onUnmounted(() => observer?.disconnect());

watch(
  () => props.item.coverTrackId,
  (id) => {
    if (id !== null && !lib.coverCache.has(id)) lib.requestCover(id);
  },
);
</script>

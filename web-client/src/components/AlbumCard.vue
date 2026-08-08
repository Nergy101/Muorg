<template>
  <!-- List mode -->
  <div
    v-if="mode === 'list'"
    ref="cardEl"
    :data-album-key="item.key"
    class="flex h-14 w-full select-none items-center gap-3 px-4 text-left transition-colors lg:cursor-pointer lg:hover:bg-surface-container/70"
    :class="isActive ? 'bg-secondary/[0.08]' : ''"
    @click="emit('open')"
    @touchstart.passive="lp.onTouchstart"
    @touchmove.passive="lp.onTouchmove"
    @touchend="lp.onTouchend"
    @contextmenu.prevent="emit('actions')"
  >
    <div class="h-12 w-12 shrink-0 overflow-hidden rounded-md bg-surface-variant">
      <img
        v-if="coverUrl"
        :src="coverUrl"
        :alt="item.album"
        class="h-full w-full object-cover"
        decoding="async"
      />
      <div v-else class="flex h-full w-full items-center justify-center">
        <MageIcon name="music" class="h-5 w-5 text-on-surface-variant/60" />
      </div>
    </div>

    <div class="min-w-0 flex-1">
      <MarqueeText
        :text="item.album"
        :class="isActive
          ? 'text-body-md font-semibold text-secondary'
          : 'text-body-md font-semibold text-on-surface'"
      />
      <MarqueeText :text="item.albumArtist" class="text-body-sm text-on-surface-variant" />
    </div>

    <div class="flex shrink-0 items-center gap-1 text-on-surface-variant">
      <MageIcon name="music" class="h-3.5 w-3.5" />
      <span class="text-label-md tabular-nums">{{ item.trackCount }}</span>
    </div>
  </div>

  <!-- Grid mode -->
  <button
    v-else
    ref="cardEl"
    :data-album-key="item.key"
    type="button"
    class="relative aspect-square w-full select-none overflow-hidden rounded-xl bg-surface-variant text-left transition-transform duration-150 active:scale-95 lg:hover:scale-[1.03]"
    :class="isActive ? 'ring-2 ring-secondary' : ''"
    :style="isActive ? { boxShadow: '0 0 20px rgba(122,170,66,0.45)' } : undefined"
    @click="emit('open')"
    @touchstart.passive="lp.onTouchstart"
    @touchmove.passive="lp.onTouchmove"
    @touchend="lp.onTouchend"
    @contextmenu.prevent="emit('actions')"
  >
    <img
      v-if="coverUrl"
      :src="coverUrl"
      :alt="item.album"
      class="absolute inset-0 h-full w-full object-cover"
      decoding="async"
    />
    <div v-else class="absolute inset-0 flex items-center justify-center">
      <MageIcon name="music" class="h-10 w-10 text-on-surface-variant/50" />
    </div>

    <!-- Scrim so the caption stays legible over any artwork -->
    <div
      class="absolute inset-x-0 bottom-0 h-[100px] bg-gradient-to-t from-[#111111e6] to-transparent"
      aria-hidden="true"
    />

    <div class="absolute inset-x-0 bottom-0 flex items-end gap-2 px-2.5 pb-2">
      <div class="min-w-0 flex-1">
        <MarqueeText :text="item.album" class="text-label-lg font-semibold text-white" />
        <div class="truncate text-label-sm text-white/75">{{ item.albumArtist }}</div>
      </div>
      <div class="flex shrink-0 items-center gap-1 pb-0.5 text-white/75">
        <MageIcon name="music" class="h-3 w-3" />
        <span class="text-label-sm tabular-nums">{{ item.trackCount }}</span>
      </div>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import MarqueeText from "./MarqueeText.vue";
import { useLibraryStore } from "../stores/library";
import { useLongPress } from "../composables/useLongPress";
import type { AlbumGridItem } from "../types";

const props = defineProps<{
  item: AlbumGridItem;
  mode: "grid" | "list";
  isActive?: boolean;
}>();

const emit = defineEmits<{ open: []; actions: [] }>();

const lib = useLibraryStore();
const cardEl = ref<HTMLElement | null>(null);
const lp = useLongPress(() => emit("actions"));

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

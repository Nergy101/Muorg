<template>
  <button
    ref="cardEl"
    :data-album-key="item.key"
    type="button"
    class="flex w-full min-h-[220px] flex-col overflow-hidden rounded text-center transition-colors hover:border-accent"
    :class="isPlaying ? 'border-[2px] border-accent' : 'border border-stone-700'"
    :style="isPlaying ? { boxShadow: '0 0 0 2px rgba(91,124,50,0.5), 0 0 20px 6px rgba(91,124,50,0.45), 0 0 40px 10px rgba(91,124,50,0.20)' } : undefined"
    @click="emit('play')"
    @contextmenu.prevent="emit('contextmenu', $event)"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend="onTouchEnd"
  >
    <!-- Album art area -->
    <div class="relative h-40 w-full shrink-0 overflow-hidden bg-stone-800">
      <!-- Spinner while loading -->
      <div
        v-if="coverLoading"
        class="absolute inset-0 flex items-center justify-center"
      >
        <svg class="album-cover-spinner h-7 w-7 text-stone-500" viewBox="0 0 24 24" fill="none">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2.5" />
          <path class="opacity-80" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>

      <!-- No cover placeholder -->
      <div
        v-else-if="!coverUrl"
        class="flex h-full w-full items-center justify-center text-stone-500"
      >
        <span class="inline-flex h-12 w-12 items-center justify-center rounded-full border border-stone-600 text-xl">♪</span>
      </div>

      <img
        v-if="coverUrl"
        :src="coverUrl"
        :alt="item.album"
        class="absolute inset-0 h-full w-full object-cover transition-opacity duration-150"
        :class="imageReady ? 'opacity-100' : 'opacity-0'"
        decoding="async"
        @load="imageReady = true"
        @error="imageReady = false"
      />
    </div>

    <!-- Frosted glass text panel -->
    <div class="w-full bg-stone-900/90 px-3 pb-3 pt-2.5 backdrop-blur-sm">
      <div class="truncate text-sm font-semibold text-stone-100">{{ item.album }}</div>
      <div class="mt-0.5 truncate text-xs text-stone-300">{{ item.albumArtist }}</div>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from "vue";
import { useLibraryStore } from "../stores/library";
import type { AlbumGridItem } from "../types";

const props = defineProps<{
  item: AlbumGridItem;
  isPlaying: boolean;
}>();

const emit = defineEmits<{
  play: [];
  contextmenu: [event: { clientX: number; clientY: number }];
}>();

const lib = useLibraryStore();
const cardEl = ref<HTMLElement | null>(null);
const imageReady = ref(false);

const coverUrl = computed(() => {
  if (!props.item.hasCover || props.item.coverTrackId === null) return null;
  return lib.coverCache.get(props.item.coverTrackId) ?? null;
});

const coverLoading = computed(() => {
  if (!props.item.hasCover || props.item.coverTrackId === null) return false;
  return !coverUrl.value && !lib.coverCache.has(props.item.coverTrackId);
});

watch(coverUrl, () => { imageReady.value = false; });

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

const LONG_PRESS_MS = 500;
let _lpTimer: ReturnType<typeof setTimeout> | null = null;
let _lpStart = { x: 0, y: 0 };
let _lpFired = false;

function onTouchStart(e: TouchEvent): void {
  _lpFired = false;
  const t = e.touches[0];
  _lpStart = { x: t.clientX, y: t.clientY };
  _lpTimer = setTimeout(() => {
    _lpTimer = null;
    _lpFired = true;
    navigator.vibrate?.(20);
    emit("contextmenu", { clientX: t.clientX, clientY: t.clientY });
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
</script>

<style scoped>
@keyframes album-cover-spin {
  to { transform: rotate(360deg); }
}
.album-cover-spinner {
  animation: album-cover-spin 0.9s linear infinite;
  transform-origin: center;
}
</style>

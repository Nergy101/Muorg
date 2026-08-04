<template>
  <tr
    :data-track-id="track.id"
    :class="[
      'table-track-row cursor-pointer select-none border-b border-stone-800/50 transition-colors hover:bg-stone-800/60',
      isPlaying ? 'row-playing' : '',
      isSelected ? 'bg-stone-700/30' : '',
    ]"
    @click.exact="handleClick"
    @click.shift="handleShiftClick"
    @dblclick="emit('play')"
    @contextmenu.prevent="emit('contextmenu', $event)"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend="onTouchEnd"
  >
    <!-- ── Phone (< sm): swipeable flex row ───────────────────────────── -->
    <td colspan="7" class="relative overflow-hidden p-0 sm:hidden" style="touch-action: pan-y">
      <!-- Revealed action buttons (behind the sliding content) -->
      <div class="absolute inset-y-0 right-0 flex" style="width: 168px">
        <button
          type="button"
          class="flex min-w-0 flex-1 flex-col items-center justify-center gap-0.5 bg-accent text-[11px] font-medium text-white active:bg-[var(--accent-hover)]"
          @click.stop="swipeQueue"
        >
          <FeatherIcon name="plus" class="h-4 w-4" />
          Queue
        </button>
        <button
          type="button"
          class="flex min-w-0 flex-1 flex-col items-center justify-center gap-0.5 bg-stone-600 text-[11px] font-medium text-stone-100 active:bg-stone-500"
          @click.stop="swipePlaylist"
        >
          <FeatherIcon name="list" class="h-4 w-4" />
          Playlist
        </button>
      </div>

      <!-- Sliding row content -->
      <div
        class="relative z-10 flex items-center gap-1 py-2 pl-1.5 pr-1"
        :class="isPlaying ? 'row-playing' : isSelected ? 'bg-stone-700/30' : 'bg-stone-900'"
        :style="swipeStyle"
      >
        <label
          class="flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center rounded border border-stone-600"
          :class="isSelected ? 'bg-accent border-accent' : ''"
          @click.stop="lib.toggleTrackSelection(track.id, false)"
        >
          <svg v-if="isSelected" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </label>
        <div class="min-w-0 flex-1 px-1">
          <p class="truncate text-sm font-medium text-stone-200">
            <span v-if="track.track_number" class="mr-1.5 text-xs font-normal text-stone-500">{{ track.track_number }}</span>{{ track.title ?? '—' }}
          </p>
          <p class="truncate text-xs text-stone-500">{{ track.artist ?? track.album_artist ?? '—' }}</p>
        </div>
        <span
          class="shrink-0 rounded border px-1 py-0.5 font-mono text-[10px] uppercase leading-none tracking-wider"
          :class="track.format === 'flac' ? 'border-accent/70 text-accent' : 'border-stone-600 text-stone-500'"
        >{{ track.format }}</span>
        <span class="shrink-0 pr-1 text-right text-xs text-stone-500 whitespace-nowrap">{{ duration }}</span>
      </div>
    </td>

    <!-- ── Desktop (≥ sm): original table cells ───────────────────────── -->
    <!-- Checkbox + cover art column -->
    <td :class="lib.tableArtSize === 'large' ? 'hidden sm:table-cell w-28 py-1.5 pl-3 pr-2' : 'hidden sm:table-cell w-px py-1 pl-1.5 pr-1 sm:w-10 sm:py-1.5 sm:pl-3 sm:pr-2'">
      <div class="flex items-center gap-1">
        <label
          class="checkbox-cell flex h-4 w-4 shrink-0 cursor-pointer items-center justify-center rounded border border-stone-600 hover:border-stone-400 sm:h-5 sm:w-5"
          :class="isSelected ? 'bg-accent border-accent' : ''"
          @click.stop="lib.toggleTrackSelection(track.id, false)"
        >
          <svg
            v-if="isSelected"
            width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3"
          >
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </label>
      </div>
    </td>

    <!-- Title + track number + artist (stacked on mobile) -->
    <td class="hidden sm:table-cell max-w-0 py-1.5 pr-4">
      <p class="truncate text-sm font-medium text-stone-200">
        <span v-if="track.track_number" class="mr-1.5 text-xs font-normal text-stone-500">{{ track.track_number }}</span>{{ track.title ?? '—' }}
      </p>
      <p class="truncate text-xs text-stone-500 sm:hidden">{{ track.artist ?? track.album_artist ?? '—' }}</p>
    </td>

    <!-- Artist (desktop) -->
    <td class="hidden max-w-0 py-1.5 pr-4 sm:table-cell">
      <p class="truncate text-sm text-stone-400">{{ track.artist ?? track.album_artist ?? '—' }}</p>
    </td>

    <!-- Album (desktop) -->
    <td class="hidden max-w-0 py-1.5 pr-4 md:table-cell">
      <p class="truncate text-sm text-stone-500">{{ track.album ?? '—' }}</p>
    </td>

    <!-- Year -->
    <td class="hidden py-1.5 pr-4 text-xs text-stone-600 lg:table-cell whitespace-nowrap">
      {{ track.year ?? '' }}
    </td>

    <!-- Format pill -->
    <td class="hidden sm:table-cell py-1.5 pr-3 whitespace-nowrap">
      <span
        class="rounded border px-1 py-0.5 font-mono text-[10px] uppercase leading-none tracking-wider"
        :class="track.format === 'flac' ? 'border-accent/70 text-accent' : 'border-stone-600 text-stone-500'"
      >{{ track.format }}</span>
    </td>

    <!-- Duration -->
    <td class="hidden sm:table-cell py-1.5 pr-3 text-right text-xs text-stone-500 whitespace-nowrap">
      {{ duration }}
    </td>
  </tr>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import type { CatalogTrack } from "../types";

const props = defineProps<{ track: CatalogTrack }>();
const emit = defineEmits<{
  play: [];
  contextmenu: [event: { clientX: number; clientY: number }];
  "add-to-playlist": [track: CatalogTrack];
}>();

const lib = useLibraryStore();

const isPlaying = computed(() => lib.nowPlaying?.id === props.track.id);
const isSelected = computed(() => lib.selectedTrackIds.has(props.track.id));

const duration = computed(() => {
  if (!props.track.duration_secs) return "";
  return formatDuration(props.track.duration_secs);
});

function handleClick(): void {
  lib.toggleTrackSelection(props.track.id, false);
  emit("play");
}

function handleShiftClick(): void {
  lib.toggleTrackSelection(props.track.id, true);
}

// ── Swipe actions (touch devices only, phones) ───────────────────────────
const SWIPE_REVEAL = 168;
const isTouch = typeof window !== "undefined" && "ontouchstart" in window;

const offset = ref(0);
const open = ref(false);
let swiping = false;
let startX = 0;
let startY = 0;

const swipeEnabled = computed(() => isTouch && lib.selectedTrackIds.size === 0);

const swipeStyle = computed(() => ({
  transform: `translateX(${offset.value}px)`,
  transition: swiping ? "none" : "transform 0.22s cubic-bezier(0.16, 1, 0.3, 1)",
}));

function onSwipeStart(e: TouchEvent): void {
  if (!swipeEnabled.value) return;
  swiping = true;
  startX = e.touches[0].clientX;
  startY = e.touches[0].clientY;
}

function onSwipeMove(e: TouchEvent): void {
  if (!swiping) return;
  const t = e.touches[0];
  const dx = t.clientX - startX;
  const dy = t.clientY - startY;
  if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 10) {
    // Vertical scroll — hand back to the browser
    swiping = false;
    offset.value = open.value ? -SWIPE_REVEAL : 0;
    return;
  }
  const base = open.value ? -SWIPE_REVEAL : 0;
  offset.value = Math.max(-SWIPE_REVEAL, Math.min(0, base + dx));
}

function onSwipeEnd(): void {
  if (!swiping) return;
  swiping = false;
  open.value = offset.value < -SWIPE_REVEAL / 2;
  offset.value = open.value ? -SWIPE_REVEAL : 0;
  if (open.value) navigator.vibrate?.(10);
}

function closeSwipe(): void {
  open.value = false;
  offset.value = 0;
}

function swipeQueue(): void {
  lib.addToQueue(props.track);
  closeSwipe();
  navigator.vibrate?.(8);
}

function swipePlaylist(): void {
  closeSwipe();
  emit("add-to-playlist", props.track);
}

// ── Long-press → context menu ──────────────────────────────────────────────
const LONG_PRESS_MS = 500;
let _lpTimer: ReturnType<typeof setTimeout> | null = null;
let _lpStart = { x: 0, y: 0 };
let _lpFired = false;

function onTouchStart(e: TouchEvent): void {
  onSwipeStart(e);
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
  onSwipeMove(e);
  if (!_lpTimer) return;
  const t = e.touches[0];
  if (Math.abs(t.clientX - _lpStart.x) > 8 || Math.abs(t.clientY - _lpStart.y) > 8) {
    clearTimeout(_lpTimer);
    _lpTimer = null;
  }
}

function onTouchEnd(e: TouchEvent): void {
  onSwipeEnd();
  if (_lpTimer) { clearTimeout(_lpTimer); _lpTimer = null; }
  if (_lpFired) { e.preventDefault(); _lpFired = false; }
}
</script>

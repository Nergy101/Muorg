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
    <!-- Checkbox + cover art column -->
    <td :class="lib.tableArtSize === 'large' ? 'w-28 py-1.5 pl-3 pr-2' : 'w-px py-1 pl-1.5 pr-1 sm:w-10 sm:py-1.5 sm:pl-3 sm:pr-2'">
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
    <td class="max-w-0 py-1.5 pr-4">
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
    <td class="py-1.5 pr-3 whitespace-nowrap">
      <span
        class="rounded border px-1 py-0.5 font-mono text-[10px] uppercase leading-none tracking-wider"
        :class="track.format === 'flac' ? 'border-accent/70 text-accent' : 'border-stone-600 text-stone-500'"
      >{{ track.format }}</span>
    </td>

    <!-- Duration -->
    <td class="py-1.5 pr-3 text-right text-xs text-stone-500 whitespace-nowrap">
      {{ duration }}
    </td>
  </tr>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import type { CatalogTrack } from "../types";

const props = defineProps<{ track: CatalogTrack }>();
const emit = defineEmits<{
  play: [];
  contextmenu: [event: { clientX: number; clientY: number }];
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

// ── Long-press → context menu ──────────────────────────────────────────────
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

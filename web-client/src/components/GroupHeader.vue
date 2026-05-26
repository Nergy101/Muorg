<template>
  <tr
    :class="[
      'cursor-pointer select-none border-b border-stone-800 transition-colors',
      active ? 'group-header-playing' : 'bg-stone-800/60 hover:bg-stone-800',
    ]"
    @click="emit('toggle')"
    @contextmenu.prevent="emit('contextmenu', $event)"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend="onTouchEnd"
  >
    <!-- Cover art (col 1) — chevron overlaid at bottom-right -->
    <td :class="lib.tableArtSize === 'large' ? 'w-28 py-1 pl-3 pr-2' : 'w-px py-1 pl-1.5 pr-1 sm:w-10 sm:py-1.5 sm:pl-3 sm:pr-2'">
      <div :class="lib.tableArtSize === 'large' ? 'h-24 w-24' : 'h-10 w-10 sm:h-8 sm:w-8'" class="relative overflow-hidden rounded">
        <img
          v-if="coverUrl"
          :src="coverUrl"
          :alt="row.label"
          class="h-full w-full object-cover"
        />
        <div v-else class="flex h-full w-full items-center justify-center bg-stone-700">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="text-stone-500">
            <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="3" />
          </svg>
        </div>
        <!-- Collapse indicator overlaid on the art -->
        <div class="absolute inset-x-0 bottom-0 flex items-end justify-end bg-gradient-to-t from-black/50 to-transparent pb-0.5 pr-0.5 pt-3">
          <svg
            :class="['transition-transform duration-150 drop-shadow', row.collapsed ? '-rotate-90' : '']"
            width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3"
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </div>
      </div>
    </td>

    <!-- Label + meta (spans remaining cols) -->
    <td class="py-1.5 pr-4" colspan="6">
      <span class="font-medium text-stone-200 text-sm">{{ row.label }}</span>
      <span class="ml-2 text-xs text-stone-500">
        {{ row.trackCount }} track{{ row.trackCount !== 1 ? 's' : '' }}
        <template v-if="row.year"> · {{ row.year }}</template>
        · {{ formatDur(row.totalDurationSecs) }}
      </span>
    </td>
  </tr>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import type { TableGroupRow } from "../types";

const props = defineProps<{ row: TableGroupRow; active?: boolean }>();
const emit = defineEmits<{
  toggle: [];
  contextmenu: [event: { clientX: number; clientY: number }];
}>();

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

const lib = useLibraryStore();

const coverUrl = computed(() => {
  if (!props.row.hasCover || props.row.coverTrackId === null) return null;
  lib.requestCover(props.row.coverTrackId);
  return lib.coverCache.get(props.row.coverTrackId) ?? null;
});

function formatDur(s: number): string {
  return formatDuration(s);
}
</script>

<style scoped>
.group-header-playing {
  background-color: rgba(91, 124, 50, 0.18);
}
.group-header-playing:hover {
  background-color: rgba(91, 124, 50, 0.26);
}
</style>

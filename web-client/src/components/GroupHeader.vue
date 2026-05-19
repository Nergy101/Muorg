<template>
  <tr
    class="cursor-pointer select-none border-b border-stone-800 bg-stone-800/60 hover:bg-stone-800"
    @click="emit('toggle')"
  >
    <td class="py-1.5 pl-3 pr-2 w-8">
      <svg
        :class="['transition-transform', row.collapsed ? '-rotate-90' : '']"
        width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
      >
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </td>
    <td class="py-1.5 pr-2" style="width: 32px;">
      <div class="h-8 w-8 overflow-hidden rounded">
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
      </div>
    </td>
    <td class="py-1.5 pr-4" colspan="4">
      <span class="font-medium text-stone-200 text-sm">{{ row.label }}</span>
      <span class="ml-2 text-xs text-stone-500">
        {{ row.trackCount }} track{{ row.trackCount !== 1 ? 's' : '' }}
        <template v-if="row.year"> · {{ row.year }}</template>
        · {{ formatDur(row.totalDurationSecs) }}
      </span>
    </td>
    <td class="py-1.5 pr-3 text-right">
      <button
        class="rounded px-2 py-0.5 text-xs text-stone-500 hover:bg-stone-700 hover:text-stone-300"
        title="Add group to playlist"
        @click.stop="emit('add-to-playlist')"
      >
        + playlist
      </button>
    </td>
  </tr>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import type { TableGroupRow } from "../types";

const props = defineProps<{ row: TableGroupRow }>();
const emit = defineEmits<{ toggle: []; "add-to-playlist": [] }>();

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

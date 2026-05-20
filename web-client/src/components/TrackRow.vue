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
  >
    <!-- Cover art column (empty for track rows) -->
    <td :class="lib.tableArtSize === 'large' ? 'w-28 py-1.5 pl-3 pr-2' : 'w-px py-1 pl-1.5 pr-1 sm:w-10 sm:py-1.5 sm:pl-3 sm:pr-2'" />

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

    <!-- Duration -->
    <td class="py-1.5 pr-3 text-right text-xs text-stone-500 whitespace-nowrap">
      {{ duration }}
    </td>

    <!-- Format -->
    <td class="hidden py-1.5 pr-3 text-xs text-stone-600 whitespace-nowrap sm:table-cell">
      {{ track.format.toUpperCase() }}
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
  contextmenu: [event: MouseEvent];
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
</script>

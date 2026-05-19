<template>
  <div v-if="stats" class="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2 text-xs text-stone-400 border-b border-stone-800">
    <span>
      <span class="font-medium text-stone-300">{{ stats.total_tracks.toLocaleString() }}</span>
      tracks
    </span>
    <span class="text-stone-700">·</span>
    <span>
      <span class="font-medium text-stone-300">{{ stats.total_albums.toLocaleString() }}</span>
      albums
    </span>
    <span class="text-stone-700">·</span>
    <span>
      <span class="font-medium text-stone-300">{{ stats.total_artists.toLocaleString() }}</span>
      artists
    </span>
    <span class="text-stone-700">·</span>
    <span>{{ totalDurationLabel }}</span>
    <span v-if="playlistName" class="ml-auto flex items-center gap-1.5">
      <span class="rounded-full bg-accent-muted border border-accent/30 px-2 py-0.5 text-accent text-xs font-medium">
        {{ playlistName }}
      </span>
      <button
        class="text-stone-500 hover:text-stone-300"
        title="Back to full library"
        @click="emit('clear-playlist')"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { LibraryStats } from "../types";

const props = defineProps<{ stats: LibraryStats; playlistName?: string }>();
const emit = defineEmits<{ "clear-playlist": [] }>();

const totalDurationLabel = computed(() => {
  const s = props.stats.total_duration_secs;
  const days = Math.floor(s / 86400);
  const hours = Math.floor((s % 86400) / 3600);
  const mins = Math.floor((s % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${mins}m`;
  return `${mins}m`;
});
</script>

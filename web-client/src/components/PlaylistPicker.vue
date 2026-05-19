<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="close"
    >
      <div class="w-full max-w-xs rounded-xl border border-stone-700 bg-stone-900 shadow-2xl">
        <div class="flex items-center justify-between border-b border-stone-800 px-4 py-3">
          <h3 class="text-sm font-semibold text-stone-100">
            Add {{ trackCount }} track{{ trackCount !== 1 ? 's' : '' }} to playlist
          </h3>
          <button class="text-stone-500 hover:text-stone-300" @click="close">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div class="max-h-64 overflow-y-auto py-1">
          <button
            v-for="p in playlists"
            :key="p.id"
            class="flex w-full items-center gap-3 px-4 py-2.5 text-sm text-stone-300 hover:bg-stone-800 hover:text-stone-100"
            @click="choose(p.id)"
          >
            <span class="text-lg leading-none">{{ p.icon ?? '🎵' }}</span>
            <span class="min-w-0 flex-1 truncate text-left">{{ p.name }}</span>
            <span class="text-xs text-stone-600">{{ p.track_count }}</span>
          </button>

          <div v-if="playlists.length === 0" class="px-4 py-3 text-sm text-stone-500">
            No playlists yet.
          </div>
        </div>

        <div class="border-t border-stone-800 px-4 py-2">
          <button
            class="flex w-full items-center gap-2 py-2 text-sm text-stone-400 hover:text-stone-200"
            @click="newPlaylist"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
            </svg>
            New playlist…
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { Playlist } from "../types";

defineProps<{ playlists: Playlist[]; trackCount: number }>();
const emit = defineEmits<{
  pick: [id: number];
  "new-playlist": [];
}>();

const visible = ref(false);

function open(): void {
  visible.value = true;
}

function close(): void {
  visible.value = false;
}

function choose(id: number): void {
  emit("pick", id);
  close();
}

function newPlaylist(): void {
  emit("new-playlist");
  close();
}

defineExpose({ open, close });
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-40" @click="close" @contextmenu.prevent="close" />
    <div
      v-if="visible"
      ref="menuEl"
      class="ctx-menu"
      :style="{ top: y + 'px', left: x + 'px' }"
    >
      <button class="ctx-menu-item" @click.stop="emit('play'); close()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <polygon points="5 3 19 12 5 21 5 3" />
        </svg>
        Play
      </button>

      <template v-if="playlists.length > 0">
        <div class="ctx-menu-separator" />
        <div class="px-3 py-1 text-xs text-stone-500 uppercase tracking-wide">Add to playlist</div>
        <button
          v-for="p in playlists"
          :key="p.id"
          class="ctx-menu-item"
          @click.stop="emit('add-to-playlist', p.id); close()"
        >
          <span class="text-base leading-none">{{ p.icon ?? '🎵' }}</span>
          {{ p.name }}
          <span class="ml-auto text-stone-600">{{ p.track_count }}</span>
        </button>
        <button class="ctx-menu-item" @click.stop="emit('new-playlist'); close()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
          </svg>
          New playlist…
        </button>
      </template>
      <template v-else>
        <div class="ctx-menu-separator" />
        <button class="ctx-menu-item" @click.stop="emit('new-playlist'); close()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
          </svg>
          Add to new playlist…
        </button>
      </template>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, nextTick } from "vue";
import type { Playlist } from "../types";

defineProps<{ playlists: Playlist[] }>();
const emit = defineEmits<{
  play: [];
  "add-to-playlist": [id: number];
  "new-playlist": [];
}>();

const visible = ref(false);
const x = ref(0);
const y = ref(0);
const menuEl = ref<HTMLElement | null>(null);

function open(event: MouseEvent): void {
  x.value = event.clientX;
  y.value = event.clientY;
  visible.value = true;
  nextTick(() => {
    if (!menuEl.value) return;
    const rect = menuEl.value.getBoundingClientRect();
    if (rect.right > window.innerWidth) x.value = window.innerWidth - rect.width - 8;
    if (rect.bottom > window.innerHeight) y.value = window.innerHeight - rect.height - 8;
  });
}

function close(): void {
  visible.value = false;
}

defineExpose({ open, close });
</script>

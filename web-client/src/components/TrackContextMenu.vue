<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-40" @click="onBackdropClick" @contextmenu.prevent="close" />
    <div
      v-if="visible"
      ref="menuEl"
      class="ctx-menu"
      :class="isMobile ? 'ctx-sheet' : ''"
      :style="{ top: y + 'px', left: x + 'px' }"
    >
      <div class="ctx-sheet-handle" />
      <button class="ctx-menu-item" @click.stop="emit('play'); close()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <polygon points="5 3 19 12 5 21 5 3" />
        </svg>
        Play
      </button>
      <button v-if="showFind" class="ctx-menu-item" @click.stop="emit('find'); close()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        Find in library
      </button>
      <button class="ctx-menu-item" @click.stop="emit('edit'); close()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
        </svg>
        Edit metadata
      </button>

      <div class="ctx-menu-separator" />

      <template v-if="membershipIds === null">
        <div class="px-3 py-2 text-xs text-stone-500">Loading playlists…</div>
      </template>
      <template v-else>
        <!-- Already in these playlists (removable) -->
        <template v-if="playlistsIn.length > 0">
          <button
            v-for="p in playlistsIn"
            :key="p.id"
            class="ctx-menu-item text-accent"
            @click.stop="emit('remove-from-playlist', p.id); close()"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            {{ p.name }}
            <span class="ml-auto text-stone-600">{{ p.track_count }}</span>
          </button>
          <div v-if="playlistsOut.length > 0" class="ctx-menu-separator" />
        </template>
        <!-- Not yet in these playlists (addable) -->
        <button
          v-for="p in playlistsOut"
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
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from "vue";
import { usePlaylistStore } from "../stores/playlists";

const props = defineProps<{ trackId: number | null; showFind?: boolean }>();
const emit = defineEmits<{
  play: [];
  find: [];
  "add-to-playlist": [id: number];
  "remove-from-playlist": [id: number];
  "new-playlist": [];
  edit: [];
}>();

const playlistStore = usePlaylistStore();
const visible = ref(false);
const x = ref(0);
const y = ref(0);
let openedAt = 0;
const menuEl = ref<HTMLElement | null>(null);
const membershipIds = ref<Set<number> | null>(null);

const isMobile = computed(() => window.matchMedia("(max-width: 639px)").matches);

const playlistsIn = computed(() =>
  membershipIds.value === null
    ? []
    : playlistStore.playlists.filter((p) => membershipIds.value!.has(p.id)),
);

const playlistsOut = computed(() =>
  membershipIds.value === null
    ? playlistStore.playlists
    : playlistStore.playlists.filter((p) => !membershipIds.value!.has(p.id)),
);

function clampPosition(): void {
  nextTick(() => {
    if (!menuEl.value) return;
    const rect = menuEl.value.getBoundingClientRect();
    if (rect.right > window.innerWidth) x.value = window.innerWidth - rect.width - 8;
    if (rect.bottom > window.innerHeight) y.value = window.innerHeight - rect.height - 8;
  });
}

watch(membershipIds, (v) => {
  if (v !== null) clampPosition();
});

async function open(event: { clientX: number; clientY: number }): Promise<void> {
  openedAt = Date.now();
  x.value = event.clientX;
  y.value = event.clientY;
  visible.value = true;
  membershipIds.value = null;
  clampPosition();

  if (props.trackId !== null) {
    membershipIds.value = await playlistStore.getPlaylistsContainingTrack(props.trackId);
  } else {
    membershipIds.value = new Set();
  }
}

function onBackdropClick(): void {
  if (Date.now() - openedAt < 300) return;
  close();
}

function close(): void {
  visible.value = false;
}

defineExpose({ open, close });
</script>

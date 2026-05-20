<template>
  <aside
    :class="[
      'relative z-40 flex flex-col border-r border-stone-800 bg-stone-800/80 transition-all duration-200',
      open ? 'w-52' : 'w-0 overflow-hidden',
    ]"
  >
    <div class="flex min-w-0 flex-col overflow-hidden" style="width: 208px;">
      <!-- Sidebar header -->
      <div class="flex shrink-0 items-center border-b border-stone-700 px-3 py-2">
        <span class="text-xs font-semibold uppercase tracking-wide text-stone-500">Playlists</span>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto p-2">
        <div class="mb-1">
          <button
            type="button"
            class="flex w-full items-center gap-2 rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
            @click="showCreateModal = true"
          >
            <FeatherIcon name="plus" class="h-4 w-4 shrink-0 text-stone-400" />
            New playlist
          </button>
        </div>

        <button
          type="button"
          class="flex w-full items-center gap-2 rounded border px-1.5 py-1.5 text-left text-sm transition-colors"
          :class="activeId === null ? 'border-stone-500 bg-stone-700 text-stone-100' : 'border-transparent text-stone-400 hover:bg-stone-700/50 hover:text-stone-200'"
          @click="select(null)"
        >
          <FeatherIcon name="home" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
          <span class="min-w-0 flex-1 truncate text-xs">All music</span>
        </button>

        <div v-if="store.loading" class="flex items-center justify-center py-6">
          <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-stone-600 border-t-stone-300" />
        </div>
        <div v-else-if="playlists.length" class="mt-0.5 flex flex-col">
          <div
            v-for="p in playlists"
            :key="p.id"
            class="group/pl flex items-center rounded border transition-colors"
            :class="activeId === p.id ? 'border-stone-500 bg-stone-700' : 'border-transparent hover:bg-stone-700/50'"
            @contextmenu.prevent="openCtxMenu($event, p)"
          >
            <button
              type="button"
              class="flex min-w-0 flex-1 items-center gap-2 truncate px-1.5 py-1.5 text-left"
              @click="select(p.id)"
            >
              <span class="shrink-0 text-base leading-none">{{ p.icon || '🎵' }}</span>
              <span
                class="min-w-0 flex-1 truncate text-xs"
                :class="activeId === p.id ? 'text-stone-100' : 'text-stone-300'"
              >{{ p.name }}</span>
            </button>
            <span class="mr-2 shrink-0 text-[0.7rem] text-stone-500">{{ p.track_count }}</span>
          </div>
        </div>
        <div v-else class="flex flex-col items-center gap-2 pt-6 text-xs text-stone-500">
          <FeatherIcon name="list" class="h-5 w-5" />
          <p class="text-center">No playlists yet.</p>
        </div>
      </div>
    </div>

    <!-- Context menu -->
    <Teleport to="body">
      <div
        v-if="ctxMenu"
        ref="ctxMenuRef"
        class="fixed z-[300] min-w-[140px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
      >
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
          @click="select(ctxMenu.playlist.id); ctxMenu = null"
        >
          <FeatherIcon name="filter" class="h-4 w-4 shrink-0 text-stone-400" />
          Filter to this playlist
        </button>
        <div class="my-1 border-t border-stone-700" />
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
          @click="startRename(ctxMenu.playlist); ctxMenu = null"
        >
          <FeatherIcon name="edit-2" class="h-4 w-4 shrink-0 text-stone-400" />
          Rename
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-400 hover:bg-stone-700 hover:text-red-300"
          @click="deletePlaylist(ctxMenu.playlist); ctxMenu = null"
        >
          <FeatherIcon name="trash-2" class="h-4 w-4 shrink-0" />
          Delete
        </button>
      </div>
    </Teleport>

    <!-- Delete confirm modal -->
    <Teleport to="body">
      <div
        v-if="deletingPlaylist"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
        @click.self="deletingPlaylist = null"
      >
        <div class="w-full max-w-xs rounded-xl border border-stone-700 bg-stone-900 p-6 shadow-2xl">
          <p class="text-sm text-stone-300">
            Delete playlist <strong class="text-stone-100">{{ deletingPlaylist.name }}</strong>?
          </p>
          <p class="mt-1 text-xs text-stone-500">This cannot be undone.</p>
          <div class="mt-4 flex justify-end gap-2">
            <button type="button" class="rounded px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-800" @click="deletingPlaylist = null">Cancel</button>
            <button type="button" class="rounded bg-red-700 px-3 py-1.5 text-sm text-white hover:bg-red-600" @click="confirmDelete">Delete</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Create / Rename modals -->
    <PlaylistModal
      v-model="showCreateModal"
      title="New Playlist"
      confirm-label="Create"
      @confirm="onCreateConfirm"
    />
    <PlaylistModal
      v-if="renamingPlaylist"
      v-model="showRenameModal"
      title="Edit Playlist"
      confirm-label="Save"
      :initial-name="renamingPlaylist.name"
      :initial-icon="renamingPlaylist.icon"
      @confirm="onRenameConfirm"
    />
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import FeatherIcon from "./FeatherIcon.vue";
import PlaylistModal from "./PlaylistModal.vue";
import { usePlaylistStore } from "../stores/playlists";
import type { Playlist } from "../types";

defineProps<{ open: boolean }>();

const store = usePlaylistStore();
const playlists = computed(() => store.playlists);
const activeId = computed(() => store.activePlaylistId);

// Create
const showCreateModal = ref(false);

async function onCreateConfirm(name: string, icon: string | null): Promise<void> {
  await store.createPlaylist(name, icon ?? undefined);
}

// Select
function select(id: number | null): void {
  store.selectPlaylist(id);
}

// Rename
const showRenameModal = ref(false);
const renamingPlaylist = ref<Playlist | null>(null);

function startRename(p: Playlist): void {
  renamingPlaylist.value = p;
  showRenameModal.value = true;
}

async function onRenameConfirm(name: string, icon: string | null): Promise<void> {
  if (!renamingPlaylist.value) return;
  await store.renamePlaylist(renamingPlaylist.value.id, name, icon);
  renamingPlaylist.value = null;
}

// Delete
const deletingPlaylist = ref<Playlist | null>(null);

function deletePlaylist(p: Playlist): void {
  deletingPlaylist.value = p;
}

async function confirmDelete(): Promise<void> {
  if (!deletingPlaylist.value) return;
  await store.deletePlaylist(deletingPlaylist.value.id);
  deletingPlaylist.value = null;
}

// Context menu
const ctxMenu = ref<{ x: number; y: number; playlist: Playlist } | null>(null);
const ctxMenuRef = ref<HTMLElement | null>(null);

function openCtxMenu(e: MouseEvent, p: Playlist): void {
  ctxMenu.value = { x: e.clientX, y: e.clientY, playlist: p };
  nextTick(() => {
    const onOutside = (ev: MouseEvent) => {
      if (ctxMenuRef.value?.contains(ev.target as Node)) return;
      ctxMenu.value = null;
      document.removeEventListener("click", onOutside);
    };
    document.addEventListener("click", onOutside);
  });
}
</script>

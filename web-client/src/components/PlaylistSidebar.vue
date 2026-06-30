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
        <div class="mb-1 flex flex-col gap-1">
          <button
            type="button"
            class="flex w-full items-center gap-2 rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
            @click="showCreateModal = true"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4 shrink-0 text-stone-400">
              <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
            </svg>
            New playlist
          </button>
          <button
            type="button"
            class="flex w-full items-center gap-2 rounded border border-stone-700 bg-stone-800 px-3 py-2 text-left text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            @click="showSmartEditor = true"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4 shrink-0 text-stone-500">
              <polygon points="1 12 12 1 23 12 12 23 1 12" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
            </svg>
            Smart playlist
          </button>
        </div>

        <button
          type="button"
          class="flex w-full items-center gap-2 rounded border px-1.5 py-1.5 text-left text-sm transition-colors"
          :class="activeId === null ? 'border-stone-500 bg-stone-700 text-stone-100' : 'border-transparent text-stone-400 hover:bg-stone-700/50 hover:text-stone-200'"
          @click="select(null)"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-3.5 w-3.5 shrink-0 text-stone-500">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" />
          </svg>
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
              <span v-if="p.smart_rules" class="shrink-0 text-[0.65rem] uppercase tracking-wider text-accent" title="Smart playlist">⚡</span>
            </button>
            <span class="mr-2 shrink-0 text-[0.7rem] text-stone-500">{{ p.track_count }}</span>
          </div>
        </div>
        <div v-else class="flex flex-col items-center gap-2 pt-6 text-xs text-stone-500">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-5 w-5">
            <line x1="8" y1="6" x2="21" y2="6" /><line x1="8" y1="12" x2="21" y2="12" /><line x1="8" y1="18" x2="21" y2="18" /><line x1="3" y1="6" x2="3.01" y2="6" /><line x1="3" y1="12" x2="3.01" y2="12" /><line x1="3" y1="18" x2="3.01" y2="18" />
          </svg>
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
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4 shrink-0 text-stone-400">
            <polygon points="22 3 22 15 16 15 16 21 10 21 10 15 2 15 2 3 22 3" />
          </svg>
          Filter to this playlist
        </button>
        <div class="my-1 border-t border-stone-700" />

        <!-- Edit rules for smart playlists -->
        <button
          v-if="ctxMenu.playlist.smart_rules"
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
          @click="editSmartRules(ctxMenu.playlist); ctxMenu = null"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4 shrink-0 text-stone-400">
            <polygon points="1 12 12 1 23 12 12 23 1 12" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
          </svg>
          Edit rules
        </button>

        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
          @click="startRename(ctxMenu.playlist); ctxMenu = null"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4 shrink-0 text-stone-400">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
          </svg>
          Rename
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-400 hover:bg-stone-700 hover:text-red-300"
          @click="deletePlaylist(ctxMenu.playlist); ctxMenu = null"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4 shrink-0">
            <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
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

    <!-- Smart playlist editor -->
    <SmartPlaylistEditor
      :visible="showSmartEditor"
      :is-editing="!!editingSmartPlaylist"
      :initial-name="editingSmartPlaylist?.name ?? ''"
      :initial-icon="editingSmartPlaylist?.icon ?? ''"
      :initial-rules="editingSmartRules"
      @confirm="onSmartPlaylistConfirm"
      @cancel="closeSmartEditor"
    />
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import PlaylistModal from "./PlaylistModal.vue";
import SmartPlaylistEditor from "./SmartPlaylistEditor.vue";
import { usePlaylistStore } from "../stores/playlists";
import type { Playlist, SmartRule } from "../types";

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

// Smart playlist editor
const showSmartEditor = ref(false);
const editingSmartPlaylist = ref<Playlist | null>(null);
const editingSmartRules = ref<SmartRule[] | undefined>(undefined);

function editSmartRules(p: Playlist): void {
  editingSmartPlaylist.value = p;
  // Parse existing rules
  if (p.smart_rules) {
    try {
      editingSmartRules.value = JSON.parse(p.smart_rules);
    } catch {
      editingSmartRules.value = [];
    }
  } else {
    editingSmartRules.value = [];
  }
  showSmartEditor.value = true;
}

function closeSmartEditor(): void {
  showSmartEditor.value = false;
  editingSmartPlaylist.value = null;
  editingSmartRules.value = undefined;
}

async function onSmartPlaylistConfirm(name: string, icon: string, rules: SmartRule[]): Promise<void> {
  if (editingSmartPlaylist.value) {
    // Update existing smart playlist
    await store.updateSmartRules(editingSmartPlaylist.value.id, rules);
    // Also update name/icon if changed
    if (name !== editingSmartPlaylist.value.name || icon !== (editingSmartPlaylist.value.icon ?? '')) {
      await store.renamePlaylist(editingSmartPlaylist.value.id, name, icon || undefined);
    }
  } else {
    // Create new smart playlist
    await store.createSmartPlaylist(name, icon, rules);
  }
  closeSmartEditor();
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

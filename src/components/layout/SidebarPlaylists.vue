<script setup lang="ts">
import { nextTick, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import { usePlaylistAdd } from "../../composables/usePlaylistAdd";
import FeatherIcon from "../shared/FeatherIcon.vue";
import PlaylistDuplicateDialog from "../shared/PlaylistDuplicateDialog.vue";
import PlaylistExportDialog from "../shared/PlaylistExportDialog.vue";
import type { Playlist } from "../../types";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { activePlaylistId, playingFromPlaylistId } = storeToRefs(store);
const { playlists, loading: playlistsLoading } = storeToRefs(playlistStore);
const { pendingAdd, tryAddToPlaylist, confirmAddAll, confirmAddDeduped, cancelPendingAdd } = usePlaylistAdd();

// ── Create playlist ────────────────────────────────────────────────────────

const newPlaylistInputRef = ref<HTMLInputElement | null>(null);
const newPlaylistName = ref("");
const isCreatingPlaylist = ref(false);

function startCreatingPlaylist() {
  isCreatingPlaylist.value = true;
  nextTick(() => newPlaylistInputRef.value?.focus());
}

async function confirmCreatePlaylist() {
  const name = newPlaylistName.value.trim();
  if (!name) { cancelCreatePlaylist(); return; }
  await playlistStore.createPlaylist(name);
  newPlaylistName.value = "";
  isCreatingPlaylist.value = false;
}

function cancelCreatePlaylist() {
  newPlaylistName.value = "";
  isCreatingPlaylist.value = false;
}

// ── Rename playlist ────────────────────────────────────────────────────────

const renamingPlaylistId = ref<number | null>(null);
const renameInputRef = ref<HTMLInputElement[]>([]);
const renameValue = ref("");

function startRenaming(id: number, currentName: string) {
  renamingPlaylistId.value = id;
  renameValue.value = currentName;
  closePlaylistContextMenu();
  nextTick(() => renameInputRef.value[0]?.focus());
}

async function confirmRename(id: number) {
  const name = renameValue.value.trim();
  if (name) await playlistStore.renamePlaylist(id, name);
  renamingPlaylistId.value = null;
  renameValue.value = "";
}

function cancelRename() {
  renamingPlaylistId.value = null;
  renameValue.value = "";
}

// ── Delete / click ─────────────────────────────────────────────────────────

async function handleDeletePlaylist(id: number, name: string) {
  closePlaylistContextMenu();
  if (!confirm(`Delete playlist "${name}"?`)) return;
  if (store.activePlaylistId === id) store.clearActivePlaylist();
  await playlistStore.deletePlaylist(id);
}

async function handleClickPlaylist(id: number) {
  if (store.activePlaylistId === id) { store.clearActivePlaylist(); return; }
  const entries = await playlistStore.getPlaylistEntries(id);
  store.setActivePlaylist(id, entries);
}

async function handlePlayNowPlaylist(id: number) {
  const entries = await playlistStore.getPlaylistEntries(id);
  store.setActivePlaylist(id, entries);
  settingsStore.setShuffle(false);
  store.setPlayingFromPlaylistId(id);
  if (entries.length > 0) {
    const first = entries[0];
    store.setPlayRequestTrackId(first.trackId);
    store.clearSelection();
    store.toggleSelection(first.trackId);
  }
  closePlaylistContextMenu();
}

async function handleShufflePlaylist(id: number) {
  const entries = await playlistStore.getPlaylistEntries(id);
  store.setActivePlaylist(id, entries);
  settingsStore.setShuffle(true);
  store.setPlayingFromPlaylistId(id);
  if (entries.length > 0) {
    const pick = entries[Math.floor(Math.random() * entries.length)];
    store.setPlayRequestTrackId(pick.trackId);
    store.clearSelection();
    store.toggleSelection(pick.trackId);
  }
  closePlaylistContextMenu();
}

// ── Context menu ───────────────────────────────────────────────────────────

const playlistContextMenu = ref<{ x: number; y: number; id: number; name: string } | null>(null);
const playlistContextMenuRef = ref<HTMLElement | null>(null);

function openPlaylistContextMenu(e: MouseEvent, id: number, name: string) {
  e.preventDefault();
  playlistContextMenu.value = { x: e.clientX, y: e.clientY, id, name };
  nextTick(() => {
    setTimeout(() => {
      const onOutside = (ev: MouseEvent) => {
        if (playlistContextMenuRef.value?.contains(ev.target as Node)) return;
        closePlaylistContextMenu();
        document.removeEventListener("click", onOutside);
        document.removeEventListener("keydown", onEscapeMenu);
      };
      const onEscapeMenu = (ev: KeyboardEvent) => {
        if (ev.key === "Escape") {
          closePlaylistContextMenu();
          document.removeEventListener("click", onOutside);
          document.removeEventListener("keydown", onEscapeMenu);
        }
      };
      document.addEventListener("click", onOutside);
      document.addEventListener("keydown", onEscapeMenu);
    }, 0);
  });
}

function closePlaylistContextMenu() {
  playlistContextMenu.value = null;
}

// ── Drag-and-drop ──────────────────────────────────────────────────────────

const dragOverPlaylistId = ref<number | null>(null);

function onPlaylistDragover(e: DragEvent, playlistId: number) {
  if (!e.dataTransfer?.types.includes("application/muorg-tracks")) return;
  e.preventDefault();
  e.dataTransfer.dropEffect = "copy";
  dragOverPlaylistId.value = playlistId;
}

function onPlaylistDragleave(playlistId: number) {
  if (dragOverPlaylistId.value === playlistId) dragOverPlaylistId.value = null;
}

async function onPlaylistDrop(e: DragEvent, playlistId: number) {
  dragOverPlaylistId.value = null;
  if (!e.dataTransfer) return;
  const raw = e.dataTransfer.getData("application/muorg-tracks");
  if (!raw) return;
  try {
    const ids = JSON.parse(raw) as number[];
    if (ids.length) {
      const playlist = playlists.value.find((p) => p.id === playlistId);
      await tryAddToPlaylist(playlistId, ids, playlist?.name ?? "");
    }
  } catch { /* ignore malformed data */ }
}

// ── Export ─────────────────────────────────────────────────────────────────

const exportingPlaylist = ref<Playlist | null>(null);
</script>

<template>
  <div>
    <!-- Create new playlist -->
    <div class="mb-1">
      <input
        v-if="isCreatingPlaylist"
        ref="newPlaylistInputRef"
        v-model="newPlaylistName"
        type="text"
        placeholder="Playlist name…"
        maxlength="128"
        class="mb-1 w-full rounded border border-stone-500 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
        @keydown.enter="confirmCreatePlaylist"
        @keydown.escape="cancelCreatePlaylist"
        @blur="confirmCreatePlaylist"
      />
      <button
        v-else
        type="button"
        class="flex w-full items-center gap-2 rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
        @click="startCreatingPlaylist"
      >
        <FeatherIcon name="plus" class="h-4 w-4 shrink-0 text-stone-400" />
        New playlist
      </button>
    </div>

    <!-- Loading state -->
    <div v-if="playlistsLoading" class="flex items-center justify-center py-6">
      <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-stone-600 border-t-stone-300" aria-label="Loading playlists" />
    </div>

    <!-- Playlist list -->
    <ul v-else-if="playlists.length" class="space-y-0.5">
      <li
        v-for="playlist in playlists"
        :key="playlist.id"
        class="group/pl relative flex items-center rounded border"
        :class="[
          playingFromPlaylistId === playlist.id
            ? 'border-[#5b7c32]/50 bg-[#5b7c32]/20'
            : activePlaylistId === playlist.id
              ? 'border-stone-500 bg-stone-700'
              : 'border-transparent hover:bg-stone-700/50',
          dragOverPlaylistId === playlist.id ? 'ring-1 ring-stone-400' : '',
        ]"
        @dragover="onPlaylistDragover($event, playlist.id)"
        @dragleave="onPlaylistDragleave(playlist.id)"
        @drop="onPlaylistDrop($event, playlist.id)"
        @contextmenu.prevent="openPlaylistContextMenu($event, playlist.id, playlist.name)"
      >
        <!-- Rename inline input -->
        <input
          v-if="renamingPlaylistId === playlist.id"
          ref="renameInputRef"
          v-model="renameValue"
          type="text"
          maxlength="128"
          class="min-w-0 flex-1 rounded bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:ring-1 focus:ring-stone-400"
          @keydown.enter="confirmRename(playlist.id)"
          @keydown.escape="cancelRename"
          @blur="confirmRename(playlist.id)"
        />
        <template v-else>
          <button
            type="button"
            class="flex min-w-0 flex-1 items-center gap-2 truncate px-2 py-1.5 text-left"
            @click="handleClickPlaylist(playlist.id)"
          >
            <FeatherIcon
              name="list"
              class="h-3.5 w-3.5 shrink-0"
              :class="playingFromPlaylistId === playlist.id ? 'text-[#8ab55a]' : activePlaylistId === playlist.id ? 'text-stone-300' : 'text-stone-500'"
            />
            <span
              class="min-w-0 flex-1 truncate text-xs"
              :class="playingFromPlaylistId === playlist.id ? 'text-[#c8e6a0]' : activePlaylistId === playlist.id ? 'text-stone-100' : 'text-stone-300'"
            >
              {{ playlist.name }}
            </span>
          </button>
          <button
            type="button"
            class="mr-1 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            :class="activePlaylistId === playlist.id ? 'opacity-100' : 'opacity-0 group-hover/pl:opacity-100'"
            aria-label="Shuffle playlist"
            @click.stop="handleShufflePlaylist(playlist.id)"
          >
            <FeatherIcon name="shuffle" class="h-3.5 w-3.5" />
          </button>
          <span class="mr-2 shrink-0 text-[0.7rem] text-stone-500">{{ playlist.track_count }}</span>
        </template>
      </li>
    </ul>

    <div v-else class="flex flex-col items-center gap-2 pt-6 text-xs text-stone-500">
      <FeatherIcon name="list" class="h-5 w-5" />
      <p class="text-center">No playlists yet.</p>
    </div>
  </div>

  <Teleport to="body">
    <div
      v-if="playlistContextMenu"
      ref="playlistContextMenuRef"
      class="fixed z-[300] min-w-[140px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
      :style="{ left: playlistContextMenu.x + 'px', top: playlistContextMenu.y + 'px' }"
      @click.stop
    >
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="handlePlayNowPlaylist(playlistContextMenu.id)"
      >
        <FeatherIcon name="play" class="h-4 w-4 shrink-0 text-stone-400" />
        Play now
      </button>
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="handleShufflePlaylist(playlistContextMenu.id)"
      >
        <FeatherIcon name="shuffle" class="h-4 w-4 shrink-0 text-stone-400" />
        Shuffle
      </button>
      <div class="my-1 border-t border-stone-700" />
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="startRenaming(playlistContextMenu.id, playlistContextMenu.name)"
      >
        <FeatherIcon name="edit-2" class="h-4 w-4 shrink-0 text-stone-400" />
        Rename
      </button>
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="exportingPlaylist = playlists.find(p => p.id === playlistContextMenu!.id) ?? null; closePlaylistContextMenu()"
      >
        <FeatherIcon name="download" class="h-4 w-4 shrink-0 text-stone-400" />
        Export to M3U
      </button>
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-400 hover:bg-stone-700 hover:text-red-300"
        @click="handleDeletePlaylist(playlistContextMenu.id, playlistContextMenu.name)"
      >
        <FeatherIcon name="trash-2" class="h-4 w-4 shrink-0" />
        Delete
      </button>
    </div>
  </Teleport>

  <PlaylistDuplicateDialog
    v-if="pendingAdd"
    :pending="pendingAdd"
    @confirm-all="confirmAddAll"
    @confirm-deduped="confirmAddDeduped"
    @cancel="cancelPendingAdd"
  />
  <PlaylistExportDialog
    v-if="exportingPlaylist"
    :playlist="exportingPlaylist"
    :open="true"
    @close="exportingPlaylist = null"
  />
</template>

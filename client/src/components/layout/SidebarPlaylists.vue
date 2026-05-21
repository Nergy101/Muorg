<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import { usePlaylistAdd } from "../../composables/usePlaylistAdd";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import EmojiPicker from "../shared/EmojiPicker.vue";
import PlaylistDuplicateDialog from "../shared/PlaylistDuplicateDialog.vue";
import PlaylistExportDialog from "../shared/PlaylistExportDialog.vue";
import SmartPlaylistModal from "../shared/SmartPlaylistModal.vue";
import type { SmartRule } from "../shared/SmartPlaylistModal.vue";
import type { Playlist } from "../../types";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { activePlaylistId, playingFromPlaylistId, tracks } = storeToRefs(store);

const availableGenres = computed(() => {
  const set = new Set<string>();
  for (const t of tracks.value) {
    const g = t.genre?.trim();
    if (g) set.add(g);
  }
  return [...set].sort((a, b) => a.localeCompare(b));
});
const { playlists, loading: playlistsLoading } = storeToRefs(playlistStore);
const { pendingAdd, tryAddToPlaylist, confirmAddAll, confirmAddDeduped, cancelPendingAdd } = usePlaylistAdd();

// ── Create playlist ────────────────────────────────────────────────────────

const newPlaylistInputRef = ref<HTMLInputElement | null>(null);
const newPlaylistName = ref("");
const newPlaylistIcon = ref("");
const isCreatingPlaylist = ref(false);
const showNewEmojiPicker = ref(false);

function startCreatingPlaylist() {
  isCreatingPlaylist.value = true;
  newPlaylistIcon.value = "";
  showNewEmojiPicker.value = false;
  nextTick(() => newPlaylistInputRef.value?.focus());
}

async function confirmCreatePlaylist() {
  const name = newPlaylistName.value.trim();
  if (!name) { cancelCreatePlaylist(); return; }
  const playlist = await playlistStore.createPlaylist(name);
  if (newPlaylistIcon.value.trim() && playlist?.id != null) {
    await playlistStore.setPlaylistIcon(playlist.id, newPlaylistIcon.value.trim());
  }
  newPlaylistName.value = "";
  newPlaylistIcon.value = "";
  showNewEmojiPicker.value = false;
  isCreatingPlaylist.value = false;
}

function cancelCreatePlaylist() {
  newPlaylistName.value = "";
  newPlaylistIcon.value = "";
  showNewEmojiPicker.value = false;
  isCreatingPlaylist.value = false;
}

// ── Edit playlist (name + icon) ────────────────────────────────────────────

const editingPlaylistId = ref<number | null>(null);
const editNameRef = ref<HTMLInputElement[]>([]);
const editName = ref("");
const editIcon = ref("");
const showEmojiPicker = ref(false);

function startEditing(id: number, currentName: string, currentIcon: string | null) {
  editingPlaylistId.value = id;
  editName.value = currentName;
  editIcon.value = currentIcon ?? "";
  showEmojiPicker.value = false;
  closePlaylistContextMenu();
  nextTick(() => editNameRef.value[0]?.focus());
}

async function confirmEdit(id: number) {
  const name = editName.value.trim();
  if (!name) { cancelEdit(); return; }
  await playlistStore.renamePlaylist(id, name);
  await playlistStore.setPlaylistIcon(id, editIcon.value.trim() || null);
  editingPlaylistId.value = null;
  showEmojiPicker.value = false;
}

function cancelEdit() {
  editingPlaylistId.value = null;
  showEmojiPicker.value = false;
}


// ── Smart playlist ─────────────────────────────────────────────────────────

const showSmartModal = ref(false);
const smartModalEditId = ref<number | null>(null);
const smartModalInitialName = ref<string | undefined>(undefined);
const smartModalInitialIcon = ref<string | undefined>(undefined);
const smartModalInitialRules = ref<SmartRule[] | undefined>(undefined);

function openNewSmartModal() {
  smartModalEditId.value = null;
  smartModalInitialName.value = undefined;
  smartModalInitialIcon.value = undefined;
  smartModalInitialRules.value = undefined;
  showSmartModal.value = true;
}

function openEditSmartModal(playlist: { id: number; name: string; icon?: string | null; smart_rules: string | null }) {
  let parsedRules: SmartRule[] = [];
  try {
    const raw = JSON.parse(playlist.smart_rules ?? "[]") as { field: string; op: string; value?: unknown }[];
    parsedRules = raw.map((r) => ({
      field: r.field,
      op: r.op,
      value: r.value == null ? "" : String(r.value),
    }));
  } catch { /* keep empty */ }
  smartModalEditId.value = playlist.id;
  smartModalInitialName.value = playlist.name;
  smartModalInitialIcon.value = playlist.icon ?? undefined;
  smartModalInitialRules.value = parsedRules;
  closePlaylistContextMenu();
  showSmartModal.value = true;
}

function rulesToJson(rules: SmartRule[]): string {
  return JSON.stringify(
    rules.map((r) => ({
      field: r.field,
      op: r.op,
      value: (r.op === "is_null" || r.op === "is_not_null")
        ? undefined
        : isNaN(Number(r.value)) ? r.value : Number(r.value),
    })),
  );
}

async function confirmSmartPlaylist(name: string, icon: string, rules: SmartRule[]) {
  showSmartModal.value = false;
  const rulesJson = rulesToJson(rules);
  if (smartModalEditId.value != null) {
    await playlistStore.renamePlaylist(smartModalEditId.value, name);
    await playlistStore.setPlaylistIcon(smartModalEditId.value, icon || null);
    await playlistStore.updateSmartPlaylistRules(smartModalEditId.value, rulesJson);
    // Refresh active playlist if it's the one being edited
    if (store.activePlaylistId === smartModalEditId.value) {
      const ids = await playlistStore.getSmartPlaylistTrackIds(smartModalEditId.value);
      store.setActivePlaylist(smartModalEditId.value, ids.map((trackId) => ({ entryId: -1, trackId })));
    }
  } else {
    const playlist = await playlistStore.createSmartPlaylist(name, rulesJson);
    if (icon && playlist?.id != null) {
      await playlistStore.setPlaylistIcon(playlist.id, icon);
    }
  }
  smartModalEditId.value = null;
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
  const playlist = playlists.value.find((p) => p.id === id);
  if (playlist?.smart_rules) {
    const ids = await playlistStore.getSmartPlaylistTrackIds(id);
    store.setActivePlaylist(id, ids.map((trackId) => ({ entryId: -1, trackId })));
  } else {
    const entries = await playlistStore.getPlaylistEntries(id);
    store.setActivePlaylist(id, entries);
  }
}

async function resolvePlaylistEntries(id: number) {
  const playlist = playlists.value.find((p) => p.id === id);
  if (playlist?.smart_rules) {
    const ids = await playlistStore.getSmartPlaylistTrackIds(id);
    return ids.map((trackId) => ({ entryId: -1, trackId }));
  }
  return playlistStore.getPlaylistEntries(id);
}

async function handlePlayNowPlaylist(id: number) {
  const entries = await resolvePlaylistEntries(id);
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
  const entries = await resolvePlaylistEntries(id);
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

// ── Track drag-and-drop (dropping tracks onto a playlist) ──────────────────

const dragOverPlaylistId = ref<number | null>(null);

function onPlaylistDragover(e: DragEvent, playlistId: number) {
  // Use the store flag rather than dataTransfer.types to avoid WKWebView MIME type normalisation issues.
  if (!store.isInternalQueueDrag) return;
  e.preventDefault();
  if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
  dragOverPlaylistId.value = playlistId;
}

function onPlaylistDragleave(e: DragEvent, playlistId: number) {
  // Only clear if the cursor actually left the playlist item (not just moved to a child).
  const target = e.currentTarget as HTMLElement | null;
  if (dragOverPlaylistId.value === playlistId && !target?.contains(e.relatedTarget as Node)) {
    dragOverPlaylistId.value = null;
  }
}

async function onPlaylistDrop(_e: DragEvent, playlistId: number) {
  dragOverPlaylistId.value = null;
  // Read track IDs from the store payload (bypasses WKWebView dataTransfer getData issues).
  const ids = store.pendingDragTrackIds?.slice() ?? null;
  if (!ids?.length) return;
  const playlist = playlists.value.find((p) => p.id === playlistId);
  await tryAddToPlaylist(playlistId, ids, playlist?.name ?? "");
}

// ── Playlist reorder — pointer-based (same pattern as queue) ───────────────

const listRef = ref<HTMLElement | null>(null);
const draggedPlaylistId = ref<number | null>(null);
const dropSlotIndex = ref<number | null>(null);

const DRAG_THRESHOLD_PX = 5;
let pointerDownPlaylistId: number | null = null;
let pointerDownY = 0;
let hasDragCrossedThreshold = false;

function slotStyle(slotIndex: number): Record<string, string> {
  const isActive = dropSlotIndex.value === slotIndex && draggedPlaylistId.value !== null;
  return {
    height: isActive ? "20px" : "0px",
    minHeight: isActive ? "20px" : "0px",
    flexShrink: "0",
    borderRadius: "4px",
    border: isActive ? "3px solid #16a34a" : "none",
    backgroundColor: isActive ? "#22c55e" : "transparent",
    boxShadow: isActive ? "0 0 12px rgba(34, 197, 94, 0.5)" : "none",
    transition: "min-height 0.12s ease, background-color 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease",
  };
}

function updateDropSlot(clientY: number) {
  if (!listRef.value) return;
  const items = listRef.value.querySelectorAll<HTMLElement>(".playlist-item");
  if (!items.length) { dropSlotIndex.value = 0; return; }
  for (let i = 0; i < items.length; i++) {
    const rect = items[i].getBoundingClientRect();
    const mid = rect.top + rect.height / 2;
    if (clientY < mid) {
      dropSlotIndex.value = i;
      return;
    }
  }
  dropSlotIndex.value = items.length;
}

function onGripPointerDown(e: MouseEvent, playlistId: number) {
  if (e.button !== 0) return;
  // Don't start reorder while a track/album HTML5 drag is in flight — it would compete
  // for the same visual state and `cursor` on playlist items.
  if (store.isInternalQueueDrag) return;
  e.preventDefault(); // Prevent text selection during reorder (was @mousedown.prevent in template).
  pointerDownPlaylistId = playlistId;
  pointerDownY = e.clientY;
  hasDragCrossedThreshold = false;
  document.addEventListener("mousemove", onReorderPointerMove);
  document.addEventListener("mouseup", onReorderPointerUp);
}

function onReorderPointerMove(e: MouseEvent) {
  if (pointerDownPlaylistId === null) return;
  if (!hasDragCrossedThreshold) {
    if (Math.abs(e.clientY - pointerDownY) < DRAG_THRESHOLD_PX) return;
    hasDragCrossedThreshold = true;
    draggedPlaylistId.value = pointerDownPlaylistId;
    dropSlotIndex.value = null;
    store.setInternalQueueDrag(true);
    document.body.style.userSelect = "none";
    document.body.style.cursor = "grabbing";
  }
  updateDropSlot(e.clientY);
}

function onReorderPointerUp() {
  if (hasDragCrossedThreshold && draggedPlaylistId.value !== null) {
    const srcId = draggedPlaylistId.value;
    const slotIdx = dropSlotIndex.value ?? 0;
    const list = [...playlists.value];
    const srcIdx = list.findIndex((p) => p.id === srcId);
    if (srcIdx !== -1) {
      const [moved] = list.splice(srcIdx, 1);
      // If slot was after the dragged item, its index shifts by 1 after removal
      const insertAt = Math.min(
        slotIdx > srcIdx ? slotIdx - 1 : slotIdx,
        list.length,
      );
      list.splice(Math.max(0, insertAt), 0, moved);
      playlistStore.reorderPlaylists(list.map((p) => p.id));
    }
  }
  cleanupReorderDrag();
}

function cleanupReorderDrag() {
  draggedPlaylistId.value = null;
  dropSlotIndex.value = null;
  pointerDownPlaylistId = null;
  hasDragCrossedThreshold = false;
  store.setInternalQueueDrag(false);
  document.body.style.userSelect = "";
  document.body.style.cursor = "";
  document.removeEventListener("mousemove", onReorderPointerMove);
  document.removeEventListener("mouseup", onReorderPointerUp);
}

onUnmounted(() => {
  cleanupReorderDrag();
});

// ── Export ─────────────────────────────────────────────────────────────────

const exportingPlaylist = ref<Playlist | null>(null);
</script>

<template>
  <div>
    <!-- Create new playlist -->
    <div class="mb-1">
      <div v-if="isCreatingPlaylist" class="relative mb-1 flex items-center gap-1">
        <!-- Emoji button -->
        <button
          type="button"
          class="icon-btn h-7 w-7 shrink-0 rounded bg-stone-700 text-base leading-none hover:bg-stone-600"
          :class="showNewEmojiPicker ? 'ring-1 ring-stone-400' : ''"
          title="Pick emoji"
          @mousedown.prevent
          @click.stop="showNewEmojiPicker = !showNewEmojiPicker"
        >
          <span v-if="newPlaylistIcon">{{ newPlaylistIcon }}</span>
          <FeatherIcon v-else name="smile" class="h-3.5 w-3.5 text-stone-400" />
        </button>
        <!-- Name input -->
        <input
          ref="newPlaylistInputRef"
          v-model="newPlaylistName"
          type="text"
          placeholder="Playlist name…"
          maxlength="128"
          class="min-w-0 flex-1 rounded border border-stone-500 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
          @keydown.enter="confirmCreatePlaylist"
          @keydown.escape="cancelCreatePlaylist"
          @blur="confirmCreatePlaylist"
        />
        <!-- Emoji picker -->
        <EmojiPicker
          :open="showNewEmojiPicker"
          class="absolute left-0 top-full z-50 mt-1"
          @pick="newPlaylistIcon = $event; showNewEmojiPicker = false"
        />
      </div>
      <div v-else class="flex gap-1">
        <button
          type="button"
          class="flex flex-1 items-center gap-2 rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
          @click="startCreatingPlaylist"
        >
          <FeatherIcon name="plus" class="h-4 w-4 shrink-0 text-stone-400" />
          New playlist
        </button>
        <button
          type="button"
          class="flex shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-700 px-2 py-2 text-stone-400 hover:bg-stone-600 hover:text-yellow-300"
          title="New smart playlist"
          @click="openNewSmartModal"
        >
          <FeatherIcon name="zap" class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="playlistsLoading" class="flex items-center justify-center py-6">
      <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-stone-600 border-t-stone-300" aria-label="Loading playlists" />
    </div>

    <!-- Playlist list -->
    <div v-else-if="playlists.length" ref="listRef" class="flex flex-col">
      <template v-for="(playlist, idx) in playlists" :key="playlist.id">
        <!-- Drop slot before this item -->
        <div :style="slotStyle(idx)" />

        <!-- Playlist item -->
        <div
          class="playlist-item group/pl relative flex cursor-grab items-center rounded border active:cursor-grabbing"
          :class="[
            playingFromPlaylistId === playlist.id
              ? 'border-[#5b7c32]/50 bg-[#5b7c32]/20'
              : activePlaylistId === playlist.id
                ? 'border-stone-500 bg-stone-700'
                : 'border-transparent hover:bg-stone-700/50',
            draggedPlaylistId === playlist.id ? 'opacity-40' : '',
          ]"
          :style="dragOverPlaylistId === playlist.id
            ? { boxShadow: '0 0 0 1.5px rgba(34,197,94,0.65), 0 0 12px rgba(34,197,94,0.35)', background: 'rgba(20,83,45,0.18)' }
            : undefined"
          @mousedown="onGripPointerDown($event, playlist.id)"
          @dragover="onPlaylistDragover($event, playlist.id)"
          @dragleave="onPlaylistDragleave($event, playlist.id)"
          @drop="onPlaylistDrop($event, playlist.id)"
          @contextmenu.prevent="openPlaylistContextMenu($event, playlist.id, playlist.name)"
        >
          <!-- Edit inline (name + emoji) -->
          <template v-if="editingPlaylistId === playlist.id">
            <div class="flex min-w-0 flex-1 flex-col gap-1 px-1.5 py-1.5">
              <div class="flex items-center gap-1">
                <!-- Emoji button: shows current emoji or placeholder, toggles picker -->
                <button
                  type="button"
                  class="icon-btn h-6 w-7 shrink-0 rounded bg-stone-700 text-base leading-none hover:bg-stone-600"
                  :class="showEmojiPicker ? 'ring-1 ring-stone-400' : ''"
                  title="Pick emoji"
                  @click.stop="showEmojiPicker = !showEmojiPicker"
                >
                  <span v-if="editIcon">{{ editIcon }}</span>
                  <FeatherIcon v-else name="smile" class="h-3.5 w-3.5 text-stone-400" />
                </button>
                <!-- Name input -->
                <input
                  ref="editNameRef"
                  v-model="editName"
                  type="text"
                  maxlength="128"
                  class="min-w-0 flex-1 rounded bg-stone-700 px-2 py-0.5 text-xs text-stone-200 outline-none focus:ring-1 focus:ring-stone-400"
                  @keydown.enter.prevent="confirmEdit(playlist.id)"
                  @keydown.escape.prevent="cancelEdit"
                />
                <!-- Confirm -->
                <button
                  type="button"
                  class="icon-btn h-6 w-6 shrink-0 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                  @click.stop="confirmEdit(playlist.id)"
                >
                  <FeatherIcon name="check" class="h-3.5 w-3.5" />
                </button>
                <!-- Cancel -->
                <button
                  type="button"
                  class="icon-btn h-6 w-6 shrink-0 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                  @click.stop="cancelEdit"
                >
                  <FeatherIcon name="x" class="h-3.5 w-3.5" />
                </button>
              </div>
              <!-- Emoji picker panel -->
              <EmojiPicker
                :open="showEmojiPicker"
                @pick="editIcon = $event; showEmojiPicker = false"
              />
            </div>
          </template>
          <template v-else>
            <button
              type="button"
              class="flex min-w-0 flex-1 items-center gap-2 truncate px-1 py-1.5 text-left"
              @click="handleClickPlaylist(playlist.id)"
            >
              <span
                v-if="playlist.icon"
                class="shrink-0 text-sm leading-none"
              >{{ playlist.icon }}</span>
              <FeatherIcon
                v-else-if="playlist.smart_rules"
                name="zap"
                class="h-3.5 w-3.5 shrink-0"
                :class="playingFromPlaylistId === playlist.id ? 'text-[#8ab55a]' : activePlaylistId === playlist.id ? 'text-stone-300' : 'text-stone-500'"
              />
              <FeatherIcon
                v-else
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
            <span class="mr-2 inline-flex shrink-0 items-center gap-0.5 text-[0.7rem] text-stone-500">
              <FeatherIcon v-if="playlist.smart_rules" name="zap" class="h-2.5 w-2.5 text-stone-500" />
              {{ playlist.track_count }}
            </span>
          </template>
        </div>
      </template>

      <!-- Final slot after last item -->
      <div :style="slotStyle(playlists.length)" />
    </div>

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
        @click="startEditing(playlistContextMenu.id, playlistContextMenu.name, playlists.find(p => p.id === playlistContextMenu!.id)?.icon ?? null)"
      >
        <FeatherIcon name="edit-2" class="h-4 w-4 shrink-0 text-stone-400" />
        Edit name
      </button>
      <button
        v-if="playlists.find(p => p.id === playlistContextMenu!.id)?.smart_rules"
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="openEditSmartModal(playlists.find(p => p.id === playlistContextMenu!.id)!)"
      >
        <FeatherIcon name="zap" class="h-4 w-4 shrink-0 text-yellow-500" />
        Edit rules
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
  <SmartPlaylistModal
    :open="showSmartModal"
    :genres="availableGenres"
    :is-editing="smartModalEditId !== null"
    :initial-name="smartModalInitialName"
    :initial-icon="smartModalInitialIcon"
    :initial-rules="smartModalInitialRules"
    @confirm="confirmSmartPlaylist"
    @cancel="showSmartModal = false; smartModalEditId = null"
  />
</template>

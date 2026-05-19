<template>
  <aside
    :class="[
      'flex flex-col border-r border-stone-800 bg-stone-900 transition-all duration-200',
      open ? 'w-52' : 'w-0 overflow-hidden',
    ]"
  >
    <div class="flex min-w-0 flex-col overflow-hidden" style="width: 208px;">
      <!-- Header -->
      <div class="flex items-center justify-between px-3 py-2.5 border-b border-stone-800">
        <span class="text-xs font-semibold uppercase tracking-wider text-stone-500">Playlists</span>
        <button
          class="flex h-6 w-6 items-center justify-center rounded text-stone-500 hover:bg-stone-800 hover:text-stone-300"
          title="New playlist"
          @click="startCreate"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </div>

      <!-- New playlist input -->
      <div v-if="creatingNew" class="px-2 py-2 border-b border-stone-800">
        <input
          ref="createInputEl"
          v-model="newName"
          type="text"
          placeholder="Playlist name"
          class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-xs text-stone-200 placeholder-stone-500 focus:border-accent focus:outline-none"
          maxlength="80"
          @keydown.enter="confirmCreate"
          @keydown.esc="creatingNew = false"
          @blur="confirmCreate"
        />
      </div>

      <!-- All Library -->
      <button
        :class="[
          'flex items-center gap-2 px-3 py-2 text-sm transition-colors',
          activeId === null
            ? 'bg-accent-muted text-accent'
            : 'text-stone-400 hover:bg-stone-800 hover:text-stone-200',
        ]"
        @click="select(null)"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" />
        </svg>
        <span class="truncate">All music</span>
      </button>

      <!-- Playlists list -->
      <div class="flex-1 overflow-y-auto">
        <div
          v-for="p in playlists"
          :key="p.id"
          :class="[
            'group flex items-center gap-2 px-3 py-2 text-sm transition-colors cursor-pointer',
            activeId === p.id
              ? 'bg-accent-muted text-accent'
              : 'text-stone-400 hover:bg-stone-800 hover:text-stone-200',
          ]"
          @click="select(p.id)"
          @contextmenu.prevent="openCtxMenu($event, p)"
        >
          <span class="text-base leading-none">{{ p.icon ?? '🎵' }}</span>

          <!-- Rename inline -->
          <template v-if="renamingId === p.id">
            <input
              ref="renameInputEl"
              v-model="renameValue"
              type="text"
              class="min-w-0 flex-1 rounded border border-stone-600 bg-stone-800 px-1.5 py-0.5 text-xs text-stone-200 focus:border-accent focus:outline-none"
              maxlength="80"
              @keydown.enter="confirmRename(p)"
              @keydown.esc="renamingId = null"
              @blur="confirmRename(p)"
              @click.stop
            />
          </template>
          <template v-else>
            <span class="min-w-0 flex-1 truncate">{{ p.name }}</span>
            <span class="text-xs text-stone-600">{{ p.track_count }}</span>
          </template>

          <button
            class="ml-auto hidden h-5 w-5 shrink-0 items-center justify-center rounded text-stone-600 hover:text-stone-300 group-hover:flex"
            @click.stop="openCtxMenu($event, p)"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
              <circle cx="5" cy="12" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="19" cy="12" r="2" />
            </svg>
          </button>
        </div>

        <p v-if="playlists.length === 0 && !creatingNew" class="px-3 py-4 text-xs text-stone-600">
          No playlists yet. Click + to create one.
        </p>
      </div>
    </div>

    <!-- Context menu -->
    <PlaylistContextMenu
      ref="ctxMenuRef"
      @rename="startRename"
      @delete="confirmDelete"
    />

    <!-- Delete confirm -->
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
            <button
              class="rounded px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-800"
              @click="deletingPlaylist = null"
            >
              Cancel
            </button>
            <button
              class="rounded bg-red-700 px-3 py-1.5 text-sm text-white hover:bg-red-600"
              @click="doDelete"
            >
              Delete
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </aside>
</template>

<script setup lang="ts">
import { ref, nextTick } from "vue";
import PlaylistContextMenu from "./PlaylistContextMenu.vue";
import { usePlaylistStore } from "../stores/playlists";
import type { Playlist } from "../types";

defineProps<{ open: boolean }>();

const store = usePlaylistStore();
const playlists = computed_from_store();
const activeId = computed_active();

import { computed } from "vue";

function computed_from_store() {
  return computed(() => store.playlists);
}

function computed_active() {
  return computed(() => store.activePlaylistId);
}

const ctxMenuRef = ref<InstanceType<typeof PlaylistContextMenu> | null>(null);
const creatingNew = ref(false);
const newName = ref("");
const createInputEl = ref<HTMLInputElement | null>(null);
const renamingId = ref<number | null>(null);
const renameValue = ref("");
const renameInputEl = ref<HTMLInputElement | null>(null);
const deletingPlaylist = ref<Playlist | null>(null);
let ctxTarget: Playlist | null = null;

function select(id: number | null): void {
  store.selectPlaylist(id);
}

function startCreate(): void {
  creatingNew.value = true;
  newName.value = "";
  nextTick(() => createInputEl.value?.focus());
}

async function confirmCreate(): Promise<void> {
  if (!creatingNew.value) return;
  const name = newName.value.trim();
  creatingNew.value = false;
  if (name) await store.createPlaylist(name);
}

function openCtxMenu(event: MouseEvent, p: Playlist): void {
  ctxTarget = p;
  ctxMenuRef.value?.open(event);
}

function startRename(): void {
  if (!ctxTarget) return;
  renamingId.value = ctxTarget.id;
  renameValue.value = ctxTarget.name;
  nextTick(() => renameInputEl.value?.focus());
}

async function confirmRename(p: Playlist): Promise<void> {
  if (renamingId.value !== p.id) return;
  const name = renameValue.value.trim();
  renamingId.value = null;
  if (name && name !== p.name) await store.renamePlaylist(p.id, name);
}

function confirmDelete(): void {
  if (!ctxTarget) return;
  deletingPlaylist.value = ctxTarget;
}

async function doDelete(): Promise<void> {
  if (!deletingPlaylist.value) return;
  await store.deletePlaylist(deletingPlaylist.value.id);
  deletingPlaylist.value = null;
}
</script>

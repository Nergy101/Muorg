<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center justify-between px-4">
      <span class="text-title-lg text-on-surface">Playlists</span>
      <button
        type="button"
        class="flex h-9 w-9 items-center justify-center rounded-full text-primary"
        aria-label="New playlist"
        @click="openCreate"
      >
        <MageIcon name="plus" class="h-5 w-5" />
      </button>
    </div>

    <div ref="scroller" class="content-col-children min-h-0 flex-1 overflow-y-auto">
      <div v-if="playlistStore.loading" class="flex justify-center py-12">
        <MageIcon name="refresh" class="h-7 w-7 animate-spin text-on-surface-variant" />
      </div>

      <div v-else-if="playlistStore.playlists.length === 0" class="flex flex-col items-center gap-1 py-12">
        <p class="text-body-lg text-on-surface">No playlists yet.</p>
        <p class="text-body-md text-on-surface-variant">Tap + to create one.</p>
      </div>

      <div v-else class="px-4 py-2">
        <div
          class="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-[repeat(auto-fill,minmax(200px,1fr))]"
        >
          <div
            v-for="p in playlistStore.sortedPlaylists"
            :key="p.id"
            class="flex aspect-square w-full select-none flex-col rounded-xl bg-surface-variant transition-transform duration-150 active:scale-95"
            role="button"
            tabindex="0"
            @click="router.push({ name: 'playlist', params: { id: String(p.id) } })"
            @keydown.enter="router.push({ name: 'playlist', params: { id: String(p.id) } })"
          >
            <div class="flex items-center justify-end gap-0.5 p-1">
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full"
                :class="playlistStore.isPinned(p.id) ? 'fill-current text-primary' : 'text-on-surface-variant'"
                :aria-label="playlistStore.isPinned(p.id) ? 'Unpin playlist' : 'Pin playlist'"
                @click.stop="playlistStore.togglePin(p.id)"
              >
                <MageIcon name="pin" class="h-4 w-4" />
              </button>
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
                aria-label="Edit playlist"
                @click.stop="openEdit(p)"
              >
                <MageIcon name="edit" class="h-4 w-4" />
              </button>
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full text-error"
                aria-label="Delete playlist"
                @click.stop="deleteTarget = p"
              >
                <MageIcon name="trash" class="h-4 w-4" />
              </button>
            </div>
            <div class="flex min-h-0 flex-1 flex-col items-center justify-center gap-0.5 px-2 pb-2 text-center">
              <span class="text-3xl leading-none">{{ p.icon ?? "🎵" }}</span>
              <p class="w-full truncate text-label-lg font-semibold text-on-surface">{{ p.name }}</p>
              <p class="text-label-sm text-on-surface-variant">
                {{ p.track_count === 1 ? "1 track" : `${p.track_count} tracks` }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <PlaylistFormDialog
      :open="formOpen"
      :title="formMode === 'create' ? 'New playlist' : 'Edit playlist'"
      :confirm-label="formMode === 'create' ? 'Create' : 'Save'"
      :initial-name="formMode === 'edit' ? editTarget?.name : undefined"
      :initial-icon="formMode === 'edit' ? (editTarget?.icon ?? undefined) : undefined"
      @confirm="onFormConfirm"
      @cancel="formOpen = false"
    />

    <ConfirmDialog
      :open="deleteTarget !== null"
      title="Delete playlist?"
      :message="deleteMessage"
      confirm-label="Delete"
      danger
      @confirm="onDeleteConfirm"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import PlaylistFormDialog from "../components/PlaylistFormDialog.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import { useScrollMemory } from "../composables/useScrollMemory";
import { usePlaylistStore } from "../stores/playlists";
import type { Playlist } from "../types";

const router = useRouter();
const playlistStore = usePlaylistStore();

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);

const formOpen = ref(false);
const formMode = ref<"create" | "edit">("create");
const editTarget = ref<Playlist | null>(null);
const deleteTarget = ref<Playlist | null>(null);

const deleteMessage = computed(
  () => `"${deleteTarget.value?.name ?? ""}" will be removed. This cannot be undone.`,
);

function openCreate(): void {
  formMode.value = "create";
  editTarget.value = null;
  formOpen.value = true;
}

function openEdit(p: Playlist): void {
  formMode.value = "edit";
  editTarget.value = p;
  formOpen.value = true;
}

async function onFormConfirm(name: string, icon: string): Promise<void> {
  if (formMode.value === "create") {
    await playlistStore.createPlaylist(name, icon);
  } else if (editTarget.value) {
    await playlistStore.renamePlaylist(editTarget.value.id, name, icon);
  }
  formOpen.value = false;
}

async function onDeleteConfirm(): Promise<void> {
  if (deleteTarget.value) await playlistStore.deletePlaylist(deleteTarget.value.id);
  deleteTarget.value = null;
}
</script>

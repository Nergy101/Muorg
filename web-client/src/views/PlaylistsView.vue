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

    <div class="content-col-children min-h-0 flex-1 overflow-y-auto">
      <div v-if="playlistStore.loading" class="flex justify-center py-12">
        <MageIcon name="refresh" class="h-7 w-7 animate-spin text-on-surface-variant" />
      </div>

      <div v-else-if="playlistStore.playlists.length === 0" class="flex flex-col items-center gap-1 py-12">
        <p class="text-body-lg text-on-surface">No playlists yet.</p>
        <p class="text-body-md text-on-surface-variant">Tap + to create one.</p>
      </div>

      <div v-else class="py-2">
        <div
          v-for="p in playlistStore.playlists"
          :key="p.id"
          class="mx-4 mb-2 flex items-center gap-3 rounded-xl bg-surface px-4 py-3"
          @click="router.push({ name: 'playlist', params: { id: String(p.id) } })"
        >
          <span class="text-title-lg">{{ p.icon ?? "🎵" }}</span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-body-lg text-on-surface">{{ p.name }}</p>
            <p class="text-body-sm text-on-surface-variant">
              {{ p.track_count === 1 ? "1 track" : `${p.track_count} tracks` }}
            </p>
          </div>
          <button
            type="button"
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant"
            aria-label="Edit playlist"
            @click.stop="openEdit(p)"
          >
            <MageIcon name="edit" class="h-5 w-5" />
          </button>
          <button
            type="button"
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-error"
            aria-label="Delete playlist"
            @click.stop="deleteTarget = p"
          >
            <MageIcon name="trash" class="h-5 w-5" />
          </button>
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
import { usePlaylistStore } from "../stores/playlists";
import type { Playlist } from "../types";

const router = useRouter();
const playlistStore = usePlaylistStore();

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

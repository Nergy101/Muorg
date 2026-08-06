<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center justify-between px-4">
      <div class="flex min-w-0 items-center gap-2">
        <MageIcon name="dashboard-fill" class="h-5 w-5 text-primary" />
        <span class="truncate text-title-lg text-on-surface">Playlists</span>
      </div>
      <div class="flex items-center gap-1">
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-full text-primary"
          aria-label="New playlist"
          @click="openCreate"
        >
          <MageIcon name="plus" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-full text-primary"
          aria-label="New smart playlist"
          @click="openCreateSmart"
        >
          <MageIcon name="zap" class="h-5 w-5" />
        </button>
      </div>
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
              <p class="flex items-center gap-1 text-label-sm text-on-surface-variant">
                <MageIcon v-if="p.smart_rules" name="zap" class="h-3 w-3 text-primary" />
                {{
                  p.smart_rules
                    ? `Dynamic · ${p.track_count} ${p.track_count === 1 ? "track" : "tracks"}`
                    : p.track_count === 1
                      ? "1 track"
                      : `${p.track_count} tracks`
                }}
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

    <SmartPlaylistDialog
      :open="smartFormOpen"
      :is-editing="smartEditTarget !== null"
      :initial-name="smartEditTarget?.name ?? undefined"
      :initial-icon="smartEditTarget?.icon ?? undefined"
      :initial-rules="smartInitialRules"
      :genres="genres"
      @confirm="onSmartFormConfirm"
      @cancel="smartFormOpen = false"
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
import SmartPlaylistDialog from "../components/SmartPlaylistDialog.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import { useScrollMemory } from "../composables/useScrollMemory";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore, rulesToSmartJson, parseSmartRules } from "../stores/playlists";
import type { Playlist, SmartRule } from "../types";

const router = useRouter();
const playlistStore = usePlaylistStore();
const lib = useLibraryStore();

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);

const genres = computed(() =>
  [...new Set(lib.tracks.map((t) => t.genre).filter((g): g is string => g != null))].sort(),
);

const formOpen = ref(false);
const formMode = ref<"create" | "edit">("create");
const editTarget = ref<Playlist | null>(null);
const deleteTarget = ref<Playlist | null>(null);

const smartFormOpen = ref(false);
const smartEditTarget = ref<Playlist | null>(null);
const smartInitialRules = ref<SmartRule[]>([]);

const deleteMessage = computed(
  () => `"${deleteTarget.value?.name ?? ""}" will be removed. This cannot be undone.`,
);

function openCreate(): void {
  formMode.value = "create";
  editTarget.value = null;
  formOpen.value = true;
}

function openCreateSmart(): void {
  smartEditTarget.value = null;
  smartInitialRules.value = [];
  smartFormOpen.value = true;
}

function openEdit(p: Playlist): void {
  if (p.smart_rules != null) {
    smartEditTarget.value = p;
    smartInitialRules.value = parseSmartRules(p.smart_rules);
    smartFormOpen.value = true;
  } else {
    formMode.value = "edit";
    editTarget.value = p;
    formOpen.value = true;
  }
}

async function onSmartFormConfirm(name: string, icon: string, rules: SmartRule[]): Promise<void> {
  const rulesJson = rulesToSmartJson(rules);
  if (smartEditTarget.value) {
    const t = smartEditTarget.value;
    await playlistStore.renamePlaylist(t.id, name, icon);
    await playlistStore.updateSmartPlaylistRules(t.id, rulesJson);
    smartEditTarget.value = null;
  } else {
    const p = await playlistStore.createSmartPlaylist(name, rulesJson);
    if (icon && icon !== "⚡") await playlistStore.renamePlaylist(p.id, p.name, icon);
  }
  smartFormOpen.value = false;
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

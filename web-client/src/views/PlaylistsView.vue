<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center justify-between gap-4 px-4">
      <!-- Sort control: field pill + direction toggle, same glass style as the
           library toolbar. -->
      <div class="glass-field flex items-center gap-0.5 rounded-full p-0.5">
        <div ref="sortMenuRef" class="relative">
          <button
            type="button"
            class="flex items-center gap-1 rounded-full px-2.5 py-1 text-label-lg text-on-surface"
            aria-label="Sort playlists"
            @click="sortMenuOpen = !sortMenuOpen"
          >
            Sort: {{ sortLabel }}
            <MageIcon name="chevron-down" class="h-3.5 w-3.5" />
          </button>
          <div
            v-if="sortMenuOpen"
            class="absolute left-0 top-full z-30 mt-1 w-40 rounded-xl bg-surface-container py-1 shadow-xl"
          >
            <button
              v-for="opt in SORT_OPTIONS"
              :key="opt.value"
              type="button"
              class="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-body-md text-on-surface"
              @click="selectSort(opt.value)"
            >
              <span>{{ opt.label }}</span>
              <MageIcon v-if="sortKey === opt.value" name="check" class="h-4 w-4 text-primary" />
            </button>
          </div>
        </div>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container/60"
          :aria-label="sortAsc ? 'Sort descending' : 'Sort ascending'"
          @click="sortAsc = !sortAsc"
        >
          <MageIcon :name="sortAsc ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
        </button>
      </div>

      <div class="glass flex items-center overflow-hidden rounded-full">
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center text-on-surface transition-colors lg:hover:bg-on-surface/10"
          aria-label="New playlist"
          @click="openCreate"
        >
          <MageIcon name="plus" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center border-l border-on-surface/15 text-on-surface transition-colors lg:hover:bg-on-surface/10"
          aria-label="New smart playlist"
          @click="openCreateSmart"
        >
          <MageIcon name="zap" class="h-5 w-5" />
        </button>
      </div>
    </div>

    <!-- Not content-col-children: the card grid should use the whole shell on
         desktop, like the library's album grid. -->
    <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <div v-if="playlistStore.loading" class="flex justify-center py-12">
        <span class="dot-loader text-on-surface-variant"><span class="dot" /><span class="dot" /><span class="dot" /></span>
      </div>

      <!-- Preloading every playlist's covers; hold the grid until all are
           ready so the cards appear as one complete set. Note the `.value`:
           usePlaylistCoverReady returns a plain object, so its ComputedRefs
           don't auto-unwrap in templates. -->
      <div v-else-if="!playlistCoverReady.allReady.value" class="flex justify-center py-12">
        <span class="dot-loader text-on-surface-variant"><span class="dot" /><span class="dot" /><span class="dot" /></span>
      </div>

      <div v-else-if="playlistStore.playlists.length === 0" class="flex flex-col items-center gap-1 py-12">
        <p class="text-body-lg text-on-surface">No playlists yet.</p>
        <p class="text-body-md text-on-surface-variant">Tap + to create one.</p>
      </div>

      <div v-else class="px-4 py-2">
        <div
          class="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-[repeat(auto-fill,minmax(200px,1fr))]"
        >
          <PlaylistCard
            v-for="p in visiblePlaylists"
            :key="p.id"
            :playlist="p"
            @open="router.push({ name: 'playlist', params: { id: String(p.id) } })"
            @edit="openEdit(p)"
            @delete="deleteTarget = p"
          />
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
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import PlaylistFormDialog from "../components/PlaylistFormDialog.vue";
import SmartPlaylistDialog from "../components/SmartPlaylistDialog.vue";
import PlaylistCard from "../components/PlaylistCard.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import { useScrollMemory } from "../composables/useScrollMemory";
import { usePlaylistCoverReady } from "../composables/usePlaylistCoverReady";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore, rulesToSmartJson, parseSmartRules } from "../stores/playlists";
import type { Playlist, SmartRule } from "../types";

const router = useRouter();
const playlistStore = usePlaylistStore();
const lib = useLibraryStore();

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);

// ── Sorting ─────────────────────────────────────────────────────────────────
// Pinned playlists always stay first (in pin order); the rest sort by the
// chosen field. The Playlist schema has no created_at, so id (assigned in
// creation order) stands in for creation date.
type PlaylistSortKey = "name" | "created" | "size";
const SORT_OPTIONS: { value: PlaylistSortKey; label: string }[] = [
  { value: "name", label: "Name" },
  { value: "created", label: "Creation date" },
  { value: "size", label: "Size" },
];

const sortKey = ref<PlaylistSortKey>("name");
const sortAsc = ref(true);
const sortMenuOpen = ref(false);
const sortMenuRef = ref<HTMLElement | null>(null);

const sortLabel = computed(() => SORT_OPTIONS.find((o) => o.value === sortKey.value)?.label ?? "Name");

function selectSort(key: PlaylistSortKey): void {
  sortKey.value = key;
  sortMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  if (!sortMenuOpen.value) return;
  const el = sortMenuRef.value;
  if (el && !el.contains(e.target as Node)) sortMenuOpen.value = false;
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));

const visiblePlaylists = computed<Playlist[]>(() => {
  const all = playlistStore.sortedPlaylists;
  const pinned = all.filter((p) => playlistStore.isPinned(p.id));
  const rest = all.filter((p) => !playlistStore.isPinned(p.id));
  const dir = sortAsc.value ? 1 : -1;
  const byKey = (a: Playlist, b: Playlist): number => {
    switch (sortKey.value) {
      case "size":
        return (a.track_count - b.track_count) * dir;
      case "created":
        return (a.id - b.id) * dir;
      default:
        return a.name.localeCompare(b.name) * dir;
    }
  };
  return [...pinned, ...rest.sort(byKey)];
});

// Preload all playlists' covers and hold the grid until every one is ready.
const playlistCoverReady = usePlaylistCoverReady(() => playlistStore.playlists);

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

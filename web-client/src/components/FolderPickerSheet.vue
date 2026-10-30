<template>
  <BottomSheet :open="open" @close="emit('close')">
    <div class="px-6 pb-1 text-title-md text-on-surface">Add folder</div>
    <p class="px-6 pb-3 text-body-sm text-on-surface-variant">
      Browsing the server's disk — this adds a folder on the server, not on this device.
    </p>

    <!-- Breadcrumb -->
    <div class="flex flex-wrap items-center gap-1 px-6 pb-2 text-body-sm">
      <template v-for="(crumb, i) in history" :key="i">
        <button
          type="button"
          class="rounded px-1 py-0.5"
          :class="i === history.length - 1 ? 'text-on-surface' : 'text-primary'"
          :disabled="i === history.length - 1"
          @click="goTo(i)"
        >{{ crumb.label }}</button>
        <MageIcon
          v-if="i < history.length - 1"
          name="chevron-right"
          class="h-3.5 w-3.5 text-on-surface-variant"
        />
      </template>
    </div>

    <!-- Directory listing -->
    <div class="min-h-[12rem]">
      <p v-if="loading" class="py-6 text-center text-body-md text-on-surface-variant">
        Loading…
      </p>
      <p v-else-if="errorMessage" class="px-6 py-6 text-center text-body-md text-error">
        {{ errorMessage }}
      </p>
      <p
        v-else-if="entries.length === 0"
        class="px-6 py-6 text-center text-body-md text-on-surface-variant"
      >
        {{ current.path === null
          ? "Nothing configured to browse on this server — type a path below instead."
          : "No subfolders here." }}
      </p>
      <button
        v-for="entry in entries"
        :key="entry.path"
        type="button"
        class="flex h-14 w-full items-center gap-3 px-6 text-left"
        @click="descend(entry)"
      >
        <MageIcon name="stack" class="h-5 w-5 shrink-0 text-on-surface-variant" />
        <span class="min-w-0 flex-1 truncate text-body-lg text-on-surface">{{ entry.name }}</span>
        <span v-if="entry.is_root" class="text-label-sm text-on-surface-variant">In library</span>
        <MageIcon name="chevron-right" class="h-4 w-4 shrink-0 text-on-surface-variant" />
      </button>
      <p v-if="truncated" class="px-6 py-2 text-body-sm text-on-surface-variant">
        Showing the first {{ entries.length }} subfolders only.
      </p>
    </div>

    <!-- Use the currently browsed folder -->
    <div v-if="current.path !== null" class="border-t border-outline/30 px-6 py-4">
      <p class="truncate text-body-sm text-on-surface-variant">{{ current.path }}</p>
      <button
        type="button"
        class="mt-2 w-full rounded-full bg-primary px-4 py-2.5 text-label-lg text-on-primary disabled:opacity-60"
        :disabled="adding"
        @click="useFolder(current.path)"
      >{{ adding ? "Adding…" : isCurrentAlreadyInLibrary ? "Rescan this folder" : "Use this folder" }}</button>
    </div>

    <!-- Typed-path fallback -->
    <div class="border-t border-outline/30 px-6 py-4">
      <p class="pb-2 text-body-sm text-on-surface-variant">
        Or type the folder's path on the server directly
      </p>
      <div class="flex gap-2">
        <input
          v-model="typedPath"
          type="text"
          placeholder="/path/on/server/music"
          autocomplete="off"
          spellcheck="false"
          class="min-w-0 flex-1 rounded-xl bg-surface-variant px-3 py-2.5 text-body-md text-on-surface outline-none placeholder:text-on-surface-variant"
          @keyup.enter="useFolder(typedPath.trim())"
        />
        <button
          type="button"
          class="shrink-0 rounded-full bg-primary px-4 py-2.5 text-label-lg text-on-primary disabled:opacity-50"
          :disabled="typedPath.trim().length === 0 || adding"
          @click="useFolder(typedPath.trim())"
        >Add</button>
      </div>
    </div>

    <div class="h-2" />
  </BottomSheet>
</template>

<script setup lang="ts">
/**
 * Browses the server's own directories (via `GET /api/admin/browse`) so a
 * web client — which cannot open a native OS file dialog the way the desktop
 * app does — can still point the library at a folder on the server's disk.
 * "Use this folder" upserts the folder as a library root (`api.rescan`,
 * which adds it if new or resurrects it if soft-deleted) and scans it.
 */
import { computed, ref, watch } from "vue";
import BottomSheet from "./BottomSheet.vue";
import MageIcon from "./MageIcon.vue";
import { useFoldersStore } from "../stores/folders";
import { ApiError } from "@shared/api";
import type { BrowseEntry } from "@shared/api";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; added: [] }>();

const folders = useFoldersStore();

interface Crumb {
  label: string;
  path: string | null;
}

const history = ref<Crumb[]>([{ label: "Start", path: null }]);
const current = computed(() => history.value[history.value.length - 1]);

const entries = ref<BrowseEntry[]>([]);
const truncated = ref(false);
const loading = ref(false);
const errorMessage = ref<string | null>(null);
const typedPath = ref("");
const adding = ref(false);

const isCurrentAlreadyInLibrary = computed(
  () => current.value.path !== null && folders.roots.includes(current.value.path),
);

function describeBrowseError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 403) return "That folder is outside the directories this server allows browsing.";
    if (e.status === 404) return "That folder no longer exists on the server.";
    if (e.status === 401) return "Not authorized — check the server connection in Settings.";
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function load(path: string | null): Promise<void> {
  loading.value = true;
  errorMessage.value = null;
  try {
    const res = await folders.browse(path ?? undefined);
    entries.value = res.entries;
    truncated.value = res.truncated;
  } catch (e) {
    entries.value = [];
    truncated.value = false;
    errorMessage.value = describeBrowseError(e);
  } finally {
    loading.value = false;
  }
}

function descend(entry: BrowseEntry): void {
  history.value = [...history.value, { label: entry.name, path: entry.path }];
  void load(entry.path);
}

function goTo(index: number): void {
  history.value = history.value.slice(0, index + 1);
  void load(history.value[index].path);
}

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return;
    history.value = [{ label: "Start", path: null }];
    typedPath.value = "";
    void load(null);
  },
  { immediate: true },
);

async function useFolder(path: string): Promise<void> {
  if (!path) return;
  adding.value = true;
  try {
    await folders.addFolder(path);
    emit("added");
    emit("close");
  } catch {
    // The store already surfaced a toast with the server's message.
  } finally {
    adding.value = false;
  }
}
</script>

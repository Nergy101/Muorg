<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import { open } from "@tauri-apps/plugin-dialog";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { roots, loading } = storeToRefs(store);
const { backendMode } = storeToRefs(settingsStore);
const isLocal = computed(() => backendMode.value === "local");

// ── Tooltip ────────────────────────────────────────────────────────────────

const tooltipPopover = ref<{
  text: string;
  x: number;
  y: number;
  isPath?: boolean;
  position: "right" | "below";
} | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent, isPath = false) {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (isPath) {
    tooltipPopover.value = { text, x: rect.right + 8, y: rect.top + rect.height / 2, isPath: true, position: "right" };
  } else {
    tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6, position: "below" };
  }
}

function scheduleHideTooltip() {
  tooltipHideTimeout = setTimeout(() => { tooltipPopover.value = null; tooltipHideTimeout = null; }, 100);
}

function cancelHideTooltip() {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function hideTooltip() {
  tooltipPopover.value = null;
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

// ── Data ───────────────────────────────────────────────────────────────────

function pathNorm(p: string): string {
  return p.replace(/\\/g, "/").replace(/\/+$/, "") || "/";
}

const sortedRoots = computed(() =>
  [...roots.value].sort((a, b) => {
    const nameA = a.split(/[/\\]/).pop() || a;
    const nameB = b.split(/[/\\]/).pop() || b;
    return nameA.localeCompare(nameB, undefined, { sensitivity: "base" });
  }),
);

const trackCountByRoot = computed(() => {
  const list = store.tracks;
  const rootsList = roots.value;
  const out: Record<string, number> = {};
  for (const r of rootsList) out[r] = 0;
  const normRoots = rootsList.map((r) => pathNorm(r));
  for (const t of list) {
    const tNorm = pathNorm(t.path);
    for (let i = 0; i < normRoots.length; i++) {
      const r = normRoots[i];
      if (tNorm === r || tNorm.startsWith(r + "/")) {
        out[rootsList[i]] += 1;
        break;
      }
    }
  }
  return out;
});

function folderInfoTooltip(root: string): string {
  const count = trackCountByRoot.value[root];
  return count !== undefined ? `${root}\n${count.toLocaleString()} tracks` : root;
}

const allRootsHidden = computed(() =>
  roots.value.length > 0 && roots.value.every((r) => store.isRootHidden(r)),
);

// ── Actions ────────────────────────────────────────────────────────────────

async function handleAddFolder() {
  const selected = await open({ directory: true, multiple: true });
  if (!selected) return;
  const paths = Array.isArray(selected) ? selected : [selected];
  if (!paths.length) return;
  try {
    if (paths.length === 1) await store.addFolder(paths[0]);
    else await store.addFolders(paths);
    await playlistStore.loadPlaylists();
  } catch { /* error shown in store */ }
}

async function handleRescan(rootPath: string) {
  try {
    await store.rescan(rootPath);
    await playlistStore.loadPlaylists();
  } catch { /* error shown in store */ }
}

async function handleRemoveFolder(rootPath: string) {
  if (!confirm(`Remove "${rootPath.split(/[/\\]/).pop() || rootPath}" from library? Files on disk are not deleted.`)) return;
  try {
    await store.removeFolder(rootPath);
    await playlistStore.loadPlaylists();
  } catch { /* error shown in store */ }
}

function handleHideAll() { store.hideAllRoots(); }
function handleShowAll() { store.showAllRoots(); }

async function handleRefreshAll() {
  await store.refreshAll();
  await playlistStore.loadPlaylists();
}

async function handleRemoveAll() {
  if (!confirm("Remove all folders from the library? Files on disk are not deleted.")) return;
  await store.removeAllFolders();
  await playlistStore.loadPlaylists();
}
</script>

<template>
  <div>
    <button
      v-if="isLocal"
      type="button"
      class="mb-2 flex w-full items-center gap-2 rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
      :disabled="loading"
      @click="handleAddFolder"
    >
      <FeatherIcon name="folder-plus" class="h-4 w-4 shrink-0 text-stone-400" />
      Add folder
    </button>

    <!-- Empty state (Online mode): no folders reachable, prompt to configure connection -->
    <div v-if="!loading && !roots.length && !isLocal" class="mt-3 rounded-lg border border-stone-700 bg-stone-800/60 px-3 py-4 text-center">
      <FeatherIcon name="wifi-off" class="mx-auto mb-2 h-5 w-5 text-stone-500" />
      <p class="text-xs font-medium text-stone-400">No folders loaded</p>
      <p class="mt-1 text-[11px] text-stone-500 leading-relaxed">
        Make sure the server is reachable and your connection is configured.
      </p>
      <button
        type="button"
        class="mt-3 inline-flex items-center gap-1.5 rounded border border-stone-600 bg-stone-700 px-3 py-1.5 text-xs text-stone-300 hover:bg-stone-600"
        @click="settingsStore.openSettings('connection')"
      >
        <FeatherIcon name="settings" class="h-3 w-3" />
        Connection settings
      </button>
    </div>

    <div v-if="roots.length && isLocal" class="mb-2 flex items-center justify-center gap-0.5">
      <span
        class="inline-flex"
        @mouseenter="showTooltip(allRootsHidden ? 'Show all in table' : 'Hide all from table', $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-200"
          :aria-label="allRootsHidden ? 'Show all in table' : 'Hide all from table'"
          @click="allRootsHidden ? handleShowAll() : handleHideAll()"
        >
          <FeatherIcon v-if="allRootsHidden" name="eye" class="h-3.5 w-3.5" />
          <FeatherIcon v-else name="eye-off" class="h-3.5 w-3.5" />
        </button>
      </span>
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Refresh all folders', $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-200"
          aria-label="Refresh all folders"
          @click="handleRefreshAll"
        >
          <FeatherIcon name="refresh-cw" class="h-3.5 w-3.5" />
        </button>
      </span>
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Remove all folders from library', $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-red-400"
          aria-label="Remove all folders from library"
          @click="handleRemoveAll"
        >
          <FeatherIcon name="x" class="h-3.5 w-3.5" />
        </button>
      </span>
    </div>

    <ul v-if="roots.length" class="space-y-1">
      <li
        v-for="root in sortedRoots"
        :key="root"
        class="group/parent flex items-center gap-1 rounded border border-stone-700 bg-stone-800/50 px-2 py-1.5"
      >
        <span class="min-w-0 flex-1 truncate text-xs text-stone-300">
          {{ root.split(/[/\\]/).pop() || root }}
        </span>
        <template v-if="isLocal">
          <span
            class="inline-flex shrink-0"
            @mouseenter="showTooltip(store.isRootHidden(root) ? 'Show in table' : 'Hide from table', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              :class="{ 'opacity-50': store.isRootHidden(root) }"
              :aria-label="store.isRootHidden(root) ? 'Show folder in table' : 'Hide folder from table'"
              @click="store.toggleRootVisibility(root)"
            >
              <FeatherIcon v-if="!store.isRootHidden(root)" name="eye" class="h-3.5 w-3.5" />
              <FeatherIcon v-else name="eye-off" class="h-3.5 w-3.5" />
            </button>
          </span>
        </template>
        <span
          class="inline-flex h-6 w-6 shrink-0 cursor-help items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-300"
          aria-label="Show full path and track count"
          @mouseenter="showTooltip(folderInfoTooltip(root), $event, true)"
          @mouseleave="scheduleHideTooltip"
        >
          <FeatherIcon name="info" class="h-3.5 w-3.5" />
        </span>
        <template v-if="isLocal">
          <span
            class="inline-flex"
            @mouseenter="showTooltip('Rescan folder', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Rescan folder"
              @click="handleRescan(root)"
            >
              <FeatherIcon name="refresh-cw" class="h-3.5 w-3.5" />
            </button>
          </span>
          <span
            class="inline-flex"
            @mouseenter="showTooltip('Remove from library', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-red-600 hover:text-white"
              aria-label="Remove from library"
              @click="handleRemoveFolder(root)"
            >
              <FeatherIcon name="x" class="h-3.5 w-3.5" />
            </button>
          </span>
        </template>
      </li>
    </ul>
  </div>

  <Teleport to="body">
    <div
      v-if="tooltipPopover"
      class="fixed z-[200] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
      :class="{ 'max-w-[320px] break-all font-mono text-stone-300 whitespace-pre-line': tooltipPopover.isPath }"
      :style="
        tooltipPopover.position === 'right'
          ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateY(-50%)' }
          : { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }
      "
      @mouseenter="cancelHideTooltip"
      @mouseleave="hideTooltip"
    >
      {{ tooltipPopover.text }}
    </div>
  </Teleport>
</template>

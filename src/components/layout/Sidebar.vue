<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { open } from "@tauri-apps/plugin-dialog";
import FeatherIcon from "../shared/FeatherIcon.vue";

defineProps<{ collapsed: boolean }>();
const emit = defineEmits<{ toggle: [] }>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { roots, loading, error, tracks, reportFilter } = storeToRefs(store);
const { missingMetadataFields, hideReportsSection } = storeToRefs(settingsStore);

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
    tooltipPopover.value = {
      text,
      x: rect.right + 8,
      y: rect.top + rect.height / 2,
      isPath: true,
      position: "right",
    };
  } else {
    tooltipPopover.value = {
      text,
      x: rect.left + rect.width / 2,
      y: rect.bottom + 6,
      position: "below",
    };
  }
}

function scheduleHideTooltip() {
  tooltipHideTimeout = setTimeout(() => {
    tooltipPopover.value = null;
    tooltipHideTimeout = null;
  }, 100);
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

function isFieldMissing(
  track: import("../../types").CatalogTrack,
  field: import("../../stores/settings").MissingMetadataField,
): boolean {
  const v = track[field as keyof typeof track];
  if (field === "year" || field === "track_number" || field === "disc_number") {
    return v == null;
  }
  return v == null || String(v).trim() === "";
}

const missingMetadataCount = computed(() => {
  const fields = missingMetadataFields.value;
  if (!fields.length) return 0;
  return tracks.value.filter((t) => fields.some((f) => isFieldMissing(t, f))).length;
});

const duplicateCount = computed(() => {
  const list = tracks.value;
  if (!list.length) return 0;
  const keyFor = (t: import("../../types").CatalogTrack) =>
    `${(t.artist ?? "").toLowerCase()}|${(t.album ?? "").toLowerCase()}|${(t.title ?? "").toLowerCase()}`;
  const map = new Map<string, number>();
  for (const t of list) {
    const key = keyFor(t);
    if (!key.trim()) continue;
    map.set(key, (map.get(key) ?? 0) + 1);
  }
  let total = 0;
  for (const count of map.values()) {
    if (count > 1) total += count - 1;
  }
  return total;
});

const missingAlbumCoverCount = computed(() =>
  tracks.value.filter((t) => !(t.has_cover ?? false)).length
);

/** Normalize path for prefix match (forward slashes, no trailing slash except for root). */
function pathNorm(p: string): string {
  const s = p.replace(/\\/g, "/").replace(/\/+$/, "") || "/";
  return s;
}

/** Roots sorted alphabetically by folder name (last path segment). */
const sortedRoots = computed(() =>
  [...roots.value].sort((a, b) => {
    const nameA = a.split(/[/\\]/).pop() || a;
    const nameB = b.split(/[/\\]/).pop() || b;
    return nameA.localeCompare(nameB, undefined, { sensitivity: "base" });
  }),
);

/** Track count per library root (folder path). */
const trackCountByRoot = computed(() => {
  const list = tracks.value;
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

/** True when every folder is hidden from the table (so we show "Show all" instead of "Hide all"). */
const allRootsHidden = computed(() => {
  const rootsList = roots.value;
  return rootsList.length > 0 && rootsList.every((r) => store.isRootHidden(r));
});

/** Tooltip text for folder info (i): full path + track count. */
function folderInfoTooltip(root: string): string {
  const count = trackCountByRoot.value[root];
  const pathLine = root;
  if (count !== undefined) {
    return `${pathLine}\n${count.toLocaleString()} tracks`;
  }
  return pathLine;
}

function openMissingMetadataReport() {
  const kind = reportFilter.value === "missing_metadata" ? null : "missing_metadata";
  store.setReportFilter(kind);
  store.clearSelection();
}

function openDuplicateReport() {
  const kind = reportFilter.value === "duplicates" ? null : "duplicates";
  store.setReportFilter(kind);
  store.clearSelection();
}

function openMissingAlbumCoverReport() {
  const kind = reportFilter.value === "missing_album_cover" ? null : "missing_album_cover";
  store.setReportFilter(kind);
  store.clearSelection();
}

async function handleRefreshReports() {
  try {
    await store.loadTracks();
  } catch {
    // error shown in store
  }
}

onMounted(async () => {
  await store.loadRoots();
  await store.loadTracks();
});

async function handleAddFolder() {
  const selected = await open({
    directory: true,
    multiple: true,
  });
  if (!selected) return;
  const paths = Array.isArray(selected) ? selected : [selected];
  if (paths.length === 0) return;
  try {
    if (paths.length === 1) {
      await store.addFolder(paths[0]);
    } else {
      await store.addFolders(paths);
    }
  } catch {
    // error shown in store
  }
}

async function handleRescan(rootPath: string) {
  try {
    await store.rescan(rootPath);
  } catch {
    // error shown in store
  }
}

async function handleRemoveFolder(rootPath: string) {
  if (!confirm(`Remove "${rootPath.split(/[/\\]/).pop() || rootPath}" from library? Files on disk are not deleted.`)) {
    return;
  }
  try {
    await store.removeFolder(rootPath);
  } catch {
    // error shown in store
  }
}

function handleHideAll() {
  store.hideAllRoots();
}

function handleShowAll() {
  store.showAllRoots();
}

async function handleRefreshAll() {
  try {
    await store.refreshAll();
  } catch {
    // error shown in store
  }
}

async function handleRemoveAll() {
  if (!confirm("Remove all folders from the library? Files on disk are not deleted.")) return;
  try {
    await store.removeAllFolders();
  } catch {
    // error shown in store
  }
}
</script>

<template>
  <aside
    :class="['flex h-full min-h-0 flex-col border-r border-stone-700 bg-stone-800/80 transition-[width] duration-200', collapsed ? 'w-12' : 'w-64']"
  >
    <!-- Collapsed: only expand button -->
    <template v-if="collapsed">
      <div class="flex flex-1 flex-col items-center justify-start pt-3">
        <span
          class="inline-flex"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded p-2 text-stone-500 hover:bg-stone-700 hover:text-stone-200"
            aria-label="Expand library"
            @click="emit('toggle')"
          >
            <FeatherIcon name="chevrons-right" class="h-5 w-5" />
          </button>
        </span>
      </div>
    </template>
    <!-- Expanded: Library header + list -->
    <template v-else>
      <div class="flex min-w-0 shrink-0 items-center gap-2 border-b border-stone-700 px-2 py-1.5">
        <div class="min-w-0 flex-1" />
        <div
          v-if="roots.length"
          class="flex shrink-0 items-center gap-0.5"
        >
          <span
            class="inline-flex"
            @mouseenter="showTooltip(allRootsHidden ? 'Show all in table' : 'Hide all from table', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
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
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-50"
              :disabled="loading"
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
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-red-600 hover:text-white disabled:opacity-50"
              :disabled="loading"
              aria-label="Remove all folders from library"
              @click="handleRemoveAll"
            >
              <FeatherIcon name="x" class="h-3.5 w-3.5" />
            </button>
          </span>
        </div>
        <div class="flex min-w-0 flex-1 justify-end">
          <span
            class="inline-flex"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-stone-200"
              aria-label="Collapse library"
              @click="emit('toggle')"
            >
              <FeatherIcon name="chevrons-left" class="h-4 w-4" />
            </button>
          </span>
        </div>
      </div>
      <div class="table-scroll-container min-h-0 flex-1 overflow-y-auto p-2 space-y-3">
        <div>
          <button
            type="button"
            class="mb-2 flex w-full items-center gap-2 rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
            :disabled="loading"
            @click="handleAddFolder"
          >
            <FeatherIcon name="folder-plus" class="h-4 w-4 shrink-0 text-stone-400" />
            Add folder
          </button>
          <ul v-if="roots.length" class="space-y-1">
            <li
              v-for="root in sortedRoots"
              :key="root"
              class="group/parent flex items-center gap-1 rounded border border-stone-700 bg-stone-800/50 px-2 py-1.5"
            >
              <span class="min-w-0 flex-1 truncate text-xs text-stone-300">
                {{ root.split(/[/\\]/).pop() || root }}
              </span>
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
              <span
                class="inline-flex h-6 w-6 shrink-0 cursor-help items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-300"
                aria-label="Show full path and track count"
                @mouseenter="showTooltip(folderInfoTooltip(root), $event, true)"
                @mouseleave="scheduleHideTooltip"
              >
                <FeatherIcon name="info" class="h-3.5 w-3.5" />
              </span>
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
                @mouseenter="showTooltip('Remove from library (files stay on disk)', $event)"
                @mouseleave="scheduleHideTooltip"
              >
                <button
                  type="button"
                  class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-red-600 hover:text-white"
                  aria-label="Remove from library (files stay on disk)"
                  @click="handleRemoveFolder(root)"
                >
                  <FeatherIcon name="x" class="h-3.5 w-3.5" />
                </button>
              </span>
            </li>
          </ul>
        </div>
        <div v-if="!hideReportsSection" class="border-t border-stone-700 pt-2">
          <div class="mb-1 flex items-center justify-between">
            <p class="text-xs font-semibold uppercase tracking-wide text-stone-500">Reports</p>
            <span
              class="inline-flex"
              @mouseenter="showTooltip('Refresh reports', $event)"
              @mouseleave="scheduleHideTooltip"
            >
              <button
                type="button"
                class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-50"
                aria-label="Refresh reports"
                :disabled="loading"
                @click="handleRefreshReports"
              >
                <FeatherIcon name="refresh-cw" class="h-3.5 w-3.5" />
              </button>
          </span>
        </div>
        <div class="space-y-1 text-xs">
            <button
              type="button"
              class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
              :class="reportFilter === 'missing_metadata' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
              @click="openMissingMetadataReport"
            >
              <span class="flex items-center gap-1.5">
                <span class="inline-flex h-4 w-4 items-center justify-center rounded bg-amber-500/20 text-amber-300">
                  !
                </span>
                <span>Missing metadata</span>
              </span>
              <span class="text-[0.7rem] text-stone-400">{{ missingMetadataCount }}</span>
            </button>
            <button
              type="button"
              class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
              :class="reportFilter === 'duplicates' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
              @click="openDuplicateReport"
            >
              <span class="flex items-center gap-1.5">
                <span class="inline-flex h-4 w-4 items-center justify-center rounded bg-red-500/20 text-red-300">
                  ≈
                </span>
                <span>Duplicates</span>
              </span>
              <span class="text-[0.7rem] text-stone-400">{{ duplicateCount }}</span>
            </button>
            <button
              type="button"
              class="flex w-full items-center justify-between rounded px-2 py-1 text-left"
              :class="reportFilter === 'missing_album_cover' ? 'bg-stone-700 text-stone-100' : 'text-stone-300 hover:bg-stone-800/70'"
              @click="openMissingAlbumCoverReport"
            >
              <span class="flex items-center gap-1.5">
                <span class="inline-flex h-4 w-4 items-center justify-center rounded bg-stone-500/20 text-stone-300">
                  🖼
                </span>
                <span>Missing album cover</span>
              </span>
              <span class="text-[0.7rem] text-stone-400">{{ missingAlbumCoverCount }}</span>
            </button>
          </div>
        </div>
      </div>
      <div v-if="error" class="mt-2 border-t border-stone-700 pt-2 text-xs text-red-400">
        {{ error }}
      </div>
    </template>
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
  </aside>
</template>


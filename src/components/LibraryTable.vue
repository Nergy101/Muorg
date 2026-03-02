<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import type { CatalogTrack } from "../types";
import TrackAlbumArt from "./TrackAlbumArt.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { filteredTracks, selectedTrackIds, searchQuery, groupBy, coverCache } = storeToRefs(store);
const { defaultGroupsExpanded } = storeToRefs(settingsStore);

/** Set of group keys that are expanded. */
const expandedGroups = ref<Set<string>>(new Set());
/** True after we've applied defaultGroupsExpanded once for the current groupBy (so we don't re-apply on every groupedRows update). */
const appliedDefaultForSession = ref(false);
const multiSelectMode = ref(false);

/** Index into visibleRows for keyboard focus. -1 when no row focused. */
const focusedRowIndex = ref(-1);
const tableContainerRef = ref<HTMLDivElement | null>(null);
const searchInputRef = ref<HTMLInputElement | null>(null);

type VisibleRow =
  | { type: "group"; key: string; group: { key: string; label: string; tracks: CatalogTrack[] } }
  | { type: "track"; track: CatalogTrack };

/** Flat list of focusable rows (group headers and tracks) in display order. */
const visibleRows = computed((): VisibleRow[] => {
  const groups = groupedRows.value;
  if (!groups) {
    return filteredTracks.value.map((track) => ({ type: "track", track }));
  }
  const out: VisibleRow[] = [];
  for (const group of groups) {
    out.push({ type: "group", key: group.key, group });
    if (expandedGroups.value.has(group.key)) {
      for (const t of group.tracks) out.push({ type: "track", track: t });
    }
  }
  return out;
});

const tooltipPopover = ref<{ text: string; x: number; y: number } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent) {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6 };
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

function formatDuration(secs: number | null): string {
  if (secs == null) return "—";
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function isSelected(id: number): boolean {
  return selectedTrackIds.value.includes(id);
}

/** Select a row: in multi-select mode toggle; otherwise select only this track. */
function selectRow(t: CatalogTrack) {
  if (multiSelectMode.value) {
    store.toggleSelection(t.id);
  } else {
    store.clearSelection();
    store.toggleSelection(t.id);
  }
}

/** Groups for table: { key, label, tracks }[] when groupBy !== 'none', else null. */
const groupedRows = computed(() => {
  const by = groupBy.value;
  const tracks = filteredTracks.value;
  if (by === "none" || !tracks.length) return null;
  const map = new Map<string, CatalogTrack[]>();
  for (const t of tracks) {
    const key = by === "artist" ? (t.artist ?? "—") : by === "album" ? (t.album ?? "—") : "";
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(t);
  }
  const keys = [...map.keys()].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));
  return keys.map((key) => ({ key, label: key, tracks: map.get(key)! }));
});

/** When grouping by album, per-group cover to show in header (same for all tracks, or null if mixed/none). */
const groupCovers = computed(() => {
  if (groupBy.value !== "album") return {} as Record<string, string | null>;
  const cache = coverCache.value;
  const groups = groupedRows.value;
  if (!groups) return {};
  const result: Record<string, string | null> = {};
  for (const group of groups) {
    const covers = group.tracks
      .map((t) => cache[t.path])
      .filter((c): c is string => c != null && c !== "");
    const unique = [...new Set(covers)];
    result[group.key] = unique.length <= 1 ? (unique[0] ?? null) : null;
  }
  return result;
});

watch(groupBy, (by) => {
  if (by === "none") return;
  appliedDefaultForSession.value = false;
  const groups = groupedRows.value;
  expandedGroups.value = defaultGroupsExpanded.value && groups?.length
    ? new Set(groups.map((g) => g.key))
    : new Set();
  if (groups?.length) appliedDefaultForSession.value = true;
}, { immediate: true });

watch(groupedRows, (groups) => {
  if (!groups?.length) return;
  const keys = new Set(groups.map((g) => g.key));
  if (!appliedDefaultForSession.value) {
    appliedDefaultForSession.value = true;
    expandedGroups.value = defaultGroupsExpanded.value
      ? new Set(keys)
      : new Set();
    return;
  }
  const next = new Set<string>();
  for (const key of expandedGroups.value) {
    if (keys.has(key)) next.add(key);
  }
  expandedGroups.value = next;
});

// When typing in the search bar, automatically expand all groups so matches are visible.
watch(searchQuery, (q) => {
  if (groupBy.value === "none") return;
  if (!q.trim()) return;
  const groups = groupedRows.value;
  if (!groups?.length) return;
  expandedGroups.value = new Set(groups.map((g) => g.key));
});

/** Fetch covers for all tracks in album groups so group header art can show. */
watch(
  () => (groupBy.value === "album" ? groupedRows.value : null),
  (groups) => {
    if (!groups) return;
    for (const g of groups) {
      for (const t of g.tracks) store.fetchCover(t.path);
    }
  },
  { immediate: true }
);

function isGroupExpanded(key: string): boolean {
  return expandedGroups.value.has(key);
}

function toggleGroup(key: string) {
  const next = new Set(expandedGroups.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedGroups.value = next;
}

function collapseAll() {
  expandedGroups.value = new Set();
}

function expandAll() {
  const groups = groupedRows.value;
  expandedGroups.value = groups ? new Set(groups.map((g) => g.key)) : new Set();
}

// Clamp focus when visible rows change (e.g. expand/collapse)
watch(visibleRows, (rows) => {
  if (focusedRowIndex.value >= rows.length) {
    focusedRowIndex.value = rows.length > 0 ? rows.length - 1 : -1;
  }
});

function focusNext() {
  const rows = visibleRows.value;
  if (!rows.length) return;
  focusedRowIndex.value = Math.min(focusedRowIndex.value + 1, rows.length - 1);
  scrollFocusedRowIntoView();
}

function focusPrev() {
  const rows = visibleRows.value;
  if (!rows.length) return;
  if (focusedRowIndex.value <= 0) {
    focusedRowIndex.value = 0;
  } else {
    focusedRowIndex.value -= 1;
  }
  scrollFocusedRowIntoView();
}

function scrollFocusedRowIntoView() {
  nextTick(() => {
    const el = tableContainerRef.value?.querySelector(`[data-row-index="${focusedRowIndex.value}"]`);
    (el as HTMLElement)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  });
}

function onTableKeydown(e: KeyboardEvent) {
  const rows = visibleRows.value;
  if (!rows.length) return;
  if (e.key === "ArrowDown") {
    e.preventDefault();
    if (focusedRowIndex.value < 0) focusedRowIndex.value = 0;
    else focusNext();
    return;
  }
  if (e.key === "ArrowUp") {
    e.preventDefault();
    if (focusedRowIndex.value < 0) focusedRowIndex.value = rows.length - 1;
    else focusPrev();
    return;
  }
  if (e.key === " ") {
    if (focusedRowIndex.value < 0) return;
    e.preventDefault();
    const row = rows[focusedRowIndex.value];
    if (row.type === "group") {
      toggleGroup(row.key);
    } else {
      selectRow(row.track);
    }
  }
}

function onGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === "f") {
    e.preventDefault();
    searchInputRef.value?.focus();
  }
}

onMounted(() => {
  document.addEventListener("keydown", onGlobalKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
});
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <div class="flex flex-wrap items-center gap-3 border-b border-stone-700 px-4 py-2">
      <input
        ref="searchInputRef"
        :value="searchQuery"
        type="search"
        placeholder="Search title, artist, album…"
        class="min-w-[200px] rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200 placeholder-stone-500"
        @input="store.setSearchQuery(($event.target as HTMLInputElement).value)"
      />
      <select
        :value="groupBy"
        class="rounded border border-stone-600 bg-stone-800 px-2.5 py-1.5 text-sm text-stone-200"
        @change="store.setGroupBy(($event.target as HTMLSelectElement).value as 'none' | 'artist' | 'album')"
      >
        <option value="none">No grouping</option>
        <option value="artist">Group by artist</option>
        <option value="album">Group by album</option>
      </select>
      <template v-if="groupedRows?.length">
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Expand all groups', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded border border-stone-600 bg-stone-800 p-1.5 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            aria-label="Expand all groups"
            @click="expandAll"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7 10l5 5 5-5M7 16l5 5 5-5" />
            </svg>
          </button>
        </span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Collapse all groups', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded border border-stone-600 bg-stone-800 p-1.5 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            aria-label="Collapse all groups"
            @click="collapseAll"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7 14l5-5 5 5M7 8l5-5 5 5" />
            </svg>
          </button>
        </span>
      </template>
    </div>
    <div
      ref="tableContainerRef"
      tabindex="0"
      class="table-scroll-container flex-1 overflow-auto outline-none"
      @keydown="onTableKeydown"
    >
      <table class="table-with-scroll-gutter w-full min-w-[800px] border-collapse text-left text-sm">
        <thead class="sticky top-0 z-10 bg-stone-800">
          <tr class="border-b border-stone-600">
            <th class="w-8 border-r border-stone-700 p-2">
              <label class="flex cursor-pointer items-center gap-1.5 text-xs text-stone-400">
                <input
                  v-model="multiSelectMode"
                  type="checkbox"
                  class="rounded border-stone-600"
                />
                Multi-select
              </label>
            </th>
            <th class="w-10 border-r border-stone-700 p-2"></th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Title</th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Artist</th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Album</th>
            <th class="w-20 border-r border-stone-700 p-2 font-medium text-stone-400">Year</th>
            <th class="w-16 border-r border-stone-700 p-2 font-medium text-stone-400">Duration</th>
            <th class="w-16 border-r border-stone-700 p-2 font-medium text-stone-400">Format</th>
            <th class="max-w-[200px] truncate border-stone-700 p-2 font-medium text-stone-400">Path</th>
          </tr>
        </thead>
        <tbody>
          <template v-if="visibleRows.length">
            <template v-for="(row, i) in visibleRows" :key="row.type === 'group' ? row.key : row.track.id">
              <tr
                v-if="row.type === 'group'"
                :data-row-index="i"
                class="cursor-pointer font-medium text-stone-400 hover:bg-stone-700/80"
                :class="focusedRowIndex === i ? 'bg-stone-700/80 ring-1 ring-inset ring-stone-400' : 'bg-stone-800/80'"
                @click="focusedRowIndex = i; toggleGroup(row.key)"
              >
                <td colspan="9" class="border-b border-stone-600 p-2">
                  <span class="inline-flex items-center gap-2">
                    <span
                      v-if="groupBy === 'album' && groupCovers[row.key]"
                      class="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded bg-stone-800"
                    >
                      <img
                        :src="`data:image/jpeg;base64,${groupCovers[row.key]}`"
                        alt=""
                        class="h-full w-full object-cover"
                      />
                    </span>
                    <span class="inline-block w-4 shrink-0 text-stone-500" aria-hidden="true">
                      {{ isGroupExpanded(row.key) ? "▼" : "▶" }}
                    </span>
                    {{ row.group.label }}
                    <span class="ml-1 text-stone-500">({{ row.group.tracks.length }})</span>
                  </span>
                </td>
              </tr>
              <tr
                v-else
                :data-row-index="i"
                class="border-b border-stone-700/50 hover:bg-stone-800/50"
                :class="[
                  { 'bg-stone-700/40': isSelected(row.track.id) && focusedRowIndex !== i },
                  { 'bg-stone-600/50 ring-1 ring-inset ring-stone-400': isSelected(row.track.id) && focusedRowIndex === i },
                  { 'bg-stone-800 ring-1 ring-inset ring-stone-400': !isSelected(row.track.id) && focusedRowIndex === i },
                ]"
                @click="focusedRowIndex = i; selectRow(row.track)"
              >
                <td class="border-r border-stone-700 p-2">
                  <input
                    type="checkbox"
                    :checked="isSelected(row.track.id)"
                    class="rounded border-stone-600"
                    @click.stop="focusedRowIndex = i; selectRow(row.track)"
                  />
                </td>
                <td class="border-r border-stone-700 p-2">
                  <TrackAlbumArt :path="row.track.path" />
                </td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.title ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.artist ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.album ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-300">{{ row.track.year ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-300">{{ formatDuration(row.track.duration_secs) }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-400">{{ row.track.format }}</td>
                <td class="max-w-[200px] truncate p-2 text-stone-500" :title="row.track.path">{{ row.track.path }}</td>
              </tr>
            </template>
          </template>
          <tr v-else class="text-stone-500">
            <td colspan="9" class="p-6 text-center">
              {{ store.searchQuery.trim() ? "No tracks match the search." : "No tracks. Add a folder to scan MP3/FLAC files." }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Teleport to="body">
      <div
        v-if="tooltipPopover"
        class="fixed z-[200] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        :style="{ left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }"
        @mouseenter="cancelHideTooltip"
        @mouseleave="hideTooltip"
      >
        {{ tooltipPopover.text }}
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.table-scroll-container {
  scrollbar-gutter: stable;
  scrollbar-color: #5b7c32 #2d2d2d;
}

/* Keep table (and sticky header) left of the scrollbar so nothing overlaps it */
.table-with-scroll-gutter {
  margin-right: 14px;
  width: calc(100% - 14px);
  max-width: calc(100% - 14px);
}

.table-scroll-container::-webkit-scrollbar {
  width: 12px;
  height: 12px;
}

.table-scroll-container::-webkit-scrollbar-track {
  background: #2d2d2d;
}

.table-scroll-container::-webkit-scrollbar-thumb {
  background: #5b7c32;
  border-radius: 6px;
}

.table-scroll-container::-webkit-scrollbar-thumb:hover {
  background: #6d8f3d;
}

</style>

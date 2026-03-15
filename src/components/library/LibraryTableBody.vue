<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { CatalogTrack } from "../../types";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();

const { filteredTracks, selectedTrackIds, groupBy, currentPlayingTrackId, multiSelectMode } = storeToRefs(store);
const {
  defaultGroupsExpanded,
  navWrap,
  navFocusFollowsMouse,
  tableDensity,
  tableColAlbumArt,
  tableColYear,
  tableColDuration,
  tableColFormat,
  tableColPath,
  tableColWidths,
  groupHeaderAlbumArt,
  hideWikipediaCoverSearch,
} = storeToRefs(settingsStore);

type GroupRow = {
  key: string;
  label: string;
  tracks: CatalogTrack[];
  artist?: string;
};

type VisibleRow =
  | { type: "group"; key: string; group: GroupRow }
  | { type: "track"; track: CatalogTrack };

function formatDuration(secs: number | null): string {
  if (secs == null) return "—";
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function isSelected(id: number): boolean {
  return selectedTrackIds.value.includes(id);
}

function selectRow(t: CatalogTrack) {
  if (multiSelectMode.value) {
    store.toggleSelection(t.id);
  } else {
    store.clearSelection();
    store.toggleSelection(t.id);
  }
}

const groupedRows = computed(() => {
  const by = groupBy.value;
  const base = filteredTracks.value;
  if (by === "none" || !base.length) return null;
  const map = new Map<string, GroupRow>();
  for (const t of base) {
    if (by === "artist") {
      const artist = t.artist ?? "—";
      let group = map.get(artist);
      if (!group) {
        group = { key: artist, label: artist, tracks: [] };
        map.set(artist, group);
      }
      group.tracks.push(t);
    } else if (by === "album") {
      const album = t.album ?? "—";
      const artist = t.artist ?? "—";
      const key = `${album}|||${artist}`;
      let group = map.get(key);
      if (!group) {
        group = { key, label: album, artist, tracks: [] };
        map.set(key, group);
      }
      group.tracks.push(t);
    }
  }
  const groups = [...map.values()];
  groups.sort((a, b) => {
    const byLabel = a.label.localeCompare(b.label, undefined, { sensitivity: "base" });
    if (byLabel !== 0) return byLabel;
    const aArtist = (a.artist ?? "").toLowerCase();
    const bArtist = (b.artist ?? "").toLowerCase();
    return aArtist.localeCompare(bArtist);
  });
  return groups;
});

const groupCovers = computed(() => {
  if (groupBy.value !== "album") return {} as Record<string, import("../../stores/catalog").CoverInfo | null | undefined>;
  const groups = groupedRows.value;
  if (!groups) return {};
  // React to path-based cover cache so headers update when covers load.
  void store.coverCache;
  const result: Record<string, import("../../stores/catalog").CoverInfo | null | undefined> = {};
  for (const group of groups) {
    const firstTrack = group.tracks[0];
    result[group.key] = firstTrack ? store.getCover(firstTrack.path) : undefined;
  }
  return result;
});

const expandedGroups = ref<Set<string>>(new Set());
const prevGroupKeys = ref<Set<string>>(new Set());
const appliedDefaultForSession = ref(false);
const focusedRowIndex = ref(-1);

watch(
  groupBy,
  (by) => {
    if (by === "none") return;
    appliedDefaultForSession.value = false;
    const groups = groupedRows.value;
    expandedGroups.value = defaultGroupsExpanded.value && groups?.length ? new Set(groups.map((g) => g.key)) : new Set();
    prevGroupKeys.value = new Set(groups?.map((g) => g.key) ?? []);
    if (groups?.length) appliedDefaultForSession.value = true;
  },
  { immediate: true },
);

watch(groupedRows, (groups) => {
  if (!groups?.length) return;
  const keys = new Set(groups.map((g) => g.key));
  if (!appliedDefaultForSession.value) {
    appliedDefaultForSession.value = true;
    prevGroupKeys.value = keys;
    expandedGroups.value = defaultGroupsExpanded.value ? new Set(keys) : new Set();
    return;
  }
  const next = new Set<string>();
  for (const key of expandedGroups.value) {
    if (keys.has(key)) next.add(key);
  }
  const prev = prevGroupKeys.value;
  for (const key of keys) {
    if (!prev.has(key)) next.add(key);
  }
  prevGroupKeys.value = keys;
  expandedGroups.value = next;
});

const visibleRows = computed((): VisibleRow[] => {
  const groups = groupedRows.value;
  if (!groups) return filteredTracks.value.map((track) => ({ type: "track", track }));
  const out: VisibleRow[] = [];
  for (const group of groups) {
    out.push({ type: "group", key: group.key, group });
    if (expandedGroups.value.has(group.key)) {
      for (const t of group.tracks) out.push({ type: "track", track: t });
    }
  }
  return out;
});

const tableColCount = computed(() => {
  let n = 1;
  if (tableColAlbumArt.value) n += 1;
  n += 3;
  if (tableColYear.value) n += 1;
  if (tableColDuration.value) n += 1;
  if (tableColFormat.value) n += 1;
  if (tableColPath.value) n += 1;
  return n;
});

const CHECKBOX_COL_WIDTH = 32;

function colWidth(columnId: string): number {
  return tableColWidths.value[columnId] ?? 120;
}

const resizeState = ref<{ columnId: string; startX: number; startWidth: number } | null>(null);

function onResizeStart(columnId: string, e: MouseEvent) {
  e.preventDefault();
  const w = tableColWidths.value[columnId];
  if (w == null) return;
  resizeState.value = { columnId, startX: e.clientX, startWidth: w };
}

function onResizeMove(e: MouseEvent) {
  const state = resizeState.value;
  if (!state) return;
  const delta = e.clientX - state.startX;
  settingsStore.setTableColWidth(state.columnId, state.startWidth + delta, false);
}

function onResizeEnd() {
  if (resizeState.value) settingsStore.saveToFile();
  resizeState.value = null;
}

watch(resizeState, (state) => {
  if (typeof document === "undefined") return;
  if (state) {
    document.body.classList.add("select-none");
    document.body.style.cursor = "col-resize";
  } else {
    document.body.classList.remove("select-none");
    document.body.style.cursor = "";
  }
}, { immediate: true });

const ROW_HEIGHT_GROUP = 44;
const ROW_HEIGHT_GROUP_SPACIOUS = 100;
const ROW_HEIGHT_TRACK_COMFORTABLE = 48;
const ROW_HEIGHT_TRACK_COMPACT = 26;
const OVERCAN_ROWS = 12;
const VIRTUALIZATION_THRESHOLD = 500;

const rowHeights = computed(() => {
  const rows = visibleRows.value;
  const density = tableDensity.value;
  const trackHeight =
    density === "compact" ? ROW_HEIGHT_TRACK_COMPACT : ROW_HEIGHT_TRACK_COMFORTABLE;
  const groupHeight =
    density === "spacious" && groupBy.value === "album"
      ? ROW_HEIGHT_GROUP_SPACIOUS
      : ROW_HEIGHT_GROUP;
  return rows.map((r) => (r.type === "group" ? groupHeight : trackHeight));
});

function getRowOffset(index: number): number {
  const heights = rowHeights.value;
  let sum = 0;
  for (let i = 0; i < index && i < heights.length; i++) sum += heights[i];
  return sum;
}

const totalScrollHeight = computed(() => rowHeights.value.reduce((a, b) => a + b, 0));
const useVirtualization = computed(() => visibleRows.value.length >= VIRTUALIZATION_THRESHOLD);

const tableContainerRef = ref<HTMLDivElement | null>(null);
const scrollTopRef = ref(0);
const containerHeightRef = ref(0);

function updateScrollMeasurements() {
  const el = tableContainerRef.value;
  if (!el) return;
  scrollTopRef.value = el.scrollTop;
  containerHeightRef.value = el.clientHeight;
}

const visibleRange = computed(() => {
  const rows = visibleRows.value;
  const heights = rowHeights.value;
  if (!rows.length || !useVirtualization.value) return { start: 0, end: Math.max(0, rows.length - 1) };
  const scrollTop = scrollTopRef.value;
  const containerHeight = containerHeightRef.value;
  if (containerHeight <= 0) return { start: 0, end: Math.min(OVERCAN_ROWS * 2, rows.length - 1) };
  let offset = 0;
  let start = 0;
  for (let i = 0; i < rows.length; i++) {
    if (offset + heights[i] > scrollTop) {
      start = Math.max(0, i - OVERCAN_ROWS);
      break;
    }
    offset += heights[i];
  }
  const bottom = scrollTop + containerHeight;
  offset = 0;
  let end = rows.length - 1;
  for (let i = 0; i < rows.length; i++) {
    const rowBottom = offset + heights[i];
    if (offset < bottom && rowBottom > scrollTop) end = i;
    offset = rowBottom;
  }
  end = Math.min(rows.length - 1, end + OVERCAN_ROWS);
  return { start, end };
});

const renderedRows = computed(() => {
  const rows = visibleRows.value;
  const { start, end } = visibleRange.value;
  if (!useVirtualization.value) return rows.map((r, i) => ({ row: r, index: i }));
  const result: { row: VisibleRow; index: number }[] = [];
  for (let i = start; i <= end && i < rows.length; i++) result.push({ row: rows[i], index: i });
  return result;
});

const topSpacerHeight = computed(() => (useVirtualization.value ? getRowOffset(visibleRange.value.start) : 0));
const bottomSpacerHeight = computed(() => {
  if (!useVirtualization.value) return 0;
  const { start, end } = visibleRange.value;
  const heights = rowHeights.value;
  const total = totalScrollHeight.value;
  let visibleSum = 0;
  for (let i = start; i <= end && i < heights.length; i++) visibleSum += heights[i];
  return Math.max(0, total - topSpacerHeight.value - visibleSum);
});

// When grouping by album, proactively fetch covers for all tracks in groups
watch(
  () => (groupBy.value === "album" ? groupedRows.value : null),
  (groups) => {
    if (!groups) return;
    for (const g of groups) {
      for (const t of g.tracks) {
        store.fetchCover(t.path);
      }
    }
  },
  { immediate: true },
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

function groupContainsSelection(group: GroupRow): boolean {
  return group.tracks.some((tr) => selectedTrackIds.value.includes(tr.id));
}

function copyPathToClipboard(path: string) {
  navigator.clipboard.writeText(path).catch(() => {});
}

function editGroup(group: GroupRow) {
  const ids = group.tracks.map((t) => t.id);
  if (!ids.length) return;
  store.setSelection(ids);
  store.setMultiSelectMode(true);
}

function expandAllGroups() {
  const groups = groupedRows.value;
  if (!groups?.length) {
    expandedGroups.value = new Set();
    return;
  }
  const keys = new Set(groups.map((g) => g.key));
  prevGroupKeys.value = keys;
  expandedGroups.value = new Set(keys);
  appliedDefaultForSession.value = true;
}

function collapseAllGroups() {
  expandedGroups.value = new Set();
  appliedDefaultForSession.value = true;
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
    if (row.type === "group") toggleGroup(row.key);
    else selectRow(row.track);
  }
}

function focusNext() {
  const rows = visibleRows.value;
  if (!rows.length) return;
  if (focusedRowIndex.value < 0) focusedRowIndex.value = 0;
  else if (focusedRowIndex.value >= rows.length - 1) focusedRowIndex.value = navWrap.value ? 0 : rows.length - 1;
  else focusedRowIndex.value += 1;
  scrollFocusedRowIntoView();
}

function focusPrev() {
  const rows = visibleRows.value;
  if (!rows.length) return;
  if (focusedRowIndex.value < 0) focusedRowIndex.value = rows.length - 1;
  else if (focusedRowIndex.value <= 0) focusedRowIndex.value = navWrap.value ? rows.length - 1 : 0;
  else focusedRowIndex.value -= 1;
  scrollFocusedRowIntoView();
}

function scrollToRowIndex(index: number) {
  const el = tableContainerRef.value;
  if (!el) return;
  const offset = getRowOffset(index);
  const padding = 80;
  const target = Math.max(0, offset - Math.min(padding, el.clientHeight / 3));
  el.scrollTop = target;
  scrollTopRef.value = target;
  nextTick(() => {
    const rowEl = el.querySelector(`[data-row-index="${index}"]`);
    (rowEl as HTMLElement | null)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  });
}

function scrollFocusedRowIntoView() {
  if (useVirtualization.value) {
    scrollToRowIndex(focusedRowIndex.value);
    return;
  }
  nextTick(() => {
    const el = tableContainerRef.value?.querySelector(`[data-row-index="${focusedRowIndex.value}"]`);
    (el as HTMLElement | null)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  });
}

watch(visibleRows, (rows) => {
  if (focusedRowIndex.value >= rows.length) focusedRowIndex.value = rows.length > 0 ? rows.length - 1 : -1;
});

watch(useVirtualization, (use) => {
  if (use) nextTick(updateScrollMeasurements);
});

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  document.addEventListener("mousemove", onResizeMove);
  document.addEventListener("mouseup", onResizeEnd);
  nextTick(() => {
    const container = tableContainerRef.value;
    if (!container) return;
    container.addEventListener("scroll", updateScrollMeasurements, { passive: true });
    updateScrollMeasurements();
    resizeObserver = new ResizeObserver(updateScrollMeasurements);
    resizeObserver.observe(container);
  });
});

onUnmounted(() => {
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  const container = tableContainerRef.value;
  if (container) container.removeEventListener("scroll", updateScrollMeasurements);
  resizeObserver?.disconnect();
  resizeObserver = null;
});

function scrollToTrackId(id: number) {
  const rows = visibleRows.value;
  const idx = rows.findIndex((r) => r.type === "track" && r.track.id === id);
  if (idx < 0) return;
  focusedRowIndex.value = idx;
  if (useVirtualization.value) scrollToRowIndex(idx);
  else scrollFocusedRowIntoView();
}

const contextMenu = ref<{ x: number; y: number; tracks: CatalogTrack[] } | null>(null);
const contextMenuRef = ref<HTMLElement | null>(null);

function openContextMenu(e: MouseEvent, tracks: CatalogTrack[]) {
  e.preventDefault();
  if (!tracks.length) return;
  contextMenu.value = { x: e.clientX, y: e.clientY, tracks };
}

function closeContextMenu() {
  contextMenu.value = null;
}

function addToQueueFromContextMenu() {
  if (contextMenu.value) {
    store.addTracksToQueue(contextMenu.value.tracks);
    closeContextMenu();
  }
}

watch(contextMenu, (menu) => {
  if (!menu) return;
  const onOutside = (e: MouseEvent) => {
    const target = e.target as Node;
    if (contextMenuRef.value?.contains(target)) return;
    closeContextMenu();
    document.removeEventListener("click", onOutside);
    document.removeEventListener("keydown", onEscape);
  };
  const onEscape = (e: KeyboardEvent) => {
    if (e.key === "Escape") {
      closeContextMenu();
      document.removeEventListener("click", onOutside);
      document.removeEventListener("keydown", onEscape);
    }
  };
  nextTick(() => {
    setTimeout(() => {
      document.addEventListener("click", onOutside);
      document.addEventListener("keydown", onEscape);
    }, 0);
  });
});

defineExpose({ scrollToTrackId, expandAllGroups, collapseAllGroups });
</script>

<template>
  <div
    ref="tableContainerRef"
    tabindex="0"
    class="table-scroll-container flex-1 overflow-auto outline-none"
    @keydown="onTableKeydown"
  >
    <table class="table-with-scroll-gutter table-fixed w-full min-w-[800px] border-collapse text-left text-sm" :class="{ 'table-density-compact': tableDensity === 'compact', 'table-density-spacious': tableDensity === 'spacious' }">
      <colgroup>
        <col :style="{ width: CHECKBOX_COL_WIDTH + 'px' }" />
        <col v-if="tableColAlbumArt" :style="{ width: colWidth('albumArt') + 'px' }" />
        <col :style="{ width: colWidth('title') + 'px' }" />
        <col :style="{ width: colWidth('artist') + 'px' }" />
        <col :style="{ width: colWidth('album') + 'px' }" />
        <col v-if="tableColYear" :style="{ width: colWidth('year') + 'px' }" />
        <col v-if="tableColDuration" :style="{ width: colWidth('duration') + 'px' }" />
        <col v-if="tableColFormat" :style="{ width: colWidth('format') + 'px' }" />
        <col v-if="tableColPath" :style="{ width: colWidth('path') + 'px' }" />
      </colgroup>
      <thead class="sticky top-0 z-10 bg-stone-800">
        <tr class="border-b border-stone-600">
          <th class="border-r border-stone-700 p-2" style="width: 32px">
            <label class="flex cursor-pointer items-center gap-1.5 text-xs text-stone-400">
              <input
                type="checkbox"
                :checked="multiSelectMode"
                class="rounded border-stone-600"
                @change="store.setMultiSelectMode(($event.target as HTMLInputElement).checked)"
              />
              Multi
            </label>
          </th>
          <th v-if="tableColAlbumArt" class="relative border-r border-stone-700 p-2">
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('albumArt', $event)"
            />
          </th>
          <th class="relative border-r border-stone-700 p-2 font-medium text-stone-400">
            Title
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('title', $event)"
            />
          </th>
          <th class="relative border-r border-stone-700 p-2 font-medium text-stone-400">
            Artist
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('artist', $event)"
            />
          </th>
          <th class="relative border-r border-stone-700 p-2 font-medium text-stone-400">
            Album
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('album', $event)"
            />
          </th>
          <th v-if="tableColYear" class="relative border-r border-stone-700 p-2 font-medium text-stone-400">
            Year
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('year', $event)"
            />
          </th>
          <th v-if="tableColDuration" class="relative border-r border-stone-700 p-2 font-medium text-stone-400">
            Duration
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('duration', $event)"
            />
          </th>
          <th v-if="tableColFormat" class="relative border-r border-stone-700 p-2 font-medium text-stone-400">
            Format
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('format', $event)"
            />
          </th>
          <th v-if="tableColPath" class="relative truncate border-r border-stone-700 p-2 font-medium text-stone-400">
            Path
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-stone-500/50"
              aria-hidden="true"
              @mousedown="onResizeStart('path', $event)"
            />
          </th>
        </tr>
      </thead>
      <tbody>
        <template v-if="visibleRows.length">
          <tr v-if="useVirtualization && topSpacerHeight > 0" class="virtual-spacer-row" aria-hidden="true">
            <td :colspan="tableColCount" :style="{ height: topSpacerHeight + 'px' }"></td>
          </tr>
          <template v-for="{ row, index } in renderedRows" :key="row.type === 'group' ? row.key : row.track.id">
            <tr
              v-if="row.type === 'group'"
              :data-row-index="index"
              class="cursor-context-menu font-medium text-stone-400 hover:bg-stone-700/80"
              :class="[
                focusedRowIndex === index ? 'bg-stone-700/80 table-row-focused' : 'bg-stone-800/80',
                { 'group-row-with-selection': groupContainsSelection(row.group) },
              ]"
              :style="useVirtualization ? { height: rowHeights[index] + 'px' } : undefined"
              @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = index) : undefined"
              @click="focusedRowIndex = index; toggleGroup(row.key)"
              @contextmenu.prevent="openContextMenu($event, row.group.tracks)"
            >
              <td
                :colspan="tableColCount"
                class="border-b border-stone-600 p-2"
                :class="{ 'py-3': tableDensity === 'spacious' && groupBy === 'album' }"
              >
                <span
                  class="inline-flex flex-wrap items-center gap-2"
                  :class="{ 'gap-3': tableDensity === 'spacious' && groupBy === 'album' }"
                >
                  <span
                    v-if="groupBy === 'album' && groupHeaderAlbumArt"
                    class="flex shrink-0 items-center justify-center overflow-hidden rounded bg-stone-800"
                    :class="tableDensity === 'spacious' ? 'h-20 w-20' : 'h-8 w-8'"
                  >
                    <img
                      v-if="groupCovers[row.key]"
                      :src="`data:${groupCovers[row.key]!.mime};base64,${groupCovers[row.key]!.base64}`"
                      alt=""
                      class="h-full w-full object-cover"
                    />
                    <span
                      v-else-if="groupCovers[row.key] === null"
                      class="inline-flex items-center justify-center rounded-full border border-stone-500 text-stone-400"
                      :class="tableDensity === 'spacious' ? 'h-8 w-8 text-lg' : 'h-4 w-4 text-[0.6rem]'"
                      aria-hidden="true"
                    >
                      ♪
                    </span>
                    <span
                      v-else
                      class="inline-block animate-spin rounded-full border-2 border-stone-500 border-t-stone-300"
                      :class="tableDensity === 'spacious' ? 'h-8 w-8' : 'h-4 w-4'"
                      aria-hidden="true"
                    />
                  </span>
                  <span
                    class="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-900/60 text-stone-400 transition-transform duration-150"
                    :class="{ 'rotate-90': isGroupExpanded(row.key) }"
                    aria-hidden="true"
                  >
                    <svg class="h-2.5 w-2.5" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24">
                      <path d="M9 6l6 6-6 6" />
                    </svg>
                  </span>
                  {{ row.group.label }}<template v-if="groupBy === 'album' && row.group.artist"><span class="ml-1 text-stone-400"> · {{ row.group.artist }}</span></template>
                  <span class="ml-1 text-stone-500">({{ row.group.tracks.length }})</span>
                  <button
                    v-if="groupBy === 'album'"
                    type="button"
                    class="rounded border border-stone-600 p-0.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                    aria-label="Edit album"
                    title="Edit album"
                    @click.stop="editGroup(row.group)"
                  >
                    <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                  </button>
                  <template v-if="groupBy === 'album' && !hideWikipediaCoverSearch">
                    <!-- cover prefetching is triggered elsewhere; this just keeps parity with previous UI -->
                  </template>
                </span>
              </td>
            </tr>
            <tr
              v-else
              :data-row-index="index"
              :data-track-id="row.track.id"
              class="cursor-context-menu border-b border-stone-700/50 hover:bg-stone-800/50"
              :class="[
                { 'bg-stone-700/25': isSelected(row.track.id) && focusedRowIndex !== index },
                { 'bg-stone-600/30 table-row-focused': isSelected(row.track.id) && focusedRowIndex === index },
                { 'bg-stone-800 table-row-focused': !isSelected(row.track.id) && focusedRowIndex === index },
                { 'table-row-playing': row.track.id === currentPlayingTrackId },
              ]"
              :style="useVirtualization ? { height: rowHeights[index] + 'px' } : undefined"
              @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = index) : undefined"
              @click="focusedRowIndex = index; selectRow(row.track)"
              @contextmenu.prevent="openContextMenu($event, [row.track])"
            >
              <td class="border-r border-stone-700 p-2">
                <input
                  v-if="multiSelectMode"
                  type="checkbox"
                  :checked="isSelected(row.track.id)"
                  class="rounded border-stone-600"
                  @click.stop="focusedRowIndex = index; selectRow(row.track)"
                />
              </td>
              <td v-if="tableColAlbumArt" class="border-r border-stone-700 p-2">
                <div class="flex justify-center items-center">
                  <TrackAlbumArt
                    :path="row.track.path"
                    :size="tableDensity === 'compact' ? 'small' : 'medium'"
                  />
                </div>
              </td>
              <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.title ?? "—" }}</td>
              <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.artist ?? "—" }}</td>
              <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.album ?? "—" }}</td>
              <td v-if="tableColYear" class="border-r border-stone-700 p-2 text-stone-300">{{ row.track.year ?? "—" }}</td>
              <td v-if="tableColDuration" class="border-r border-stone-700 p-2 text-stone-300">{{ formatDuration(row.track.duration_secs) }}</td>
              <td v-if="tableColFormat" class="border-r border-stone-700 p-2 text-stone-400">{{ row.track.format }}</td>
              <td v-if="tableColPath" class="min-w-0 p-2 text-stone-500">
                <div class="flex min-w-0 items-center gap-1">
                  <button type="button" class="shrink-0 rounded p-0.5 text-stone-500 hover:bg-stone-600 hover:text-stone-300" aria-label="Copy path" title="Copy path" @click.stop="copyPathToClipboard(row.track.path)">
                    <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                    </svg>
                  </button>
                  <span class="min-w-0 truncate cursor-default" :title="row.track.path">{{ row.track.path }}</span>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="useVirtualization && bottomSpacerHeight > 0" class="virtual-spacer-row" aria-hidden="true">
            <td :colspan="tableColCount" :style="{ height: bottomSpacerHeight + 'px' }"></td>
          </tr>
        </template>
        <tr v-else class="text-stone-500">
          <td :colspan="tableColCount" class="p-6 text-center">
            {{ store.searchQuery.trim() ? "No tracks match the search." : "No tracks to show. Add or unhide a folder to show MP3/FLAC files." }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <Teleport to="body">
    <div
      v-if="contextMenu"
      ref="contextMenuRef"
      class="fixed z-[300] min-w-[160px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="addToQueueFromContextMenu"
      >
        <svg class="h-4 w-4 shrink-0 text-stone-400" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M16 20q-1.25 0-2.125-.875T13 17t.875-2.125T16 14q.275 0 .525.038T17 14.2V7q0-.425.288-.712T18 6h3q.425 0 .713.288T22 7t-.288.713T21 8h-2v9q0 1.25-.875 2.125T16 20M4 16q-.425 0-.712-.288T3 15t.288-.712T4 14h6q.425 0 .713.288T11 15t-.288.713T10 16zm0-4q-.425 0-.712-.288T3 11t.288-.712T4 10h10q.425 0 .713.288T15 11t-.288.713T14 12zm0-4q-.425 0-.712-.288T3 7t.288-.712T4 6h10q.425 0 .713.288T15 7t-.288.713T14 8z" />
        </svg>
        Add to queue
      </button>
    </div>
  </Teleport>
</template>

<style scoped>
.virtual-spacer-row td {
  padding: 0 !important;
  border: none !important;
  line-height: 0;
  vertical-align: top;
}

.table-with-scroll-gutter {
  margin-right: 14px;
  width: calc(100% - 14px);
  max-width: calc(100% - 14px);
}

.table-density-compact th,
.table-density-compact td {
  padding: 0.2rem 0.375rem !important;
}
</style>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <!-- Toolbar -->
    <div class="flex flex-wrap items-center gap-2 border-b border-outline/10 px-3 py-2">
      <div ref="groupMenuRef" class="relative">
        <button
          type="button"
          class="glass-field flex items-center gap-1.5 rounded-full px-3 py-1.5 text-label-lg text-on-surface"
          @click="groupMenuOpen = !groupMenuOpen"
        >
          <MageIcon name="stack" class="h-4 w-4" />
          <span>Group: {{ groupByLabel }}</span>
          <MageIcon name="chevron-down" class="h-3.5 w-3.5" />
        </button>
        <div
          v-if="groupMenuOpen"
          class="absolute left-0 top-full z-30 mt-1 w-36 rounded-xl bg-surface-container py-1 shadow-xl"
        >
          <button
            v-for="opt in GROUP_OPTIONS"
            :key="opt.value"
            type="button"
            class="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-body-md text-on-surface"
            @click="selectGroupBy(opt.value)"
          >
            <span>{{ opt.label }}</span>
            <MageIcon v-if="tableStore.groupBy === opt.value" name="check" class="h-4 w-4 text-primary" />
          </button>
        </div>
      </div>

      <template v-if="tableStore.groupBy !== 'none'">
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container/60"
          aria-label="Expand all groups"
          title="Expand all groups"
          @click="expandAllGroups"
        >
          <MageIcon name="chevron-down" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container/60"
          aria-label="Collapse all groups"
          title="Collapse all groups"
          @click="collapseAllGroups"
        >
          <MageIcon name="chevron-up" class="h-4 w-4" />
        </button>
      </template>

      <div class="glass-field flex items-center gap-0.5 rounded-full p-0.5">
        <button
          v-for="d in DENSITY_OPTIONS"
          :key="d.value"
          type="button"
          class="rounded-full px-2.5 py-1 text-label-sm transition-colors"
          :class="tableStore.density === d.value ? 'bg-primary/[0.18] text-primary' : 'text-on-surface-variant'"
          :aria-pressed="tableStore.density === d.value"
          @click="tableStore.setDensity(d.value)"
        >
          {{ d.label }}
        </button>
      </div>

      <div class="flex-1" />

      <span v-if="selection.count > 0" class="text-label-lg text-on-surface-variant">
        {{ selection.count }} selected
      </span>

      <div ref="colMenuRef" class="relative">
        <button
          type="button"
          class="glass-field flex items-center gap-1.5 rounded-full px-3 py-1.5 text-label-lg text-on-surface"
          @click="colMenuOpen = !colMenuOpen"
        >
          <MageIcon name="layout-grid" class="h-4 w-4" />
          <span>Columns</span>
        </button>
        <div
          v-if="colMenuOpen"
          class="absolute right-0 top-full z-30 mt-1 w-44 rounded-xl bg-surface-container py-1 shadow-xl"
        >
          <label
            v-for="col in TABLE_COLUMNS"
            :key="col.id"
            class="flex items-center gap-2 px-3 py-1.5 text-label-lg text-on-surface"
          >
            <input
              type="checkbox"
              :checked="tableStore.visibleColumns[col.id]"
              @change="tableStore.setColumnVisible(col.id, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ col.label }}</span>
          </label>
          <button
            type="button"
            class="mt-1 w-full border-t border-outline/10 px-3 pt-2 text-left text-label-lg text-primary"
            @click="tableStore.resetColumnWidths()"
          >
            Reset widths
          </button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div
      ref="scrollRef"
      tabindex="0"
      class="scrollbar-overlay min-h-0 flex-1 overflow-auto pb-[var(--bottom-inset)] outline-none"
      @keydown="onKeydown"
    >
      <table class="w-full table-fixed border-collapse text-left text-body-md">
        <colgroup>
          <col
            v-for="col in visibleColumnDefs"
            :key="col.id"
            :style="{ width: tableStore.columnWidths[col.id] + 'px' }"
          />
          <col />
        </colgroup>
        <thead class="sticky top-0 z-10 bg-surface">
          <tr class="border-b border-outline/10">
            <th
              v-for="col in visibleColumnDefs"
              :key="col.id"
              class="relative px-2 py-1.5 text-label-sm font-medium text-on-surface-variant"
            >
              <button
                v-if="col.sortable"
                type="button"
                class="flex items-center gap-1 hover:text-on-surface"
                @click="onSortClick(col.id as TableSortColumn)"
              >
                <span>{{ col.label }}</span>
                <MageIcon
                  v-if="tableStore.sortColumn === col.id"
                  :name="tableStore.sortDirection === 'asc' ? 'chevron-up' : 'chevron-down'"
                  class="h-3.5 w-3.5 text-primary"
                />
              </button>
              <span v-else>{{ col.label }}</span>
              <span
                class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none hover:bg-primary/30"
                aria-hidden="true"
                @mousedown="onResizeStart(col.id, $event)"
              />
            </th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="topSpacer > 0" aria-hidden="true">
            <td :colspan="colCount" class="!border-none !p-0" :style="{ height: topSpacer + 'px' }" />
          </tr>
          <template v-for="{ row, index } in renderedRows" :key="row.type === 'group' ? `g:${row.key}` : row.track.id">
            <tr
              v-if="row.type === 'group'"
              :data-row-index="index"
              draggable="true"
              class="cursor-pointer select-none border-b border-outline/5 font-medium text-on-surface-variant hover:bg-surface-container/60"
              :class="focusedIndex === index ? 'bg-surface-container/70' : ''"
              :style="{ height: rowHeights[index] + 'px' }"
              @click="onGroupRowClick(row, index)"
              @contextmenu.prevent="openContextMenu($event, row.group.tracks)"
              @dragstart="onDragStart($event, row.group.tracks)"
            >
              <td :colspan="colCount" class="px-2">
                <div class="flex items-center gap-2">
                  <img v-if="groupCover(row.group)" :src="groupCover(row.group)!" class="h-7 w-7 shrink-0 rounded object-cover" alt="" />
                  <MageIcon v-else name="music" class="h-4 w-4 shrink-0 text-on-surface-variant/50" />
                  <MageIcon
                    name="chevron-right"
                    :class="
                      expandedGroups.has(row.key)
                        ? 'h-3.5 w-3.5 shrink-0 rotate-90 transition-transform'
                        : 'h-3.5 w-3.5 shrink-0 transition-transform'
                    "
                  />
                  <span class="truncate text-body-md text-on-surface">{{ row.group.label }}</span>
                  <span v-if="tableStore.groupBy === 'album' && row.group.artist" class="shrink-0 text-on-surface-variant">
                    · {{ row.group.artist }}
                  </span>
                  <span class="shrink-0 text-on-surface-variant">({{ row.group.tracks.length }})</span>
                  <StarRatingCell
                    v-if="groupAvgRating(row.group.tracks) !== null"
                    class="shrink-0"
                    :model-value="Math.round(groupAvgRating(row.group.tracks)!)"
                    partial
                    readonly
                  />
                </div>
              </td>
              <td></td>
            </tr>
            <tr
              v-else
              :data-row-index="index"
              :data-track-id="row.track.id"
              draggable="true"
              class="cursor-pointer select-none border-b border-outline/5"
              :class="rowClass(row.track, index)"
              :style="{ height: rowHeights[index] + 'px' }"
              @click="onTrackRowClick(row.track, $event, index)"
              @dblclick="onTrackRowDblClick(row.track)"
              @contextmenu.prevent="openContextMenu($event, [row.track])"
              @dragstart="onDragStart($event, [row.track])"
            >
              <td v-for="col in visibleColumnDefs" :key="col.id" class="overflow-hidden px-2 py-0.5">
                <div v-if="col.id === 'art'" class="flex h-full items-center justify-center">
                  <img v-if="coverUrl(row.track)" :src="coverUrl(row.track)!" class="h-8 w-8 rounded object-cover" alt="" />
                  <MageIcon v-else name="music" class="h-4 w-4 text-on-surface-variant/50" />
                </div>
                <div v-else-if="col.id === 'title'" class="flex min-w-0 items-center gap-2">
                  <EqualizerBars
                    v-if="row.track.id === player.currentTrack?.id"
                    class="shrink-0 text-primary"
                    :paused="!player.isPlaying"
                  />
                  <span
                    class="truncate"
                    :class="row.track.id === player.currentTrack?.id ? 'text-primary' : 'text-on-surface'"
                  >{{ row.track.title ?? "—" }}</span>
                </div>
                <span v-else-if="col.id === 'artist'" class="block truncate text-on-surface">{{ row.track.artist ?? "—" }}</span>
                <span v-else-if="col.id === 'album'" class="block truncate text-on-surface">{{ row.track.album ?? "—" }}</span>
                <span v-else-if="col.id === 'year'" class="block truncate text-on-surface-variant">{{ row.track.year ?? "—" }}</span>
                <span v-else-if="col.id === 'duration'" class="block truncate text-on-surface-variant">{{ formatDuration(row.track.duration_secs ?? 0) }}</span>
                <StarRatingCell
                  v-else-if="col.id === 'rating'"
                  :model-value="row.track.rating ?? null"
                  @update:model-value="(r) => onSetSingleRating(row.track, r)"
                  @click.stop
                />
                <span v-else-if="col.id === 'format'" class="block truncate text-on-surface-variant">{{ row.track.format.toUpperCase() }}</span>
                <div v-else-if="col.id === 'path'" class="flex min-w-0 items-center gap-1">
                  <button
                    type="button"
                    class="shrink-0 text-on-surface-variant/70 hover:text-on-surface"
                    aria-label="Copy path"
                    title="Copy path"
                    @click.stop="copyPath(row.track.path)"
                  >
                    <MageIcon name="note-text" class="h-3.5 w-3.5" />
                  </button>
                  <span class="min-w-0 truncate text-on-surface-variant" :title="row.track.path">{{ row.track.path }}</span>
                </div>
              </td>
              <td></td>
            </tr>
          </template>
          <tr v-if="bottomSpacer > 0" aria-hidden="true">
            <td :colspan="colCount" class="!border-none !p-0" :style="{ height: bottomSpacer + 'px' }" />
          </tr>
          <tr v-if="rows.length === 0">
            <td :colspan="colCount" class="px-4 py-10 text-center text-body-md text-on-surface-variant">
              {{ tableStore.minRating > 0 ? "No tracks match the rating filter." : "No tracks to show." }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Context menu -->
    <Teleport to="body">
      <div
        v-if="contextMenu"
        ref="contextMenuRef"
        class="glass-strong fixed z-50 w-60 rounded-xl py-1 shadow-2xl"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <template v-if="!infoOpen && !addToPlaylistOpen">
          <button type="button" :class="MENU_ROW" @click="menuPlay">
            <MageIcon name="play" class="h-4 w-4" />
            <span>Play</span>
          </button>
          <button type="button" :class="MENU_ROW" @click="menuPlayNext">
            <MageIcon name="next" class="h-4 w-4" />
            <span>Play next</span>
          </button>
          <button type="button" :class="MENU_ROW" @click="menuAddToQueue">
            <MageIcon name="playlist" class="h-4 w-4" />
            <span>Add to queue</span>
          </button>
          <button type="button" :class="MENU_ROW" @click="addToPlaylistOpen = true">
            <MageIcon name="playlist-add" class="h-4 w-4" />
            <span>Add to playlist…</span>
          </button>
          <button type="button" :class="MENU_ROW" @click="menuToggleFavorite">
            <MageIcon name="heart" class="h-4 w-4" :class="contextMenuAllFavorite ? 'text-primary' : ''" />
            <span>{{ contextMenuAllFavorite ? "Unfavourite" : "Favourite" }}</span>
          </button>
          <div class="flex items-center justify-between gap-3 px-4 py-2">
            <span class="flex items-center gap-2 text-body-lg text-on-surface">
              <MageIcon name="star" class="hidden" />
              Rate
            </span>
            <StarRatingCell :model-value="contextMenuRating" @update:model-value="menuSetRating" />
          </div>
          <div class="my-1 border-t border-outline/10" />
          <button type="button" :class="MENU_ROW" @click="menuEditMetadata">
            <MageIcon name="edit" class="h-4 w-4" />
            <span>Edit metadata…</span>
          </button>
          <button v-if="contextMenu.tracks.length === 1" type="button" :class="MENU_ROW" @click="menuCopyPath">
            <MageIcon name="note-text" class="h-4 w-4" />
            <span>Copy path</span>
          </button>
          <button v-if="contextMenu.tracks.length === 1" type="button" :class="MENU_ROW" @click="infoOpen = true">
            <MageIcon name="information-circle" class="h-4 w-4" />
            <span>Track info</span>
          </button>
          <div class="my-1 border-t border-outline/10" />
          <button type="button" :class="[MENU_ROW, 'text-error']" @click="requestDelete">
            <MageIcon name="trash" class="h-4 w-4" />
            <span>Delete…</span>
          </button>
        </template>

        <template v-else-if="addToPlaylistOpen">
          <button type="button" class="flex w-full items-center gap-2 px-4 py-2 text-left text-label-lg text-on-surface-variant" @click="addToPlaylistOpen = false">
            <MageIcon name="arrow-left" class="h-4 w-4" />
            <span>Back</span>
          </button>
          <div class="my-1 border-t border-outline/10" />
          <button
            v-for="p in manualPlaylists"
            :key="p.id"
            type="button"
            :class="MENU_ROW"
            @click="menuAddToPlaylist(p.id, p.name)"
          >
            <span class="truncate">{{ p.icon }} {{ p.name }}</span>
          </button>
        </template>

        <template v-else>
          <button type="button" class="flex w-full items-center gap-2 px-4 py-2 text-left text-label-lg text-on-surface-variant" @click="infoOpen = false">
            <MageIcon name="arrow-left" class="h-4 w-4" />
            <span>Back</span>
          </button>
          <div class="my-1 border-t border-outline/10" />
          <div v-for="f in infoFields" :key="f.label" class="flex items-start justify-between gap-4 px-4 py-1.5">
            <span class="shrink-0 text-body-sm text-on-surface-variant">{{ f.label }}</span>
            <span class="break-all text-right text-body-sm text-on-surface">{{ f.value }}</span>
          </div>
        </template>
      </div>
    </Teleport>

    <ConfirmDialog
      :open="ratingConfirm !== null"
      title="Rate tracks?"
      :message="`Set the rating for ${ratingConfirm?.ids.length ?? 0} tracks?`"
      confirm-label="Rate"
      @confirm="confirmRating"
      @cancel="ratingConfirm = null"
    />
    <ConfirmDialog
      :open="deleteConfirm !== null"
      title="Delete tracks?"
      :message="`Permanently delete ${deleteConfirm?.length ?? 0} track${(deleteConfirm?.length ?? 0) === 1 ? '' : 's'} from the server? This cannot be undone.`"
      confirm-label="Delete"
      danger
      @confirm="confirmDelete"
      @cancel="deleteConfirm = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import MageIcon from "../MageIcon.vue";
import ConfirmDialog from "../ConfirmDialog.vue";
import StarRatingCell from "./StarRatingCell.vue";
import EqualizerBars from "@shared/components/EqualizerBars.vue";
import { useLibraryStore, formatDuration } from "../../stores/library";
import { usePlayerStore } from "../../stores/player";
import { usePlaylistStore } from "../../stores/playlists";
import { useSelectionStore } from "../../stores/selection";
import { useLibraryTableStore, TABLE_COLUMNS, type TableColumnId } from "../../stores/libraryTable";
import { openMetadataEditor } from "../../composables/useMetadataEditor";
import { showToast } from "../../composables/useToast";
import { startTrackDrag } from "../../composables/useTrackDrag";
import {
  buildTableRows,
  groupAvgRating,
  groupTracks,
  cycleSortDirection,
  type TableGroup,
  type TableGroupBy,
  type TableRow,
  type TableSortColumn,
} from "./tableModel";
import type { CatalogTrack } from "../../types";

const MENU_ROW = "flex h-10 w-full items-center gap-3 px-4 text-left text-body-lg text-on-surface hover:bg-surface-container/60";

const GROUP_OPTIONS: { value: TableGroupBy; label: string }[] = [
  { value: "none", label: "None" },
  { value: "artist", label: "Artist" },
  { value: "album", label: "Album" },
];
const DENSITY_OPTIONS = [
  { value: "compact" as const, label: "Compact" },
  { value: "comfortable" as const, label: "Comfortable" },
  { value: "spacious" as const, label: "Spacious" },
];

const TRACK_ROW_HEIGHT: Record<"compact" | "comfortable" | "spacious", number> = {
  compact: 28,
  comfortable: 44,
  spacious: 64,
};
const GROUP_ROW_HEIGHT = 40;
const OVERSCAN = 10;

const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();
const selection = useSelectionStore();
const tableStore = useLibraryTableStore();

// --- Toolbar -----------------------------------------------------------

const groupMenuOpen = ref(false);
const groupMenuRef = ref<HTMLElement | null>(null);
const colMenuOpen = ref(false);
const colMenuRef = ref<HTMLElement | null>(null);

const groupByLabel = computed(() => GROUP_OPTIONS.find((o) => o.value === tableStore.groupBy)?.label ?? "None");

function selectGroupBy(value: TableGroupBy): void {
  tableStore.setGroupBy(value);
  groupMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  const target = e.target as Node;
  if (groupMenuOpen.value && groupMenuRef.value && !groupMenuRef.value.contains(target)) groupMenuOpen.value = false;
  if (colMenuOpen.value && colMenuRef.value && !colMenuRef.value.contains(target)) colMenuOpen.value = false;
}

// --- Columns -------------------------------------------------------------

const visibleColumnDefs = computed(() => TABLE_COLUMNS.filter((c) => tableStore.visibleColumns[c.id]));
const colCount = computed(() => visibleColumnDefs.value.length + 1);

function onSortClick(column: TableSortColumn): void {
  const next = cycleSortDirection({ column: tableStore.sortColumn, direction: tableStore.sortDirection }, column);
  tableStore.setSort(next.column, next.direction);
}

const resizeState = ref<{ id: TableColumnId; startX: number; startWidth: number } | null>(null);

function onResizeStart(id: TableColumnId, e: MouseEvent): void {
  e.preventDefault();
  resizeState.value = { id, startX: e.clientX, startWidth: tableStore.columnWidths[id] };
}

function onResizeMove(e: MouseEvent): void {
  const s = resizeState.value;
  if (!s) return;
  tableStore.setColumnWidth(s.id, s.startWidth + (e.clientX - s.startX));
}

function onResizeEnd(): void {
  resizeState.value = null;
}

// --- Rows: filter + sort + group -----------------------------------------

const sourceTracks = computed(() => lib.filteredTracks);
const expandedGroups = ref<Set<string>>(new Set());
const groupsUnordered = computed(() => groupTracks(sourceTracks.value, tableStore.groupBy));
let prevGroupKeys = new Set<string>();
let appliedDefault = false;

watch(
  () => tableStore.groupBy,
  (by) => {
    appliedDefault = false;
    if (by === "none") {
      expandedGroups.value = new Set();
      prevGroupKeys = new Set();
      return;
    }
    const groups = groupsUnordered.value ?? [];
    expandedGroups.value = tableStore.defaultGroupsExpanded ? new Set(groups.map((g) => g.key)) : new Set();
    prevGroupKeys = new Set(groups.map((g) => g.key));
    appliedDefault = true;
  },
  { immediate: true },
);

watch(groupsUnordered, (groups) => {
  if (!groups?.length) return;
  const keys = new Set(groups.map((g) => g.key));
  if (!appliedDefault) {
    appliedDefault = true;
    prevGroupKeys = keys;
    expandedGroups.value = tableStore.defaultGroupsExpanded ? new Set(keys) : new Set();
    return;
  }
  const next = new Set<string>();
  for (const k of expandedGroups.value) if (keys.has(k)) next.add(k);
  for (const k of keys) if (!prevGroupKeys.has(k)) next.add(k);
  prevGroupKeys = keys;
  expandedGroups.value = next;
});

function toggleGroup(key: string): void {
  const next = new Set(expandedGroups.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedGroups.value = next;
}

function expandAllGroups(): void {
  const groups = groupsUnordered.value;
  if (!groups?.length) {
    expandedGroups.value = new Set();
    return;
  }
  prevGroupKeys = new Set(groups.map((g) => g.key));
  expandedGroups.value = new Set(groups.map((g) => g.key));
  appliedDefault = true;
}

function collapseAllGroups(): void {
  expandedGroups.value = new Set();
  appliedDefault = true;
}

const built = computed(() =>
  buildTableRows(sourceTracks.value, {
    sortColumn: tableStore.sortColumn,
    sortDirection: tableStore.sortDirection,
    groupBy: tableStore.groupBy,
    minRating: tableStore.minRating,
    expandedGroups: expandedGroups.value,
  }),
);
const rows = computed(() => built.value.rows);
const orderedTracks = computed(() =>
  rows.value.filter((r): r is Extract<TableRow, { type: "track" }> => r.type === "track").map((r) => r.track),
);
const orderedTrackIds = computed(() => orderedTracks.value.map((t) => t.id));

// --- Virtualization (own scroll container; mixed group/track row heights) ---

const scrollRef = ref<HTMLElement | null>(null);
const scrollTop = ref(0);
const viewportHeight = ref(0);

const rowHeights = computed(() =>
  rows.value.map((r) => (r.type === "group" ? GROUP_ROW_HEIGHT : TRACK_ROW_HEIGHT[tableStore.density])),
);
const rowOffsets = computed(() => {
  const heights = rowHeights.value;
  const offsets = new Array<number>(heights.length + 1);
  offsets[0] = 0;
  for (let i = 0; i < heights.length; i++) offsets[i + 1] = offsets[i] + heights[i];
  return offsets;
});
const totalHeight = computed(() => rowOffsets.value[rowOffsets.value.length - 1] ?? 0);

function offsetToIndex(offsets: number[], y: number): number {
  let lo = 0;
  let hi = offsets.length - 2;
  if (hi < 0) return 0;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (offsets[mid] <= y) lo = mid;
    else hi = mid - 1;
  }
  return lo;
}

const visibleRange = computed(() => {
  const offsets = rowOffsets.value;
  const n = rows.value.length;
  if (n === 0) return { start: 0, end: 0 };
  const top = scrollTop.value;
  const bottom = top + viewportHeight.value;
  const start = Math.max(0, offsetToIndex(offsets, top) - OVERSCAN);
  const end = Math.min(n, offsetToIndex(offsets, bottom) + 1 + OVERSCAN);
  return { start, end };
});

const renderedRows = computed(() => {
  const { start, end } = visibleRange.value;
  const out: { row: TableRow; index: number }[] = [];
  for (let i = start; i < end; i++) out.push({ row: rows.value[i], index: i });
  return out;
});

const topSpacer = computed(() => rowOffsets.value[visibleRange.value.start] ?? 0);
const bottomSpacer = computed(
  () => totalHeight.value - (rowOffsets.value[visibleRange.value.end] ?? totalHeight.value),
);

let frame = 0;
function schedule(): void {
  if (frame !== 0) return;
  frame = requestAnimationFrame(() => {
    frame = 0;
    measure();
  });
}
function measure(): void {
  const el = scrollRef.value;
  if (!el) return;
  scrollTop.value = el.scrollTop;
  viewportHeight.value = el.clientHeight;
}

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  const el = scrollRef.value;
  if (el) {
    el.addEventListener("scroll", schedule, { passive: true });
    resizeObserver = new ResizeObserver(schedule);
    resizeObserver.observe(el);
  }
  measure();
  document.addEventListener("mousemove", onResizeMove);
  document.addEventListener("mouseup", onResizeEnd);
  document.addEventListener("click", onDocumentClick);
});

onBeforeUnmount(() => {
  scrollRef.value?.removeEventListener("scroll", schedule);
  resizeObserver?.disconnect();
  if (frame !== 0) cancelAnimationFrame(frame);
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  document.removeEventListener("click", onDocumentClick);
});

watch(() => rows.value.length, () => nextTick(measure));

function scrollFocusedIntoView(): void {
  const el = scrollRef.value;
  if (!el || focusedIndex.value < 0) return;
  const top = rowOffsets.value[focusedIndex.value] ?? 0;
  const height = rowHeights.value[focusedIndex.value] ?? 0;
  const bottom = top + height;
  if (top < el.scrollTop) el.scrollTop = top;
  else if (bottom > el.scrollTop + el.clientHeight) el.scrollTop = bottom - el.clientHeight;
}

// --- Covers ----------------------------------------------------------------

function coverUrl(track: CatalogTrack): string | null {
  if (!track.has_cover) return null;
  lib.requestCover(track.id);
  return lib.coverCache.get(track.id) ?? null;
}

function groupCover(group: TableGroup): string | null {
  const first = group.tracks.find((t) => t.has_cover);
  if (!first) return null;
  lib.requestCover(first.id);
  return lib.coverCache.get(first.id) ?? null;
}

// --- Selection / focus / row click ------------------------------------------

const focusedIndex = ref(-1);

function rowClass(track: CatalogTrack, index: number): string {
  const isSelected = selection.has(track.id);
  const isFocused = focusedIndex.value === index;
  const isPlaying = track.id === player.currentTrack?.id;
  const classes: string[] = [];
  if (isSelected) classes.push("bg-primary/10");
  else if (isFocused) classes.push("bg-surface-container/50");
  else classes.push("hover:bg-surface-container/40");
  if (isPlaying) classes.push("text-primary");
  return classes.join(" ");
}

/** Plain click selects exactly this row (Explorer-style); Shift/Ctrl extend
 *  or toggle via the shared selection store. Double-click plays instead. */
function onTrackRowClick(track: CatalogTrack, e: MouseEvent, index: number): void {
  focusedIndex.value = index;
  if (!e.shiftKey && !e.ctrlKey && !e.metaKey) {
    selection.begin(track.id);
    return;
  }
  selection.click(track.id, orderedTrackIds.value, { shift: e.shiftKey, ctrl: e.ctrlKey || e.metaKey });
}

function onTrackRowDblClick(track: CatalogTrack): void {
  void player.playTrack(track, orderedTracks.value);
}

function onGroupRowClick(row: Extract<TableRow, { type: "group" }>, index: number): void {
  focusedIndex.value = index;
  toggleGroup(row.key);
}

function onSetSingleRating(track: CatalogTrack, rating: number | null): void {
  void lib.setRatings([track.id], rating);
}

// --- Keyboard navigation -----------------------------------------------------

function onKeydown(e: KeyboardEvent): void {
  const target = e.target as HTMLElement | null;
  if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return;
  const n = rows.value.length;
  if (n === 0) return;

  if (e.key === "ArrowDown") {
    e.preventDefault();
    focusedIndex.value = focusedIndex.value < 0 ? 0 : Math.min(n - 1, focusedIndex.value + 1);
    scrollFocusedIntoView();
    return;
  }
  if (e.key === "ArrowUp") {
    e.preventDefault();
    focusedIndex.value = focusedIndex.value < 0 ? n - 1 : Math.max(0, focusedIndex.value - 1);
    scrollFocusedIntoView();
    return;
  }
  if (e.key === " ") {
    if (focusedIndex.value < 0) return;
    e.preventDefault();
    const row = rows.value[focusedIndex.value];
    if (row.type === "group") toggleGroup(row.key);
    else if (!selection.selecting) selection.begin(row.track.id);
    else selection.toggle(row.track.id);
    return;
  }
  if (e.key === "Enter") {
    if (focusedIndex.value < 0) return;
    const row = rows.value[focusedIndex.value];
    if (row.type !== "track") return;
    e.preventDefault();
    if (player.currentTrack?.id === row.track.id) player.playPause();
    else void player.playTrack(row.track, orderedTracks.value);
    return;
  }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "a") {
    e.preventDefault();
    selection.selectAll(orderedTrackIds.value);
    return;
  }
  if (/^[0-5]$/.test(e.key) && selection.hasSelection) {
    e.preventDefault();
    void applyRating(selection.ids, e.key === "0" ? null : Number(e.key));
  }
}

// --- Rating (with confirm for multi-track) ----------------------------------

const ratingConfirm = ref<{ ids: number[]; rating: number | null } | null>(null);

async function applyRating(ids: readonly number[], rating: number | null): Promise<void> {
  if (ids.length > 1) {
    ratingConfirm.value = { ids: [...ids], rating };
    return;
  }
  await lib.setRatings(ids, rating);
}

function confirmRating(): void {
  const pending = ratingConfirm.value;
  ratingConfirm.value = null;
  if (pending) void lib.setRatings(pending.ids, pending.rating);
}

// --- Drag to playlist --------------------------------------------------------

function onDragStart(e: DragEvent, tracks: CatalogTrack[]): void {
  const ids =
    tracks.length === 1 && selection.has(tracks[0].id) && selection.count > 1 ? selection.ids : tracks.map((t) => t.id);
  startTrackDrag(e, ids);
}

// --- Context menu -------------------------------------------------------------

interface ContextMenuState {
  x: number;
  y: number;
  tracks: CatalogTrack[];
}

const contextMenu = ref<ContextMenuState | null>(null);
const contextMenuRef = ref<HTMLElement | null>(null);
const addToPlaylistOpen = ref(false);
const infoOpen = ref(false);

const manualPlaylists = computed(() => playlistStore.playlists.filter((p) => p.smart_rules == null));

function openContextMenu(e: MouseEvent, tracks: CatalogTrack[]): void {
  if (!tracks.length) return;
  addToPlaylistOpen.value = false;
  infoOpen.value = false;
  if (tracks.length === 1) {
    const id = tracks[0].id;
    if (!selection.has(id)) {
      selection.begin(id);
      contextMenu.value = { x: e.clientX, y: e.clientY, tracks };
      return;
    }
    if (selection.count > 1) {
      const selectedIds = new Set(selection.ids);
      const selectedTracks = orderedTracks.value.filter((t) => selectedIds.has(t.id));
      contextMenu.value = { x: e.clientX, y: e.clientY, tracks: selectedTracks };
      return;
    }
  }
  contextMenu.value = { x: e.clientX, y: e.clientY, tracks };
}

function closeContextMenu(): void {
  contextMenu.value = null;
}

watch(contextMenu, (menu) => {
  if (!menu) return;
  const onOutside = (e: MouseEvent): void => {
    const target = e.target as Node;
    if (contextMenuRef.value?.contains(target)) return;
    closeContextMenu();
    document.removeEventListener("click", onOutside);
    document.removeEventListener("keydown", onEscape);
  };
  const onEscape = (e: KeyboardEvent): void => {
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

function menuPlay(): void {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  closeContextMenu();
  void player.playTrack(menu.tracks[0], menu.tracks);
}

function menuPlayNext(): void {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  closeContextMenu();
  for (const t of [...menu.tracks].reverse()) player.playNext(t);
}

function menuAddToQueue(): void {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  closeContextMenu();
  player.addTracksToQueue(menu.tracks);
}

async function menuAddToPlaylist(playlistId: number, name: string): Promise<void> {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  const ids = menu.tracks.map((t) => t.id);
  closeContextMenu();
  try {
    await playlistStore.addTracks(playlistId, ids);
    showToast(`Added to ${name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

const contextMenuAllFavorite = computed(() => {
  const tracks = contextMenu.value?.tracks ?? [];
  return tracks.length > 0 && tracks.every((t) => player.favorites.has(t.id));
});

async function menuToggleFavorite(): Promise<void> {
  const menu = contextMenu.value;
  const tracks = menu?.tracks ?? [];
  if (!tracks.length) return;
  const makeFavorite = !contextMenuAllFavorite.value;
  closeContextMenu();
  for (const t of tracks) {
    if (player.favorites.has(t.id) !== makeFavorite) await player.toggleFavorite(t);
  }
}

const contextMenuRating = computed<number | null>(() => {
  const tracks = contextMenu.value?.tracks ?? [];
  if (!tracks.length) return null;
  const first = tracks[0].rating ?? null;
  return tracks.every((t) => (t.rating ?? null) === first) ? first : null;
});

function menuSetRating(rating: number | null): void {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  const ids = menu.tracks.map((t) => t.id);
  closeContextMenu();
  void applyRating(ids, rating);
}

function menuEditMetadata(): void {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  const ids = menu.tracks.map((t) => t.id);
  closeContextMenu();
  openMetadataEditor(ids);
}

function menuCopyPath(): void {
  const menu = contextMenu.value;
  if (menu?.tracks.length !== 1) return;
  copyPath(menu.tracks[0].path);
  closeContextMenu();
}

function copyPath(path: string): void {
  navigator.clipboard.writeText(path).catch(() => {
    showToast("Couldn't copy path");
  });
}

const deleteConfirm = ref<number[] | null>(null);

function requestDelete(): void {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  deleteConfirm.value = menu.tracks.map((t) => t.id);
  closeContextMenu();
}

async function confirmDelete(): Promise<void> {
  const ids = deleteConfirm.value;
  deleteConfirm.value = null;
  if (!ids) return;
  const result = await lib.deleteTracks(ids);
  selection.prune((id) => lib.trackById.has(id));
  if (result.failed.length > 0) {
    showToast(`Deleted ${result.deleted.length}, ${result.failed.length} failed`);
  } else {
    showToast(`Deleted ${result.deleted.length} track${result.deleted.length === 1 ? "" : "s"}`);
  }
}

function formatTimestamp(secs: number): string {
  const d = new Date(secs * 1000);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const infoFields = computed(() => {
  const t = contextMenu.value?.tracks[0];
  if (!t) return [];
  const fields: { label: string; value: string }[] = [];
  const push = (label: string, value: string | number | null | undefined): void => {
    if (value === null || value === undefined || value === "") return;
    fields.push({ label, value: String(value) });
  };
  push("Title", t.title);
  push("Artist", t.artist);
  if (t.album_artist !== t.artist) push("Album Artist", t.album_artist);
  push("Featuring", t.featuring);
  push("Album", t.album);
  push("Year", t.year);
  push("Genre", t.genre);
  push("Track #", t.track_number);
  push("Disc #", t.disc_number);
  if (t.duration_secs) push("Duration", formatDuration(t.duration_secs));
  push("Format", t.format.toUpperCase());
  if (t.rating !== null) push("Rating", `${t.rating} / 5`);
  if (t.play_count > 0) push("Play Count", t.play_count);
  if (t.last_played_at !== null) push("Last Played", formatTimestamp(t.last_played_at));
  push("File", t.path);
  return fields;
});
</script>

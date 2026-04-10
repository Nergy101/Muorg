<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import { usePlaylistAdd } from "../../composables/usePlaylistAdd";
import type { CatalogTrack } from "../../types";
import { useOverlayScrollbars } from "../../composables/useOverlayScrollbars";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import FeatherIcon from "../shared/FeatherIcon.vue";
import MarqueeCell from "../shared/MarqueeCell.vue";
import PlaylistDuplicateDialog from "../shared/PlaylistDuplicateDialog.vue";
import StarRating from "../shared/StarRating.vue";

const emit = defineEmits<{ (e: "openMetadata"): void }>();
const props = withDefaults(defineProps<{
  tracksOverride?: CatalogTrack[] | null;
  disableGrouping?: boolean;
  hideArtistColumn?: boolean;
  hideAlbumColumn?: boolean;
  hideYearColumn?: boolean;
}>(), {
  tracksOverride: null,
  disableGrouping: false,
  hideArtistColumn: false,
  hideAlbumColumn: false,
  hideYearColumn: false,
});

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { playlists } = storeToRefs(playlistStore);
const { pendingAdd, tryAddToPlaylist, confirmAddAll, confirmAddDeduped, cancelPendingAdd } = usePlaylistAdd();
const { activePlaylistTrackIds, activePlaylistEntryIds } = storeToRefs(store);

const { filteredTracks, selectedTrackIds, groupBy, currentPlayingTrackId, multiSelectMode } = storeToRefs(store);
const effectiveGroupBy = computed<"none" | "artist" | "album">(() => (
  props.disableGrouping ? "none" : groupBy.value
));
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
  tableColRating,
  tableColWidths,
  groupHeaderAlbumArt,
  groupHeaderAlbumArtForArtist,
  splitAlbumHeadersByArtist,
  hideAlbumArtColInAlbumGroups,
  hideGroupTrackCount,
  hideWikipediaCoverSearch,
  tableSortColumn,
  tableSortDirection,
} = storeToRefs(settingsStore);

function applySortToTracks(tracks: CatalogTrack[]): CatalogTrack[] {
  const col = tableSortColumn.value;
  if (!col) return tracks;
  const dir = tableSortDirection.value === "desc" ? -1 : 1;
  return [...tracks].sort((a, b) => {
    let va: string | number | null | undefined;
    let vb: string | number | null | undefined;
    if (col === "title") { va = a.title ?? ""; vb = b.title ?? ""; }
    else if (col === "artist") { va = a.artist ?? ""; vb = b.artist ?? ""; }
    else if (col === "album") { va = a.album ?? ""; vb = b.album ?? ""; }
    else if (col === "year") { va = a.year ?? 0; vb = b.year ?? 0; }
    else if (col === "duration") { va = a.duration_secs ?? 0; vb = b.duration_secs ?? 0; }
    else if (col === "rating") { va = a.rating ?? 0; vb = b.rating ?? 0; }
    else return 0;
    if (typeof va === "string" && typeof vb === "string") {
      return dir * va.localeCompare(vb, undefined, { sensitivity: "base" });
    }
    return dir * ((va as number) - (vb as number));
  });
}

const baseTracks = computed(() => props.tracksOverride ?? filteredTracks.value);
const sortedFilteredTracks = computed(() => applySortToTracks(baseTracks.value));

function toggleSortColumn(col: "title" | "artist" | "album" | "year" | "duration" | "rating") {
  if (tableSortColumn.value === col) {
    if (tableSortDirection.value === "asc") {
      settingsStore.setTableSort(col, "desc");
    } else {
      settingsStore.setTableSort(null, "asc");
    }
  } else {
    settingsStore.setTableSort(col, "asc");
  }
}

type GroupRow = {
  key: string;
  label: string;
  tracks: CatalogTrack[];
  artist?: string;
};

type VisibleRow =
  | { type: "group"; key: string; group: GroupRow }
  | { type: "track"; track: CatalogTrack; playlistEntryId?: number; groupKey?: string };

function groupAvgRating(tracks: CatalogTrack[]): number | null {
  const rated = tracks.filter((t) => t.rating != null);
  if (!rated.length) return null;
  return Math.round(rated.reduce((s, t) => s + t.rating!, 0) / rated.length);
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

function selectRow(t: CatalogTrack) {
  if (multiSelectMode.value) {
    store.toggleSelection(t.id);
  } else {
    store.clearSelection();
    store.toggleSelection(t.id);
  }
}

const groupedRows = computed(() => {
  const by = effectiveGroupBy.value;
  const base = sortedFilteredTracks.value;
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
      const key = splitAlbumHeadersByArtist.value ? `${album}|||${artist}` : album;
      let group = map.get(key);
      if (!group) {
        group = { key, label: album, artist: splitAlbumHeadersByArtist.value ? artist : undefined, tracks: [] };
        map.set(key, group);
      }
      group.tracks.push(t);
    }
  }
  const groups = [...map.values()];
  const col = tableSortColumn.value;
  const dir = tableSortDirection.value === "desc" ? -1 : 1;
  groups.sort((a, b) => {
    if (col === "year") {
      const aYear = a.tracks.find((t) => t.year != null)?.year ?? 0;
      const bYear = b.tracks.find((t) => t.year != null)?.year ?? 0;
      const diff = aYear - bYear;
      if (diff !== 0) return dir * diff;
    } else if (col === "duration") {
      const aDur = a.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
      const bDur = b.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
      const diff = aDur - bDur;
      if (diff !== 0) return dir * diff;
    } else if (col === "artist") {
      const cmp = (a.artist ?? a.label).localeCompare(b.artist ?? b.label, undefined, { sensitivity: "base" });
      if (cmp !== 0) return dir * cmp;
    } else if (col === "album") {
      const cmp = a.label.localeCompare(b.label, undefined, { sensitivity: "base" });
      if (cmp !== 0) return dir * cmp;
    } else if (col === "title") {
      const cmp = (a.tracks[0]?.title ?? "").localeCompare(b.tracks[0]?.title ?? "", undefined, { sensitivity: "base" });
      if (cmp !== 0) return dir * cmp;
    } else if (col === "rating") {
      const aRated = a.tracks.filter((t) => t.rating != null);
      const bRated = b.tracks.filter((t) => t.rating != null);
      const aAvg = aRated.length ? aRated.reduce((s, t) => s + t.rating!, 0) / aRated.length : 0;
      const bAvg = bRated.length ? bRated.reduce((s, t) => s + t.rating!, 0) / bRated.length : 0;
      const diff = aAvg - bAvg;
      if (diff !== 0) return dir * diff;
    }
    // Default tie-break: alphabetical by label then artist
    const byLabel = a.label.localeCompare(b.label, undefined, { sensitivity: "base" });
    if (byLabel !== 0) return byLabel;
    return (a.artist ?? "").localeCompare(b.artist ?? "", undefined, { sensitivity: "base" });
  });
  return groups;
});

const groupCovers = computed(() => {
  const by = effectiveGroupBy.value;
  const showForAlbum = by === "album" && groupHeaderAlbumArt.value;
  const showForArtist = by === "artist" && groupHeaderAlbumArtForArtist.value;
  if (!showForAlbum && !showForArtist) return {} as Record<string, import("../../stores/catalog").CoverInfo | null | undefined>;
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
  effectiveGroupBy,
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

// When a playlist is opened, reset group header state to match defaultGroupsExpanded.
watch(
  () => store.activePlaylistId,
  (id) => {
    if (id === null) return;
    nextTick(() => {
      const groups = groupedRows.value;
      if (!groups?.length) return;
      const keys = new Set(groups.map((g) => g.key));
      expandedGroups.value = defaultGroupsExpanded.value ? new Set(keys) : new Set();
      prevGroupKeys.value = keys;
      appliedDefaultForSession.value = true;
    });
  },
);

/**
 * When a playlist is active this maps each visible (filtered) track back to the
 * `playlist_tracks.id` that corresponds to it — keyed by (trackId, occurrenceIndex)
 * so duplicate entries in the same playlist are each resolvable to their own row.
 *
 * Built from the *filtered* entry list (respects the search query) so the occurrence
 * indices stay in sync with what the user actually sees.
 */
const filteredEntryIdsByTrackOccurrence = computed((): Map<number, number[]> | null => {
  const trackIds = activePlaylistTrackIds.value;
  const entryIds = activePlaylistEntryIds.value;
  if (!trackIds || !entryIds) return null;

  const q = store.searchQuery.trim().toLowerCase();
  const idToTrack = new Map(store.tracks.map((t) => [t.id, t]));

  // Walk the full ordered playlist and collect entry IDs only for visible entries.
  const map = new Map<number, number[]>(); // trackId → [entryId, ...]
  for (let i = 0; i < trackIds.length; i++) {
    const trackId = trackIds[i];
    const track = idToTrack.get(trackId);
    if (!track) continue;
    if (q) {
      const passes =
        (track.title ?? "").toLowerCase().includes(q) ||
        (track.artist ?? "").toLowerCase().includes(q) ||
        (track.album ?? "").toLowerCase().includes(q);
      if (!passes) continue;
    }
    if (!map.has(trackId)) map.set(trackId, []);
    map.get(trackId)!.push(entryIds[i]);
  }
  return map;
});

const visibleRows = computed((): VisibleRow[] => {
  const groups = groupedRows.value;
  const entryMap = filteredEntryIdsByTrackOccurrence.value;

  // Per-call occurrence counter so each duplicate gets the right entryId slot.
  const occurrenceCount = new Map<number, number>();
  function nextEntryId(trackId: number): number | undefined {
    if (!entryMap) return undefined;
    const n = occurrenceCount.get(trackId) ?? 0;
    occurrenceCount.set(trackId, n + 1);
    return entryMap.get(trackId)?.[n];
  }

  if (!groups) {
    return sortedFilteredTracks.value.map((track) => ({
      type: "track",
      track,
      playlistEntryId: nextEntryId(track.id),
    }));
  }

  const out: VisibleRow[] = [];
  for (const group of groups) {
    out.push({ type: "group", key: group.key, group });
    if (expandedGroups.value.has(group.key)) {
      for (const t of group.tracks) {
        out.push({ type: "track", track: t, playlistEntryId: nextEntryId(t.id), groupKey: group.key });
      }
    }
  }
  return out;
});

const focusedGroupKey = computed<string | null>(() => {
  const idx = focusedRowIndex.value;
  if (idx == null) return null;
  const row = visibleRows.value[idx];
  if (row?.type === "group" && expandedGroups.value.has(row.key)) return row.key;
  return null;
});

const showAlbumArtCol = computed(() =>
  tableColAlbumArt.value && !(hideAlbumArtColInAlbumGroups.value && effectiveGroupBy.value === "album")
);
const showArtistCol = computed(() => !props.hideArtistColumn);
const showAlbumCol = computed(() => !props.hideAlbumColumn);
const showYearCol = computed(() => tableColYear.value && !props.hideYearColumn);

const tableColCount = computed(() => {
  let n = 1;
  if (multiSelectMode.value) n += 1;
  if (showAlbumArtCol.value) n += 1;
  n += 3;
  if (!showArtistCol.value) n -= 1;
  if (!showAlbumCol.value) n -= 1;
  if (showYearCol.value) n += 1;
  if (tableColDuration.value) n += 1;
  if (tableColFormat.value) n += 1;
  if (tableColPath.value) n += 1;
  if (tableColRating.value) n += 1;
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
const ROW_HEIGHT_GROUP_COMFORTABLE = 64;
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
    density === "spacious" && effectiveGroupBy.value === "album"
      ? ROW_HEIGHT_GROUP_SPACIOUS
      : density === "comfortable" && effectiveGroupBy.value === "album" && groupHeaderAlbumArt.value
        ? ROW_HEIGHT_GROUP_COMFORTABLE
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
const { viewportRef: scrollViewportRef } = useOverlayScrollbars(tableContainerRef, { overflow: { x: "scroll" } });
const scrollTopRef = ref(0);
const containerHeightRef = ref(0);
const containerClientWidth = ref(0);

function getScrollElement(): HTMLElement | null {
  return scrollViewportRef.value ?? tableContainerRef.value;
}

function updateScrollMeasurements() {
  const el = getScrollElement();
  if (!el) return;
  scrollTopRef.value = el.scrollTop;
  containerHeightRef.value = el.clientHeight;
  containerClientWidth.value = el.clientWidth;
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

// When grouping with cover art headers enabled, proactively fetch covers for all tracks in groups
watch(
  () => {
    const by = effectiveGroupBy.value;
    if (by === "album" && groupHeaderAlbumArt.value) return groupedRows.value;
    if (by === "artist" && groupHeaderAlbumArtForArtist.value) return groupedRows.value;
    return null;
  },
  (groups) => {
    if (!groups) return;
    for (const g of groups) {
      // For artist groups we only need the first track's cover; for album groups fetch all
      const tracks = effectiveGroupBy.value === "artist" ? g.tracks.slice(0, 1) : g.tracks;
      for (const t of tracks) {
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
  emit("openMetadata");
}

function openGroupCoverFromWikipedia(group: GroupRow) {
  const ids = group.tracks.map((t) => t.id);
  if (!ids.length) return;
  store.setSelection(ids);
  store.setMultiSelectMode(true);
  emit("openMetadata");
  nextTick(() => store.setOpenWikipediaModal(true));
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
  const el = getScrollElement();
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
    const container = getScrollElement();
    const el = container?.querySelector(`[data-row-index="${focusedRowIndex.value}"]`);
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
let scrollElementForCleanup: HTMLElement | null = null;

onMounted(() => {
  document.addEventListener("mousemove", onResizeMove);
  document.addEventListener("mouseup", onResizeEnd);
  nextTick(() => {
    const container = getScrollElement();
    if (!container) return;
    scrollElementForCleanup = container;
    container.addEventListener("scroll", updateScrollMeasurements, { passive: true });
    updateScrollMeasurements();
    resizeObserver = new ResizeObserver(updateScrollMeasurements);
    resizeObserver.observe(container);
  });
});

onUnmounted(() => {
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  if (scrollElementForCleanup) {
    scrollElementForCleanup.removeEventListener("scroll", updateScrollMeasurements);
    scrollElementForCleanup = null;
  }
  resizeObserver?.disconnect();
  resizeObserver = null;
});

function scrollToTrackId(id: number) {
  // If the track's group is collapsed, expand it first so the row becomes visible.
  const groups = groupedRows.value;
  if (groups?.length) {
    for (const group of groups) {
      if (group.tracks.some((t) => t.id === id) && !expandedGroups.value.has(group.key)) {
        const next = new Set(expandedGroups.value);
        next.add(group.key);
        expandedGroups.value = next;
        // Wait for Vue to re-render the newly expanded rows before scrolling.
        nextTick(() => scrollToTrackId(id));
        return;
      }
    }
  }
  const rows = visibleRows.value;
  const idx = rows.findIndex((r) => r.type === "track" && r.track.id === id);
  if (idx < 0) return;
  focusedRowIndex.value = idx;
  if (useVirtualization.value) scrollToRowIndex(idx);
  else scrollFocusedRowIntoView();
}

const contextMenu = ref<{
  x: number;
  y: number;
  tracks: CatalogTrack[];
  /** Set only for single-track rows in an active playlist — the playlist_tracks.id to remove. */
  playlistEntryId?: number;
} | null>(null);
const contextMenuRef = ref<HTMLElement | null>(null);

function openContextMenu(e: MouseEvent, tracks: CatalogTrack[], playlistEntryId?: number) {
  e.preventDefault();
  if (!tracks.length) return;
  contextMenu.value = { x: e.clientX, y: e.clientY, tracks, playlistEntryId };
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

async function addToPlaylistFromContextMenu(playlistId: number) {
  if (!contextMenu.value?.tracks.length) return;
  const trackIds = contextMenu.value.tracks.map((t) => t.id);
  const playlist = playlists.value.find((p) => p.id === playlistId);
  closeContextMenu();
  await tryAddToPlaylist(playlistId, trackIds, playlist?.name ?? "");
}

async function removeFromPlaylist() {
  const menu = contextMenu.value;
  const playlistId = store.activePlaylistId;
  if (!menu?.tracks.length || playlistId == null) return;
  closeContextMenu();
  if (menu.playlistEntryId != null) {
    await playlistStore.removePlaylistEntry(menu.playlistEntryId);
  } else {
    await playlistStore.removeTracksFromPlaylist(playlistId, menu.tracks.map((t) => t.id));
  }
  const entries = await playlistStore.getPlaylistEntries(playlistId);
  store.setActivePlaylist(playlistId, entries);
}

async function setRatingFromContextMenu(rating: number | null) {
  const menu = contextMenu.value;
  if (!menu?.tracks.length) return;
  const paths = menu.tracks.map((t) => t.path);
  closeContextMenu();
  await store.setRating(paths, rating);
}

/** The current rating displayed in the context menu (common rating of all selected tracks, or null). */
const contextMenuRating = computed(() => {
  const tracks = contextMenu.value?.tracks ?? [];
  if (!tracks.length) return null;
  const first = tracks[0].rating ?? null;
  return tracks.every((t) => (t.rating ?? null) === first) ? first : null;
});

// Playlist submenu open state
const playlistSubmenuOpen = ref(false);
const playlistBtnContainerRef = ref<HTMLElement | null>(null);
const playlistSubmenuFlipUp = computed(() => {
  if (!playlistBtnContainerRef.value) return false;
  const rect = playlistBtnContainerRef.value.getBoundingClientRect();
  return rect.bottom + 220 > window.innerHeight;
});

watch(contextMenu, (menu) => {
  if (!menu) {
    playlistSubmenuOpen.value = false;
    return;
  }
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

// ── Drag-and-drop (tracks → playlists) ────────────────────────────────────

function onTrackDragStart(e: DragEvent, tracks: CatalogTrack[]) {
  if (!e.dataTransfer) return;
  e.dataTransfer.setData(
    "application/muorg-tracks",
    JSON.stringify(tracks.map((t) => t.id))
  );
  e.dataTransfer.effectAllowed = "copy";
  store.setInternalQueueDrag(true);
}

function onTrackDragEnd() {
  store.setInternalQueueDrag(false);
}

defineExpose({ scrollToTrackId, expandAllGroups, collapseAllGroups });
</script>

<template>
  <div
    ref="tableContainerRef"
    tabindex="0"
    class="table-scroll-container flex-1 overflow-auto outline-none"
    data-overlayscrollbars-initialize
    @keydown="onTableKeydown"
  >
    <table
      class="table-fixed text-left text-sm"
      :class="{ 'table-density-compact': tableDensity === 'compact', 'table-density-spacious': tableDensity === 'spacious' }"
      :style="containerClientWidth > 0 ? { width: containerClientWidth + 'px' } : { width: '100%' }"
    >
      <colgroup>
        <col v-if="multiSelectMode" :style="{ width: CHECKBOX_COL_WIDTH + 'px' }" />
        <col v-if="showAlbumArtCol" :style="{ width: colWidth('albumArt') + 'px' }" />
        <col :style="{ width: colWidth('title') + 'px' }" />
        <col v-if="tableColRating" :style="{ width: colWidth('rating') + 'px' }" />
        <col v-if="showArtistCol" :style="{ width: colWidth('artist') + 'px' }" />
        <col v-if="showAlbumCol" :style="{ width: colWidth('album') + 'px' }" />
        <col v-if="showYearCol" :style="{ width: colWidth('year') + 'px' }" />
        <col v-if="tableColDuration" :style="{ width: colWidth('duration') + 'px' }" />
        <col v-if="tableColFormat" :style="{ width: colWidth('format') + 'px' }" />
        <col v-if="tableColPath" :style="{ width: colWidth('path') + 'px' }" />
        <col /><!-- filler: absorbs remaining space -->
      </colgroup>
      <thead class="sticky top-0 z-10 bg-stone-800">
        <tr>
          <th v-if="multiSelectMode" class="p-2" style="width: 32px">
            <label class="flex cursor-pointer items-center gap-1.5 text-xs text-stone-400">
              <input
                type="checkbox"
                :checked="multiSelectMode"
                class="rounded border-stone-600"
                @change="store.setMultiSelectMode(($event.target as HTMLInputElement).checked)"
              />
              Multi<template v-if="selectedTrackIds.length"> ({{ selectedTrackIds.length }})</template>
            </label>
          </th>
          <th v-if="showAlbumArtCol" class="relative p-2">
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('albumArt', $event)"
            />
          </th>
          <th class="relative p-2 font-medium text-stone-400 overflow-hidden">
            <button type="button" class="inline-flex items-center gap-1 hover:text-stone-200" @click="toggleSortColumn('title')">
              Title
              <span v-if="tableSortColumn === 'title'" class="text-stone-300">{{ tableSortDirection === 'asc' ? '▲' : '▼' }}</span>
            </button>
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('title', $event)"
            />
          </th>
          <th v-if="tableColRating" class="relative p-2 font-medium text-stone-400">
            <button type="button" class="inline-flex items-center gap-1 hover:text-stone-200" @click="toggleSortColumn('rating')">
              Rating
              <span v-if="tableSortColumn === 'rating'" class="text-stone-300">{{ tableSortDirection === 'asc' ? '▲' : '▼' }}</span>
            </button>
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('rating', $event)"
            />
          </th>
          <th v-if="showArtistCol" class="relative p-2 font-medium text-stone-400 overflow-hidden">
            <button type="button" class="inline-flex items-center gap-1 hover:text-stone-200" @click="toggleSortColumn('artist')">
              Artist
              <span v-if="tableSortColumn === 'artist'" class="text-stone-300">{{ tableSortDirection === 'asc' ? '▲' : '▼' }}</span>
            </button>
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('artist', $event)"
            />
          </th>
          <th v-if="showAlbumCol" class="relative p-2 font-medium text-stone-400 overflow-hidden">
            <button type="button" class="inline-flex items-center gap-1 hover:text-stone-200" @click="toggleSortColumn('album')">
              Album
              <span v-if="tableSortColumn === 'album'" class="text-stone-300">{{ tableSortDirection === 'asc' ? '▲' : '▼' }}</span>
            </button>
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('album', $event)"
            />
          </th>
          <th v-if="showYearCol" class="relative p-2 font-medium text-stone-400">
            <button type="button" class="inline-flex items-center gap-1 hover:text-stone-200" @click="toggleSortColumn('year')">
              Year
              <span v-if="tableSortColumn === 'year'" class="text-stone-300">{{ tableSortDirection === 'asc' ? '▲' : '▼' }}</span>
            </button>
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('year', $event)"
            />
          </th>
          <th v-if="tableColDuration" class="relative p-2 font-medium text-stone-400">
            <button type="button" class="inline-flex items-center gap-1 hover:text-stone-200" @click="toggleSortColumn('duration')">
              Duration
              <span v-if="tableSortColumn === 'duration'" class="text-stone-300">{{ tableSortDirection === 'asc' ? '▲' : '▼' }}</span>
            </button>
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('duration', $event)"
            />
          </th>
          <th v-if="tableColFormat" class="relative p-2 font-medium text-stone-400">
            Format
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('format', $event)"
            />
          </th>
          <th v-if="tableColPath" class="relative truncate p-2 font-medium text-stone-400">
            Path
            <span
              class="absolute right-0 top-0 h-full w-1 cursor-col-resize touch-none bg-stone-600/30 hover:bg-stone-500/70"
              aria-hidden="true"
              @mousedown="onResizeStart('path', $event)"
            />
          </th>
          <th></th><!-- filler -->
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
              draggable="true"
              class="cursor-context-menu font-medium text-stone-400 hover:bg-stone-700/80"
              :class="[
                focusedRowIndex === index ? 'bg-stone-700/80 table-row-focused' : 'bg-stone-800/80',
                { 'group-row-with-selection': groupContainsSelection(row.group) },
              ]"
              :style="useVirtualization ? { height: rowHeights[index] + 'px' } : undefined"
              @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = index) : undefined"
              @click="focusedRowIndex = index; toggleGroup(row.key)"
              @contextmenu.prevent="openContextMenu($event, row.group.tracks)"
              @dragstart="onTrackDragStart($event, row.group.tracks)"
              @dragend="onTrackDragEnd"
            >
              <td
                :colspan="tableColCount"
                class="p-2"
                :class="{ 'py-3': tableDensity === 'spacious' && effectiveGroupBy === 'album' }"
              >
                <span
                  class="inline-flex flex-wrap items-center gap-2"
                  :class="{ 'gap-3': tableDensity === 'spacious' && effectiveGroupBy === 'album' }"
                >
                  <span
                    v-if="(effectiveGroupBy === 'album' && groupHeaderAlbumArt) || (effectiveGroupBy === 'artist' && groupHeaderAlbumArtForArtist)"
                    class="flex shrink-0 items-center justify-center overflow-hidden rounded bg-stone-800"
                    :class="tableDensity === 'spacious' ? 'h-20 w-20' : tableDensity === 'comfortable' ? 'h-12 w-12' : 'h-8 w-8'"
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
                      :class="tableDensity === 'spacious' ? 'h-8 w-8 text-lg' : tableDensity === 'comfortable' ? 'h-5 w-5 text-xs' : 'h-4 w-4 text-[0.6rem]'"
                      aria-hidden="true"
                    >
                      ♪
                    </span>
                    <span
                      v-else
                      class="inline-block animate-spin rounded-full border-2 border-stone-500 border-t-stone-300"
                      :class="tableDensity === 'spacious' ? 'h-8 w-8' : tableDensity === 'comfortable' ? 'h-5 w-5' : 'h-4 w-4'"
                      aria-hidden="true"
                    />
                  </span>
                  <span
                    class="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-900/60 text-stone-400 transition-transform duration-150"
                    :class="{ 'rotate-90': isGroupExpanded(row.key) }"
                    aria-hidden="true"
                  >
                    <FeatherIcon name="chevron-right" class="h-2.5 w-2.5" />
                  </span>
                  {{ row.group.label }}<template v-if="effectiveGroupBy === 'album' && row.group.artist"><span class="ml-1 text-stone-400"> · {{ row.group.artist }}</span></template>
                  <span v-if="!hideGroupTrackCount" class="ml-1 text-stone-500">({{ row.group.tracks.length }})</span>
                  <StarRating
                    v-if="groupAvgRating(row.group.tracks) !== null"
                    :model-value="groupAvgRating(row.group.tracks)"
                    :partial="row.group.tracks.some((t) => t.rating == null)"
                    readonly
                    class="ml-1"
                  />
                  <button
                    v-if="effectiveGroupBy === 'album'"
                    type="button"
                    class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                    aria-label="Edit album"
                    title="Edit album"
                    @click.stop="editGroup(row.group)"
                  >
                    <FeatherIcon name="edit-2" class="h-3.5 w-3.5" />
                  </button>
                  <button
                    v-if="effectiveGroupBy === 'album' && groupCovers[row.key] === null && !hideWikipediaCoverSearch"
                    type="button"
                    class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                    aria-label="From Wikipedia"
                    title="From Wikipedia"
                    @click.stop="openGroupCoverFromWikipedia(row.group)"
                  >
                    <FeatherIcon name="globe" class="h-3.5 w-3.5" />
                  </button>
                </span>
              </td>
            </tr>
            <tr
              v-else
              :data-row-index="index"
              :data-track-id="row.track.id"
              draggable="true"
              class="cursor-context-menu hover:bg-stone-800/50"
              :class="[
                { 'bg-stone-700/25': isSelected(row.track.id) && focusedRowIndex !== index },
                { 'bg-stone-600/30 table-row-focused': isSelected(row.track.id) && focusedRowIndex === index },
                { 'bg-stone-800 table-row-focused': !isSelected(row.track.id) && focusedRowIndex === index },
                { 'table-row-playing': row.track.id === currentPlayingTrackId },
                { 'group-child-focused': row.groupKey != null && row.groupKey === focusedGroupKey },
                { 'group-child-album': row.groupKey != null },
              ]"
              :style="useVirtualization ? { height: rowHeights[index] + 'px' } : undefined"
              @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = index) : undefined"
              @click="focusedRowIndex = index; selectRow(row.track)"
              @contextmenu.prevent="openContextMenu($event, [row.track], row.playlistEntryId)"
              @dragstart="onTrackDragStart($event, [row.track])"
              @dragend="onTrackDragEnd"
            >
              <td v-if="multiSelectMode" class="p-2">
                <input
                  type="checkbox"
                  :checked="isSelected(row.track.id)"
                  class="rounded border-stone-600"
                  @click.stop="focusedRowIndex = index; selectRow(row.track)"
                />
              </td>
              <td v-if="showAlbumArtCol" class="p-2">
                <div class="flex justify-center items-center">
                  <TrackAlbumArt
                    :path="row.track.path"
                    :size="tableDensity === 'compact' ? 'small' : 'medium'"
                  />
                </div>
              </td>
              <td class="p-2 text-stone-200">
                <MarqueeCell :text="row.track.title ?? '—'" />
              </td>
              <td v-if="tableColRating" class="px-4 py-2" @click.stop>
                <StarRating
                  class="mr-3"
                  :model-value="row.track.rating ?? null"
                  @update:model-value="(r) => store.setRating([row.track.path], r)"
                />
              </td>
              <td v-if="showArtistCol" class="p-2 text-stone-200">
                <MarqueeCell :text="row.track.artist ?? '—'" />
              </td>
              <td v-if="showAlbumCol" class="p-2 text-stone-200">
                <MarqueeCell :text="row.track.album ?? '—'" />
              </td>
              <td v-if="showYearCol" class="p-2 text-stone-300">{{ row.track.year ?? "—" }}</td>
              <td v-if="tableColDuration" class="p-2 text-stone-300">{{ formatDuration(row.track.duration_secs) }}</td>
              <td v-if="tableColFormat" class="p-2 text-stone-400">{{ row.track.format }}</td>
              <td v-if="tableColPath" class="min-w-0 p-2 text-stone-500">
                <div class="flex min-w-0 items-center gap-1">
                  <button type="button" class="shrink-0 rounded p-0.5 text-stone-500 hover:bg-stone-600 hover:text-stone-300" aria-label="Copy path" title="Copy path" @click.stop="copyPathToClipboard(row.track.path)">
                    <FeatherIcon name="clipboard" class="h-3.5 w-3.5" />
                  </button>
                  <span class="min-w-0 truncate cursor-default" :title="row.track.path">{{ row.track.path }}</span>
                </div>
              </td>
              <td></td><!-- filler -->
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
      class="fixed z-[300] min-w-[180px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="addToQueueFromContextMenu"
      >
        <FeatherIcon name="clock" class="h-4 w-4 shrink-0 text-stone-400" />
        Add to queue
      </button>

      <!-- Add to playlist section -->
      <div class="my-1 border-t border-stone-700" />
      <div
        ref="playlistBtnContainerRef"
        class="relative"
        @mouseenter="playlistSubmenuOpen = true"
        @mouseleave="playlistSubmenuOpen = false"
      >
        <button
          type="button"
          class="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
          @click="playlistSubmenuOpen = !playlistSubmenuOpen"
        >
          <span class="flex items-center gap-2">
            <FeatherIcon name="list" class="h-4 w-4 shrink-0 text-stone-400" />
            Add to playlist
          </span>
          <FeatherIcon name="chevron-right" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
        </button>
        <!-- Transparent bridge covering the gap between button and submenu -->
        <div class="absolute right-0 top-0 h-full w-2 translate-x-full" />
        <!-- Playlist submenu -->
        <div
          v-if="playlistSubmenuOpen && playlists.length"
          class="absolute left-full z-[310] min-w-[160px] max-w-[220px] rounded-lg border border-stone-600 bg-stone-800 py-1 shadow-xl"
          :class="playlistSubmenuFlipUp ? 'bottom-0' : 'top-0'"
          style="margin-left: 2px"
        >
          <button
            v-for="pl in playlists"
            :key="pl.id"
            type="button"
            class="flex w-full min-w-0 items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
            @click="addToPlaylistFromContextMenu(pl.id)"
          >
            <span v-if="pl.icon" class="shrink-0 text-sm leading-none">{{ pl.icon }}</span>
            <FeatherIcon v-else name="list" class="h-3.5 w-3.5 shrink-0 text-stone-400" />
            <span class="min-w-0 truncate">{{ pl.name }}</span>
          </button>
        </div>
        <div
          v-else-if="playlistSubmenuOpen && !playlists.length"
          class="absolute left-full z-[310] min-w-[160px] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 shadow-xl text-xs text-stone-500"
          :class="playlistSubmenuFlipUp ? 'bottom-0' : 'top-0'"
          style="margin-left: 2px"
        >
          No playlists yet.
        </div>
      </div>

      <!-- Copy path -->
      <div class="my-1 border-t border-stone-700" />
      <button
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-700 hover:text-stone-50"
        @click="copyPathToClipboard(contextMenu.tracks[0]?.path ?? ''); closeContextMenu()"
      >
        <FeatherIcon name="clipboard" class="h-4 w-4 shrink-0 text-stone-400" />
        Copy path
      </button>

      <!-- Remove from playlist (when viewing a playlist: single track or full album) -->
      <template v-if="store.activePlaylistId != null && contextMenu.tracks.length">
        <div class="my-1 border-t border-stone-700" />
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-400 hover:bg-stone-700 hover:text-red-300"
          @click="removeFromPlaylist"
        >
          <FeatherIcon name="trash-2" class="h-4 w-4 shrink-0" />
          Remove from playlist
        </button>
      </template>

      <!-- Rating section (last) -->
      <div class="my-1 border-t border-stone-700" />
      <div class="flex items-center justify-center px-3 py-2">
        <StarRating
          :model-value="contextMenuRating"
          @update:model-value="setRatingFromContextMenu"
        />
      </div>
    </div>
  </Teleport>

  <PlaylistDuplicateDialog
    v-if="pendingAdd"
    :pending="pendingAdd"
    @confirm-all="confirmAddAll"
    @confirm-deduped="confirmAddDeduped"
    @cancel="cancelPendingAdd"
  />
</template>

<style scoped>
.virtual-spacer-row td {
  padding: 0 !important;
  border: none !important;
  line-height: 0;
  vertical-align: top;
}

.table-density-compact th,
.table-density-compact td {
  padding: 0.2rem 0.375rem !important;
}
</style>

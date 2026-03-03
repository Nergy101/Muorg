<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import type { ThemeId, DefaultGroupBy, TableDensity, MissingMetadataField } from "../stores/settings";
import type { CatalogTrack } from "../types";
import { check } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import type { Update } from "@tauri-apps/plugin-updater";
import TrackAlbumArt from "./TrackAlbumArt.vue";
import packageJson from "../../package.json";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { filteredTracks, selectedTrackIds, searchQuery, groupBy, albumCoverCache, currentPlayingTrackId, reportFilter } =
  storeToRefs(store);
const {
  defaultGroupsExpanded,
  theme,
  defaultGroupBy,
  autoplayOnSelect,
  navWrap,
  navFocusFollowsMouse,
  tableDensity,
  tableColAlbumArt,
  tableColYear,
  tableColDuration,
  tableColFormat,
  tableColPath,
  missingMetadataFields,
  groupHeaderAlbumArt,
} = storeToRefs(settingsStore);

const tableDensityOptions: { value: TableDensity; label: string }[] = [
  { value: "comfortable", label: "Comfortable" },
  { value: "compact", label: "Compact" },
];

const missingMetadataFieldOptions: { value: MissingMetadataField; label: string }[] = [
  { value: "title", label: "Title" },
  { value: "artist", label: "Artist" },
  { value: "album", label: "Album" },
  { value: "album_artist", label: "Album artist" },
  { value: "year", label: "Year" },
  { value: "genre", label: "Genre" },
  { value: "track_number", label: "Track #" },
  { value: "disc_number", label: "Disc #" },
];

function isFieldMissing(track: CatalogTrack, field: MissingMetadataField): boolean {
  const v = track[field as keyof CatalogTrack];
  if (field === "year" || field === "track_number" || field === "disc_number") {
    return v == null;
  }
  return v == null || String(v).trim() === "";
}

const activeReportTracks = computed(() => {
  const kind = reportFilter.value;
  if (!kind) return [];
  const base = filteredTracks.value;

  if (kind === "missing_metadata") {
    const fields = missingMetadataFields.value;
    if (!fields.length) return [];
    return base.filter((t) => fields.some((f) => isFieldMissing(t, f)));
  }

  // duplicates: same normalized artist + album + title
  const keyFor = (t: CatalogTrack) =>
    `${(t.artist ?? "").toLowerCase()}|${(t.album ?? "").toLowerCase()}|${(t.title ?? "").toLowerCase()}`;
  const map = new Map<string, CatalogTrack[]>();
  for (const t of base) {
    const key = keyFor(t);
    if (!key.trim()) continue;
    const list = map.get(key);
    if (list) list.push(t);
    else map.set(key, [t]);
  }
  const dupIds = new Set<number>();
  for (const list of map.values()) {
    if (list.length > 1) {
      for (const t of list) dupIds.add(t.id);
    }
  }
  if (!dupIds.size) return [];
  return base.filter((t) => dupIds.has(t.id));
});

const activeReportTitle = computed(() => {
  if (reportFilter.value === "missing_metadata") return "Missing metadata";
  if (reportFilter.value === "duplicates") return "Duplicates";
  return "";
});

const showReportModal = computed(() => !!reportFilter.value && !!activeReportTitle.value);

const themeOptions: { value: ThemeId; label: string }[] = [
  { value: "auto", label: "Auto" },
  { value: "dark", label: "Dark" },
  { value: "light", label: "Light" },
  { value: "orkish", label: "Orkish" },
  { value: "doom", label: "DOOM" },
];

const defaultGroupByOptions: { value: DefaultGroupBy; label: string }[] = [
  { value: "none", label: "No grouping" },
  { value: "artist", label: "By artist" },
  { value: "album", label: "By album" },
];

/** Base URL for GitHub releases (used for "See release" link). */
const GITHUB_RELEASE_BASE = "https://github.com/Nergy101/Muorg/releases";

const appVersion = packageJson.version;

const showSettingsModal = ref(false);
const settingsModalRef = ref<HTMLDivElement | null>(null);
type SettingsTabId = "general" | "playback" | "keyboard" | "grouping" | "table" | "reports";
const settingsTab = ref<SettingsTabId>("general");
const settingsTabs: { id: SettingsTabId; label: string }[] = [
  { id: "general", label: "General" },
  { id: "playback", label: "Playback" },
  { id: "keyboard", label: "Keyboard" },
  { id: "grouping", label: "Grouping" },
  { id: "table", label: "Table" },
  { id: "reports", label: "Reports" },
];
const showKeyMapModal = ref(false);
  const keyMapModalRef = ref<HTMLDivElement | null>(null);

  const showFeedbackModal = ref(false);
  const feedbackMessage = ref("");
  const feedbackEmail = ref("");

  const updateCheckStatus = ref<"idle" | "checking" | "up-to-date" | "available" | "error">("idle");
  const availableUpdate = ref<Update | null>(null);
  const updateError = ref<string | null>(null);
  const updateDownloadProgress = ref<number | null>(null);
  const showUpdateCompleteModal = ref(false);
  const updateCompleteVersion = ref("");

const keyMapEntries: { keys: string; description: string }[] = [
  { keys: "Ctrl+F / ⌘F", description: "Focus search bar" },
  { keys: "Escape", description: "Close metadata panel (discard changes)" },
  { keys: "↓ Arrow Down", description: "Move focus down in track list" },
  { keys: "↑ Arrow Up", description: "Move focus up in track list" },
  { keys: "Space", description: "On group row: expand or collapse. On track row: select (add to selection in multi-select)" },
  { keys: "Enter", description: "With one track selected: start playback or pause if already playing" },
];

watch(showSettingsModal, async (open) => {
  if (open) {
    await nextTick();
    settingsModalRef.value?.focus();
  }
});

watch(showKeyMapModal, async (open) => {
  if (open) {
    await nextTick();
    keyMapModalRef.value?.focus();
  }
});

function closeSettingsModal() {
  showSettingsModal.value = false;
}

function onSettingsKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") closeSettingsModal();
}

function openKeyMapModal() {
  hideTooltip();
  showKeyMapModal.value = true;
}

function closeKeyMapModal() {
  showKeyMapModal.value = false;
}

function onKeyMapKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") closeKeyMapModal();
}

function openFeedbackModal() {
  hideTooltip();
  feedbackMessage.value = "";
  feedbackEmail.value = "";
  showFeedbackModal.value = true;
}

function closeFeedbackModal() {
  showFeedbackModal.value = false;
}

function onFeedbackKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") closeFeedbackModal();
}

function sendFeedback() {
  const subject = "Muorg Feedback";
  const lines = [feedbackMessage.value.trim()];
  if (feedbackEmail.value.trim()) {
    lines.push("", "Reply to: " + feedbackEmail.value.trim());
  }
  const body = lines.join("\n");
  const mailto = `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
  window.open(mailto, "_blank", "noopener,noreferrer");
  closeFeedbackModal();
}

async function checkForUpdates() {
  updateCheckStatus.value = "checking";
  updateError.value = null;
  availableUpdate.value = null;
  try {
    const update = await check();
    if (update) {
      availableUpdate.value = update;
      updateCheckStatus.value = "available";
    } else {
      updateCheckStatus.value = "up-to-date";
    }
  } catch (e) {
    updateError.value = e instanceof Error ? e.message : String(e);
    updateCheckStatus.value = "error";
  }
}

async function installUpdate() {
  const update = availableUpdate.value;
  if (!update) return;
  updateDownloadProgress.value = 0;
  let downloaded = 0;
  let contentLength: number | null = null;
  try {
    await update.downloadAndInstall((event) => {
      if (event.event === "Started" && event.data.contentLength != null) {
        contentLength = event.data.contentLength;
      } else if (event.event === "Progress") {
        downloaded += event.data.chunkLength;
        if (contentLength != null && contentLength > 0) {
          updateDownloadProgress.value = Math.min(100, Math.round((downloaded / contentLength) * 100));
        }
      } else if (event.event === "Finished") {
        updateDownloadProgress.value = 100;
      }
    });
    updateDownloadProgress.value = null;
    updateCompleteVersion.value = update.version;
    showUpdateCompleteModal.value = true;
  } catch (e) {
    updateError.value = e instanceof Error ? e.message : String(e);
    updateDownloadProgress.value = null;
    updateCheckStatus.value = "error";
  }
}

function closeUpdateCompleteModal() {
  showUpdateCompleteModal.value = false;
  updateCompleteVersion.value = "";
}

async function restartAfterUpdate() {
  closeUpdateCompleteModal();
  await relaunch();
}

function onDocumentKeydown(e: KeyboardEvent) {
  if (e.key !== "Escape") return;
  if (showKeyMapModal.value) {
    closeKeyMapModal();
    e.preventDefault();
    e.stopPropagation();
  } else if (showFeedbackModal.value) {
    closeFeedbackModal();
    e.preventDefault();
    e.stopPropagation();
  } else if (showSettingsModal.value) {
    closeSettingsModal();
    e.preventDefault();
    e.stopPropagation();
  } else if (showUpdateCompleteModal.value) {
    closeUpdateCompleteModal();
    e.preventDefault();
    e.stopPropagation();
  }
}

function setDefaultGroupBy(value: DefaultGroupBy) {
  settingsStore.setDefaultGroupBy(value);
  store.groupBy = value;
}

function setDefaultGroupsExpanded(value: boolean) {
  settingsStore.setDefaultGroupsExpanded(value);
}

/** True if this group contains any currently selected track. */
function groupContainsSelection(group: { key: string; label: string; tracks: CatalogTrack[] }): boolean {
  return group.tracks.some((tr) => selectedTrackIds.value.includes(tr.id));
}

/** Select a track from the report modal: expand its group, select the track, close the report. */
function selectTrackFromReport(t: CatalogTrack) {
  if (groupBy.value !== "none") {
    const key = groupBy.value === "artist" ? (t.artist ?? "—") : (t.album ?? "—");
    const next = new Set(expandedGroups.value);
    next.add(key);
    expandedGroups.value = next;
  }
  store.clearSelection();
  store.toggleSelection(t.id);
  store.setReportFilter(null);
  const rows = visibleRows.value;
  const index = rows.findIndex((r) => r.type === "track" && r.track.id === t.id);
  if (index >= 0) {
    focusedRowIndex.value = index;
    if (useVirtualization.value) {
      nextTick(() => {
        scrollToRowIndex(index);
      });
    } else {
      nextTick(() => {
        const row = document.querySelector(`[data-track-id="${t.id}"]`);
        row?.scrollIntoView({ block: "center", behavior: "smooth" });
      });
    }
  }
}

/** Set of group keys that are expanded. */
const expandedGroups = ref<Set<string>>(new Set());
/** True after we've applied defaultGroupsExpanded once for the current groupBy (so we don't re-apply on every groupedRows update). */
const appliedDefaultForSession = ref(false);
const multiSelectMode = ref(false);

/** Index into visibleRows for keyboard focus. -1 when no row focused. */
const focusedRowIndex = ref(-1);
const tableContainerRef = ref<HTMLDivElement | null>(null);
const searchInputRef = ref<HTMLInputElement | null>(null);

/** Virtualization: only when row count exceeds this. */
const VIRTUALIZATION_THRESHOLD = 500;
const ROW_HEIGHT_GROUP = 40;
const ROW_HEIGHT_TRACK_COMFORTABLE = 40;
const ROW_HEIGHT_TRACK_COMPACT = 32;
const OVERCAN_ROWS = 12;

/** Scroll position and container height for virtual range (updated on scroll/resize). */
const scrollTopRef = ref(0);
const containerHeightRef = ref(0);

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
  const base = filteredTracks.value;
  if (by === "none" || !base.length) return null;
  const map = new Map<string, CatalogTrack[]>();
  for (const t of base) {
    const key = by === "artist" ? (t.artist ?? "—") : by === "album" ? (t.album ?? "—") : "";
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(t);
  }
  const keys = [...map.keys()].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));
  return keys.map((key) => ({ key, label: key, tracks: map.get(key)! }));
});

/** When grouping by album, per-group cover to show in header. Uses albumCoverCache (set when any track in that album is fetched) so headers show as soon as track art loads. */
const groupCovers = computed(() => {
  if (groupBy.value !== "album") return {} as Record<string, import("../stores/catalog").CoverInfo | null>;
  const albumCache = albumCoverCache.value;
  const groups = groupedRows.value;
  if (!groups) return {};
  const result: Record<string, import("../stores/catalog").CoverInfo | null> = {};
  for (const group of groups) {
    result[group.key] = albumCache[group.key] ?? null;
  }
  return result;
});

const tableColCount = computed(() => {
  // Selection checkbox column is always present.
  let n = 1;
  if (tableColAlbumArt.value) n += 1;
  // Title, Artist, Album always present.
  n += 3;
  if (tableColYear.value) n += 1;
  if (tableColDuration.value) n += 1;
  if (tableColFormat.value) n += 1;
  if (tableColPath.value) n += 1;
  return n;
});

/** Per-row height for virtualization (group vs track, density). */
const rowHeights = computed(() => {
  const rows = visibleRows.value;
  const trackHeight =
    tableDensity.value === "compact" ? ROW_HEIGHT_TRACK_COMPACT : ROW_HEIGHT_TRACK_COMFORTABLE;
  return rows.map((r) => (r.type === "group" ? ROW_HEIGHT_GROUP : trackHeight));
});

const totalScrollHeight = computed(() => rowHeights.value.reduce((a, b) => a + b, 0));

const useVirtualization = computed(
  () => visibleRows.value.length >= VIRTUALIZATION_THRESHOLD
);

/** Cumulative offset from top for row i (sum of rowHeights[0..i-1]). */
function getRowOffset(index: number): number {
  const heights = rowHeights.value;
  let sum = 0;
  for (let i = 0; i < index && i < heights.length; i++) sum += heights[i];
  return sum;
}

/** Visible range [start, end] (inclusive) with overscan. */
const visibleRange = computed(() => {
  const rows = visibleRows.value;
  const heights = rowHeights.value;
  if (!rows.length || !useVirtualization.value) {
    return { start: 0, end: Math.max(0, rows.length - 1) };
  }
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

/** Rows to render: either full list or virtual slice. */
const renderedRows = computed(() => {
  const rows = visibleRows.value;
  const { start, end } = visibleRange.value;
  if (!useVirtualization.value) return rows.map((r, i) => ({ row: r, index: i }));
  const result: { row: VisibleRow; index: number }[] = [];
  for (let i = start; i <= end && i < rows.length; i++) {
    result.push({ row: rows[i], index: i });
  }
  return result;
});

const topSpacerHeight = computed(() => {
  if (!useVirtualization.value) return 0;
  const { start } = visibleRange.value;
  return getRowOffset(start);
});

const bottomSpacerHeight = computed(() => {
  if (!useVirtualization.value) return 0;
  const { start, end } = visibleRange.value;
  const heights = rowHeights.value;
  const total = totalScrollHeight.value;
  let visibleSum = 0;
  for (let i = start; i <= end && i < heights.length; i++) visibleSum += heights[i];
  return Math.max(0, total - topSpacerHeight.value - visibleSum);
});

watch(useVirtualization, (use) => {
  if (use) nextTick(updateScrollMeasurements);
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
  if (focusedRowIndex.value < 0) {
    focusedRowIndex.value = 0;
  } else if (focusedRowIndex.value >= rows.length - 1) {
    focusedRowIndex.value = navWrap.value ? 0 : rows.length - 1;
  } else {
    focusedRowIndex.value += 1;
  }
  scrollFocusedRowIntoView();
}

function focusPrev() {
  const rows = visibleRows.value;
  if (!rows.length) return;
  if (focusedRowIndex.value < 0) {
    focusedRowIndex.value = rows.length - 1;
  } else if (focusedRowIndex.value <= 0) {
    focusedRowIndex.value = navWrap.value ? rows.length - 1 : 0;
  } else focusedRowIndex.value -= 1;
  scrollFocusedRowIntoView();
}

function updateScrollMeasurements() {
  const el = tableContainerRef.value;
  if (!el) return;
  scrollTopRef.value = el.scrollTop;
  containerHeightRef.value = el.clientHeight;
}

/** Scroll container so that row at index is in the visible virtual range, then scrollIntoView. */
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
    (rowEl as HTMLElement)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  });
}

function scrollFocusedRowIntoView() {
  if (useVirtualization.value) {
    scrollToRowIndex(focusedRowIndex.value);
    return;
  }
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

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  document.addEventListener("keydown", onGlobalKeydown);
  document.addEventListener("keydown", onDocumentKeydown);
  nextTick(() => {
    const container = tableContainerRef.value;
    if (container) {
      container.addEventListener("scroll", updateScrollMeasurements, { passive: true });
      updateScrollMeasurements();
      resizeObserver = new ResizeObserver(updateScrollMeasurements);
      resizeObserver.observe(container);
    }
  });
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
  document.removeEventListener("keydown", onDocumentKeydown);
  const container = tableContainerRef.value;
  if (container) {
    container.removeEventListener("scroll", updateScrollMeasurements);
  }
  resizeObserver?.disconnect();
  resizeObserver = null;
});
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-stone-700 px-4 py-2">
      <div class="flex flex-wrap items-center gap-3">
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
        class="relative z-[210] flex shrink-0 items-center gap-2"
        @mouseenter="showTooltip('Version ' + appVersion, $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <img src="/favicon.svg" alt="" class="h-6 w-6 shrink-0" />
        <span class="text-sm font-semibold text-stone-200">Muorg</span>
        <span
          class="relative z-[220] inline-flex"
          @mouseenter="showTooltip('Key map', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Key map"
            @mousedown.stop="openKeyMapModal"
            @click.stop="openKeyMapModal"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16a2 2 0 012 2v8a2 2 0 01-2 2H4a2 2 0 01-2-2V8a2 2 0 012-2z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M7 10h2M11 10h2M15 10h2M7 14h2M11 14h2M15 14h2" />
            </svg>
          </button>
        </span>
        <span
          v-if="false"
          class="relative z-[220] inline-flex"
          @mouseenter="showTooltip('Send feedback', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Send feedback"
            @mousedown.stop="openFeedbackModal"
            @click.stop="openFeedbackModal"
          >
            <!-- Chat bubble (feedback / report bug) icon -->
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a5.969 5.969 0 01-.474-.065 4.48 4.48 0 00.978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z" />
            </svg>
          </button>
        </span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Settings', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Application settings"
            @click="showSettingsModal = true"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </button>
        </span>
      </div>
    </div>
    <div
      ref="tableContainerRef"
      tabindex="0"
      class="table-scroll-container flex-1 overflow-auto outline-none"
      @keydown="onTableKeydown"
    >
      <table
        class="table-with-scroll-gutter w-full min-w-[800px] border-collapse text-left text-sm"
        :class="{ 'table-density-compact': tableDensity === 'compact' }"
      >
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
            <th v-if="tableColAlbumArt" class="w-10 border-r border-stone-700 p-2"></th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Title</th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Artist</th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Album</th>
            <th v-if="tableColYear" class="w-20 border-r border-stone-700 p-2 font-medium text-stone-400">Year</th>
            <th v-if="tableColDuration" class="w-16 border-r border-stone-700 p-2 font-medium text-stone-400">Duration</th>
            <th v-if="tableColFormat" class="w-16 border-r border-stone-700 p-2 font-medium text-stone-400">Format</th>
            <th v-if="tableColPath" class="max-w-[200px] truncate border-stone-700 p-2 font-medium text-stone-400">Path</th>
          </tr>
        </thead>
        <tbody>
          <template v-if="visibleRows.length">
            <!-- Virtualization: top spacer so total height matches scroll area -->
            <tr
              v-if="useVirtualization && topSpacerHeight > 0"
              class="virtual-spacer-row"
              aria-hidden="true"
            >
              <td :colspan="tableColCount" :style="{ height: topSpacerHeight + 'px' }" />
            </tr>
            <template v-for="{ row, index } in renderedRows" :key="row.type === 'group' ? row.key : row.track.id">
              <tr
                v-if="row.type === 'group'"
                :data-row-index="index"
                class="cursor-pointer font-medium text-stone-400 hover:bg-stone-700/80"
                :class="[
                  focusedRowIndex === index ? 'bg-stone-700/80 table-row-focused' : 'bg-stone-800/80',
                  { 'group-row-with-selection': groupContainsSelection(row.group) },
                ]"
                :style="useVirtualization ? { height: rowHeights[index] + 'px' } : undefined"
                @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = index) : undefined"
                @click="focusedRowIndex = index; toggleGroup(row.key)"
              >
                <td :colspan="tableColCount" class="border-b border-stone-600 p-2">
                  <span class="inline-flex items-center gap-2">
                    <span
                      v-if="groupBy === 'album' && groupCovers[row.key] && groupHeaderAlbumArt"
                      class="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded bg-stone-800"
                    >
                      <img
                        :src="groupCovers[row.key] ? `data:${groupCovers[row.key]!.mime};base64,${groupCovers[row.key]!.base64}` : ''"
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
                :data-row-index="index"
                :data-track-id="row.track.id"
                class="border-b border-stone-700/50 hover:bg-stone-800/50"
                :class="[
                  { 'bg-stone-700/25': isSelected(row.track.id) && focusedRowIndex !== index },
                  { 'bg-stone-600/30 table-row-focused': isSelected(row.track.id) && focusedRowIndex === index },
                  { 'bg-stone-800 table-row-focused': !isSelected(row.track.id) && focusedRowIndex === index },
                  { 'table-row-playing': row.track.id === currentPlayingTrackId },
                ]"
                :style="useVirtualization ? { height: rowHeights[index] + 'px' } : undefined"
                @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = index) : undefined"
                @click="focusedRowIndex = index; selectRow(row.track)"
              >
                <td class="border-r border-stone-700 p-2">
                  <input
                    type="checkbox"
                    :checked="isSelected(row.track.id)"
                    class="rounded border-stone-600"
                    @click.stop="focusedRowIndex = index; selectRow(row.track)"
                  />
                </td>
                <td v-if="tableColAlbumArt" class="border-r border-stone-700 p-2">
                  <TrackAlbumArt :path="row.track.path" />
                </td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.title ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.artist ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ row.track.album ?? "—" }}</td>
                <td v-if="tableColYear" class="border-r border-stone-700 p-2 text-stone-300">{{ row.track.year ?? "—" }}</td>
                <td v-if="tableColDuration" class="border-r border-stone-700 p-2 text-stone-300">{{ formatDuration(row.track.duration_secs) }}</td>
                <td v-if="tableColFormat" class="border-r border-stone-700 p-2 text-stone-400">{{ row.track.format }}</td>
                <td v-if="tableColPath" class="max-w-[200px] truncate p-2 text-stone-500" :title="row.track.path">{{ row.track.path }}</td>
              </tr>
            </template>
            <!-- Virtualization: bottom spacer -->
            <tr
              v-if="useVirtualization && bottomSpacerHeight > 0"
              class="virtual-spacer-row"
              aria-hidden="true"
            >
              <td :colspan="tableColCount" :style="{ height: bottomSpacerHeight + 'px' }" />
            </tr>
          </template>
          <tr v-else class="text-stone-500">
            <td :colspan="tableColCount" class="p-6 text-center">
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
    <!-- Settings modal -->
    <Teleport to="body">
      <div
        v-if="showSettingsModal"
        ref="settingsModalRef"
        class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-modal-title"
        tabindex="-1"
        @keydown="onSettingsKeydown"
        @click.self="closeSettingsModal"
      >
        <div
          class="settings-modal flex flex-col w-full max-w-2xl rounded-lg border border-stone-600 bg-stone-800 shadow-xl overflow-hidden"
          @click.stop
        >
          <div class="flex shrink-0 items-center justify-between border-b border-stone-700 px-4 py-3">
            <h2 id="settings-modal-title" class="text-sm font-semibold text-stone-200">Settings</h2>
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Close"
              @click="closeSettingsModal"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="flex min-h-0 flex-1">
          <nav class="settings-tab-nav flex flex-col w-36 shrink-0 border-r border-stone-700 bg-stone-800/90 py-2" aria-label="Settings sections">
            <button
              v-for="tab in settingsTabs"
              :key="tab.id"
              type="button"
              class="settings-tab-btn px-3 py-2 text-left text-xs font-medium transition-colors"
              :class="settingsTab === tab.id ? 'settings-tab-btn--active bg-stone-700 text-stone-100' : 'text-stone-400 hover:bg-stone-700/60 hover:text-stone-200'"
              @click="settingsTab = tab.id"
            >
              {{ tab.label }}
            </button>
          </nav>
          <div class="flex-1 min-w-0 overflow-y-auto max-h-[70vh] p-4">
            <div v-show="settingsTab === 'general'" class="space-y-4">
              <div>
                <label class="block text-xs font-medium text-stone-500">Theme</label>
                <select
                  :value="theme"
                  class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200"
                  @change="(e) => settingsStore.setTheme((e.target as HTMLSelectElement).value as ThemeId)"
                >
                  <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </div>
              <div>
                <label class="block text-xs font-medium text-stone-500">Default grouping</label>
                <select
                  :value="defaultGroupBy"
                  class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200"
                  @change="(e) => setDefaultGroupBy((e.target as HTMLSelectElement).value as DefaultGroupBy)"
                >
                  <option v-for="opt in defaultGroupByOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
                <p class="mt-0.5 text-xs text-stone-500">Applied when the app starts.</p>
              </div>
              <div>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="defaultGroupsExpanded"
                    class="rounded border-stone-600"
                    @change="(e) => setDefaultGroupsExpanded((e.target as HTMLInputElement).checked)"
                  />
                  Groups start expanded
                </label>
                <p class="mt-0.5 text-xs text-stone-500">When grouping is on, expand all groups by default.</p>
              </div>
              <div class="border-t border-stone-700 pt-4">
                <p class="text-xs font-semibold text-stone-400 mb-2">Updates</p>
                <button
                  type="button"
                  class="rounded border border-stone-600 bg-stone-800 px-3 py-1.5 text-sm text-stone-200 hover:bg-stone-700 disabled:opacity-50"
                  :disabled="updateCheckStatus === 'checking' || updateDownloadProgress != null"
                  @click="checkForUpdates"
                >
                  {{ updateCheckStatus === 'checking' ? 'Checking...' : updateDownloadProgress != null ? 'Downloading...' : 'Check for updates' }}
                </button>
                <p v-if="updateCheckStatus === 'up-to-date'" class="mt-2 text-xs text-stone-500">You're up to date.</p>
                <p v-else-if="updateCheckStatus === 'error'" class="mt-2 text-xs text-red-400">{{ updateError }}</p>
                <div v-else-if="updateCheckStatus === 'available' && availableUpdate" class="mt-3 space-y-2">
                  <p class="text-xs text-stone-300">
                    <strong>Version {{ availableUpdate.version }}</strong>
                    <span v-if="availableUpdate.date" class="text-stone-500"> · {{ availableUpdate.date }}</span>
                  </p>
                  <p v-if="availableUpdate.body" class="text-xs text-stone-400 whitespace-pre-line">{{ availableUpdate.body }}</p>
                  <div class="flex items-center gap-3">
                    <button
                      type="button"
                      class="rounded bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white shadow-sm hover:bg-emerald-500 disabled:opacity-50"
                      :disabled="updateDownloadProgress != null"
                      @click="installUpdate"
                    >
                      {{ updateDownloadProgress != null ? `Downloading ${updateDownloadProgress}%...` : 'Download and install' }}
                    </button>
                    <a
                      :href="`${GITHUB_RELEASE_BASE}/tag/v${availableUpdate.version}`"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="text-xs text-stone-400 underline hover:text-stone-300"
                    >
                      See release
                    </a>
                  </div>
                </div>
              </div>
            </div>
            <div v-show="settingsTab === 'playback'" class="space-y-4">
              <p class="text-xs font-semibold text-stone-400">Playback</p>
              <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                <input
                  type="checkbox"
                  :checked="autoplayOnSelect"
                  class="rounded border-stone-600"
                  @change="(e) => settingsStore.setAutoplayOnSelect((e.target as HTMLInputElement).checked)"
                />
                Auto-play when selecting a single track
              </label>
              <p class="mt-0.5 text-xs text-stone-500">If enabled, selecting a single track immediately starts playback.</p>
            </div>
            <div v-show="settingsTab === 'keyboard'" class="space-y-4">
              <p class="text-xs font-semibold text-stone-400">Keyboard</p>
              <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                <input
                  type="checkbox"
                  :checked="navWrap"
                  class="rounded border-stone-600"
                  @change="(e) => settingsStore.setNavWrap((e.target as HTMLInputElement).checked)"
                />
                Wrap focus at ends (↑/↓)
              </label>
              <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                <input
                  type="checkbox"
                  :checked="navFocusFollowsMouse"
                  class="rounded border-stone-600"
                  @change="(e) => settingsStore.setNavFocusFollowsMouse((e.target as HTMLInputElement).checked)"
                />
                Focus follows mouse hover
              </label>
            </div>
            <div v-show="settingsTab === 'grouping'" class="space-y-4">
              <p class="text-xs font-semibold text-stone-400">Grouping headers</p>
              <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                <input
                  type="checkbox"
                  :checked="groupHeaderAlbumArt"
                  class="rounded border-stone-600"
                  @change="(e) => settingsStore.setGroupHeaderAlbumArt((e.target as HTMLInputElement).checked)"
                />
                Show album art in album group header
              </label>
              <p class="mt-0.5 text-xs text-stone-500">When grouping by album, show the cover in the group row (if all tracks share the same art).</p>
            </div>
            <div v-show="settingsTab === 'table'" class="space-y-4">
              <p class="text-xs font-semibold text-stone-400">Table</p>
              <div>
                <label class="block text-xs font-medium text-stone-500">Density</label>
                <select
                  :value="tableDensity"
                  class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200"
                  @change="(e) => settingsStore.setTableDensity((e.target as HTMLSelectElement).value as TableDensity)"
                >
                  <option v-for="opt in tableDensityOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </div>
              <div class="mt-3 grid grid-cols-2 gap-2">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColAlbumArt"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColAlbumArt((e.target as HTMLInputElement).checked)"
                  />
                  Album art column
                </label>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColYear"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColYear((e.target as HTMLInputElement).checked)"
                  />
                  Year
                </label>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColDuration"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColDuration((e.target as HTMLInputElement).checked)"
                  />
                  Duration
                </label>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColFormat"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColFormat((e.target as HTMLInputElement).checked)"
                  />
                  Format
                </label>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="tableColPath"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setTableColPath((e.target as HTMLInputElement).checked)"
                  />
                  Path
                </label>
              </div>
            </div>
            <div v-show="settingsTab === 'reports'" class="space-y-4">
              <p class="text-xs font-semibold text-stone-400">Reports</p>
              <p class="mt-1 text-xs text-stone-500">Fields to consider missing for the "Missing metadata" report:</p>
              <div class="mt-2 grid grid-cols-2 gap-2">
                <label
                  v-for="opt in missingMetadataFieldOptions"
                  :key="opt.value"
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="missingMetadataFields.includes(opt.value)"
                    class="rounded border-stone-600"
                    @change="(e) => {
                      const checked = (e.target as HTMLInputElement).checked;
                      const set = new Set(missingMetadataFields);
                      if (checked) set.add(opt.value);
                      else set.delete(opt.value);
                      settingsStore.setMissingMetadataFields(Array.from(set));
                    }"
                  />
                  {{ opt.label }}
                </label>
              </div>
            </div>
          </div>
          </div>
        </div>
      </div>
    </Teleport>
    <!-- Key map modal -->
    <Teleport to="body">
      <div
        v-if="showKeyMapModal"
        ref="keyMapModalRef"
        class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-labelledby="keymap-modal-title"
        tabindex="-1"
        @keydown="onKeyMapKeydown"
        @click.self="closeKeyMapModal"
      >
        <div
          class="w-full max-w-md rounded-lg border border-stone-600 bg-stone-800 shadow-xl"
          @click.stop
        >
          <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
            <h2 id="keymap-modal-title" class="text-sm font-semibold text-stone-200">Key map</h2>
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Close"
              @click="closeKeyMapModal"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="max-h-[70vh] overflow-y-auto p-4">
            <dl class="space-y-3">
              <div v-for="entry in keyMapEntries" :key="entry.keys" class="flex gap-3 text-sm">
                <dt class="w-36 shrink-0 font-mono text-stone-400">{{ entry.keys }}</dt>
                <dd class="min-w-0 text-stone-300">{{ entry.description }}</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
    </Teleport>
    <!-- Feedback modal -->
    <Teleport to="body">
      <div
        v-if="showFeedbackModal"
        class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-labelledby="feedback-modal-title"
        tabindex="-1"
        @keydown="onFeedbackKeydown"
        @click.self="closeFeedbackModal"
      >
        <div
          class="feedback-modal w-full max-w-md rounded-lg border border-stone-600 bg-stone-800 shadow-xl"
          @click.stop
        >
          <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
            <h2 id="feedback-modal-title" class="text-sm font-semibold text-stone-200">Send feedback</h2>
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Close"
              @click="closeFeedbackModal"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <form class="p-4 space-y-4" @submit.prevent="sendFeedback">
            <div>
              <label for="feedback-message" class="block text-xs font-medium text-stone-500 mb-1">Your feedback</label>
              <textarea
                id="feedback-message"
                v-model="feedbackMessage"
                class="w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200 placeholder-stone-500 resize-y min-h-[120px]"
                placeholder="Tell us what you think, report a bug, or suggest an improvement..."
                rows="4"
              />
            </div>
            <div>
              <label for="feedback-email" class="block text-xs font-medium text-stone-500 mb-1">Your email (optional)</label>
              <input
                id="feedback-email"
                v-model="feedbackEmail"
                type="email"
                class="w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200 placeholder-stone-500"
                placeholder="So we can reply if needed"
              />
            </div>
            <div class="flex justify-end gap-2">
              <button
                type="button"
                class="rounded border border-stone-600 px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200"
                @click="closeFeedbackModal"
              >
                Cancel
              </button>
              <button
                type="submit"
                class="rounded bg-stone-600 px-3 py-1.5 text-sm font-medium text-stone-100 hover:bg-stone-500"
                :disabled="!feedbackMessage.trim()"
              >
                Send feedback
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
    <!-- Update complete: prompt to restart -->
    <Teleport to="body">
      <div
        v-if="showUpdateCompleteModal"
        class="fixed inset-0 z-[305] flex items-center justify-center bg-stone-950/70 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="update-complete-title"
        @keydown.escape="closeUpdateCompleteModal"
        @click.self="closeUpdateCompleteModal"
      >
        <div
          class="w-full max-w-sm rounded-lg border border-stone-600 bg-stone-800 shadow-xl p-4"
          @click.stop
        >
          <h2 id="update-complete-title" class="text-sm font-semibold text-stone-200">Update installed</h2>
          <p class="mt-2 text-xs text-stone-400">
            Version {{ updateCompleteVersion }} has been installed. Restart the app to use the new version.
          </p>
          <div class="mt-4 flex justify-end gap-2">
            <button
              type="button"
              class="rounded border border-stone-600 px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200"
              @click="closeUpdateCompleteModal"
            >
              Later
            </button>
            <button
              type="button"
              class="rounded bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-500"
              @click="restartAfterUpdate"
            >
              Restart now
            </button>
          </div>
        </div>
      </div>
    </Teleport>
    <!-- Reports modal -->
    <Teleport to="body">
      <div
        v-if="showReportModal"
        class="fixed inset-0 z-[310] flex items-center justify-center bg-stone-950/75 p-4"
        role="dialog"
        aria-modal="true"
        @click.self="store.setReportFilter(null)"
      >
        <div
          class="report-modal flex max-h-[80vh] w-full max-w-6xl flex-col overflow-hidden rounded-lg border shadow-xl"
        >
          <div class="flex items-center justify-between border-b px-4 py-3">
            <div class="min-w-0">
              <h2 class="truncate text-sm font-semibold text-stone-100">{{ activeReportTitle }}</h2>
              <p class="mt-0.5 text-xs text-stone-400">
                {{ activeReportTracks.length }} track{{ activeReportTracks.length === 1 ? "" : "s" }} in this report.
              </p>
            </div>
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-700 hover:text-stone-100"
              aria-label="Close report"
              @click="store.setReportFilter(null)"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto px-3 py-2 text-xs">
            <table class="w-full border-collapse text-left">
              <thead class="sticky top-0 bg-stone-900/95">
                <tr class="border-b border-stone-700 text-[0.7rem] uppercase tracking-wide text-stone-500">
                  <th class="px-2 py-1 font-medium">Title</th>
                  <th class="px-2 py-1 font-medium">Artist</th>
                  <th class="px-2 py-1 font-medium">Album</th>
                  <th class="px-2 py-1 font-medium">Path</th>
                  <th class="px-2 py-1 text-right font-medium">Go</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="t in activeReportTracks"
                  :key="t.id"
                  class="border-b border-stone-800/70 hover:bg-stone-800/70"
                >
                  <td class="min-w-0 max-w-[220px] truncate px-2 py-1 text-stone-100" :title="t.title || '—'">{{ t.title || "—" }}</td>
                  <td class="min-w-0 max-w-[200px] truncate px-2 py-1 text-stone-200" :title="t.artist || '—'">{{ t.artist || "—" }}</td>
                  <td class="min-w-0 max-w-[320px] truncate px-2 py-1 text-stone-200" :title="t.album || '—'">{{ t.album || "—" }}</td>
                  <td class="min-w-0 max-w-[320px] truncate px-2 py-1 text-stone-500" :title="t.path">{{ t.path }}</td>
                  <td class="px-2 py-1 text-right">
                    <button
                      type="button"
                      class="rounded border border-stone-600 px-2 py-0.5 text-[0.7rem] text-stone-200 hover:bg-stone-700"
                      @click="selectTrackFromReport(t)"
                    >
                      Edit
                    </button>
                  </td>
                </tr>
                <tr v-if="!activeReportTracks.length">
                  <td colspan="5" class="px-2 py-4 text-center text-stone-500">
                    No tracks currently match this report.
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.table-scroll-container {
  scrollbar-gutter: stable;
  scrollbar-color: #5b7c32 #2d2d2d;
}

/* Virtualization: spacer rows have no visible content or borders */
.virtual-spacer-row td {
  padding: 0 !important;
  border: none !important;
  line-height: 0;
  vertical-align: top;
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

/* Table density */
.table-density-compact th,
.table-density-compact td {
  padding: 0.375rem 0.5rem !important;
}

</style>

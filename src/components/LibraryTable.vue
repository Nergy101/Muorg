<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import type { ThemeId, DefaultGroupBy, TableDensity, MissingMetadataField } from "../stores/settings";
import type { CatalogTrack } from "../types";
import TrackAlbumArt from "./TrackAlbumArt.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { filteredTracks, selectedTrackIds, searchQuery, groupBy, coverCache, currentPlayingTrackId, reportFilter } =
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

const showSettingsModal = ref(false);
const settingsModalRef = ref<HTMLDivElement | null>(null);
const showKeyMapModal = ref(false);
const keyMapModalRef = ref<HTMLDivElement | null>(null);

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

function onDocumentKeydown(e: KeyboardEvent) {
  if (e.key !== "Escape") return;
  if (showKeyMapModal.value) {
    closeKeyMapModal();
    e.preventDefault();
    e.stopPropagation();
  } else if (showSettingsModal.value) {
    closeSettingsModal();
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
  nextTick(() => {
    const row = document.querySelector(`[data-track-id="${t.id}"]`);
    row?.scrollIntoView({ block: "center", behavior: "smooth" });
  });
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
  document.addEventListener("keydown", onDocumentKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
  document.removeEventListener("keydown", onDocumentKeydown);
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
      <div class="relative z-[210] flex shrink-0 items-center gap-2">
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
            <template v-for="(row, i) in visibleRows" :key="row.type === 'group' ? row.key : row.track.id">
              <tr
                v-if="row.type === 'group'"
                :data-row-index="i"
                class="cursor-pointer font-medium text-stone-400 hover:bg-stone-700/80"
                :class="[
                  focusedRowIndex === i ? 'bg-stone-700/80 table-row-focused' : 'bg-stone-800/80',
                  { 'group-row-with-selection': groupContainsSelection(row.group) },
                ]"
                @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = i) : undefined"
                @click="focusedRowIndex = i; toggleGroup(row.key)"
              >
                <td :colspan="tableColCount" class="border-b border-stone-600 p-2">
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
                :data-track-id="row.track.id"
                class="border-b border-stone-700/50 hover:bg-stone-800/50"
                :class="[
                  // Selected (but not focused): subtle tint
                  { 'bg-stone-700/25': isSelected(row.track.id) && focusedRowIndex !== i },
                  // Selected + focused: combine focus ring with slightly stronger tint
                  { 'bg-stone-600/30 table-row-focused': isSelected(row.track.id) && focusedRowIndex === i },
                  { 'bg-stone-800 table-row-focused': !isSelected(row.track.id) && focusedRowIndex === i },
                  { 'table-row-playing': row.track.id === currentPlayingTrackId },
                ]"
                @mouseenter="navFocusFollowsMouse ? (focusedRowIndex = i) : undefined"
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
          class="w-full max-w-md rounded-lg border border-stone-600 bg-stone-800 shadow-xl"
          @click.stop
        >
          <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
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
          <div class="space-y-4 p-4">
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
              <p class="text-xs font-semibold text-stone-400">Playback</p>
              <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
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
            <div class="border-t border-stone-700 pt-4">
              <p class="text-xs font-semibold text-stone-400">Keyboard</p>
              <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                <input
                  type="checkbox"
                  :checked="navWrap"
                  class="rounded border-stone-600"
                  @change="(e) => settingsStore.setNavWrap((e.target as HTMLInputElement).checked)"
                />
                Wrap focus at ends (↑/↓)
              </label>
              <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                <input
                  type="checkbox"
                  :checked="navFocusFollowsMouse"
                  class="rounded border-stone-600"
                  @change="(e) => settingsStore.setNavFocusFollowsMouse((e.target as HTMLInputElement).checked)"
                />
                Focus follows mouse hover
              </label>
            </div>
            <div class="border-t border-stone-700 pt-4">
              <p class="text-xs font-semibold text-stone-400">Table</p>
              <div class="mt-2">
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
            <div class="border-t border-stone-700 pt-4">
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

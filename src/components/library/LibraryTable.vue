<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { usePlaylistStore } from "../../stores/playlists";
import type { CatalogTrack } from "../../types";
import type { MissingMetadataField } from "../../stores/settings";
import { extractBestFromPath, buildUpdateFromExtracted } from "../../utils/pathFormat";
import LibraryHeader from "./LibraryHeader.vue";
import LibraryTableBody from "./LibraryTableBody.vue";
import LibrarySettingsModal from "../modals/LibrarySettingsModal.vue";
import LibraryKeyMapModal from "../modals/LibraryKeyMapModal.vue";
import LibraryReportsModal from "../modals/LibraryReportsModal.vue";

const props = defineProps<{
  activeTab: "library" | "metadata" | "player" | "queue";
  sidebarCollapsed: boolean;
}>();

const emit = defineEmits<{
  (e: "update:activeTab", value: "library" | "metadata" | "player" | "queue"): void;
  (e: "expandSidebar"): void;
  (e: "expandPlayer"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const playlistStore = usePlaylistStore();
const { tracks, filteredTracks, reportFilter, reportSingleField, revealTrackId } = storeToRefs(store);
const { pathFormatTemplates } = storeToRefs(settingsStore);

watch(revealTrackId, (id) => {
  if (id == null) return;
  store.setRevealTrackId(null);
  const needsClear = !filteredTracks.value.some((t) => t.id === id);
  if (needsClear) {
    store.setSearchQuery("");
    store.clearActivePlaylist();
  }
  emit("update:activeTab", "library");
  // Wait for Vue to flush the filter change and the virtual list to re-render its rows.
  nextTick(() => nextTick(() => tableBodyRef.value?.scrollToTrackId(id)));
});
const { missingMetadataFields } = storeToRefs(settingsStore);

const showSettingsModal = ref(false);
const showKeyMapModal = ref(false);
const tableBodyRef = ref<InstanceType<typeof LibraryTableBody> | null>(null);

function isFieldMissing(track: CatalogTrack, field: MissingMetadataField): boolean {
  if (field === "has_cover") return !track.has_cover;
  if (field === "rating") return track.rating == null;
  const v = track[field as keyof CatalogTrack];
  if (field === "year" || field === "track_number" || field === "disc_number") return v == null;
  return v == null || String(v).trim() === "";
}

const FIELD_LABELS: Record<MissingMetadataField, string> = {
  title: "Title", artist: "Artist", album: "Album", album_artist: "Album artist",
  year: "Year", genre: "Genre", track_number: "Track #", disc_number: "Disc #",
  rating: "Rating", has_cover: "Cover art",
};

const activeReportTracks = computed(() => {
  const kind = reportFilter.value;
  if (!kind) return [];
  // Reports should be based on the full catalog, not the current table view
  // (search query / playlist filter / hidden roots).
  const base = tracks.value;

  if (kind === "missing_metadata") {
    const single = reportSingleField.value;
    const fields = single ? [single] : missingMetadataFields.value;
    if (!fields.length) return [];
    return base.filter((t) => fields.some((f) => isFieldMissing(t, f)));
  }
  if (kind === "missing_album_cover") return base.filter((t) => !t.has_cover);

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
    if (list.length > 1) for (const t of list) dupIds.add(t.id);
  }
  return dupIds.size ? base.filter((t) => dupIds.has(t.id)) : [];
});

const activeReportTitle = computed(() => {
  if (reportFilter.value === "missing_metadata") {
    const f = reportSingleField.value;
    return f ? `Missing ${FIELD_LABELS[f] ?? f}` : "Missing metadata";
  }
  if (reportFilter.value === "duplicates") return "Duplicates";
  if (reportFilter.value === "missing_album_cover") return "Missing album cover";
  return "";
});

const duplicateCountInReport = computed(() => {
  if (reportFilter.value !== "duplicates") return null;
  const list = activeReportTracks.value;
  if (!list.length) return 0;
  const keyFor = (t: CatalogTrack) =>
    `${(t.artist ?? "").toLowerCase()}|${(t.album ?? "").toLowerCase()}|${(t.title ?? "").toLowerCase()}`;
  const map = new Map<string, number>();
  for (const t of list) {
    const key = keyFor(t);
    if (!key.trim()) continue;
    map.set(key, (map.get(key) ?? 0) + 1);
  }
  let total = 0;
  for (const count of map.values()) if (count > 1) total += count - 1;
  return total;
});

const showReportModal = computed(() => !!reportFilter.value && !!activeReportTitle.value);
const isMissingMetadataReport = computed(() => reportFilter.value === "missing_metadata");
const canSavePlaylist = computed(() => isMissingMetadataReport.value && activeReportTracks.value.length > 0);
const canApplyFromPath = computed(() => isMissingMetadataReport.value && activeReportTracks.value.length > 0 && pathFormatTemplates.value.some((t) => t.trim()));
const applyFromPathTooltip = computed(() => {
  const templates = pathFormatTemplates.value;
  const reportTracks = activeReportTracks.value;
  if (!templates.some((t) => t.trim()) || !reportTracks.length) return undefined;
  const matched = reportTracks.filter((t) => {
    const extracted = extractBestFromPath(templates, t.path);
    return extracted && Object.keys(buildUpdateFromExtracted(extracted)).length > 0;
  }).length;
  return `Applying to ${matched}/${reportTracks.length} tracks`;
});

async function saveReportAsPlaylist() {
  const trackIds = activeReportTracks.value.map((t) => t.id);
  if (!trackIds.length) return;
  const base = activeReportTitle.value || "Report";
  const existing = new Set(playlistStore.playlists.map((p) => p.name));
  let name = base;
  let i = 2;
  while (existing.has(name)) name = `${base} ${i++}`;
  await playlistStore.createPlaylistFromTracks(name, trackIds);
  store.setReportFilter(null);
}

async function applyAllFromPath() {
  const reportTracks = activeReportTracks.value;
  const templates = pathFormatTemplates.value;
  if (!reportTracks.length || !templates.some((t) => t.trim())) return;
  const total = reportTracks.length;
  store.setBulkProgress({ current: 0, total });
  try {
    for (let i = 0; i < reportTracks.length; i++) {
      const track = reportTracks[i];
      const extracted = extractBestFromPath(templates, track.path);
      if (extracted) {
        const update = buildUpdateFromExtracted(extracted);
        if (Object.keys(update).length > 0) {
          await invoke("write_track_metadata", {
            path: track.path,
            update,
            backupBeforeWrite: settingsStore.backupBeforeWrite,
          });
        }
      }
      store.setBulkProgress({ current: i + 1, total });
    }
    await store.loadTracks();
    store.setReportFilter(null);
  } finally {
    store.setBulkProgress(null);
  }
}

function selectTrackFromReport(t: CatalogTrack) {
  store.setReportFilter(null);

  // Ensure the table can actually show the reported item.
  store.clearActivePlaylist();

  const albumName = (t.album ?? "").trim();
  const artistKey = ((t.album_artist ?? t.artist) ?? "").trim();

  if (albumName) {
    // Select the whole album (best-effort) so cover/metadata fixes apply to all tracks.
    const ids = tracks.value
      .filter((x) => (x.album ?? "").trim() === albumName && (((x.album_artist ?? x.artist) ?? "").trim() === artistKey))
      .map((x) => x.id);
    store.setSelection(ids.length ? ids : [t.id]);
    store.setMultiSelectMode((ids.length ? ids : [t.id]).length > 1);
    store.setSearchQuery(albumName);
  } else {
    store.clearSelection();
    store.toggleSelection(t.id);
    store.setMultiSelectMode(false);
    store.setSearchQuery((t.title ?? "").trim() || t.path.split(/[/\\]/).pop() || "");
  }

  emit("update:activeTab", "metadata");

  // Scroll after the table updates from the search query/selection changes.
  const targetId = t.id;
  queueMicrotask(() => tableBodyRef.value?.scrollToTrackId(targetId));
}
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <LibraryHeader
      :activeTab="props.activeTab"
      :sidebarCollapsed="props.sidebarCollapsed"
      @update:activeTab="emit('update:activeTab', $event)"
      @expandSidebar="emit('expandSidebar')"
      @openSettings="showSettingsModal = true"
      @openKeyMap="showKeyMapModal = true"
      @expandAllGroups="tableBodyRef?.expandAllGroups?.()"
      @collapseAllGroups="tableBodyRef?.collapseAllGroups?.()"
      @expandPlayer="emit('expandPlayer')"
    />

    <LibraryTableBody ref="tableBodyRef" @openMetadata="emit('update:activeTab', 'metadata')" />

    <LibrarySettingsModal v-model:open="showSettingsModal" />
    <LibraryKeyMapModal v-model:open="showKeyMapModal" />

    <LibraryReportsModal
      :open="showReportModal"
      :title="activeReportTitle"
      :tracks="activeReportTracks"
      :duplicateCount="duplicateCountInReport"
      :canSavePlaylist="canSavePlaylist"
      :canApplyFromPath="canApplyFromPath"
      :applyFromPathTooltip="applyFromPathTooltip"
      @close="store.setReportFilter(null)"
      @selectTrack="selectTrackFromReport"
      @saveAsPlaylist="saveReportAsPlaylist"
      @applyAllFromPath="applyAllFromPath"
    />
  </div>
</template>


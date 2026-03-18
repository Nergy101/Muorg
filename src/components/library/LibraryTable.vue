<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { CatalogTrack } from "../../types";
import type { MissingMetadataField } from "../../stores/settings";
import LibraryHeader from "./LibraryHeader.vue";
import LibraryTableBody from "./LibraryTableBody.vue";
import LibrarySettingsModal from "../modals/LibrarySettingsModal.vue";
import LibraryKeyMapModal from "../modals/LibraryKeyMapModal.vue";
import LibraryReportsModal from "../modals/LibraryReportsModal.vue";

const props = defineProps<{
  activeTab: "library" | "metadata" | "player" | "queue";
}>();

const emit = defineEmits<{
  (e: "update:activeTab", value: "library" | "metadata" | "player" | "queue"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { tracks, reportFilter, reportSingleField } = storeToRefs(store);
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
      @update:activeTab="emit('update:activeTab', $event)"
      @openSettings="showSettingsModal = true"
      @openKeyMap="showKeyMapModal = true"
      @expandAllGroups="tableBodyRef?.expandAllGroups?.()"
      @collapseAllGroups="tableBodyRef?.collapseAllGroups?.()"
    />

    <LibraryTableBody ref="tableBodyRef" @openMetadata="emit('update:activeTab', 'metadata')" />

    <LibrarySettingsModal v-model:open="showSettingsModal" />
    <LibraryKeyMapModal v-model:open="showKeyMapModal" />

    <LibraryReportsModal
      :open="showReportModal"
      :title="activeReportTitle"
      :tracks="activeReportTracks"
      :duplicateCount="duplicateCountInReport"
      @close="store.setReportFilter(null)"
      @selectTrack="selectTrackFromReport"
    />
  </div>
</template>


<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import type { CatalogTrack } from "../types";
import type { MissingMetadataField } from "../stores/settings";
import LibraryHeader from "./LibraryHeader.vue";
import LibraryTableBody from "./LibraryTableBody.vue";
import LibrarySettingsModal from "./LibrarySettingsModal.vue";
import LibraryKeyMapModal from "./LibraryKeyMapModal.vue";
import LibraryReportsModal from "./LibraryReportsModal.vue";

const props = defineProps<{
  activeTab: "library" | "metadata" | "play";
}>();

const emit = defineEmits<{
  (e: "update:activeTab", value: "library" | "metadata" | "play"): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { filteredTracks, reportFilter } = storeToRefs(store);
const { missingMetadataFields } = storeToRefs(settingsStore);

const showSettingsModal = ref(false);
const showKeyMapModal = ref(false);
const tableBodyRef = ref<InstanceType<typeof LibraryTableBody> | null>(null);

function isFieldMissing(track: CatalogTrack, field: MissingMetadataField): boolean {
  const v = track[field as keyof CatalogTrack];
  if (field === "year" || field === "track_number" || field === "disc_number") return v == null;
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
  if (reportFilter.value === "missing_metadata") return "Missing metadata";
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
  store.clearSelection();
  store.toggleSelection(t.id);
  store.setReportFilter(null);
  tableBodyRef.value?.scrollToTrackId(t.id);
}
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <LibraryHeader
      :activeTab="props.activeTab"
      @update:activeTab="emit('update:activeTab', $event)"
      @openSettings="showSettingsModal = true"
      @openKeyMap="showKeyMapModal = true"
    />

    <LibraryTableBody ref="tableBodyRef" />

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


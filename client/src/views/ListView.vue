<script setup lang="ts">
import { watch } from "vue";
import { storeToRefs } from "pinia";
import LibraryTableBody from "../components/library/LibraryTableBody.vue";
import LibraryControls from "../components/library/LibraryControls.vue";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";

const store = useCatalogStore();
const settings = useSettingsStore();
const { tableOrderedTracks, filteredTracks } = storeToRefs(store);

// The Library sort control also drives the track table's column sort.
watch(
  () => settings.albumGridSortBy,
  (by) => {
    settings.tableSortColumn = by;
    settings.tableSortDirection = "asc";
  },
  { immediate: true },
);

const emit = defineEmits<{ (e: "openMetadata"): void }>();
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="flex shrink-0 items-baseline gap-3 px-5 pb-2 pt-4">
      <h1 class="view-title">Tracks</h1>
      <span class="view-subtitle">{{ filteredTracks.length }} of {{ tableOrderedTracks.length }} tracks</span>
      <LibraryControls />
    </div>
    <LibraryTableBody @open-metadata="emit('openMetadata')" />
  </div>
</template>

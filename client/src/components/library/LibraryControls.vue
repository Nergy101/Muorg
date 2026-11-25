<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import PrettySelect from "../shared/PrettySelect.vue";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore, type AlbumGridSortBy } from "../../stores/settings";

const store = useCatalogStore();
const settings = useSettingsStore();
const { tracks } = storeToRefs(store);

const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: "year", label: "Year" },
  { value: "album", label: "Album" },
  { value: "artist", label: "Artist" },
];

const genres = computed(() => {
  const set = new Set<string>();
  for (const t of tracks.value) {
    const g = (t.genre ?? "").trim();
    if (g) set.add(g);
  }
  return [...set].sort((a, b) => a.localeCompare(b));
});
</script>

<template>
  <div class="ml-auto flex items-center gap-2">
    <PrettySelect
      label="Sort"
      :model-value="settings.albumGridSortBy"
      :options="SORT_OPTIONS"
      @update:model-value="settings.setAlbumGridSortBy($event as AlbumGridSortBy)"
    />
    <PrettySelect
      label="Genre"
      :model-value="store.filterGenre ?? ''"
      :options="[{ value: '', label: 'All genres' }, ...genres.map((g) => ({ value: g, label: g }))]"
      @update:model-value="store.setFilterGenre($event || null)"
    />
  </div>
</template>

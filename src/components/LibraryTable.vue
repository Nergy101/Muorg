<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import type { CatalogTrack } from "../types";
import TrackAlbumArt from "./TrackAlbumArt.vue";

const store = useCatalogStore();
const { filteredTracks, selectedTrackIds, searchQuery, groupBy } = storeToRefs(store);

/** Set of group keys that are expanded. When groupBy changes, reset to all expanded. */
const expandedGroups = ref<Set<string>>(new Set());
const multiSelectMode = ref(false);

function formatDuration(secs: number | null): string {
  if (secs == null) return "—";
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function isSelected(id: number): boolean {
  return selectedTrackIds.value.includes(id);
}

function toggle(t: CatalogTrack) {
  store.toggleSelection(t.id);
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
  const tracks = filteredTracks.value;
  if (by === "none" || !tracks.length) return null;
  const map = new Map<string, CatalogTrack[]>();
  for (const t of tracks) {
    const key = by === "artist" ? (t.artist ?? "—") : by === "album" ? (t.album ?? "—") : "";
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(t);
  }
  const keys = [...map.keys()].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));
  return keys.map((key) => ({ key, label: key, tracks: map.get(key)! }));
});

watch(groupBy, (by) => {
  if (by === "none") return;
  const groups = groupedRows.value;
  if (groups) {
    expandedGroups.value = new Set(groups.map((g) => g.key));
  }
}, { immediate: true });

watch(groupedRows, (groups) => {
  if (!groups?.length) return;
  const next = new Set(expandedGroups.value);
  for (const g of groups) {
    if (!next.has(g.key)) next.add(g.key);
  }
  expandedGroups.value = next;
});

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
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <div class="flex flex-wrap items-center gap-3 border-b border-stone-700 px-4 py-2">
      <h2 class="text-sm font-medium text-stone-300">Tracks</h2>
      <input
        :value="searchQuery"
        type="search"
        placeholder="Search title, artist, album…"
        class="min-w-[200px] rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200 placeholder-stone-500"
        @input="store.setSearchQuery(($event.target as HTMLInputElement).value)"
      />
      <select
        :value="groupBy"
        class="rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200"
        @change="store.setGroupBy(($event.target as HTMLSelectElement).value as 'none' | 'artist' | 'album')"
      >
        <option value="none">No grouping</option>
        <option value="artist">Group by artist</option>
        <option value="album">Group by album</option>
      </select>
      <template v-if="groupedRows?.length">
        <button
          type="button"
          class="rounded border border-stone-600 bg-stone-800 px-2 py-1 text-xs text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          title="Expand all groups"
          @click="expandAll"
        >
          Expand all
        </button>
        <button
          type="button"
          class="rounded border border-stone-600 bg-stone-800 px-2 py-1 text-xs text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          title="Collapse all groups"
          @click="collapseAll"
        >
          Collapse all
        </button>
      </template>
    </div>
    <div class="table-scroll-container flex-1 overflow-auto">
      <table class="table-with-scroll-gutter w-full min-w-[800px] border-collapse text-left text-sm">
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
            <th class="w-10 border-r border-stone-700 p-2"></th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Title</th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Artist</th>
            <th class="border-r border-stone-700 p-2 font-medium text-stone-400">Album</th>
            <th class="w-20 border-r border-stone-700 p-2 font-medium text-stone-400">Year</th>
            <th class="w-16 border-r border-stone-700 p-2 font-medium text-stone-400">Duration</th>
            <th class="w-16 border-r border-stone-700 p-2 font-medium text-stone-400">Format</th>
            <th class="max-w-[200px] truncate border-stone-700 p-2 font-medium text-stone-400">Path</th>
          </tr>
        </thead>
        <tbody>
          <template v-if="groupedRows">
            <template v-for="group in groupedRows" :key="group.key">
              <tr
                class="cursor-pointer bg-stone-800/80 font-medium text-stone-400 hover:bg-stone-700/80"
                @click="toggleGroup(group.key)"
              >
                <td colspan="9" class="border-b border-stone-600 p-2">
                  <span class="inline-block w-4 text-stone-500" aria-hidden="true">
                    {{ isGroupExpanded(group.key) ? "▼" : "▶" }}
                  </span>
                  {{ group.label }}
                  <span class="ml-1 text-stone-500">({{ group.tracks.length }})</span>
                </td>
              </tr>
              <template v-if="isGroupExpanded(group.key)">
                <tr
                  v-for="t in group.tracks"
                  :key="t.id"
                  class="border-b border-stone-700/50 hover:bg-stone-800/50"
                  :class="{ 'bg-stone-700/40': isSelected(t.id) }"
                  @click="selectRow(t)"
                >
                <td class="border-r border-stone-700 p-2">
                  <input
                    type="checkbox"
                    :checked="isSelected(t.id)"
                    class="rounded border-stone-600"
                    @click.stop="selectRow(t)"
                  />
                </td>
                <td class="border-r border-stone-700 p-2">
                  <TrackAlbumArt :path="t.path" />
                </td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ t.title ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ t.artist ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-200">{{ t.album ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-300">{{ t.year ?? "—" }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-300">{{ formatDuration(t.duration_secs) }}</td>
                <td class="border-r border-stone-700 p-2 text-stone-400">{{ t.format }}</td>
                <td class="max-w-[200px] truncate p-2 text-stone-500" :title="t.path">{{ t.path }}</td>
              </tr>
              </template>
            </template>
          </template>
          <template v-else>
            <tr
              v-for="t in filteredTracks"
              :key="t.id"
              class="border-b border-stone-700/50 hover:bg-stone-800/50"
              :class="{ 'bg-stone-700/40': isSelected(t.id) }"
              @click="selectRow(t)"
            >
              <td class="border-r border-stone-700 p-2">
                <input
                  type="checkbox"
                  :checked="isSelected(t.id)"
                  class="rounded border-stone-600"
                  @click.stop="selectRow(t)"
                />
              </td>
              <td class="border-r border-stone-700 p-2">
                <TrackAlbumArt :path="t.path" />
              </td>
              <td class="border-r border-stone-700 p-2 text-stone-200">{{ t.title ?? "—" }}</td>
              <td class="border-r border-stone-700 p-2 text-stone-200">{{ t.artist ?? "—" }}</td>
              <td class="border-r border-stone-700 p-2 text-stone-200">{{ t.album ?? "—" }}</td>
              <td class="border-r border-stone-700 p-2 text-stone-300">{{ t.year ?? "—" }}</td>
              <td class="border-r border-stone-700 p-2 text-stone-300">{{ formatDuration(t.duration_secs) }}</td>
              <td class="border-r border-stone-700 p-2 text-stone-400">{{ t.format }}</td>
              <td class="max-w-[200px] truncate p-2 text-stone-500" :title="t.path">{{ t.path }}</td>
            </tr>
            <tr v-if="!filteredTracks.length" class="text-stone-500">
              <td colspan="9" class="p-6 text-center">
                {{ store.searchQuery.trim() ? "No tracks match the search." : "No tracks. Add a folder to scan MP3/FLAC files." }}
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
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

</style>

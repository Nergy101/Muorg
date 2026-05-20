<template>
  <header class="flex min-w-0 items-start gap-2 border-b border-stone-700 bg-stone-900 px-4 py-2">

    <!-- Mobile sidebar toggle -->
    <button
      type="button"
      class="mr-1 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-800 text-stone-400 hover:bg-stone-700 hover:text-stone-200 md:hidden"
      aria-label="Toggle sidebar"
      @click="emit('toggle-sidebar')"
    >
      <FeatherIcon name="sidebar" class="h-4 w-4" />
    </button>

    <!-- Middle: all variable controls, wraps on mobile -->
    <div class="flex min-w-0 flex-1 flex-wrap items-center gap-2">

      <!-- Back button (album detail view) -->
      <button
        v-if="props.showBack"
        type="button"
        class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-800 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
        aria-label="Back"
        @click="emit('back')"
      >
        <FeatherIcon name="arrow-left" class="h-4 w-4" />
      </button>

      <!-- Active playlist tag -->
      <div
        v-if="playlistStore.activePlaylistId !== null"
        class="flex shrink-0 items-center gap-1 rounded-full border border-stone-600 bg-stone-700 py-0.5 pl-2 pr-1 text-xs text-stone-200"
      >
        <FeatherIcon name="list" class="h-3 w-3 shrink-0 text-stone-400" />
        <span class="max-w-[140px] truncate">{{ activePlaylistName ?? "Playlist" }}</span>
        <button
          type="button"
          class="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-stone-400 hover:bg-stone-600 hover:text-stone-200"
          aria-label="Clear playlist filter"
          @click="playlistStore.selectPlaylist(null)"
        >
          <FeatherIcon name="x" class="h-3 w-3" />
        </button>
      </div>

      <!-- Expandable search -->
      <div class="flex shrink-0 items-center">
        <button
          type="button"
          class="inline-flex h-8 w-8 shrink-0 items-center justify-center border border-stone-600 bg-stone-800 text-stone-400 hover:bg-stone-700 hover:text-stone-200"
          :class="searchExpanded ? 'rounded-l border-r-0' : 'rounded'"
          aria-label="Search"
          @click="expandSearch"
        >
          <FeatherIcon name="search" class="h-4 w-4" />
        </button>
        <div
          class="overflow-hidden transition-[width] duration-200 ease-out"
          :style="{ width: searchExpanded ? '212px' : '0px' }"
        >
          <div class="relative flex items-center">
            <input
              ref="searchInputRef"
              :value="lib.searchQuery"
              :tabindex="searchExpanded ? 0 : -1"
              type="text"
              placeholder="Search title, artist, album…"
              class="h-8 w-[212px] rounded-r border border-l-0 border-stone-600 bg-stone-800 py-0 pl-2 pr-7 text-sm text-stone-200 placeholder-stone-500"
              @input="lib.searchQuery = ($event.target as HTMLInputElement).value"
              @blur="onSearchBlur"
              @keydown.escape="lib.searchQuery = ''; searchExpandedLocal = false"
            />
            <button
              v-if="lib.searchQuery"
              type="button"
              class="absolute right-1.5 inline-flex h-5 w-5 items-center justify-center rounded text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Clear search"
              @mousedown.prevent="lib.searchQuery = ''"
            >
              <FeatherIcon name="x" class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>

      <!-- Group-by dropdown (table mode) -->
      <div v-if="viewMode === 'table'" ref="groupByRef" class="relative shrink-0">
        <button
          type="button"
          class="flex items-center gap-1.5 whitespace-nowrap rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200 hover:border-stone-500 hover:bg-stone-700"
          :class="groupByOpen ? 'border-stone-500 bg-stone-700' : ''"
          @click="groupByOpen = !groupByOpen"
        >
          <span>{{ groupByLabels[lib.groupBy] }}</span>
          <FeatherIcon name="chevron-down" class="h-3.5 w-3.5 text-stone-400 transition-transform" :class="groupByOpen ? 'rotate-180' : ''" />
        </button>
        <div
          v-if="groupByOpen"
          class="absolute left-0 top-full z-[300] mt-1 min-w-full rounded border border-stone-600 bg-stone-800 py-1 shadow-[0_8px_32px_rgba(0,0,0,0.5)]"
        >
          <button
            v-for="(label, val) in groupByLabels"
            :key="val"
            type="button"
            class="flex w-full items-center gap-2 whitespace-nowrap px-3 py-1.5 text-left text-sm hover:bg-stone-700"
            :class="lib.groupBy === val ? 'text-stone-200' : 'text-stone-400'"
            @click="lib.setGroupBy(val as 'none' | 'artist' | 'album'); groupByOpen = false"
          >
            <FeatherIcon
              name="check"
              class="h-3.5 w-3.5 shrink-0"
              :class="lib.groupBy === val ? 'text-stone-200' : 'invisible'"
            />
            {{ label }}
          </button>
        </div>
      </div>

      <!-- Album grid sort dropdown (grid mode) -->
      <div v-if="viewMode === 'grid'" ref="gridSortRef" class="relative shrink-0">
        <button
          type="button"
          class="flex items-center gap-1.5 whitespace-nowrap rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200 hover:border-stone-500 hover:bg-stone-700"
          :class="gridSortOpen ? 'border-stone-500 bg-stone-700' : ''"
          @click="gridSortOpen = !gridSortOpen"
        >
          <span>{{ gridSortLabels[lib.gridSortBy] }}</span>
          <FeatherIcon name="chevron-down" class="h-3.5 w-3.5 text-stone-400 transition-transform" :class="gridSortOpen ? 'rotate-180' : ''" />
        </button>
        <div
          v-if="gridSortOpen"
          class="absolute left-0 top-full z-[300] mt-1 min-w-full rounded border border-stone-600 bg-stone-800 py-1 shadow-[0_8px_32px_rgba(0,0,0,0.5)]"
        >
          <button
            v-for="(label, val) in gridSortLabels"
            :key="val"
            type="button"
            class="flex w-full items-center gap-2 whitespace-nowrap px-3 py-1.5 text-left text-sm hover:bg-stone-700"
            :class="lib.gridSortBy === val ? 'text-stone-200' : 'text-stone-400'"
            @click="lib.setGridSortBy(val as 'album' | 'artist' | 'year'); gridSortOpen = false"
          >
            <FeatherIcon
              name="check"
              class="h-3.5 w-3.5 shrink-0"
              :class="lib.gridSortBy === val ? 'text-stone-200' : 'invisible'"
            />
            {{ label }}
          </button>
        </div>
      </div>

      <!-- Layout toggle -->
      <div class="flex items-center gap-1 rounded border border-stone-600 bg-stone-800 p-0.5">
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded transition-colors"
          :class="viewMode === 'table' ? 'bg-stone-600 text-stone-100' : 'text-stone-400 hover:bg-stone-700 hover:text-stone-200'"
          aria-label="Table layout"
          @click="lib.setViewMode('table')"
        >
          <FeatherIcon name="list" class="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded transition-colors"
          :class="viewMode === 'grid' ? 'bg-stone-600 text-stone-100' : 'text-stone-400 hover:bg-stone-700 hover:text-stone-200'"
          aria-label="Album grid layout"
          @click="lib.setViewMode('grid')"
        >
          <FeatherIcon name="grid" class="h-3.5 w-3.5" />
        </button>
      </div>

      <!-- Art size toggle (table mode, when grouped) -->
      <div
        v-if="viewMode === 'table' && lib.groupBy !== 'none'"
        class="flex items-center gap-0.5 rounded border border-stone-600 bg-stone-800 p-0.5"
      >
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded transition-colors"
          :class="lib.tableArtSize === 'small' ? 'bg-stone-600 text-stone-100' : 'text-stone-400 hover:bg-stone-700 hover:text-stone-200'"
          title="Small cover art"
          @click="lib.setTableArtSize('small')"
        >
          <svg width="11" height="11" viewBox="0 0 16 16" fill="currentColor"><rect x="1" y="1" width="6" height="6" rx="1"/><rect x="9" y="1" width="6" height="6" rx="1"/><rect x="1" y="9" width="6" height="6" rx="1"/><rect x="9" y="9" width="6" height="6" rx="1"/></svg>
        </button>
        <button
          type="button"
          class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded transition-colors"
          :class="lib.tableArtSize === 'large' ? 'bg-stone-600 text-stone-100' : 'text-stone-400 hover:bg-stone-700 hover:text-stone-200'"
          title="Large cover art"
          @click="lib.setTableArtSize('large')"
        >
          <svg width="13" height="13" viewBox="0 0 16 16" fill="currentColor"><rect x="1" y="1" width="14" height="14" rx="2"/></svg>
        </button>
      </div>

    </div>

    <!-- Right: utility buttons — always pinned to top-right -->
    <div class="flex shrink-0 items-center gap-1">
      <button
        type="button"
        class="hidden sm:inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
        title="Mouse controls"
        @click="showKeymap = true"
      >
        <FeatherIcon name="mouse-pointer" class="h-4 w-4" />
      </button>
      <button
        type="button"
        class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
        :title="`Muorg Web v${version}`"
      >
        <FeatherIcon name="info" class="h-4 w-4" />
      </button>
      <button
        type="button"
        class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
        title="Statistics"
        @click="emit('open-stats')"
      >
        <FeatherIcon name="bar-chart-2" class="h-4 w-4" />
      </button>
      <button
        type="button"
        class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-600 hover:text-stone-200"
        title="Disconnect"
        @click="emit('disconnect')"
      >
        <FeatherIcon name="log-out" class="h-4 w-4" />
      </button>
    </div>

  </header>

  <KeymapModal v-model="showKeymap" />
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import FeatherIcon from "./FeatherIcon.vue";
import KeymapModal from "./KeymapModal.vue";

const props = defineProps<{ showBack?: boolean }>();

const emit = defineEmits<{
  disconnect: [];
  "toggle-sidebar": [];
  back: [];
  "open-stats": [];
}>();

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const viewMode = computed(() => lib.viewMode);
const activePlaylistName = computed(() =>
  playlistStore.playlists.find((p) => p.id === playlistStore.activePlaylistId)?.name,
);

const groupByLabels: Record<string, string> = {
  album: "Group by album",
  artist: "Group by artist",
  none: "No grouping",
};

const gridSortLabels: Record<string, string> = {
  album: "Sort by album",
  artist: "Sort by artist",
  year: "Sort by year",
};

// Search expand
const searchInputRef = ref<HTMLInputElement | null>(null);
const searchExpandedLocal = ref(false);
const searchExpanded = computed(() => searchExpandedLocal.value || !!lib.searchQuery);

function expandSearch() {
  searchExpandedLocal.value = true;
  nextTick(() => searchInputRef.value?.focus());
}

function onSearchBlur() {
  if (!lib.searchQuery) searchExpandedLocal.value = false;
}

const version = import.meta.env.VITE_APP_VERSION ?? 'dev';
const showKeymap = ref(false);

// Dropdowns
const groupByOpen = ref(false);
const groupByRef = ref<HTMLElement | null>(null);
const gridSortOpen = ref(false);
const gridSortRef = ref<HTMLElement | null>(null);

function onClickOutside(e: MouseEvent) {
  if (groupByRef.value && !groupByRef.value.contains(e.target as Node)) groupByOpen.value = false;
  if (gridSortRef.value && !gridSortRef.value.contains(e.target as Node)) gridSortOpen.value = false;
}

watch([groupByOpen, gridSortOpen], ([gb, gs]) => {
  if (gb || gs) {
    document.addEventListener("mousedown", onClickOutside);
  } else {
    document.removeEventListener("mousedown", onClickOutside);
  }
});

</script>

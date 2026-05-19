<template>
  <header class="flex items-center gap-2 border-b border-stone-800 bg-stone-900 px-3 py-2">
    <!-- Hamburger (mobile sidebar toggle) -->
    <button
      class="mr-1 flex h-8 w-8 items-center justify-center rounded text-stone-400 hover:bg-stone-800 hover:text-stone-200 md:hidden"
      @click="emit('toggle-sidebar')"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="18" x2="21" y2="18" />
      </svg>
    </button>

    <!-- View toggle -->
    <div class="flex rounded-lg border border-stone-700 overflow-hidden">
      <button
        :class="viewMode === 'grid' ? 'bg-stone-700 text-stone-100' : 'text-stone-400 hover:bg-stone-800 hover:text-stone-200'"
        class="flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium transition-colors"
        title="Album grid"
        @click="lib.setViewMode('grid')"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" />
          <rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" />
        </svg>
        <span class="hidden sm:inline">Grid</span>
      </button>
      <button
        :class="viewMode === 'table' ? 'bg-stone-700 text-stone-100' : 'text-stone-400 hover:bg-stone-800 hover:text-stone-200'"
        class="flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium transition-colors"
        title="Track table"
        @click="lib.setViewMode('table')"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" />
          <line x1="3" y1="18" x2="21" y2="18" />
        </svg>
        <span class="hidden sm:inline">Table</span>
      </button>
    </div>

    <!-- Search -->
    <div class="relative min-w-0 flex-1 max-w-xs">
      <svg
        class="absolute left-2.5 top-1/2 -translate-y-1/2 text-stone-500"
        width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
      >
        <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
      </svg>
      <input
        :value="lib.searchQuery"
        type="text"
        placeholder="Search…"
        class="w-full rounded-lg border border-stone-700 bg-stone-800 py-1.5 pl-8 pr-8 text-xs text-stone-200 placeholder-stone-500 focus:border-transparent focus:outline-none focus:ring-1 focus:ring-accent"
        @input="lib.searchQuery = ($event.target as HTMLInputElement).value"
      />
      <button
        v-if="lib.searchQuery"
        class="absolute right-2 top-1/2 -translate-y-1/2 text-stone-500 hover:text-stone-300"
        @click="lib.searchQuery = ''"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>

    <!-- Grid sort (grid mode) -->
    <select
      v-if="viewMode === 'grid'"
      :value="lib.gridSortBy"
      class="hidden rounded-lg border border-stone-700 bg-stone-800 px-2 py-1.5 text-xs text-stone-300 sm:block focus:outline-none focus:ring-1 focus:ring-accent"
      @change="lib.setGridSortBy(($event.target as HTMLSelectElement).value as 'album' | 'artist' | 'year')"
    >
      <option value="album">Sort: Album</option>
      <option value="artist">Sort: Artist</option>
      <option value="year">Sort: Year</option>
    </select>

    <!-- Table controls (table mode) -->
    <template v-if="viewMode === 'table'">
      <select
        :value="lib.groupBy"
        class="hidden rounded-lg border border-stone-700 bg-stone-800 px-2 py-1.5 text-xs text-stone-300 sm:block focus:outline-none focus:ring-1 focus:ring-accent"
        @change="lib.setGroupBy(($event.target as HTMLSelectElement).value as 'none' | 'album' | 'artist')"
      >
        <option value="none">No grouping</option>
        <option value="album">Group: Album</option>
        <option value="artist">Group: Artist</option>
      </select>
    </template>

    <!-- Theme + disconnect -->
    <div class="ml-auto flex items-center gap-1">
      <button
        class="flex h-8 w-8 items-center justify-center rounded text-stone-400 hover:bg-stone-800 hover:text-stone-200"
        title="Toggle theme"
        @click="toggleTheme"
      >
        <svg v-if="isDark" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="5" /><line x1="12" y1="1" x2="12" y2="3" /><line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" /><line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" /><line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" /><line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      </button>

      <button
        class="flex h-8 w-8 items-center justify-center rounded text-stone-400 hover:bg-stone-800 hover:text-stone-200"
        title="Disconnect"
        @click="emit('disconnect')"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <polyline points="16 17 21 12 16 7" />
          <line x1="21" y1="12" x2="9" y2="12" />
        </svg>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from "vue";
import { useLibraryStore } from "../stores/library";

const emit = defineEmits<{
  disconnect: [];
  "toggle-sidebar": [];
}>();

const lib = useLibraryStore();
const viewMode = computed(() => lib.viewMode);

const isDark = ref(true);

onMounted(() => {
  isDark.value = document.documentElement.getAttribute("data-theme") !== "light";
});

function toggleTheme(): void {
  isDark.value = !isDark.value;
  document.documentElement.setAttribute("data-theme", isDark.value ? "dark" : "light");
  localStorage.setItem("muorg-web-theme", isDark.value ? "dark" : "light");
}
</script>

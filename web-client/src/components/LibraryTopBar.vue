<template>
  <!-- Library top bar: sort, genre filter, layout toggle and shuffle-all live
       here instead of the bottom bar next to the player. Menus open downward. -->
  <div class="content-col flex shrink-0 items-center gap-1 border-b border-outline/10 px-4 py-2">
    <div ref="sortMenuRef" class="relative" :class="settings.albumViewStyle === 'tracks' ? 'lg:hidden' : ''">
      <button
        ref="sortBtnRef"
        type="button"
        class="text-label-lg text-on-surface"
        @click="toggleSort"
      >
        Sort: {{ sortLabel }}
      </button>
      <!-- Teleported to body so the view's overflow-hidden shell can't clip it. -->
      <Teleport to="body">
        <div
          v-if="sortMenuOpen"
          class="glass-strong fixed z-50 w-32 rounded-xl py-1"
          :style="sortMenuPos"
        >
          <button
            v-for="opt in SORT_OPTIONS"
            :key="opt.value"
            type="button"
            class="flex w-full items-center justify-between px-3 py-2 text-label-lg text-on-surface"
            @click="selectSort(opt.value)"
          >
            <span>{{ opt.label }}</span>
            <MageIcon v-if="settings.sortMode === opt.value" name="check" class="h-4 w-4 text-primary" />
          </button>
        </div>
      </Teleport>
    </div>

    <button
      type="button"
      class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
      :class="settings.albumViewStyle === 'tracks' ? 'lg:hidden' : ''"
      :aria-label="settings.sortAscending ? 'Sort descending' : 'Sort ascending'"
      @click="settings.setSortAscending(!settings.sortAscending)"
    >
      <MageIcon :name="settings.sortAscending ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
    </button>

    <!-- Desktop-class table controls: column sort + min-rating filter, tracks
         layout only (the table has its own header-click sort too; this is a
         toolbar-level shortcut, especially useful for the min-rating filter
         which has no other UI). Hidden below lg — the mobile tracks list
         stays as-is, unaffected by tableStore state. -->
    <template v-if="settings.albumViewStyle === 'tracks'">
      <div ref="tableSortMenuRef" class="relative hidden lg:block">
        <button
          ref="tableSortBtnRef"
          type="button"
          class="text-label-lg text-on-surface"
          @click="toggleTableSort"
        >
          Sort: {{ tableSortLabel }}
        </button>
        <Teleport to="body">
          <div
            v-if="tableSortMenuOpen"
            class="glass-strong fixed z-50 w-36 rounded-xl py-1"
            :style="tableSortMenuPos"
          >
            <button
              v-for="opt in TABLE_SORT_OPTIONS"
              :key="opt.value ?? 'none'"
              type="button"
              class="flex w-full items-center justify-between px-3 py-2 text-left text-label-lg text-on-surface"
              @click="selectTableSort(opt.value)"
            >
              <span>{{ opt.label }}</span>
              <MageIcon v-if="tableStore.sortColumn === opt.value" name="check" class="h-4 w-4 text-primary" />
            </button>
          </div>
        </Teleport>
      </div>

      <button
        type="button"
        class="hidden h-8 w-8 items-center justify-center rounded-full text-on-surface-variant lg:flex"
        :aria-label="tableStore.sortDirection === 'asc' ? 'Sort descending' : 'Sort ascending'"
        @click="tableStore.setSort(tableStore.sortColumn, tableStore.sortDirection === 'asc' ? 'desc' : 'asc')"
      >
        <MageIcon :name="tableStore.sortDirection === 'asc' ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
      </button>

      <div ref="minRatingMenuRef" class="relative hidden lg:block">
        <button
          ref="minRatingBtnRef"
          type="button"
          class="text-label-lg text-on-surface"
          @click="toggleMinRating"
        >
          Rating: {{ minRatingLabel }}
        </button>
        <Teleport to="body">
          <div
            v-if="minRatingMenuOpen"
            class="glass-strong fixed z-50 w-28 rounded-xl py-1"
            :style="minRatingMenuPos"
          >
            <button
              v-for="opt in MIN_RATING_OPTIONS"
              :key="opt"
              type="button"
              class="flex w-full items-center justify-between px-3 py-2 text-left text-label-lg text-on-surface"
              @click="selectMinRating(opt)"
            >
              <span>{{ opt === 0 ? "All" : `${opt}+★` }}</span>
              <MageIcon v-if="tableStore.minRating === opt" name="check" class="h-4 w-4 text-primary" />
            </button>
          </div>
        </Teleport>
      </div>
    </template>

    <div ref="genreMenuRef" class="relative">
      <button
        ref="genreBtnRef"
        type="button"
        class="flex max-w-28 items-center gap-1 text-label-lg text-on-surface"
        @click="toggleGenre"
      >
        <span class="truncate">Genre: {{ genreLabel }}</span>
        <MageIcon :name="genreMenuOpen ? 'chevron-up' : 'chevron-down'" class="h-4 w-4 shrink-0" />
      </button>
      <Teleport to="body">
        <div
          v-if="genreMenuOpen"
          class="glass-strong fixed z-50 max-h-72 w-48 overflow-y-auto rounded-xl py-1"
          :style="genreMenuPos"
        >
          <button
            type="button"
            class="flex w-full items-center justify-between px-3 py-2 text-label-lg text-on-surface"
            @click="selectGenre(null)"
          >
            <span>All genres</span>
            <MageIcon v-if="!lib.genreFilter" name="check" class="h-4 w-4 text-primary" />
          </button>
          <button
            v-for="g in lib.genres"
            :key="g.value"
            type="button"
            class="flex w-full items-center justify-between gap-2 px-3 py-2 text-label-lg text-on-surface"
            @click="selectGenre(g.value)"
          >
            <span class="truncate">{{ g.label }}</span>
            <MageIcon v-if="lib.genreFilter === g.value" name="check" class="h-4 w-4 shrink-0 text-primary" />
          </button>
        </div>
      </Teleport>
    </div>

    <div class="flex-1" />

    <button
      type="button"
      class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
      aria-label="Change layout"
      @click="settings.cycleAlbumViewStyle()"
    >
      <MageIcon :name="viewStyleIcon" class="h-4 w-4" />
    </button>

    <button
      type="button"
      class="flex h-8 w-8 items-center justify-center rounded-full transition-colors"
      :class="player.shuffleAllActive ? 'text-primary' : 'text-on-surface'"
      aria-label="Shuffle all"
      :aria-pressed="player.shuffleAllActive"
      @click="player.startShuffleAll(lib.filteredTracks)"
    >
      <MageIcon name="exchange" class="h-4 w-4" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import MageIcon from "./MageIcon.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useLibraryTableStore, TABLE_COLUMNS } from "../stores/libraryTable";
import type { TableSortColumn } from "./library/tableModel";
import type { SortMode } from "../types";

const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();
const tableStore = useLibraryTableStore();

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: "album", label: "Album" },
  { value: "artist", label: "Artist" },
  { value: "year", label: "Year" },
];

/** Column sort options for the desktop-class tracks table/toolbar — every
 *  sortable table column, plus "None" to clear the sort. */
const TABLE_SORT_OPTIONS: { value: TableSortColumn | null; label: string }[] = [
  { value: null, label: "None" },
  ...TABLE_COLUMNS.filter((c) => c.sortable).map((c) => ({ value: c.id as TableSortColumn, label: c.label })),
];

const MIN_RATING_OPTIONS = [0, 1, 2, 3, 4, 5];

// --- Sort dropdown ---------------------------------------------------------

const sortMenuOpen = ref(false);
const sortMenuRef = ref<HTMLElement | null>(null);
const sortBtnRef = ref<HTMLElement | null>(null);

const sortLabel = computed(
  () => SORT_OPTIONS.find((o) => o.value === settings.sortMode)?.label ?? "Album",
);

const viewStyleIcon = computed(() =>
  settings.albumViewStyle === "tracks" ? "music" : "layout-grid",
);

/** Position the sort menu just below its trigger (the bar sits at the top). */
const sortMenuPos = computed(() => {
  const b = sortBtnRef.value?.getBoundingClientRect();
  if (!b) return {};
  return { top: `${b.bottom + 4}px`, left: `${b.left}px` };
});

function toggleSort(): void {
  sortMenuOpen.value = !sortMenuOpen.value;
}

function selectSort(mode: SortMode): void {
  settings.setSortMode(mode);
  sortMenuOpen.value = false;
}

// --- Genre filter dropdown ----------------------------------------------

const genreMenuOpen = ref(false);
const genreMenuRef = ref<HTMLElement | null>(null);
const genreBtnRef = ref<HTMLElement | null>(null);

const genreLabel = computed(
  () => lib.genres.find((g) => g.value === lib.genreFilter)?.label ?? "All",
);

const genreMenuPos = computed(() => {
  const b = genreBtnRef.value?.getBoundingClientRect();
  if (!b) return {};
  return { top: `${b.bottom + 4}px`, left: `${b.left}px` };
});

function toggleGenre(): void {
  genreMenuOpen.value = !genreMenuOpen.value;
}

function selectGenre(value: string | null): void {
  lib.genreFilter = value;
  genreMenuOpen.value = false;
}

// --- Table sort dropdown (desktop-class tracks table/toolbar) --------------

const tableSortMenuOpen = ref(false);
const tableSortMenuRef = ref<HTMLElement | null>(null);
const tableSortBtnRef = ref<HTMLElement | null>(null);

const tableSortLabel = computed(
  () => TABLE_SORT_OPTIONS.find((o) => o.value === tableStore.sortColumn)?.label ?? "None",
);

const tableSortMenuPos = computed(() => {
  const b = tableSortBtnRef.value?.getBoundingClientRect();
  if (!b) return {};
  return { top: `${b.bottom + 4}px`, left: `${b.left}px` };
});

function toggleTableSort(): void {
  tableSortMenuOpen.value = !tableSortMenuOpen.value;
}

function selectTableSort(column: TableSortColumn | null): void {
  tableStore.setSort(column, tableStore.sortDirection);
  tableSortMenuOpen.value = false;
}

// --- Min-rating filter dropdown (desktop-class tracks table/toolbar) -------

const minRatingMenuOpen = ref(false);
const minRatingMenuRef = ref<HTMLElement | null>(null);
const minRatingBtnRef = ref<HTMLElement | null>(null);

const minRatingLabel = computed(() => (tableStore.minRating > 0 ? `${tableStore.minRating}+★` : "All"));

const minRatingMenuPos = computed(() => {
  const b = minRatingBtnRef.value?.getBoundingClientRect();
  if (!b) return {};
  return { top: `${b.bottom + 4}px`, left: `${b.left}px` };
});

function toggleMinRating(): void {
  minRatingMenuOpen.value = !minRatingMenuOpen.value;
}

function selectMinRating(v: number): void {
  tableStore.setMinRating(v);
  minRatingMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  if (
    !sortMenuOpen.value &&
    !genreMenuOpen.value &&
    !tableSortMenuOpen.value &&
    !minRatingMenuOpen.value
  )
    return;
  const target = e.target as Node;
  if (sortMenuOpen.value && sortMenuRef.value && !sortMenuRef.value.contains(target)) {
    sortMenuOpen.value = false;
  }
  if (genreMenuOpen.value && genreMenuRef.value && !genreMenuRef.value.contains(target)) {
    genreMenuOpen.value = false;
  }
  if (tableSortMenuOpen.value && tableSortMenuRef.value && !tableSortMenuRef.value.contains(target)) {
    tableSortMenuOpen.value = false;
  }
  if (minRatingMenuOpen.value && minRatingMenuRef.value && !minRatingMenuRef.value.contains(target)) {
    minRatingMenuOpen.value = false;
  }
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));
</script>

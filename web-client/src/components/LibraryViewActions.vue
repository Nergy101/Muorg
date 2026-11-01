<template>
  <!-- View-level library actions (sort, genre, layout, shuffle-all), shared by
       the library top bar (grid view / mobile) and the desktop tracks table
       toolbar, so both surfaces offer the same controls in one row. Menus
       teleport to body and open downward from their trigger. -->
  <div ref="sortMenuRef" class="relative">
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
    :aria-label="settings.sortAscending ? 'Sort descending' : 'Sort ascending'"
    @click="settings.setSortAscending(!settings.sortAscending)"
  >
    <MageIcon :name="settings.sortAscending ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
  </button>

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
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import MageIcon from "./MageIcon.vue";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import type { SortMode } from "../types";

const lib = useLibraryStore();
const player = usePlayerStore();
const settings = useSettingsStore();

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: "album", label: "Album" },
  { value: "artist", label: "Artist" },
  { value: "year", label: "Year" },
];

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

function onDocumentClick(e: MouseEvent): void {
  if (!sortMenuOpen.value && !genreMenuOpen.value) return;
  const target = e.target as Node;
  if (sortMenuOpen.value && sortMenuRef.value && !sortMenuRef.value.contains(target)) {
    sortMenuOpen.value = false;
  }
  if (genreMenuOpen.value && genreMenuRef.value && !genreMenuRef.value.contains(target)) {
    genreMenuOpen.value = false;
  }
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));
</script>

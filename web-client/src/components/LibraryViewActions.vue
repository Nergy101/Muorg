<template>
  <!-- View-level library actions (layout, shuffle-all, genre, sort), shared
       by the library top bar (grid view / mobile) and the desktop tracks
       table toolbar. The fixed gap-1 wrapper keeps every button at the same
       position in both rows, so nothing jitters when switching layouts. All
       menus open as bottom sheets, like the Android app. -->
  <div class="flex items-center gap-1">
  <!-- Layout switcher first, on the far left in both layouts. Same icon in
       both layouts — the pane-divided window reads as "layout" — with a small
       badge showing which layout is active: 1 = album covers, 2 = album list, 3 = tracks. -->
  <button
    type="button"
    class="relative flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
    aria-label="Change layout"
    title="Change layout"
    @click="settings.cycleAlbumViewStyle()"
  >
    <MageIcon name="layout-grid" class="h-4 w-4" />
    <span
      class="absolute -bottom-0.5 -right-0.5 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-primary px-0.5 text-[9px] font-bold leading-none text-on-primary"
    >{{ settings.albumViewStyle === "list" ? "2" : settings.albumViewStyle === "tracks" ? "3" : "1" }}</span>
  </button>

  <!-- Shuffle-and-play-all sits right of the layout selection. -->
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

  <!-- Genre select in its own pill, styled exactly like the sort pill. -->
  <GenreFilterMenu />

  <!-- Sort select in its own pill group, last in the row. The tracks table
       keeps its own column sort in its toolbar below; this view-level sort
       (album/artist/year) stays visible in the top row in every layout. -->
  <div :class="PILL">
    <button
      type="button"
      :class="PILL_BTN"
      @click="sortMenuOpen = true"
    >
      Sort: {{ sortLabel }}
    </button>
  </div>

  <button
    type="button"
    class="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant"
    :aria-label="settings.sortAscending ? 'Sort descending' : 'Sort ascending'"
    @click="settings.setSortAscending(!settings.sortAscending)"
  >
    <MageIcon :name="settings.sortAscending ? 'arrow-up' : 'arrow-down'" class="h-4 w-4" />
  </button>
  </div>

  <!-- Sort options as a bottom sheet. -->
  <BottomSheet :open="sortMenuOpen" @close="sortMenuOpen = false">
    <div :class="SHEET_LABEL">Sort by</div>
    <button
      v-for="opt in SORT_OPTIONS"
      :key="opt.value"
      type="button"
      :class="SHEET_ROW"
      @click="selectSort(opt.value)"
    >
      <span class="flex-1">{{ opt.label }}</span>
      <MageIcon v-if="settings.sortMode === opt.value" name="check" class="h-5 w-5 text-primary" />
    </button>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import GenreFilterMenu from "./GenreFilterMenu.vue";
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

/** Shared pill styling so the sort and genre groups render identically. */
const PILL = "glass-field flex items-center gap-0.5 rounded-full p-0.5";
const PILL_BTN = "flex items-center gap-1 rounded-full px-2.5 py-1 text-label-lg text-on-surface";

const SHEET_ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";
const SHEET_LABEL = "px-6 pt-3 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";

// --- Sort bottom sheet -----------------------------------------------------

const sortMenuOpen = ref(false);

const sortLabel = computed(
  () => SORT_OPTIONS.find((o) => o.value === settings.sortMode)?.label ?? "Album",
);

function selectSort(mode: SortMode): void {
  settings.setSortMode(mode);
  sortMenuOpen.value = false;
}
</script>

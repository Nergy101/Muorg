<template>
  <!-- View-level library actions (sort, genre, layout, shuffle-all), shared by
       the library top bar (grid view / mobile) and the desktop tracks table
       toolbar, so both surfaces offer the same controls in one row. Menus
       teleport to body and open downward from their trigger. -->
  <!-- Layout switcher first, on the far left in both layouts. Same icon in
       both layouts — the pane-divided window reads as "layout" — with a small
       badge showing which layout is active: 1 = album covers, 2 = tracks. -->
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
    >{{ settings.albumViewStyle === "tracks" ? "2" : "1" }}</span>
  </button>

  <!-- Sort select in its own pill group. -->
  <div :class="PILL">
    <!-- In the tracks layout the table toolbar has its own column sort, so
         the view-level sort hides on desktop to avoid two "Sort:" buttons;
         mobile tracks keeps it (the flat list is sorted by it). -->
    <div ref="sortMenuRef" class="relative" :class="settings.albumViewStyle === 'tracks' ? 'lg:hidden' : ''">
      <button
        ref="sortBtnRef"
        type="button"
        :class="PILL_BTN"
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
  </div>

  <!-- Genre select in its own pill, styled exactly like the sort pill. -->
  <GenreFilterMenu />

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

// --- Sort dropdown ---------------------------------------------------------

const sortMenuOpen = ref(false);
const sortMenuRef = ref<HTMLElement | null>(null);
const sortBtnRef = ref<HTMLElement | null>(null);

const sortLabel = computed(
  () => SORT_OPTIONS.find((o) => o.value === settings.sortMode)?.label ?? "Album",
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

function onDocumentClick(e: MouseEvent): void {
  if (!sortMenuOpen.value) return;
  const target = e.target as Node;
  if (sortMenuRef.value && !sortMenuRef.value.contains(target)) {
    sortMenuOpen.value = false;
  }
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));
</script>

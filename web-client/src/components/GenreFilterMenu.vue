<template>
  <!-- Genre filter select in its own pill, styled like the sort pill. The
       options open as a bottom sheet, like the Android app. -->
  <div :class="PILL">
    <button
      type="button"
      :class="[PILL_BTN, 'max-w-28']"
      @click="genreMenuOpen = true"
    >
      <span class="truncate">Genre: {{ genreLabel }}</span>
      <MageIcon name="chevron-down" class="h-4 w-4 shrink-0" />
    </button>
  </div>

  <BottomSheet :open="genreMenuOpen" @close="genreMenuOpen = false">
    <div :class="SHEET_LABEL">Genre</div>
    <button type="button" :class="SHEET_ROW" @click="selectGenre(null)">
      <span class="flex-1">All genres</span>
      <MageIcon v-if="!lib.genreFilter" name="check" class="h-5 w-5 text-primary" />
    </button>
    <button
      v-for="g in lib.genres"
      :key="g.value"
      type="button"
      :class="SHEET_ROW"
      @click="selectGenre(g.value)"
    >
      <span class="flex-1 truncate">{{ g.label }}</span>
      <MageIcon v-if="lib.genreFilter === g.value" name="check" class="h-5 w-5 shrink-0 text-primary" />
    </button>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import { useLibraryStore } from "../stores/library";

/** Shared pill styling (same as the sort pill in LibraryViewActions). */
const PILL = "glass-field flex items-center gap-0.5 rounded-full p-0.5";
const PILL_BTN = "flex items-center gap-1 rounded-full px-2.5 py-1 text-label-lg text-on-surface";

const SHEET_ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";
const SHEET_LABEL = "px-6 pt-3 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";

const lib = useLibraryStore();

const genreMenuOpen = ref(false);

const genreLabel = computed(
  () => lib.genres.find((g) => g.value === lib.genreFilter)?.label ?? "All",
);

function selectGenre(value: string | null): void {
  lib.genreFilter = value;
  genreMenuOpen.value = false;
}
</script>

<template>
  <!-- Library top bar for the grid layout and mobile: sort, genre, layout
       toggle and shuffle-all. In the desktop tracks layout the table toolbar
       renders these same actions instead, so the library shows a single row.
       The album grid below is full-bleed, so in grid view the bar drops the
       centred content-col measure and spans the full width, left-aligned
       with the grid; the track list keeps the centred measure. -->
  <div
    class="flex shrink-0 flex-wrap items-center gap-1 border-b border-outline/10 px-4 py-2"
    :class="showTrackList ? 'content-col' : ''"
  >
    <LibraryViewActions />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import LibraryViewActions from "./LibraryViewActions.vue";
import { useLibraryStore } from "../stores/library";
import { useSettingsStore } from "../stores/settings";

const lib = useLibraryStore();
const settings = useSettingsStore();

/** The flat track list (search results, mobile tracks) keeps the centred
 *  900px measure on desktop; the album grid does not. */
const showTrackList = computed(
  () => lib.searchQuery.trim().length > 0 || settings.albumViewStyle === "tracks",
);
</script>

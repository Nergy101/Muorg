<template>
  <!-- Library top bar for the grid layout and mobile: sort, genre, layout
       toggle and shuffle-all. In the desktop tracks layout the table toolbar
       renders these same actions instead, so the library shows a single row. -->
  <div class="content-col flex shrink-0 flex-wrap items-center gap-1 border-b border-outline/10 px-4 py-2">
    <LibraryViewActions />
  </div>
</template>

<script setup lang="ts">
import LibraryViewActions from "./LibraryViewActions.vue";
</script>

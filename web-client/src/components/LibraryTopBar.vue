<template>
  <!-- Library top bar: sort, genre, layout toggle and shuffle-all. Always
       full-width and left-aligned so it lines up with the album grid and the
       tracks table toolbar below it. -->
  <div class="flex shrink-0 flex-wrap items-center gap-1 border-b border-outline/10 px-4 py-2">
    <LibraryViewActions />
  </div>
</template>

<script setup lang="ts">
import LibraryViewActions from "./LibraryViewActions.vue";
</script>

<template>
  <!-- Placeholder tiles while a home shelf loads: same grid and aspect-square
       size as the real AlbumCard/MixCard tiles, so the layout doesn't jump. -->
  <div class="grid gap-3 pb-4" :style="{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }">
    <div
      v-for="i in count"
      :key="i"
      class="aspect-square w-full animate-pulse rounded-xl bg-surface-variant"
      aria-hidden="true"
    />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  count: number;
  cols: number;
}>();
</script>

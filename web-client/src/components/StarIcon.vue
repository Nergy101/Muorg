<template>
  <!-- Mage's icon set has no star glyph; this five-point star mirrors the
       desktop app's rating widget shape so both clients read the same. -->
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    :fill="filled ? 'currentColor' : 'transparent'"
    aria-hidden="true"
  >
    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
  </svg>
</template>

<script setup lang="ts">
defineProps<{
  /** Solid fill instead of an outline — the active/hovered state. */
  filled?: boolean;
}>();
</script>

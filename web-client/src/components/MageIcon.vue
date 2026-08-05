<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  name: string;
  class?: string;
}>();

/**
 * Mage Icons (https://mageicons.com/, Apache-2.0), vendored as raw SVGs under
 * src/assets/mage-icons/. Eager glob so every icon the app uses ships in the
 * bundle and name → file resolution is a plain key lookup.
 */
const icons = import.meta.glob("../assets/mage-icons/*.svg", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

const svgContent = computed(() => {
  const raw = icons[`../assets/mage-icons/${props.name}.svg`];
  if (!raw) return "";
  // Mage ships stroke="black"; inherit the surrounding text colour instead.
  // The caller's classes land on the <svg> itself (they beat the 24×24
  // width/height attributes), same contract FeatherIcon had.
  return raw
    .replace("<svg", `<svg class="${props.class ?? ""}"`)
    .replace(/stroke="black"/g, 'stroke="currentColor"');
});
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center justify-center [&>svg]:block"
    role="img"
    aria-hidden="true"
    v-html="svgContent"
  />
</template>

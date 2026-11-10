<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  name: string;
  class?: string;
}>();

/**
 * Mage Icons (https://mageicons.com/, Apache-2.0), vendored as raw SVGs under
 * src/assets/mage-icons/. Eager glob so every icon the app uses ships in the
 * bundle and name → file resolution is a plain key lookup. Shared by the web
 * client and the desktop client so both render the same set.
 */
const icons = import.meta.glob("../assets/mage-icons/*.svg", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

const svgContent = computed(() => {
  const raw = icons[`../assets/mage-icons/${props.name}.svg`];
  if (!raw) return "";
  // Mage ships stroke="black" on outline glyphs and fill="black" on the
  // -fill variants; inherit the surrounding text colour for both. The root
  // <svg> carries fill="none", so only the black path fills are rewritten.
  // The caller's classes land on the <svg> itself (they beat the 24×24
  // width/height attributes).
  return raw
    .replace("<svg", `<svg class="${props.class ?? ""}"`)
    .replace(/stroke="black"/g, 'stroke="currentColor"')
    .replace(/fill="black"/g, 'fill="currentColor"');
});
</script>

<template>
  <!-- eslint-disable vue/no-v-html -- svgContent is built from vendored, trusted Mage SVGs only -->
  <span
    class="inline-flex shrink-0 items-center justify-center [&>svg]:block"
    role="img"
    aria-hidden="true"
    v-html="svgContent"
  />
  <!-- eslint-enable vue/no-v-html -->
</template>

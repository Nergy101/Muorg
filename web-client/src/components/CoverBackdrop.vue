<template>
  <!-- The dominant-colour backdrop shared by the maximized player and the
       album view: a darkened dominant colour as the base, then the cover
       stretched and blurred across the pane with masked edges that fade into
       the base. Paints its own background so the parent's theme background
       stays fully covered. -->
  <div
    class="pointer-events-none absolute inset-0 z-0 overflow-hidden"
    :style="baseStyle"
    aria-hidden="true"
  >
    <div
      v-if="coverUrl"
      class="edge-blur-art absolute"
      :style="{ backgroundImage: `url(${coverUrl})`, opacity: 0.9 }"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { isColorBland, useDominantColor } from "../composables/useDominantColor";

const props = withDefaults(
  defineProps<{
    coverUrl: string | null;
    /**
     * "player" (default) keeps a dark dominant-colour wash in the centre.
     * Both variants fade to the theme background (#1C1917) at the edges, so
     * the masked art blends into the sidebar/background instead of showing a
     * hard seam at the pane's left edge on desktop.
     */
    variant?: "player" | "album";
  }>(),
  { variant: "player" },
);

const coverUrlRef = computed(() => props.coverUrl);
const glowRgb = useDominantColor(coverUrlRef);

const baseStyle = computed(() => {
  // Both variants end at the theme background (#1C1917) at the edges, so the
  // masked art fades into the sidebar/background colour — on desktop the
  // player sheet sits beside the sidebar, and a near-black base there showed
  // as a hard vertical seam on its left edge. The player keeps a dark
  // dominant-colour wash in the centre; the album stays flat #1C1917.
  if (props.variant === "album") return { backgroundColor: "#1C1917" };
  const parts = glowRgb.value.split(",").map(Number);
  const dark =
    parts.length !== 3 || isColorBland(glowRgb.value)
      ? "#0c0a09"
      : `rgb(${Math.round(parts[0] * 0.08)},${Math.round(parts[1] * 0.08)},${Math.round(parts[2] * 0.08)})`;
  return {
    backgroundColor: "#1C1917",
    backgroundImage: `radial-gradient(closest-side at center, ${dark} 30%, #1C1917 100%)`,
  };
});
</script>

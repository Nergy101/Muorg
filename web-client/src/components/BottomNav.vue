<template>
  <!-- A tiny fixed 12px, not the safe-area inset: the full ~34px inset pushed the
       bar up off the edge and read as a dead gap. This lifts the icon row clear
       of the home indicator and its gesture strip while the background, which
       fills the padding box, still runs to the physical bottom of the screen. -->
  <nav class="shrink-0 border-t border-outline/30 bg-surface-container pb-3">
    <!-- Bar spans the shell, tabs stay grouped: four icons spread over 1200px
         would sit absurdly far apart. -->
    <div class="relative mx-auto flex h-16 w-full max-w-[600px] items-stretch">
      <!-- Sliding selection pill -->
      <div
        class="pointer-events-none absolute left-0 top-1/2 flex w-1/4 justify-center transition-transform duration-300"
        style="transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1)"
        :style="{ transform: `translateX(${activeIndex * 100}%) translateY(-50%)` }"
        aria-hidden="true"
      >
        <div class="h-10 w-14 rounded-full bg-primary/[0.18]" />
      </div>

      <RouterLink
        v-for="(tab, i) in TABS"
        :key="tab.name"
        :to="{ name: tab.name }"
        class="relative z-10 flex flex-1 items-center justify-center"
        :aria-label="tab.label"
        :aria-current="activeIndex === i ? 'page' : undefined"
        @click="onTabClick(tab.name)"
      >
        <FeatherIcon
          :name="tab.icon"
          class="h-6 w-6 transition-transform duration-200"
          :class="activeIndex === i ? 'scale-[1.15] text-primary' : 'text-on-surface-variant'"
        />
      </RouterLink>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { scrollToActiveSignal } from "../composables/useScrollSignal";

const TABS = [
  { name: "library", label: "Library", icon: "home" },
  { name: "playlists", label: "Playlists", icon: "list" },
  { name: "queue", label: "Queue", icon: "align-justify" },
  { name: "settings", label: "Settings", icon: "settings" },
] as const;

const route = useRoute();

/** Album and playlist detail keep their parent tab lit. */
const activeIndex = computed(() => {
  const name = String(route.name);
  if (name === "album") return 0;
  if (name === "playlist") return 1;
  const i = TABS.findIndex((t) => t.name === name);
  return i < 0 ? 0 : i;
});

function onTabClick(name: string): void {
  if (name === "library" && route.name === "library") scrollToActiveSignal.value++;
}
</script>

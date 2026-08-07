<template>
  <!-- A tiny fixed 12px, not the safe-area inset: the full ~34px inset pushed the
       bar up off the edge and read as a dead gap. This lifts the icon row clear
       of the home indicator and its gesture strip while the background, which
       fills the padding box, still runs to the physical bottom of the screen. -->
  <nav class="shrink-0 border-t border-outline/30 bg-surface-container pb-3">
    <!-- Bar spans the shell, tabs stay grouped: four icons spread over 1200px
         would sit absurdly far apart. -->
    <div class="relative mx-auto flex h-16 w-full max-w-[600px] items-stretch">
      <!-- Sliding selection pill. Hidden off-tab (the queue screen) rather than
           parked on Library, which would claim the wrong destination. -->
      <div
        v-if="activeIndex >= 0"
        class="pointer-events-none absolute left-0 top-1/2 flex justify-center transition-transform duration-300"
        :style="{
          width: `${100 / TABS.length}%`,
          transform: `translateX(${activeIndex * 100}%) translateY(-50%)`,
          transitionTimingFunction: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
        }"
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
        <MageIcon
          :name="activeIndex === i ? tab.iconActive : tab.icon"
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
import MageIcon from "./MageIcon.vue";
import { scrollToActiveSignal } from "../composables/useScrollSignal";
import { NAV_TABS as TABS, tabIndexForRoute } from "../nav-tabs";

const route = useRoute();

/** Album and playlist detail keep their parent tab lit; -1 when off-tab. */
const activeIndex = computed(() => tabIndexForRoute(String(route.name)));

function onTabClick(name: string): void {
  if (name === "library" && route.name === "library") scrollToActiveSignal.value++;
}
</script>

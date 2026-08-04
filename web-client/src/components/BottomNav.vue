<template>
  <nav
    class="relative flex h-16 shrink-0 items-stretch border-t border-outline/30 bg-surface-container pb-[env(safe-area-inset-bottom)]"
  >
    <!-- Sliding selection pill -->
    <div
      class="pointer-events-none absolute top-1.5 left-0 flex w-1/4 justify-center transition-transform duration-300"
      style="transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1)"
      :style="{ transform: `translateX(${activeIndex * 100}%)` }"
      aria-hidden="true"
    >
      <div class="h-10 w-14 rounded-full bg-primary/[0.18]" />
    </div>

    <RouterLink
      v-for="(tab, i) in TABS"
      :key="tab.name"
      :to="{ name: tab.name }"
      class="relative z-10 flex flex-1 flex-col items-center justify-center gap-0.5"
      :aria-current="activeIndex === i ? 'page' : undefined"
      @click="onTabClick(tab.name)"
    >
      <FeatherIcon
        :name="tab.icon"
        class="h-5 w-5 transition-transform duration-200"
        :class="activeIndex === i ? 'scale-[1.22] text-primary' : 'text-on-surface-variant'"
      />
      <span
        class="text-label-sm"
        :class="activeIndex === i ? 'text-primary' : 'text-on-surface-variant'"
      >{{ tab.label }}</span>
    </RouterLink>
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

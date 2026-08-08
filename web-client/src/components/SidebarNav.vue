<template>
  <!-- Desktop navigation rail (lg+). Mobile keeps the bottom nav; this aside is
       hidden below lg. Mirrors BottomNav's active-state vocabulary: the lit tab
       is the filled glyph on a translucent primary pill. -->
  <aside class="hidden w-60 shrink-0 flex-col border-r border-outline/20 bg-surface lg:flex">
    <!-- Brand -->
    <div class="flex h-14 shrink-0 items-center gap-2.5 px-5">
      <MageIcon name="compact-disk-fill" class="h-6 w-6 text-primary" />
      <span class="text-title-md font-bold tracking-tight text-on-surface">Muorg</span>
    </div>

    <!-- Tabs. Queue sits with them as a plain destination: it is not a tab
         (tabIndexForRoute returns -1 for it), so it lights on its own route. -->
    <nav class="flex flex-col gap-1 px-3 py-2">
      <RouterLink
        v-for="(tab, i) in TABS"
        :key="tab.name"
        :to="{ name: tab.name }"
        class="flex h-11 items-center gap-3 rounded-lg px-3 text-label-lg transition-colors"
        :class="activeIndex === i ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
        :aria-current="activeIndex === i ? 'page' : undefined"
        @click="onTabClick(tab.name)"
      >
        <MageIcon :name="activeIndex === i ? tab.iconActive : tab.icon" class="h-5 w-5 shrink-0" />
        <span class="font-medium">{{ tab.label }}</span>
      </RouterLink>

      <RouterLink
        :to="{ name: 'queue' }"
        class="flex h-11 items-center gap-3 rounded-lg px-3 text-label-lg transition-colors"
        :class="isQueue ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
        :aria-current="isQueue ? 'page' : undefined"
        @click="onQueueClick"
      >
        <MageIcon name="stack" class="h-5 w-5 shrink-0" />
        <span class="font-medium">Queue</span>
      </RouterLink>
    </nav>

    <div class="flex-1" />

    <p class="px-5 pb-4 text-label-sm text-on-surface-variant/70">Muorg Web</p>
  </aside>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "./MageIcon.vue";
import { scrollToActiveSignal } from "../composables/useScrollSignal";
import { NAV_TABS as TABS, tabIndexForRoute } from "../nav-tabs";

const route = useRoute();
const router = useRouter();

/** Album and playlist detail keep their parent tab lit; -1 when off-tab. */
const activeIndex = computed(() => tabIndexForRoute(String(route.name)));

const isQueue = computed(() => route.name === "queue" || route.name === "player-queue");

function onTabClick(name: string): void {
  if (name === "library" && route.name === "library") scrollToActiveSignal.value++;
}

/** Queue doubles as a toggle: already on a queue screen → back to the last one. */
function onQueueClick(e: MouseEvent): void {
  if (!isQueue.value) return;
  e.preventDefault();
  if (window.history.state?.back) router.back();
  else void router.push({ name: "home" });
}
</script>

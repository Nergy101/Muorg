<template>
  <!-- pt keeps the header/top bar clear of the notch and status bar. -->
  <div
    class="mx-auto flex h-full w-full max-w-[600px] flex-col overflow-hidden bg-background pt-[env(safe-area-inset-top)] md:max-w-[900px] lg:max-w-[2400px]"
  >
    <OfflineBanner />
    <UpdateBanner />

    <!-- Swipe pages between tabs, or pops the stack from the left edge. Bound
         here, not on the shell, so a drag on the bottom nav or mini player
         doesn't navigate. -->
    <div
      class="relative min-h-0 flex-1 [touch-action:pan-y_pinch-zoom]"
      @pointerdown="onPointerdown"
    >
      <RouterView v-slot="{ Component }">
        <Transition :name="navTransition">
          <!-- KeepAlive keeps each view's component state (filters, loaded
               data) while it is covered. Scroll position is NOT covered by it:
               a deactivated subtree is detached, which resets scrollTop, so
               views restore their own offset via useScrollMemory. -->
          <KeepAlive>
            <component :is="Component" />
          </KeepAlive>
        </Transition>
      </RouterView>

      <!-- Edge affordance: tracks an in-progress back gesture so the swipe is
           legible before it commits. -->
      <div
        v-if="backProgress > 0"
        class="pointer-events-none absolute inset-y-0 left-0 z-10 w-16 bg-gradient-to-r from-black/30 to-transparent"
        :style="{ opacity: backProgress }"
      />
    </div>

    <MiniPlayer v-if="showMiniPlayer" />
    <BottomNav v-if="showBottomNav" />
  </div>
  <Toast />
</template>

<script setup lang="ts">
import { computed, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import OfflineBanner from "./components/OfflineBanner.vue";
import UpdateBanner from "./components/UpdateBanner.vue";
import BottomNav from "./components/BottomNav.vue";
import MiniPlayer from "./components/MiniPlayer.vue";
import Toast from "./components/Toast.vue";
import { usePlayerStore } from "./stores/player";
import { useSettingsStore } from "./stores/settings";
import { useSwipeNavigate } from "./composables/useSwipeNavigate";
import { useSwipeBack } from "./composables/useSwipeBack";
import { navTransition } from "./composables/useNavTransition";
import { NAV_TABS } from "./nav-tabs";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
// Instantiating the store installs the data-theme effect.
useSettingsStore();

const BARELESS = ["connect", "player", "player-queue"];

const showMiniPlayer = computed(
  () =>
    (player.currentTrack != null || player.errorMessage != null) &&
    !BARELESS.includes(String(route.name)),
);
// The /queue page keeps both bars: it is reached from the mini player, so
// hiding the bar you tapped to get there — and the tabs with it — stranded the
// screen. The nav renders with no tab lit (tabIndexForRoute returns -1) rather
// than pretending the queue belongs to one. /player/queue is the opposite case:
// it is pushed from the maximized player, which has no bars either, so it stays
// full-bleed.
const showBottomNav = computed(() => !BARELESS.includes(String(route.name)));
// Transition name comes from the router hook, which compares stack depth so a
// forward move animates and a pop does not.

// --- Swipe between tabs ---
// Only pages from a tab itself: on album/playlist detail or the queue a
// sideways drag should stay a back gesture, not jump to another tab.
const swipeTabIndex = computed(() => NAV_TABS.findIndex((t) => t.name === String(route.name)));

function goToTab(index: number): void {
  const tab = NAV_TABS[index];
  // No wrap-around; swiping past either end is a no-op.
  if (tab) void router.push({ name: tab.name });
}

const swipe = useSwipeNavigate({
  enabled: () => swipeTabIndex.value >= 0,
  onNext: () => goToTab(swipeTabIndex.value + 1),
  onPrev: () => goToTab(swipeTabIndex.value - 1),
});

// --- Swipe back ---
// Anything deeper than a tab can be popped; the player is a sheet and has its
// own dismiss affordance.
const swipeBack = useSwipeBack({
  enabled: () => route.meta.depth !== "modal" && Number(route.meta.depth ?? 0) > 0,
  onCommit: () => router.back(),
});
// Top-level ref so the template unwraps it.
const backProgress = swipeBack.progress;

function onPointerdown(e: PointerEvent): void {
  // Back wins when the drag starts in the edge strip; the tab pager ignores
  // that strip anyway, so at most one of these ever arms.
  swipeBack.onPointerdown(e);
  swipe.onPointerdown(e);
}

watchEffect(() => {
  const t = player.currentTrack;
  if (t) {
    const artist = t.artist ?? t.album_artist;
    document.title = artist ? `${t.title} - ${artist} | Muorg` : `${t.title} | Muorg`;
  } else {
    document.title = "Muorg Web";
  }
});
</script>

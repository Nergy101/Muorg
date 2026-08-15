<template>
  <!-- pt keeps the header/top bar clear of the notch and status bar. On desktop
       the layout widens into a sidebar + content pane (lg). -->
  <div
    class="flex h-full w-full max-w-[600px] flex-col overflow-hidden pt-[env(safe-area-inset-top)] md:max-w-[900px] lg:max-w-none lg:flex-row lg:pt-0"
  >
    <!-- Desktop navigation rail; mobile keeps the bottom nav. -->
    <SidebarNav v-if="showSidebar" />

    <!-- Main column: banners, routed views, mini player, bottom nav. -->
    <div class="flex min-h-0 flex-1 flex-col">
      <OfflineBanner />
      <UpdateBanner />

      <!-- Swipe pages between tabs, or pops the stack from the left edge. Bound
           here, not on the shell, so a drag on the bottom nav or mini player
           doesn't navigate. The mini player + bottom nav overlay this area so
           content scrolls behind them and shows through the frosted glass. -->
      <div class="relative min-h-0 flex-1">
        <div
          ref="scrollerHost"
          class="absolute inset-0 [touch-action:pan-y_pinch-zoom]"
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

        <!-- Mobile: one frosted island holding the mini player above the tabs.
             Desktop (lg): the bottom nav is hidden and the mini player floats as
             its own bar at the bottom. -->
        <div
          v-if="showMiniPlayer || showBottomNav"
          ref="mobileBarEl"
          class="glass-deep absolute inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[600px] overflow-hidden rounded-t-3xl md:max-w-[900px] lg:hidden"
        >
          <MiniPlayer v-if="showMiniPlayer" />
          <!-- The library search/filter bar lives below the mini player and
               collapses on scroll-down like the tab row, leaving the mini
               player pinned. Only shown on the library route. -->
          <div
            data-collapsible
            class="overflow-hidden transition-[max-height] duration-300 ease-out"
            :class="navHidden && route.name === 'library' ? 'max-h-0' : 'max-h-52'"
          >
            <LibrarySearchBar v-if="showLibrarySearch" />
          </div>
          <!-- The tab row collapses away on scroll-down and returns on
               scroll-up, leaving the mini player pinned. -->
          <div
            data-collapsible
            class="overflow-hidden transition-[max-height] duration-300 ease-out"
            :class="navHidden && showBottomNav ? 'max-h-0' : 'max-h-20'"
          >
            <BottomNav v-if="showBottomNav" />
          </div>
          <div class="h-[max(env(safe-area-inset-bottom),0.75rem)] shrink-0" />
        </div>

        <!-- Desktop-only floating frosted bar (bottom nav hidden at lg): the
             library search/filter sits above the mini player. Wrapped so it
             gets its own glass + positioning. -->
        <div
          v-if="showMiniPlayer || showLibrarySearch"
          ref="desktopBarEl"
          class="glass-deep absolute inset-x-0 bottom-4 z-20 mx-auto hidden w-[calc(100%-2rem)] max-w-none overflow-hidden rounded-2xl lg:block"
        >
          <LibrarySearchBar v-if="showLibrarySearch" />
          <MiniPlayer v-if="showMiniPlayer" />
        </div>
      </div>
    </div>
  </div>
  <Toast />
</template>

<script setup lang="ts">
import { computed, ref, watch, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import OfflineBanner from "./components/OfflineBanner.vue";
import UpdateBanner from "./components/UpdateBanner.vue";
import BottomNav from "./components/BottomNav.vue";
import SidebarNav from "./components/SidebarNav.vue";
import MiniPlayer from "./components/MiniPlayer.vue";
import LibrarySearchBar from "./components/LibrarySearchBar.vue";
import Toast from "./components/Toast.vue";
import { usePlayerStore } from "./stores/player";
import { useSettingsStore } from "./stores/settings";
import { useSwipeNavigate } from "./composables/useSwipeNavigate";
import { useSwipeBack } from "./composables/useSwipeBack";
import { useKeyboardShortcuts } from "./composables/useKeyboardShortcuts";
import { useScrollNavHide } from "./composables/useScrollNavHide";
import { useBottomInset } from "./composables/useBottomInset";
import { navTransition } from "./composables/useNavTransition";
import { NAV_TABS } from "./nav-tabs";
import { loadPref, savePref } from "./stores/prefs";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
// Instantiating the store installs the data-theme effect.
const settings = useSettingsStore();
// Desktop transport keys (Space/K, ←/→, J/L, Ctrl+←/→, M).
useKeyboardShortcuts();

// The scroller host is the container that every view's inner scroller lives
// inside; scroll events bubble to it, which drives the bottom-nav auto-hide.
const scrollerHost = ref<HTMLElement | null>(null);
const navHidden = useScrollNavHide(scrollerHost);

const BARELESS = ["connect", "player", "player-queue"];

// The connect screen is a full-pane onboarding flow — no nav rail beside it.
// Everything else (including the player sheet) keeps the rail on desktop so
// navigation stays one click away; mobile hides the rail via CSS regardless.
const showSidebar = computed(() => String(route.name) !== "connect");

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
// The library search/filter bar is part of the bottom shell — only on the
// library route, where it replaces the old sticky top toolbar.
const showLibrarySearch = computed(() => String(route.name) === "library");

// The island overlays the routed views, so its measured height is published as
// `--bottom-inset` and every scroller pads by it. Re-measured whenever the rows
// it holds change.
const mobileBarEl = ref<HTMLElement | null>(null);
const desktopBarEl = ref<HTMLElement | null>(null);
useBottomInset(mobileBarEl, desktopBarEl, () => [
  showMiniPlayer.value,
  showBottomNav.value,
  showLibrarySearch.value,
]);
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

// --- "Open on last tab" (Settings → Layout) --------------------------------
// Remember which tab the user last stood on, and restore it after a reload —
// unless the load is a deep link (then the URL wins) or the setting is off.
const TAB_NAMES = new Set<string>(NAV_TABS.map((t) => t.name));

watch(
  () => route.name,
  (name) => {
    if (typeof name === "string" && TAB_NAMES.has(name)) savePref("muorg-web-last-tab", name);
  },
);

if (settings.openOnLastTab) {
  // Read the saved tab synchronously at setup: the "/" → home redirect below
  // writes "home" as the last tab before the initial navigation settles, so
  // reading inside isReady() would always see "home" and never restore.
  const savedLastTab = loadPref("muorg-web-last-tab", "") as string;
  void router.isReady().then(() => {
    // Only a bare boot (the "/" → home redirect) gets redirected: a deep link
    // in the URL wins, so a shared album link isn't clobbered by the restore.
    const current = String(router.currentRoute.value.name);
    if (current !== "home") return;
    const tab = NAV_TABS.find((t) => t.name === savedLastTab);
    if (tab && tab.name !== current) {
      void router.replace({ name: tab.name });
    }
  });
}
</script>

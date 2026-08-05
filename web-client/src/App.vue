<template>
  <!-- pt keeps the header/top bar clear of the notch and status bar. -->
  <div
    class="mx-auto flex h-full w-full max-w-[600px] flex-col overflow-hidden bg-background pt-[env(safe-area-inset-top)] md:max-w-[900px] lg:max-w-[2400px]"
  >
    <OfflineBanner />

    <!-- Swipe pages between tabs. Bound here, not on the shell, so a drag on the
         bottom nav or mini player doesn't navigate. -->
    <div class="relative min-h-0 flex-1" @pointerdown="swipe.onPointerdown">
      <RouterView v-slot="{ Component }">
        <Transition :name="transitionName">
          <!-- KeepAlive: tab/detail views stay mounted while covered, so the
               library (and every other screen) keeps its scroll position and
               state when you navigate back instead of remounting at the top. -->
          <KeepAlive>
            <component :is="Component" />
          </KeepAlive>
        </Transition>
      </RouterView>
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
import BottomNav from "./components/BottomNav.vue";
import MiniPlayer from "./components/MiniPlayer.vue";
import Toast from "./components/Toast.vue";
import { usePlayerStore } from "./stores/player";
import { useSettingsStore } from "./stores/settings";
import { useSwipeNavigate } from "./composables/useSwipeNavigate";
import { NAV_TABS } from "./nav-tabs";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
// Instantiating the store installs the data-theme effect.
useSettingsStore();

const showMiniPlayer = computed(
  () => (player.currentTrack != null || player.errorMessage != null) && route.name !== "player",
);
const showBottomNav = computed(() => !["connect", "player"].includes(String(route.name)));
const transitionName = computed(() => (route.name === "player" ? "slide-up" : ""));

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

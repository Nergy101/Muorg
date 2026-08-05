<template>
  <!-- pt keeps the header/top bar clear of the notch and status bar. -->
  <div
    class="mx-auto flex h-full w-full max-w-[600px] flex-col overflow-hidden bg-background pt-[env(safe-area-inset-top)] md:max-w-[900px] lg:max-w-[1200px]"
  >
    <OfflineBanner />

    <div class="relative min-h-0 flex-1">
      <RouterView v-slot="{ Component }">
        <Transition :name="transitionName">
          <component :is="Component" />
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
import { useRoute } from "vue-router";
import OfflineBanner from "./components/OfflineBanner.vue";
import BottomNav from "./components/BottomNav.vue";
import MiniPlayer from "./components/MiniPlayer.vue";
import Toast from "./components/Toast.vue";
import { usePlayerStore } from "./stores/player";
import { useSettingsStore } from "./stores/settings";

const route = useRoute();
const player = usePlayerStore();
// Instantiating the store installs the data-theme effect.
useSettingsStore();

const showMiniPlayer = computed(
  () => (player.currentTrack != null || player.errorMessage != null) && route.name !== "player",
);
const showBottomNav = computed(() => !["connect", "player"].includes(String(route.name)));
const transitionName = computed(() => (route.name === "player" ? "slide-up" : ""));

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

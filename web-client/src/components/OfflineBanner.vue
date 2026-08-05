<template>
  <Transition
    enter-active-class="sheet-motion"
    enter-from-class="-translate-y-full"
    leave-active-class="sheet-motion"
    leave-to-class="-translate-y-full"
  >
    <div
      v-if="!online"
      class="relative z-50 flex items-center justify-center gap-2 bg-amber-700/90 px-4 py-1.5 text-xs text-white shadow-lg backdrop-blur-sm"
    >
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
        <line x1="1" y1="1" x2="23" y2="23" /><path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55" /><path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39" /><path d="M10.71 5.05A16 16 0 0 1 22.56 9" /><path d="M1.42 9a15.91 15.91 0 0 1 4.7-2.88" /><path d="M8.53 16.11a6 6 0 0 1 6.95 0" /><line x1="12" y1="20" x2="12.01" y2="20" />
      </svg>
      <span>You are offline. Cached content is still available.</span>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";

const online = ref(navigator.onLine);

function onOnline(): void {
  online.value = true;
}

function onOffline(): void {
  online.value = false;
}

onMounted(() => {
  window.addEventListener("online", onOnline);
  window.addEventListener("offline", onOffline);
});

onUnmounted(() => {
  window.removeEventListener("online", onOnline);
  window.removeEventListener("offline", onOffline);
});
</script>

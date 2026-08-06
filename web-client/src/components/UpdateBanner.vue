<template>
  <Transition
    enter-active-class="sheet-motion"
    enter-from-class="-translate-y-full"
    leave-active-class="sheet-motion"
    leave-to-class="-translate-y-full"
  >
    <div
      v-if="pwaUpdateAvailable"
      class="relative z-50 flex items-center justify-center gap-3 bg-primary px-4 py-2 text-xs text-on-primary shadow-lg"
    >
      <span>A new version of Muorg is available.</span>
      <button
        type="button"
        class="flex items-center gap-1 rounded-full bg-white/20 px-3 py-1 font-semibold"
        @click="refreshApp"
      >
        <MageIcon name="reload" class="h-3.5 w-3.5" />
        Refresh now
      </button>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import MageIcon from "./MageIcon.vue";
import { pwaUpdateAvailable, refreshApp } from "../composables/usePwaUpdate";
</script>

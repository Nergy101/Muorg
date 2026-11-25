<script setup lang="ts">
import { toastMessage } from "../../state/toast";
</script>

<template>
  <Teleport to="body">
    <Transition name="toast">
      <div
        v-if="toastMessage"
        class="fixed bottom-24 left-1/2 z-[600] -translate-x-1/2 rounded-full bg-surface px-4 py-2 text-[13px] font-medium text-on-surface shadow-xl ring-1 ring-outline/30"
        role="status"
        aria-live="polite"
      >
        {{ toastMessage }}
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}
</style>

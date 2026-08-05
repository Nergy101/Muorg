<template>
  <Teleport to="body">
    <Transition
      enter-active-class="sheet-motion"
      enter-from-class="translate-y-2 opacity-0"
      leave-active-class="sheet-motion"
      leave-to-class="translate-y-2 opacity-0"
    >
      <div
        v-if="message"
        class="pointer-events-none fixed inset-x-0 z-[60] flex justify-center px-6"
        :style="{ bottom: bottomOffset }"
        role="status"
      >
        <div
          class="max-w-full truncate rounded-full bg-surface-container px-4 py-2 text-body-md text-on-surface shadow-xl"
        >{{ message }}</div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { useToast } from "../composables/useToast";
import { usePlayerStore } from "../stores/player";

const { message } = useToast();
const route = useRoute();
const player = usePlayerStore();

// Sit above whatever chrome is currently on screen.
const bottomOffset = computed(() => {
  const name = String(route.name);
  if (name === "player" || name === "connect") return "calc(env(safe-area-inset-bottom) + 1.5rem)";
  const hasMini = player.currentTrack != null || player.errorMessage != null;
  return `calc(env(safe-area-inset-bottom) + ${hasMini ? "9.5rem" : "5rem"})`;
});
</script>

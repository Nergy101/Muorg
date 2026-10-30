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
          class="glass-strong flex max-w-full items-center gap-3 rounded-full px-4 py-2 text-body-md text-on-surface shadow-xl"
        >
          <span class="truncate">{{ message }}</span>
          <button
            v-if="action"
            type="button"
            class="pointer-events-auto shrink-0 font-semibold text-primary"
            @click="runToastAction"
          >{{ action.label }}</button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { runToastAction, useToast } from "../composables/useToast";
import { usePlayerStore } from "../stores/player";

const { message, action } = useToast();
const route = useRoute();
const player = usePlayerStore();

// Sit above whatever chrome is currently on screen.
const bottomOffset = computed(() => {
  const name = String(route.name);
  // Bareless routes (see App.vue): nothing below the toast but the safe area.
  if (name === "player" || name === "player-queue" || name === "connect") {
    return "calc(env(safe-area-inset-bottom) + 1.5rem)";
  }
  const hasMini = player.currentTrack != null || player.errorMessage != null;
  return `calc(env(safe-area-inset-bottom) + ${hasMini ? "9.5rem" : "5rem"})`;
});
</script>

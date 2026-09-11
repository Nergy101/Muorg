<script setup lang="ts">
/**
 * The body-teleported tooltip that pairs with `useTooltipPopover`. Pass the
 * composable's `tooltip` ref and its `styleFor`; the mouse handlers keep a
 * hovered tooltip alive.
 */
import type { TooltipState } from "../../composables/useTooltipPopover";

defineProps<{
  state: TooltipState | null;
  styleFor: (state: TooltipState) => Record<string, string>;
  /** Above modals (250) and below nothing else. */
  zIndex?: number;
}>();

const emit = defineEmits<{ (e: "enter"): void; (e: "leave"): void }>();
</script>

<template>
  <Teleport to="body">
    <div
      v-if="state"
      class="fixed whitespace-pre-line rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
      :style="{ ...styleFor(state), zIndex: String(zIndex ?? 200) }"
      @mouseenter="emit('enter')"
      @mouseleave="emit('leave')"
    >
      {{ state.text }}
    </div>
  </Teleport>
</template>

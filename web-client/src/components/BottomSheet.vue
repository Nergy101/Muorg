<template>
  <Teleport to="body">
    <Transition
      enter-active-class="sheet-motion"
      enter-from-class="opacity-0"
      leave-active-class="sheet-motion"
      leave-to-class="opacity-0"
    >
      <div v-if="open" class="fixed inset-0 z-50 bg-black/50" @click="emit('close')" />
    </Transition>

    <Transition
      enter-active-class="sheet-motion"
      enter-from-class="translate-y-full"
      leave-active-class="sheet-motion"
      leave-to-class="translate-y-full"
    >
      <div
        v-if="open"
        ref="panel"
        class="fixed inset-x-0 bottom-0 z-50 mx-auto max-h-[85vh] max-w-[600px] overflow-y-auto rounded-t-[28px] bg-surface pb-[env(safe-area-inset-bottom)] shadow-2xl"
        :style="dragStyle"
        role="dialog"
        aria-modal="true"
        @pointerdown="onPointerdown"
      >
        <div class="sticky top-0 flex justify-center bg-surface py-3">
          <div class="h-1 w-8 rounded-full bg-on-surface-variant/40" />
        </div>
        <slot />
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

/** Release past this fraction of the panel height dismisses it. */
const DISMISS_FRACTION = 0.25;

defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const panel = ref<HTMLElement | null>(null);
const dragY = ref(0);
const dragging = ref(false);

let startY = 0;
let height = 1;
let pointerId: number | null = null;
let fromScrollTop = 0;

const dragStyle = computed(() =>
  dragY.value > 0
    ? { transform: `translateY(${dragY.value}px)`, transition: dragging.value ? "none" : undefined }
    : undefined,
);

function cleanup(): void {
  pointerId = null;
  dragging.value = false;
  window.removeEventListener("pointermove", onMove);
  window.removeEventListener("pointerup", onUp);
  window.removeEventListener("pointercancel", onUp);
}

function onMove(e: PointerEvent): void {
  if (e.pointerId !== pointerId) return;
  const dy = e.clientY - startY;
  if (!dragging.value) {
    // Only start dragging on a downward pull from an un-scrolled panel.
    if (dy < 8 || fromScrollTop > 0) {
      if (dy < -8 || fromScrollTop > 0) cleanup();
      return;
    }
    dragging.value = true;
  }
  e.preventDefault();
  dragY.value = Math.max(0, dy);
}

function onUp(e: PointerEvent): void {
  if (e.pointerId !== pointerId) return;
  const dismissed = dragY.value >= height * DISMISS_FRACTION;
  cleanup();
  dragY.value = 0;
  if (dismissed) emit("close");
}

function onPointerdown(e: PointerEvent): void {
  cleanup();
  pointerId = e.pointerId;
  startY = e.clientY;
  height = panel.value?.getBoundingClientRect().height ?? 1;
  fromScrollTop = panel.value?.scrollTop ?? 0;
  window.addEventListener("pointermove", onMove, { passive: false });
  window.addEventListener("pointerup", onUp);
  window.addEventListener("pointercancel", onUp);
}
</script>

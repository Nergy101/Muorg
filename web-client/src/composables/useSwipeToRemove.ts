import { ref } from "vue";
import type { Ref } from "vue";

/** Fraction of the row width that must be travelled to commit the removal. */
const DISMISS_FRACTION = 0.4;

export interface SwipeToRemoveState {
  /** Current horizontal offset, always <= 0 (leftwards only). */
  offsetX: Ref<number>;
  swiping: Ref<boolean>;
  onPointerdown: (e: PointerEvent) => void;
}

/**
 * Left-only swipe to dismiss, matching the Android list where
 * enableDismissFromStartToEnd is false. Vertical intent releases the gesture
 * so the list can still scroll.
 */
export function useSwipeToRemove(onRemove: () => void): SwipeToRemoveState {
  const offsetX = ref(0);
  const swiping = ref(false);

  let startX = 0;
  let startY = 0;
  let width = 1;
  let pointerId: number | null = null;

  function cleanup(): void {
    pointerId = null;
    swiping.value = false;
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    window.removeEventListener("pointercancel", onCancel);
  }

  function onMove(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;
    if (!swiping.value) {
      if (Math.abs(dy) > Math.abs(dx)) {
        // Vertical scroll wins.
        offsetX.value = 0;
        cleanup();
        return;
      }
      if (Math.abs(dx) < 6) return;
      swiping.value = true;
    }
    e.preventDefault();
    offsetX.value = Math.min(0, dx);
  }

  function onUp(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const committed = -offsetX.value >= width * DISMISS_FRACTION;
    cleanup();
    if (committed) {
      offsetX.value = -width;
      onRemove();
    } else {
      offsetX.value = 0;
    }
  }

  function onCancel(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    offsetX.value = 0;
    cleanup();
  }

  function onPointerdown(e: PointerEvent): void {
    cleanup();
    pointerId = e.pointerId;
    startX = e.clientX;
    startY = e.clientY;
    width = (e.currentTarget as HTMLElement).getBoundingClientRect().width || 1;
    window.addEventListener("pointermove", onMove, { passive: false });
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onCancel);
  }

  return { offsetX, swiping, onPointerdown };
}

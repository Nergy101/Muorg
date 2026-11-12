import { ref } from "vue";
import type { Ref } from "vue";

/** Android's estimatedItemHeightPx for both reorderable lists. */
export const REORDER_ROW_HEIGHT = 56;

const LONG_PRESS_MS = 500;
/** Auto-scroll tuning: pointer within EDGE px of the container's top/bottom
 *  scrolls by STEP px every TICK ms while the drag is armed. */
const AUTO_SCROLL_EDGE = 56;
const AUTO_SCROLL_STEP = 16;
const AUTO_SCROLL_TICK_MS = 50;

export interface DragReorderOptions {
  itemCount: () => number;
  /** Uniform row height in px. */
  rowHeight: number;
  /** `from`/`to` are positions within the list; `offsetY` is the final pointer
   *  delta (px) at release, so callers can detect cross-list drop zones. */
  onCommit: (from: number, to: number, offsetY?: number) => void;
  /** true = press and drag straight away; false = long-press to arm first. */
  immediate?: boolean;
  /** Optional scroller that auto-scrolls while the pointer rests near its
   *  top/bottom edge mid-drag. The scroll delta also feeds the drop index, so
   *  the dragged row can be placed beyond the currently visible area. */
  autoScrollContainer?: () => HTMLElement | null;
}

export interface DragReorderState {
  draggingIndex: Ref<number | null>;
  dropIndex: Ref<number | null>;
  offsetY: Ref<number>;
  start: (index: number, e: PointerEvent) => void;
}

/**
 * Pointer-driven reorder over a uniform-height list. Accumulates the pointer
 * delta and converts it to whole rows moved, mirroring the Compose lists.
 */
export function useDragReorder(opts: DragReorderOptions): DragReorderState {
  const draggingIndex = ref<number | null>(null);
  const dropIndex = ref<number | null>(null);
  const offsetY = ref(0);

  let armTimer: ReturnType<typeof setTimeout> | undefined;
  let autoScrollTimer: ReturnType<typeof setInterval> | undefined;
  let startY = 0;
  let pointerId: number | null = null;
  /** Last seen pointer Y and the accumulated auto-scroll compensation, so the
   *  drop index keeps advancing while the list scrolls under a held pointer. */
  let lastPointerY = 0;
  let scrollDelta = 0;

  function stopAutoScroll(): void {
    if (autoScrollTimer !== undefined) {
      clearInterval(autoScrollTimer);
      autoScrollTimer = undefined;
    }
  }

  function beginAutoScroll(): void {
    stopAutoScroll();
    const container = opts.autoScrollContainer?.();
    if (!container) return;
    autoScrollTimer = setInterval(() => {
      const from = draggingIndex.value;
      if (from === null) return;
      const rect = container.getBoundingClientRect();
      let step = 0;
      if (lastPointerY < rect.top + AUTO_SCROLL_EDGE) step = -AUTO_SCROLL_STEP;
      else if (lastPointerY > rect.bottom - AUTO_SCROLL_EDGE) step = AUTO_SCROLL_STEP;
      if (step === 0) return;
      container.scrollBy({ top: step });
      // Treat the scroll as virtual pointer movement so the dragged row can be
      // placed past the visible edge instead of clamping at the pointer.
      scrollDelta += step;
      offsetY.value = lastPointerY - startY + scrollDelta;
      dropIndex.value = rowsMoved(from);
    }, AUTO_SCROLL_TICK_MS);
  }

  /** Clamped destination row for a drag that started at `from`. */
  function rowsMoved(from: number): number {
    const max = opts.itemCount() - 1;
    const delta = Math.round(offsetY.value / opts.rowHeight);
    return Math.max(0, Math.min(max, from + delta));
  }

  function onMove(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const from = draggingIndex.value;
    if (from === null) {
      // Still waiting for the long-press to arm: a real move cancels it.
      if (Math.abs(e.clientY - startY) > 8) cleanup();
      return;
    }
    e.preventDefault();
    lastPointerY = e.clientY;
    offsetY.value = e.clientY - startY + scrollDelta;
    dropIndex.value = rowsMoved(from);
  }

  function onUp(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const from = draggingIndex.value;
    const to = dropIndex.value;
    const finalOffset = offsetY.value;
    cleanup();
    // Always report the drop (even when from === to) so callers can detect
    // cross-list drops like Up next → Your queue, where the dragged row is
    // already at the edge of its own list and dropIndex clamps to it. Within-list
    // reorder handlers no-op on from === to themselves.
    if (from !== null && to !== null) opts.onCommit(from, to, finalOffset);
  }

  function cleanup(): void {
    clearTimeout(armTimer);
    armTimer = undefined;
    stopAutoScroll();
    draggingIndex.value = null;
    dropIndex.value = null;
    offsetY.value = 0;
    pointerId = null;
    scrollDelta = 0;
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    window.removeEventListener("pointercancel", onUp);
  }

  function start(index: number, e: PointerEvent): void {
    cleanup();
    pointerId = e.pointerId;
    startY = e.clientY;
    lastPointerY = e.clientY;
    window.addEventListener("pointermove", onMove, { passive: false });
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);

    // Mouse drags arm straight away (a natural click-and-drag); touch/pen
    // still long-press to arm, so both gestures work on every viewport.
    if (opts.immediate || e.pointerType === "mouse") {
      e.preventDefault();
      draggingIndex.value = index;
      dropIndex.value = index;
      beginAutoScroll();
      return;
    }
    armTimer = setTimeout(() => {
      armTimer = undefined;
      navigator.vibrate?.(20);
      draggingIndex.value = index;
      dropIndex.value = index;
      beginAutoScroll();
    }, LONG_PRESS_MS);
  }

  return { draggingIndex, dropIndex, offsetY, start };
}

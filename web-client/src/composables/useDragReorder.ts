import { ref } from "vue";
import type { Ref } from "vue";

/** Android's estimatedItemHeightPx for both reorderable lists. */
export const REORDER_ROW_HEIGHT = 56;

const LONG_PRESS_MS = 500;

export interface DragReorderOptions {
  itemCount: () => number;
  /** Uniform row height in px. */
  rowHeight: number;
  onCommit: (from: number, to: number) => void;
  /** true = press and drag straight away; false = long-press to arm first. */
  immediate?: boolean;
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
  let startY = 0;
  let pointerId: number | null = null;

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
    offsetY.value = e.clientY - startY;
    dropIndex.value = rowsMoved(from);
  }

  function onUp(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const from = draggingIndex.value;
    const to = dropIndex.value;
    cleanup();
    if (from !== null && to !== null && from !== to) opts.onCommit(from, to);
  }

  function cleanup(): void {
    clearTimeout(armTimer);
    armTimer = undefined;
    draggingIndex.value = null;
    dropIndex.value = null;
    offsetY.value = 0;
    pointerId = null;
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    window.removeEventListener("pointercancel", onUp);
  }

  function start(index: number, e: PointerEvent): void {
    cleanup();
    pointerId = e.pointerId;
    startY = e.clientY;
    window.addEventListener("pointermove", onMove, { passive: false });
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);

    if (opts.immediate) {
      draggingIndex.value = index;
      dropIndex.value = index;
      return;
    }
    armTimer = setTimeout(() => {
      armTimer = undefined;
      navigator.vibrate?.(20);
      draggingIndex.value = index;
      dropIndex.value = index;
    }, LONG_PRESS_MS);
  }

  return { draggingIndex, dropIndex, offsetY, start };
}

/** Horizontal travel before the gesture commits to paging. */
const COMMIT_PX = 60;
/** Travel before we decide the gesture is horizontal at all. */
const AXIS_LOCK_PX = 10;
/** Left/right strip left to the OS back/forward swipe. */
const EDGE_GUARD_PX = 24;
/** A swipe ending on empty space fires no click; don't leave the trap armed. */
const CLICK_TRAP_MS = 350;

export interface SwipeNavigateHandlers {
  onPointerdown: (e: PointerEvent) => void;
}

/**
 * Horizontal swipe to page between the bottom-nav tabs.
 *
 * Follows useSwipeToRemove's axis-intent handling: vertical movement releases
 * the gesture so lists still scroll. Never calls preventDefault, so scrolling
 * stays on the compositor; the committed swipe instead swallows the click that
 * would otherwise land on whatever it was dragged over.
 */
export function useSwipeNavigate(opts: {
  /** Checked at pointerdown; false on screens that are not tabs. */
  enabled: () => boolean;
  onPrev: () => void;
  onNext: () => void;
}): SwipeNavigateHandlers {
  let pointerId: number | null = null;
  let startX = 0;
  let startY = 0;
  let locked = false;

  function cleanup(): void {
    pointerId = null;
    locked = false;
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    window.removeEventListener("pointercancel", onCancel);
  }

  function suppressNextClick(): void {
    const swallow = (e: MouseEvent): void => {
      e.preventDefault();
      e.stopPropagation();
    };
    window.addEventListener("click", swallow, { capture: true, once: true });
    setTimeout(() => window.removeEventListener("click", swallow, { capture: true }), CLICK_TRAP_MS);
  }

  function onMove(e: PointerEvent): void {
    if (e.pointerId !== pointerId || locked) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;
    if (Math.abs(dy) > Math.abs(dx)) {
      // Vertical scroll wins outright.
      cleanup();
      return;
    }
    if (Math.abs(dx) >= AXIS_LOCK_PX) locked = true;
  }

  function onUp(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const dx = e.clientX - startX;
    const committed = locked && Math.abs(dx) >= COMMIT_PX;
    cleanup();
    if (!committed) return;
    suppressNextClick();
    if (dx < 0) opts.onNext();
    else opts.onPrev();
  }

  function onCancel(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    cleanup();
  }

  function onPointerdown(e: PointerEvent): void {
    cleanup();
    if (!opts.enabled()) return;
    // A mouse drag paging the app would fight text selection.
    if (e.pointerType === "mouse") return;
    if (e.clientX < EDGE_GUARD_PX || e.clientX > window.innerWidth - EDGE_GUARD_PX) return;
    pointerId = e.pointerId;
    startX = e.clientX;
    startY = e.clientY;
    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onCancel);
  }

  return { onPointerdown };
}

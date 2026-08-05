import { ref, type Ref } from "vue";

/** Only a drag starting inside this strip counts as a back gesture. */
const EDGE_ZONE_PX = 32;
/** Travel before the gesture is considered horizontal at all. */
const AXIS_LOCK_PX = 10;
/** Fraction of the viewport that commits the pop on release. */
const COMMIT_FRACTION = 0.3;
/** A fast flick commits regardless of distance (px per ms). */
const FLICK_VELOCITY = 0.5;

export interface SwipeBackHandlers {
  onPointerdown: (e: PointerEvent) => void;
  /** 0..1 travel of an in-progress gesture, for the edge affordance. */
  progress: Ref<number>;
}

/**
 * Edge swipe that pops the navigation stack.
 *
 * The gesture commits the pop and lets the normal `nav-pop` transition play,
 * rather than transforming the current view under the finger. That is a
 * deliberate limit: the shell keeps exactly one routed view mounted (a single
 * `<KeepAlive>` slot), so the screen being returned to does not exist in the DOM
 * mid-gesture — a finger-following card would slide over empty background
 * instead of revealing the previous screen. Committing lets Vue mount it and
 * animate the reveal properly. `progress` drives an edge affordance so the
 * gesture is still legible while undecided.
 *
 * Only active in an installed PWA: in a browser tab the platform's own
 * back-swipe already handles this, and handling both would pop twice.
 */
export function useSwipeBack(opts: {
  /** Checked at pointerdown; false when there is nothing to pop. */
  enabled: () => boolean;
  onCommit: () => void;
}): SwipeBackHandlers {
  const progress = ref(0);

  let pointerId: number | null = null;
  let startX = 0;
  let startY = 0;
  let startedAt = 0;
  let locked = false;

  function cleanup(): void {
    pointerId = null;
    locked = false;
    progress.value = 0;
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    window.removeEventListener("pointercancel", onCancel);
  }

  function onMove(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;

    if (!locked) {
      // A vertical intent is a list scroll; drop the gesture entirely.
      if (Math.abs(dy) > Math.abs(dx)) {
        cleanup();
        return;
      }
      if (Math.abs(dx) < AXIS_LOCK_PX) return;
      // Leftward from the left edge is not a back gesture.
      if (dx < 0) {
        cleanup();
        return;
      }
      locked = true;
    }

    progress.value = Math.min(1, Math.max(0, dx) / (window.innerWidth * COMMIT_FRACTION));

    // Commit as soon as the threshold is crossed rather than waiting for the
    // release. The browser fires pointercancel the moment it decides it owns a
    // touch — which it frequently does mid-drag — and a gesture that only
    // commits on pointerup silently dies when that happens.
    if (progress.value >= 1) {
      cleanup();
      opts.onCommit();
    }
  }

  function onUp(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const dx = e.clientX - startX;
    const elapsed = Math.max(1, e.timeStamp - startedAt);
    // A short, fast flick counts even though it never reached the distance.
    const flicked = locked && dx > AXIS_LOCK_PX && dx / elapsed >= FLICK_VELOCITY;
    cleanup();
    if (flicked) opts.onCommit();
  }

  function onCancel(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    cleanup();
  }

  function onPointerdown(e: PointerEvent): void {
    cleanup();
    // A mouse drag paging the app would fight text selection.
    if (e.pointerType === "mouse") return;
    // In a browser tab the platform's own back-swipe already does this.
    if (!window.matchMedia("(display-mode: standalone)").matches) return;
    if (e.clientX > EDGE_ZONE_PX) return;
    if (!opts.enabled()) return;
    pointerId = e.pointerId;
    startX = e.clientX;
    startY = e.clientY;
    startedAt = e.timeStamp;
    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onCancel);
  }

  return { onPointerdown, progress };
}

import { ref, type Ref } from "vue";

/** Travel before the gesture is considered vertical at all. */
const AXIS_LOCK_PX = 10;
/** Fraction of the viewport height that commits the dismiss on release. */
const COMMIT_FRACTION = 0.25;
/** A fast flick commits regardless of distance (px per ms). */
const FLICK_VELOCITY = 0.5;

export interface SwipeDownHandlers {
  onPointerdown: (e: PointerEvent) => void;
  /** 0..1 travel of an in-progress gesture, for a finger-follow transform. */
  progress: Ref<number>;
}

/**
 * Vertical swipe that dismisses the full-screen player sheet.
 *
 * Mirrors `useSwipeBack`'s model: the gesture is tracked at the shell level
 * (window listeners), horizontal intent (the seek bar) bails out, and a
 * committed drag pops the navigation stack. `progress` lets the sheet follow
 * the finger a little; the existing `modal-out` leave transition takes over
 * once the commit happens, so the sheet finishes its fall on its own.
 *
 * Touch and pen only — a mouse drag would fight text selection, matching the
 * rest of the app's gestures.
 */
export function useSwipeDown(opts: {
  /** Checked at pointerdown; false when there is nothing to dismiss. */
  enabled: () => boolean;
  onCommit: () => void;
}): SwipeDownHandlers {
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
      // A horizontal intent is a seek-bar drag; drop the gesture entirely.
      if (Math.abs(dx) > Math.abs(dy)) {
        cleanup();
        return;
      }
      if (Math.abs(dy) < AXIS_LOCK_PX) return;
      // Upward drag is not a dismiss.
      if (dy < 0) {
        cleanup();
        return;
      }
      locked = true;
    }

    progress.value = Math.min(1, Math.max(0, dy) / (window.innerHeight * COMMIT_FRACTION));

    // Commit as soon as the threshold is crossed rather than waiting for the
    // release. The browser fires pointercancel the moment it decides it owns
    // a touch — which it frequently does mid-drag — and a gesture that only
    // commits on pointerup silently dies when that happens.
    if (progress.value >= 1) {
      cleanup();
      opts.onCommit();
    }
  }

  function onUp(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    const dy = e.clientY - startY;
    const elapsed = Math.max(1, e.timeStamp - startedAt);
    // A short, fast flick counts even though it never reached the distance.
    const flicked = locked && dy > AXIS_LOCK_PX && dy / elapsed >= FLICK_VELOCITY;
    cleanup();
    if (flicked) opts.onCommit();
  }

  function onCancel(e: PointerEvent): void {
    if (e.pointerId !== pointerId) return;
    cleanup();
  }

  function onPointerdown(e: PointerEvent): void {
    cleanup();
    if (e.pointerType === "mouse") return;
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

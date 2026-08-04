const LONG_PRESS_MS = 500;
const MOVE_CANCEL_PX = 8;

export interface LongPressHandlers {
  onTouchstart: (e: TouchEvent) => void;
  onTouchmove: (e: TouchEvent) => void;
  onTouchend: (e: TouchEvent) => void;
}

/**
 * Touch long-press with a movement cancel and haptic tick. Bind all three
 * handlers; `touchstart`/`touchmove` may be passive, `touchend` must not be
 * (it calls preventDefault to swallow the click that follows a fired press).
 */
export function useLongPress(
  onLongPress: (pos: { clientX: number; clientY: number }) => void,
): LongPressHandlers {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let start = { x: 0, y: 0 };
  let fired = false;

  return {
    onTouchstart(e) {
      fired = false;
      const t = e.touches[0];
      if (!t) return;
      start = { x: t.clientX, y: t.clientY };
      timer = setTimeout(() => {
        timer = undefined;
        fired = true;
        navigator.vibrate?.(20);
        onLongPress({ clientX: t.clientX, clientY: t.clientY });
      }, LONG_PRESS_MS);
    },
    onTouchmove(e) {
      if (!timer) return;
      const t = e.touches[0];
      if (!t) return;
      if (
        Math.abs(t.clientX - start.x) > MOVE_CANCEL_PX ||
        Math.abs(t.clientY - start.y) > MOVE_CANCEL_PX
      ) {
        clearTimeout(timer);
        timer = undefined;
      }
    },
    onTouchend(e) {
      clearTimeout(timer);
      timer = undefined;
      if (fired) {
        e.preventDefault();
        fired = false;
      }
    },
  };
}

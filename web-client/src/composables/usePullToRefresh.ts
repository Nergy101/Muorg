import { computed, onMounted, onUnmounted, ref, type Ref } from "vue";

/**
 * Pull-to-refresh for touch devices on small screens.
 * Attach `elRef` to the scrollable container (must be `position: relative`).
 * Use `ptr.pullDistance` / `ptr.refreshing` to render an indicator and
 * `ptr.contentTransform` to nudge the content down while pulling.
 */
export function usePullToRefresh(
  elRef: Ref<HTMLElement | null>,
  onRefresh: () => Promise<void>,
) {
  const pullDistance = ref(0);
  const refreshing = ref(false);

  const ENABLED =
    typeof window !== "undefined" &&
    "ontouchstart" in window &&
    window.matchMedia("(max-width: 767px)").matches;

  let startY = 0;
  let tracking = false;

  const THRESHOLD = 64;

  function onStart(e: TouchEvent): void {
    const el = elRef.value;
    if (!el || refreshing.value) return;
    if (el.scrollTop > 0) return;
    tracking = true;
    startY = e.touches[0].clientY;
  }

  function onMove(e: TouchEvent): void {
    const el = elRef.value;
    if (!tracking || !el) return;
    const dy = e.touches[0].clientY - startY;
    if (dy <= 0) {
      pullDistance.value = 0;
      return;
    }
    if (el.scrollTop > 0) {
      pullDistance.value = 0;
      return;
    }
    // Rubber-band: resist the more you pull
    pullDistance.value = Math.min(110, dy * 0.45);
  }

  function onEnd(): void {
    if (!tracking) return;
    tracking = false;
    if (pullDistance.value >= THRESHOLD && !refreshing.value) {
      refreshing.value = true;
      pullDistance.value = THRESHOLD;
      onRefresh().finally(() => {
        refreshing.value = false;
        pullDistance.value = 0;
      });
    } else {
      pullDistance.value = 0;
    }
  }

  onMounted(() => {
    if (!ENABLED) return;
    const el = elRef.value;
    if (!el) return;
    el.addEventListener("touchstart", onStart, { passive: true });
    el.addEventListener("touchmove", onMove, { passive: true });
    el.addEventListener("touchend", onEnd, { passive: true });
  });

  onUnmounted(() => {
    const el = elRef.value;
    if (!el) return;
    el.removeEventListener("touchstart", onStart);
    el.removeEventListener("touchmove", onMove);
    el.removeEventListener("touchend", onEnd);
  });

  const indicatorStyle = computed(() => ({
    transform: `translateY(${pullDistance.value - 12}px)`,
    opacity: pullDistance.value > 4 ? Math.min(1, pullDistance.value / THRESHOLD) : 0,
  }));

  const contentTransform = computed(() =>
    pullDistance.value > 0 ? `translateY(${pullDistance.value}px)` : "",
  );

  return { pullDistance, refreshing, indicatorStyle, contentTransform };
}

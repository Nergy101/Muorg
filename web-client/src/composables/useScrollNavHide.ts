import { onBeforeUnmount, ref, watch, type Ref } from "vue";

/**
 * Auto-hides the bottom nav when the user scrolls down through a list, and
 * reveals it again when they scroll up or return near the top. Listens for
 * scroll in the capture phase on a shared ancestor (the router container), so
 * every view's inner `overflow-y-auto` scroller reports through it without any
 * per-view wiring — the nav is only ever told the *direction* of travel.
 */
export function useScrollNavHide(container: Ref<HTMLElement | null>): Ref<boolean> {
  const hidden = ref(false);
  const lastTop = new Map<EventTarget, number>();

  function onScroll(e: Event): void {
    const el = e.target as HTMLElement | null;
    if (!el || typeof el.scrollTop !== "number") return;
    const prev = lastTop.get(el) ?? el.scrollTop;
    const delta = el.scrollTop - prev;
    lastTop.set(el, el.scrollTop);
    if (el.scrollTop <= 8 || delta < 0) {
      hidden.value = false; // at the top, or scrolling up
    } else if (delta > 0 && el.scrollTop > 24) {
      hidden.value = true; // scrolling down past the threshold
    }
  }

  watch(
    container,
    (el, oldEl) => {
      if (oldEl) oldEl.removeEventListener("scroll", onScroll, true);
      if (el) el.addEventListener("scroll", onScroll, true);
    },
    { flush: "post" },
  );

  onBeforeUnmount(() => {
    container.value?.removeEventListener("scroll", onScroll, true);
  });

  return hidden;
}

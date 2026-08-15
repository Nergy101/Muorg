import { onBeforeUnmount, onMounted, watch, type Ref } from "vue";

/**
 * Publishes the height of the floating bottom island (mini player + library
 * search bar + tab row + safe area) as `--bottom-inset` on `<html>`.
 *
 * The island is positioned over the routed views rather than beside them, so
 * every scrolling view has to pad past it — otherwise its last row is parked
 * permanently behind the glass and can never be scrolled into sight. The
 * previous hardcoded `pb-[9rem]` was both too small (a visible mini player adds
 * another 4rem) and absent from half the views.
 *
 * Two details this has to get right:
 *
 * - The search and tab rows collapse via `max-height` while you scroll down.
 *   Measuring the animating wrapper would make the padding breathe in and out
 *   and yank any scroller sitting at its bottom, so the *content* inside each
 *   `data-collapsible` wrapper is measured instead: the island's natural,
 *   uncollapsed height.
 * - Exactly one of the two bars is laid out at a time (the other is
 *   `display:none` at its breakpoint), which is what the `offsetParent` check
 *   detects. Neither present — the player and connect screens — means no inset.
 *
 * @param mobileBar  the flush-to-the-edge island shown below `lg`
 * @param desktopBar the floating bar shown at `lg`
 * @param deps       reactive values that change which rows the island holds
 */
export function useBottomInset(
  mobileBar: Ref<HTMLElement | null>,
  desktopBar: Ref<HTMLElement | null>,
  deps: () => unknown[],
): void {
  // Clearing the island exactly leaves the last row kissing the glass edge.
  const BREATHING_ROOM_PX = 8;

  let ro: ResizeObserver | null = null;

  function naturalHeight(host: HTMLElement | null): number {
    if (!host || host.offsetParent === null) return 0;
    let total = 0;
    for (const child of Array.from(host.children) as HTMLElement[]) {
      const inner = child.hasAttribute("data-collapsible")
        ? (child.firstElementChild as HTMLElement | null)
        : child;
      total += inner?.offsetHeight ?? 0;
    }
    // The desktop bar floats clear of the viewport edge; the mobile one is
    // flush. Whatever gap it leaves is part of the area content must clear.
    const parent = host.offsetParent as HTMLElement;
    const gap = parent.getBoundingClientRect().bottom - host.getBoundingClientRect().bottom;
    return total + Math.max(0, gap);
  }

  function measure(): void {
    const px = naturalHeight(mobileBar.value) || naturalHeight(desktopBar.value);
    const inset = px > 0 ? px + BREATHING_ROOM_PX : 0;
    document.documentElement.style.setProperty("--bottom-inset", `${inset}px`);
  }

  /** Re-aim the observer at the current rows, then republish the height. */
  function sync(): void {
    if (!ro) return;
    ro.disconnect();
    for (const host of [mobileBar.value, desktopBar.value]) {
      if (!host) continue;
      ro.observe(host);
      for (const child of Array.from(host.children) as HTMLElement[]) {
        const inner = child.hasAttribute("data-collapsible") ? child.firstElementChild : null;
        if (inner) ro.observe(inner as HTMLElement);
      }
    }
    measure();
  }

  onMounted(() => {
    ro = new ResizeObserver(() => measure());
    sync();
    window.addEventListener("resize", measure);
  });

  // Rows appearing or disappearing changes the child list, not just a size, so
  // the observer has to be re-aimed — `post` so the new DOM is already laid out.
  watch(deps, sync, { flush: "post" });

  onBeforeUnmount(() => {
    ro?.disconnect();
    ro = null;
    window.removeEventListener("resize", measure);
  });
}

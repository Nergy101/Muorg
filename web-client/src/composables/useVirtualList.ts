import { computed, onBeforeUnmount, ref, watch, type ComputedRef, type Ref } from "vue";

export interface VirtualListOptions {
  /** The scrolling element the list lives in. */
  scroller: Ref<HTMLElement | null>;
  /** Spacer inside `scroller` that reserves the full list height. */
  anchor: Ref<HTMLElement | null>;
  count: () => number;
  /** Row pitch in px, gap included. */
  rowHeight: () => number;
  /** Items per row; omit for a plain list. */
  columns?: () => number;
  /** Rows kept mounted above and below the viewport. */
  overscan?: number;
}

export interface VirtualList {
  /** First item index to render. */
  start: Ref<number>;
  /** One past the last item index to render. */
  end: Ref<number>;
  /** Height the spacer has to reserve for the whole list. */
  totalHeight: ComputedRef<number>;
  /** Translation that puts the rendered window back where it belongs. */
  offsetTop: Ref<number>;
  scrollToIndex: (index: number) => void;
}

/**
 * Renders only the rows near the viewport of an existing page scroller.
 *
 * The list does not own the scroll container — the library screen scrolls its
 * search history and toolbar away above the list — so the window is derived
 * from the spacer's offset inside `scroller` rather than from `scrollTop`
 * alone. `anchor` must be an element sized to `totalHeight`; the rendered slice
 * goes in a child translated by `offsetTop`.
 */
export function useVirtualList(o: VirtualListOptions): VirtualList {
  const overscan = o.overscan ?? 3;
  const cols = (): number => Math.max(1, Math.floor(o.columns?.() ?? 1));
  const pitch = (): number => Math.max(1, o.rowHeight());

  const start = ref(0);
  const end = ref(0);
  const offsetTop = ref(0);

  const totalHeight = computed(() => Math.ceil(o.count() / cols()) * pitch());

  /** Distance from the scroller's content top down to the spacer's top edge. */
  function anchorOffset(sc: HTMLElement, an: HTMLElement): number {
    return an.getBoundingClientRect().top - sc.getBoundingClientRect().top + sc.scrollTop;
  }

  function measure(): void {
    const n = o.count();
    const c = cols();
    const p = pitch();
    const rows = Math.ceil(n / c);
    const sc = o.scroller.value;
    const an = o.anchor.value;

    if (!sc || !an || rows === 0) {
      // No geometry yet: render one viewport's worth so the first paint is not
      // blank, and let the first real measurement correct it.
      start.value = 0;
      end.value = Math.min(n, c * (2 * overscan + 1));
      offsetTop.value = 0;
      return;
    }

    const top = sc.scrollTop - anchorOffset(sc, an);
    const firstRow = Math.min(Math.max(0, Math.floor(top / p) - overscan), rows - 1);
    const lastRow = Math.min(rows, Math.ceil((top + sc.clientHeight) / p) + overscan);

    start.value = firstRow * c;
    end.value = Math.min(n, Math.max(lastRow, firstRow + 1) * c);
    offsetTop.value = firstRow * p;
  }

  let frame = 0;

  function schedule(): void {
    if (frame !== 0) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      measure();
    });
  }

  let detach: (() => void) | null = null;

  watch(
    o.scroller,
    (sc) => {
      detach?.();
      detach = null;
      if (!sc) return;
      const ro = new ResizeObserver(schedule);
      ro.observe(sc);
      sc.addEventListener("scroll", schedule, { passive: true });
      detach = () => {
        ro.disconnect();
        sc.removeEventListener("scroll", schedule);
      };
      measure();
    },
    { immediate: true, flush: "post" },
  );

  // Data or geometry changed. Re-window once the DOM has settled rather than a
  // frame later, so the spacer height and the rendered slice never disagree on
  // screen.
  watch([() => o.count(), () => o.rowHeight(), cols, o.anchor], measure, { flush: "post" });

  onBeforeUnmount(() => {
    if (frame !== 0) cancelAnimationFrame(frame);
    detach?.();
  });

  function scrollToIndex(index: number): void {
    const sc = o.scroller.value;
    const an = o.anchor.value;
    if (!sc || !an || index < 0) return;
    const p = pitch();
    const row = Math.floor(index / cols());
    const target = anchorOffset(sc, an) + row * p - Math.max(0, (sc.clientHeight - p) / 2);
    sc.scrollTo({ top: Math.max(0, target), behavior: "smooth" });
  }

  return { start, end, totalHeight, offsetTop, scrollToIndex };
}

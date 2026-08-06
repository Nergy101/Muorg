import { onBeforeUnmount, ref, watch, type Ref } from "vue";

/**
 * Column count for the virtualized album grid, mirroring the CSS breakpoints
 * the static grid used (grid-cols-2 / md:grid-cols-3 / lg:auto-fill
 * minmax(200px, 1fr)). Kept in a ref so the template's gridTemplateColumns and
 * the virtual list's spacer height always agree; a ResizeObserver on the
 * scroller keeps it honest across window and container resizes.
 */
export function useGridColumns(scroller: Ref<HTMLElement | null>): Ref<number> {
  const cols = ref(2);

  function colsFor(width: number): number {
    if (width >= 1024) return Math.max(3, Math.floor((width - 32 + 12) / (200 + 12)));
    if (width >= 768) return 3;
    return 2;
  }

  let ro: ResizeObserver | null = null;

  watch(
    scroller,
    (el) => {
      ro?.disconnect();
      ro = null;
      if (!el) return;
      ro = new ResizeObserver(() => {
        cols.value = colsFor(el.clientWidth);
      });
      ro.observe(el);
    },
    { flush: "post" },
  );

  onBeforeUnmount(() => ro?.disconnect());

  return cols;
}

import { type Ref, onUnmounted, ref, watch } from "vue";
import { OverlayScrollbars, type PartialOptions } from "overlayscrollbars";

const DEFAULT_OPTIONS: PartialOptions = {
  overflow: { x: "hidden", y: "scroll" },
  scrollbars: { theme: "os-theme-muorg" },
};

/**
 * Initializes OverlayScrollbars on the given container ref.
 * Returns a ref to the scroll viewport element (for scrollTop, clientHeight, scroll listener).
 * The viewport is set after mount; use it for scroll-related logic instead of the container.
 *
 * Watches the containerRef reactively — reinitializes automatically after collapse/expand cycles.
 * flush:'post' ensures Vue has committed the DOM (and the container has its children) before
 * OverlayScrollbars initializes and moves them into its internal viewport.
 */
export function useOverlayScrollbars(
  containerRef: Ref<HTMLElement | null>,
  options: PartialOptions = {},
): { viewportRef: Ref<HTMLElement | null> } {
  const viewportRef = ref<HTMLElement | null>(null);
  let instance: ReturnType<typeof OverlayScrollbars> | null = null;

  function cleanup() {
    if (instance) {
      instance.destroy();
      instance = null;
    }
    viewportRef.value = null;
  }

  watch(
    containerRef,
    (el) => {
      cleanup();
      if (!el) return;
      const merged: PartialOptions = { ...DEFAULT_OPTIONS, ...options };
      instance = OverlayScrollbars(el, merged) as ReturnType<typeof OverlayScrollbars>;
      viewportRef.value = instance.elements().viewport;
    },
    { immediate: true, flush: "post" },
  );

  onUnmounted(cleanup);

  return { viewportRef };
}

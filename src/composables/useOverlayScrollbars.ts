import { type Ref, onMounted, onUnmounted, ref } from "vue";
import { OverlayScrollbars, type PartialOptions } from "overlayscrollbars";

const DEFAULT_OPTIONS: PartialOptions = {
  overflow: { x: "hidden", y: "scroll" },
  scrollbars: { theme: "os-theme-muorg" },
};

/**
 * Initializes OverlayScrollbars on the given container ref.
 * Returns a ref to the scroll viewport element (for scrollTop, clientHeight, scroll listener).
 * The viewport is set after mount; use it for scroll-related logic instead of the container.
 */
export function useOverlayScrollbars(
  containerRef: Ref<HTMLElement | null>,
  options: PartialOptions = {},
): { viewportRef: Ref<HTMLElement | null> } {
  const viewportRef = ref<HTMLElement | null>(null);
  let instance: ReturnType<typeof OverlayScrollbars> | null = null;

  onMounted(() => {
    const el = containerRef.value;
    if (!el) return;
    const merged: PartialOptions = { ...DEFAULT_OPTIONS, ...options };
    instance = OverlayScrollbars(el, merged) as ReturnType<typeof OverlayScrollbars>;
    viewportRef.value = instance.elements().viewport;
  });

  onUnmounted(() => {
    if (instance) {
      instance.destroy();
      instance = null;
    }
    viewportRef.value = null;
  });

  return { viewportRef };
}

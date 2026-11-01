import { onBeforeUnmount, onMounted, ref, type Ref } from "vue";

/**
 * Reactive desktop-viewport flag, mirroring Tailwind's `lg` breakpoint
 * (min-width: 1024px). Used where a feature should load more items on
 * desktop than on mobile (home shelves, mixes).
 */
export function useIsDesktop(): Ref<boolean> {
  const isDesktop = ref(false);

  onMounted(() => {
    const mq = window.matchMedia("(min-width: 1024px)");
    isDesktop.value = mq.matches;
    const onChange = (e: MediaQueryListEvent) => {
      isDesktop.value = e.matches;
    };
    mq.addEventListener("change", onChange);
    onBeforeUnmount(() => mq.removeEventListener("change", onChange));
  });

  return isDesktop;
}

import { nextTick, onActivated, onBeforeUnmount, onDeactivated, watch, type Ref } from "vue";
import { useRoute } from "vue-router";

/**
 * Remembers a scroll container's offset per route and restores it on return.
 *
 * `<KeepAlive>` preserves component state but not scroll: every view scrolls an
 * inner element, and deactivation detaches that subtree from the document, which
 * resets `scrollTop` to 0. So the offset has to be saved and reapplied by hand.
 *
 * Keyed by `fullPath`, not by component, because `<KeepAlive>` reuses one
 * instance for a parameterised route — without a per-path key, opening a second
 * album would inherit the first album's offset.
 */
const offsets = new Map<string, number>();

export function useScrollMemory(el: Ref<HTMLElement | null>): void {
  const route = useRoute();

  /**
   * The path this instance is currently showing.
   *
   * Load-bearing: cached views stay alive while covered, so by the time one is
   * deactivated `route.fullPath` already points at the *incoming* screen. Saving
   * under that would file this view's offset against another view's path and
   * overwrite it.
   */
  let ownPath = route.fullPath;
  let ownName = route.name;
  let active = false;

  function save(key: string): void {
    const node = el.value;
    if (node) offsets.set(key, node.scrollTop);
  }

  function restore(key: string): void {
    const node = el.value;
    if (!node) return;
    // Always assign: a route with no stored offset must land at the top rather
    // than keep whatever the reused instance was left showing.
    const top = offsets.get(key) ?? 0;
    node.scrollTop = top;
    // The subtree is re-attached around activation, and content that settles
    // after the first paint can clamp the assignment; reapply once.
    requestAnimationFrame(() => {
      if (el.value && el.value.scrollTop !== top) el.value.scrollTop = top;
    });
  }

  onActivated(() => {
    active = true;
    ownPath = route.fullPath;
    ownName = route.name;
    restore(ownPath);
  });

  onDeactivated(() => {
    active = false;
    save(ownPath);
  });

  onBeforeUnmount(() => save(ownPath));

  // Same component, different params (album A -> album B): no activation fires,
  // so the swap is handled here. Ignored for every other navigation — a covered
  // view must not react to a route that is not its own.
  //
  // `pre` flush matters: by the time the new list has rendered, the shorter
  // content has already clamped scrollTop to 0 and the outgoing offset is gone.
  watch(
    () => route.fullPath,
    (to) => {
      if (!active || route.name !== ownName) return;
      save(ownPath);
      ownPath = to;
      void nextTick(() => restore(to));
    },
    { flush: "pre" },
  );
}

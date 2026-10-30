import { onActivated, onDeactivated, onMounted, onUnmounted, ref } from "vue";

type IdsGetter = () => number[];

/**
 * Which view's track list is currently on screen, as a getter for its
 * *displayed order* (sorted/filtered/grouped as shown). `SelectionBar` is
 * mounted once in App.vue, outside the routed view's component tree, so this
 * can't be plain provide/inject — it's a small module-level registry instead,
 * the same pattern `useMetadataEditor`/`useToast` already use for singletons
 * shared across the app shell and the active view.
 */
const activeGetter = ref<IdsGetter | null>(null);

/**
 * Registers the ordered id list the calling view currently displays, so a
 * Shift-click range and the selection bar's "Select all" follow what the user
 * sees rather than catalog order. Call once in a view's `<script setup>`.
 *
 * Views stay mounted under `KeepAlive` while navigated away, so registration
 * tracks `onActivated`/`onDeactivated` (not just mount/unmount) — otherwise a
 * backgrounded view would keep "winning" the registry over the view actually
 * on screen.
 */
export function provideVisibleIds(get: IdsGetter): void {
  function register(): void {
    activeGetter.value = get;
  }
  function unregister(): void {
    if (activeGetter.value === get) activeGetter.value = null;
  }
  onMounted(register);
  onActivated(register);
  onDeactivated(unregister);
  onUnmounted(unregister);
}

/** The currently registered visible ids, or an empty list when no view has
 *  registered one. */
export function visibleIds(): number[] {
  return activeGetter.value?.() ?? [];
}

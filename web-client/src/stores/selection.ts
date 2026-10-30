import { defineStore } from "pinia";
import { computed, ref } from "vue";

/** The modifier keys of the event that triggered a selection click. */
export interface SelectModifiers {
  shift?: boolean;
  /** Ctrl on Windows/Linux, Cmd on macOS. */
  ctrl?: boolean;
}

/**
 * Multi-selection of tracks, shared by every track list (library table, album,
 * search, playlist, report, queue).
 *
 * Selection is by track id. Lists pass their *currently displayed order* into
 * `click` / `selectRange` / `selectAll`, so a Shift-click range follows what the
 * user sees (sorted, filtered, grouped) and not the catalog order.
 *
 * `selecting` is an explicit mode: on touch there is no Ctrl/Shift, so a
 * long-press (or the "Select" button) enters the mode and taps then toggle.
 * The mode also ends by itself when the last item is deselected.
 */
export const useSelectionStore = defineStore("selection", () => {
  const selected = ref<Set<number>>(new Set());
  const selecting = ref(false);
  /** Last item toggled — the fixed end of a Shift-click range. */
  const anchorId = ref<number | null>(null);

  const count = computed(() => selected.value.size);
  const ids = computed(() => [...selected.value]);
  const hasSelection = computed(() => selected.value.size > 0);

  function has(id: number): boolean {
    return selected.value.has(id);
  }

  function replace(next: Iterable<number>): void {
    selected.value = new Set(next);
    if (selected.value.size === 0) selecting.value = false;
  }

  function toggle(id: number): void {
    const next = new Set(selected.value);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    anchorId.value = id;
    replace(next);
  }

  /** Adds everything between the anchor and `id` (inclusive), in `ordered` order. */
  function selectRange(id: number, ordered: readonly number[]): void {
    const to = ordered.indexOf(id);
    const from = anchorId.value === null ? -1 : ordered.indexOf(anchorId.value);
    if (to === -1) return;
    if (from === -1) {
      toggle(id);
      return;
    }
    const [lo, hi] = from < to ? [from, to] : [to, from];
    const next = new Set(selected.value);
    for (let i = lo; i <= hi; i++) next.add(ordered[i]);
    // The anchor stays put so a second Shift-click re-ranges from the same end.
    replace(next);
  }

  function selectAll(ordered: readonly number[]): void {
    if (ordered.length === 0) return;
    selecting.value = true;
    anchorId.value = ordered[0];
    selected.value = new Set(ordered);
  }

  /** Starts selection mode with `id` selected (long-press on touch). */
  function begin(id: number): void {
    selecting.value = true;
    anchorId.value = id;
    selected.value = new Set([id]);
  }

  function clear(): void {
    selected.value = new Set();
    selecting.value = false;
    anchorId.value = null;
  }

  /**
   * Handles a row click. Returns true when the click was consumed as a
   * selection gesture, false when the caller should treat it as a normal
   * activation (play / open). In selection mode every click is a selection;
   * outside it, only Ctrl/Shift-click starts one.
   */
  function click(id: number, ordered: readonly number[], mods: SelectModifiers = {}): boolean {
    if (mods.shift) {
      selecting.value = true;
      selectRange(id, ordered);
      return true;
    }
    if (mods.ctrl) {
      selecting.value = true;
      toggle(id);
      return true;
    }
    if (selecting.value) {
      toggle(id);
      return true;
    }
    return false;
  }

  /** Drops ids that no longer exist (after a delete or a catalog reload). */
  function prune(existing: (id: number) => boolean): void {
    const next = [...selected.value].filter(existing);
    if (next.length !== selected.value.size) replace(next);
  }

  return {
    selected,
    selecting,
    anchorId,
    count,
    ids,
    hasSelection,
    has,
    toggle,
    selectRange,
    selectAll,
    begin,
    clear,
    click,
    prune,
  };
});

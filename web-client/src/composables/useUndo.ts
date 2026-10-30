import { computed, shallowRef } from "vue";
import { showActionToast, showToast } from "./useToast";

/** Matches the desktop app's depth. */
export const MAX_UNDO_DEPTH = 50;

/**
 * One reversible change. `undo` must restore the state from before the change;
 * `redo` re-applies it. Both run against the server (they are API calls), so
 * both are async and either may fail.
 */
export interface UndoEntry {
  /** Short past-tense description, e.g. "Rated 3 tracks". */
  label: string;
  undo: () => Promise<void>;
  redo: () => Promise<void>;
}

// shallowRef: entries are compared by identity (see `recordUndoable`), which a
// deep `ref` would break by handing out reactive proxies of each entry.
const undoStack = shallowRef<UndoEntry[]>([]);
const redoStack = shallowRef<UndoEntry[]>([]);
let busy = false;

export const canUndo = computed(() => undoStack.value.length > 0);
export const canRedo = computed(() => redoStack.value.length > 0);

/** Records a change that has already been applied. Clears the redo stack. */
export function pushUndo(entry: UndoEntry): void {
  undoStack.value = [...undoStack.value, entry].slice(-MAX_UNDO_DEPTH);
  redoStack.value = [];
}

/**
 * Records a change and shows a toast with an Undo button — the web client's
 * answer to "Ctrl+Z only works in the editor" (Q5): every write offers its own
 * undo. Undoing from the toast undoes exactly this entry, and only while it is
 * still the newest one.
 */
export function recordUndoable(entry: UndoEntry): void {
  pushUndo(entry);
  showActionToast(entry.label, {
    label: "Undo",
    run: async () => {
      if (undoStack.value[undoStack.value.length - 1] !== entry) {
        showToast("Can't undo: newer changes were made");
        return;
      }
      await undoLast();
    },
  });
}

/** Undoes the newest change. Returns false when there is nothing to undo or an undo is running. */
export async function undoLast(): Promise<boolean> {
  const entry = undoStack.value[undoStack.value.length - 1];
  if (!entry || busy) return false;
  busy = true;
  try {
    await entry.undo();
    undoStack.value = undoStack.value.slice(0, -1);
    redoStack.value = [...redoStack.value, entry];
    showToast(`Undid: ${entry.label}`);
    return true;
  } catch (e) {
    showToast(`Undo failed: ${(e as Error).message}`);
    return false;
  } finally {
    busy = false;
  }
}

/** Re-applies the newest undone change. */
export async function redoLast(): Promise<boolean> {
  const entry = redoStack.value[redoStack.value.length - 1];
  if (!entry || busy) return false;
  busy = true;
  try {
    await entry.redo();
    redoStack.value = redoStack.value.slice(0, -1);
    undoStack.value = [...undoStack.value, entry];
    showToast(`Redid: ${entry.label}`);
    return true;
  } catch (e) {
    showToast(`Redo failed: ${(e as Error).message}`);
    return false;
  } finally {
    busy = false;
  }
}

/** Forgets all history (logout). */
export function clearUndo(): void {
  undoStack.value = [];
  redoStack.value = [];
}

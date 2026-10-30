import { ref } from "vue";
import type { Ref } from "vue";

const TOAST_MS = 2000;
/** Warnings get longer to be read than a confirmation. */
const WARNING_TOAST_MS = 6000;
/** A toast with an action (e.g. Undo) needs time to be reached for. */
const ACTION_TOAST_MS = 8000;

/** A button shown inside the toast, e.g. "Undo". */
export interface ToastAction {
  label: string;
  run: () => void | Promise<void>;
}

const message = ref<string | null>(null);
const action = ref<ToastAction | null>(null);
let timer: ReturnType<typeof setTimeout> | undefined;

function dismiss(): void {
  message.value = null;
  action.value = null;
  timer = undefined;
}

export function showToast(msg: string, ms = TOAST_MS, toastAction?: ToastAction): void {
  message.value = msg;
  action.value = toastAction ?? null;
  clearTimeout(timer);
  timer = setTimeout(dismiss, ms);
}

/** A warning the user should actually read before it disappears. */
export function showWarning(msg: string): void {
  showToast(msg, WARNING_TOAST_MS);
}

/** A confirmation with an action button, shown long enough to click it. */
export function showActionToast(msg: string, toastAction: ToastAction): void {
  showToast(msg, ACTION_TOAST_MS, toastAction);
}

/** Runs the visible toast's action (if any) and closes the toast. */
export async function runToastAction(): Promise<void> {
  const current = action.value;
  if (!current) return;
  clearTimeout(timer);
  dismiss();
  await current.run();
}

export interface ToastState {
  /** The currently visible toast message, or null when nothing is showing. */
  message: Ref<string | null>;
  /** The action button on the visible toast, or null. */
  action: Ref<ToastAction | null>;
}

export function useToast(): ToastState {
  return { message, action };
}

import { ref } from "vue";
import type { Ref } from "vue";

const TOAST_MS = 2000;
/** Warnings get longer to be read than a confirmation. */
const WARNING_TOAST_MS = 6000;

const message = ref<string | null>(null);
let timer: ReturnType<typeof setTimeout> | undefined;

export function showToast(msg: string, ms = TOAST_MS): void {
  message.value = msg;
  clearTimeout(timer);
  timer = setTimeout(() => {
    message.value = null;
    timer = undefined;
  }, ms);
}

/** A warning the user should actually read before it disappears. */
export function showWarning(msg: string): void {
  showToast(msg, WARNING_TOAST_MS);
}

export interface ToastState {
  /** The currently visible toast message, or null when nothing is showing. */
  message: Ref<string | null>;
}

export function useToast(): ToastState {
  return { message };
}

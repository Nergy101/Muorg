import { ref } from "vue";
import type { Ref } from "vue";

const TOAST_MS = 2000;

const message = ref<string | null>(null);
let timer: ReturnType<typeof setTimeout> | undefined;

export function showToast(msg: string): void {
  message.value = msg;
  clearTimeout(timer);
  timer = setTimeout(() => {
    message.value = null;
    timer = undefined;
  }, TOAST_MS);
}

export interface ToastState {
  /** The currently visible toast message, or null when nothing is showing. */
  message: Ref<string | null>;
}

export function useToast(): ToastState {
  return { message };
}

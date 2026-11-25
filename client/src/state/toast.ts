import { ref } from "vue";

/** Global transient toast (bottom-center pill). */
export const toastMessage = ref<string | null>(null);
let toastTimer: ReturnType<typeof setTimeout> | null = null;

export function showToast(message: string, ms = 2200): void {
  toastMessage.value = message;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toastMessage.value = null;
    toastTimer = null;
  }, ms);
}

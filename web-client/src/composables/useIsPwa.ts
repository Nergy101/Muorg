import { computed } from "vue";

/**
 * True when the app runs as an installed PWA (standalone display mode) —
 * Android/Chrome "display-mode: standalone" or iOS Safari's
 * navigator.standalone. In that mode there is no browser chrome, so
 * features that depend on it (e.g. the PiP popout button) are hidden.
 */
export function useIsPwa() {
  const isStandalone =
    window.matchMedia("(display-mode: standalone)").matches ||
    ("standalone" in window.navigator &&
      (window.navigator as unknown as { standalone: boolean }).standalone === true);
  return computed(() => isStandalone);
}

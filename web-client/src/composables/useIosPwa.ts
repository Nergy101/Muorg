import { computed } from "vue";

/**
 * True when the app runs as an installed PWA on iOS/WebKit. iOS 26+ draws a
 * system blur/fade over the top edge in this mode that CSS cannot disable
 * (see index.html), so the shell needs extra top padding to sit below it.
 */
export function useIosPwa() {
  const isIos =
    /iphone|ipad|ipod/i.test(navigator.userAgent) ||
    // iPadOS 13+ reports as MacIntel with touch points
    (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const isStandalone =
    window.matchMedia("(display-mode: standalone)").matches ||
    ("standalone" in window.navigator &&
      (window.navigator as unknown as { standalone: boolean }).standalone === true);
  return computed(() => isIos && isStandalone);
}

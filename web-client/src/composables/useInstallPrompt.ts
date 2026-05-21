import { ref, computed, onMounted, onUnmounted } from "vue";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null);
const isStandalone = ref(false);
const isIos = ref(false);

export function useInstallPrompt() {
  onMounted(() => {
    isStandalone.value =
      window.matchMedia("(display-mode: standalone)").matches ||
      ("standalone" in window.navigator &&
        (window.navigator as unknown as { standalone: boolean }).standalone === true);

    isIos.value =
      /iphone|ipad|ipod/i.test(navigator.userAgent) ||
      // iPadOS 13+ reports as MacIntel with touch points
      (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

    const onPrompt = (e: Event) => {
      e.preventDefault();
      deferredPrompt.value = e as BeforeInstallPromptEvent;
    };

    window.addEventListener("beforeinstallprompt", onPrompt);

    onUnmounted(() => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
    });
  });

  // Show the button when: not already installed, and either the browser
  // offered a native prompt (Android) or we're on iOS (manual flow).
  const canInstall = computed(
    () => !isStandalone.value && (!!deferredPrompt.value || isIos.value),
  );

  async function install(): Promise<void> {
    if (deferredPrompt.value) {
      // Android: trigger the native browser install prompt
      await deferredPrompt.value.prompt();
      const { outcome } = await deferredPrompt.value.userChoice;
      if (outcome === "accepted") {
        deferredPrompt.value = null;
      }
    }
    // iOS: the button's tooltip guides the user — nothing to do programmatically
  }

  return { canInstall, isIos, install };
}

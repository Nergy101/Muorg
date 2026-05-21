import { ref, computed, onMounted, onUnmounted } from "vue";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null);

// True when already running as an installed PWA
const isStandalone = ref(false);

export function useInstallPrompt() {
  onMounted(() => {
    isStandalone.value =
      window.matchMedia("(display-mode: standalone)").matches ||
      ("standalone" in window.navigator && (window.navigator as unknown as { standalone: boolean }).standalone === true);

    const onPrompt = (e: Event) => {
      e.preventDefault();
      deferredPrompt.value = e as BeforeInstallPromptEvent;
    };

    window.addEventListener("beforeinstallprompt", onPrompt);

    onUnmounted(() => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
    });
  });

  const canInstall = computed(() => !isStandalone.value && !!deferredPrompt.value);

  async function install(): Promise<void> {
    if (!deferredPrompt.value) return;
    await deferredPrompt.value.prompt();
    const { outcome } = await deferredPrompt.value.userChoice;
    if (outcome === "accepted") {
      deferredPrompt.value = null;
    }
  }

  return { canInstall, install };
}

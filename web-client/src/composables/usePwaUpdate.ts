import { ref } from "vue";
import { registerSW } from "virtual:pwa-register";

/** True while a newer build has been fetched by the service worker and is
 *  waiting to take control. Surface a refresh affordance when set. */
export const pwaUpdateAvailable = ref(false);

const updateServiceWorker = registerSW({
  immediate: true,
  onNeedRefresh() {
    pwaUpdateAvailable.value = true;
  },
  onOfflineReady() {
    // First install cached the app for offline use; nothing to surface.
  },
  onRegisteredSW(_swUrl, registration) {
    // Browsers only check for a new service worker on navigation. Poll while
    // the app stays open so an update surfaces without a manual reload.
    if (registration) {
      setInterval(() => {
        registration.update().catch(() => {});
      }, 30 * 60 * 1000);
    }
  },
});

/** Activate the waiting service worker and reload with the new version. */
export function refreshApp(): void {
  void updateServiceWorker(true);
}

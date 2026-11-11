import { ref } from "vue";
import { registerSW } from "virtual:pwa-register";

/** True while a newer build has been fetched by the service worker and is
 *  waiting to take control. Surface a refresh affordance when set. */
export const pwaUpdateAvailable = ref(false);

/** In dev, never let a service worker control this origin: a production SW
 *  registered earlier on the same port would keep serving its stale cached
 *  bundle over the dev server. Unregister everything and skip registration
 *  so the dev build always bypasses the SW cache. Production keeps the
 *  normal prompt-style registration (with the update banner). */
const isDev = import.meta.env.DEV;

if (isDev && "serviceWorker" in navigator) {
  void navigator.serviceWorker.getRegistrations().then((registrations) => {
    for (const registration of registrations) void registration.unregister();
  });
}

const updateServiceWorker = isDev
  ? () => Promise.resolve()
  : registerSW({
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
  if (isDev) {
    window.location.reload();
    return;
  }
  void updateServiceWorker(true);
}

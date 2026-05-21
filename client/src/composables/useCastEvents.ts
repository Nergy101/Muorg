import { listen } from "@tauri-apps/api/event";
import { onMounted, onUnmounted } from "vue";
import { useCastStore } from "../stores/cast";
import * as castApi from "../api/cast";
import type { CastDevice } from "../stores/cast";

const POLL_INTERVAL_MS = 1000;

/**
 * Wires up cast event listeners and status polling.
 * Call once at App.vue top level so the interval lives for the app's lifetime.
 * Device list comes from the Tauri-native mDNS (in-process, has proper macOS entitlements).
 * Cast status/volume is polled from the server sidecar.
 */
export function useCastEvents() {
  const castStore = useCastStore();
  let timer: ReturnType<typeof setInterval> | null = null;
  let unlisten: (() => void) | null = null;

  async function poll() {
    try {
      const { session, volume } = await castApi.getStatus();
      castStore.handleStatusEvent(session);
      castStore.handleVolumeEvent(volume);
    } catch {
      // Server unreachable — don't spam errors
    }
  }

  onMounted(async () => {
    poll();
    timer = setInterval(poll, POLL_INTERVAL_MS);

    // Subscribe to device-list events emitted by Tauri-native mDNS discovery.
    unlisten = await listen<CastDevice[]>("cast://device-list-changed", (event) => {
      castStore.handleDeviceListEvent(event.payload);
    });

    // Seed the initial device list (may already have results if discovery started at app init).
    try {
      const devices = await castApi.getDevices();
      castStore.handleDeviceListEvent(devices);
    } catch {
      // ignore
    }
  });

  onUnmounted(() => {
    if (timer !== null) clearInterval(timer);
    if (unlisten) unlisten();
  });
}

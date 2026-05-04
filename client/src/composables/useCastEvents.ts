import { onMounted, onUnmounted } from "vue";
import { useCastStore } from "../stores/cast";
import * as castApi from "../api/cast";

const POLL_INTERVAL_MS = 1000;

/**
 * Polls the server for cast status/devices instead of using Tauri events.
 * Call once at App.vue top level so the interval lives for the app's lifetime.
 */
export function useCastEvents() {
  const castStore = useCastStore();
  let timer: ReturnType<typeof setInterval> | null = null;

  async function poll() {
    try {
      const { session, volume } = await castApi.getStatus();
      castStore.handleStatusEvent(session);
      castStore.handleVolumeEvent(volume);
    } catch {
      // Server unreachable — don't spam errors
    }

    try {
      const devices = await castApi.getDevices();
      castStore.handleDeviceListEvent(devices);
    } catch {
      // ignore
    }
  }

  onMounted(() => {
    poll();
    timer = setInterval(poll, POLL_INTERVAL_MS);
  });

  onUnmounted(() => {
    if (timer !== null) clearInterval(timer);
  });
}

import { onMounted, onUnmounted } from "vue";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { useCastStore } from "../stores/cast";
import type { CastDevice, CastSessionStatus } from "../stores/cast";

/** Register Cast event listeners once. Call this at App.vue top level so it lives for the app's lifetime. */
export function useCastEvents() {
  const castStore = useCastStore();
  let unlistenStatus: UnlistenFn | null = null;
  let unlistenDevices: UnlistenFn | null = null;
  let unlistenVolume: UnlistenFn | null = null;

  onMounted(async () => {
    unlistenStatus = await listen<CastSessionStatus>("cast://status-changed", (event) => {
      castStore.handleStatusEvent(event.payload);
    });
    unlistenDevices = await listen<CastDevice[]>("cast://device-list-changed", (event) => {
      castStore.handleDeviceListEvent(event.payload);
    });
    unlistenVolume = await listen<number>("cast://volume-changed", (event) => {
      castStore.handleVolumeEvent(event.payload);
    });
  });

  onUnmounted(() => {
    unlistenStatus?.();
    unlistenDevices?.();
    unlistenVolume?.();
  });
}

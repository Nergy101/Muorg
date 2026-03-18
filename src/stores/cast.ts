import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { invoke } from "@tauri-apps/api/core";
import { emit as tauriEmit } from "@tauri-apps/api/event";

export type CastSessionStatus =
  | { status: "idle" }
  | { status: "connecting" }
  | { status: "transcoding" }
  | { status: "playing"; positionSecs?: number }
  | { status: "paused"; positionSecs?: number }
  | { status: "stopped"; finished: boolean }
  | { status: "error"; message: string };

export interface CastDevice {
  id: string;
  name: string;
  address: string;
  port: number;
}

export const useCastStore = defineStore("cast", () => {
  const castStatus = ref<CastSessionStatus>({ status: "idle" });
  const connectedDeviceId = ref<string | null>(null);
  const connectedDeviceName = ref<string | null>(null);
  const discoveredDevices = ref<CastDevice[]>([]);
  /** Current volume of the cast device (0–1), or null when not casting. */
  const castVolume = ref<number | null>(null);

  const isCasting = computed(() =>
    ["playing", "paused", "transcoding", "connecting"].includes(castStatus.value.status),
  );

  /**
   * Set to true when a seek was issued while casting, awaiting cast "playing" confirmation
   * before resuming the local (muted) audio element. Shared so both PlayerBar and
   * PlayScreenPlayBar can coordinate without duplicating the watch logic.
   */
  const pendingCastResume = ref(false);
  function setPendingCastResume(v: boolean) {
    pendingCastResume.value = v;
  }

  const castError = computed(() =>
    castStatus.value.status === "error"
      ? (castStatus.value as { status: "error"; message: string }).message
      : null,
  );

  function handleStatusEvent(payload: CastSessionStatus) {
    castStatus.value = payload;

    if (payload.status === "stopped" && payload.finished) {
      tauriEmit("cast:track-ended", {});
    } else if (payload.status === "error") {
      // Unexpected failure — clear device so the UI resets
      connectedDeviceId.value = null;
      connectedDeviceName.value = null;
      castVolume.value = null;
    }
    // "stopped" fired during a track change is expected — keep connectedDeviceId
    // intact so the next track change still knows which device to target.
  }

  function handleDeviceListEvent(devices: CastDevice[]) {
    discoveredDevices.value = devices;
  }

  function setConnectedDevice(id: string, name: string) {
    connectedDeviceId.value = id;
    connectedDeviceName.value = name;
  }

  async function stopCast() {
    // Clear immediately so the UI resets before the async confirm arrives
    connectedDeviceId.value = null;
    connectedDeviceName.value = null;
    castVolume.value = null;
    try {
      await invoke("cast_stop");
    } catch (e) {
      console.error("[Cast] stop error:", e);
    }
  }

  function handleVolumeEvent(level: number) {
    castVolume.value = level;
  }

  async function setCastVolume(level: number) {
    const clamped = Math.min(1, Math.max(0, level));
    castVolume.value = clamped;
    try {
      await invoke("cast_set_volume", { level: clamped });
    } catch (e) {
      console.error("[Cast] set_volume error:", e);
    }
  }

  return {
    castStatus,
    connectedDeviceId,
    connectedDeviceName,
    discoveredDevices,
    castVolume,
    isCasting,
    castError,
    pendingCastResume,
    setPendingCastResume,
    handleStatusEvent,
    handleDeviceListEvent,
    handleVolumeEvent,
    setConnectedDevice,
    stopCast,
    setCastVolume,
  };
});

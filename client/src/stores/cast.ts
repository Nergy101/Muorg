import { computed, ref } from "vue";
import { defineStore } from "pinia";
import * as castApi from "../api/cast";

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
  const castVolume = ref<number | null>(null);
  /** Session id returned by castPlay when this device started the cast. */
  const sessionId = ref<string | null>(null);

  const isCasting = computed(() =>
    ["playing", "paused", "transcoding", "connecting"].includes(castStatus.value.status),
  );

  const pendingCastResume = ref(false);
  function setPendingCastResume(v: boolean) {
    pendingCastResume.value = v;
  }

  const castError = computed(() =>
    castStatus.value.status === "error"
      ? (castStatus.value as { status: "error"; message: string }).message
      : null,
  );

  // track-ended callbacks registered by the player
  const _trackEndedCallbacks: (() => void)[] = [];
  function onTrackEnded(cb: () => void) {
    _trackEndedCallbacks.push(cb);
    return () => {
      const i = _trackEndedCallbacks.indexOf(cb);
      if (i >= 0) _trackEndedCallbacks.splice(i, 1);
    };
  }

  function handleStatusEvent(payload: CastSessionStatus) {
    const prev = castStatus.value.status;
    castStatus.value = payload;

    if (payload.status === "stopped" && payload.finished) {
      for (const cb of _trackEndedCallbacks) cb();
    } else if (payload.status === "error") {
      connectedDeviceId.value = null;
      connectedDeviceName.value = null;
      castVolume.value = null;
      sessionId.value = null;
    }

    // When cast confirms it's playing after a track change, clear the pending flag
    // so the local (muted) audio element resumes position tracking.
    if (prev !== "playing" && payload.status === "playing" && pendingCastResume.value) {
      pendingCastResume.value = false;
    }
  }

  function handleDeviceListEvent(devices: CastDevice[]) {
    discoveredDevices.value = devices;
  }

  function setConnectedDevice(id: string, name: string) {
    connectedDeviceId.value = id;
    connectedDeviceName.value = name;
    sessionId.value = null;
  }

  function setSessionId(id: string | null) {
    sessionId.value = id;
  }

  /**
   * Called on every status poll with the server's current session id.
   * There is one global cast session per server: if we started a cast (we
   * hold a session id) but the server reports an active session carrying a
   * different id, another device took the session over. Clear our local cast
   * state so the UI stops showing "casting". Returns true when a takeover was
   * detected, so the caller can notify the user (fires once per takeover).
   */
  function handleSessionId(polledSessionId: string): boolean {
    if (sessionId.value === null) return false;
    if (!isCasting.value) return false;
    if (polledSessionId === "" || polledSessionId === sessionId.value) return false;
    sessionId.value = null;
    connectedDeviceId.value = null;
    connectedDeviceName.value = null;
    castVolume.value = null;
    return true;
  }

  async function stopCast() {
    connectedDeviceId.value = null;
    connectedDeviceName.value = null;
    castVolume.value = null;
    sessionId.value = null;
    try {
      await castApi.castStop();
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
      await castApi.setCastVolume(clamped);
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
    sessionId,
    isCasting,
    castError,
    pendingCastResume,
    setPendingCastResume,
    handleStatusEvent,
    handleDeviceListEvent,
    handleVolumeEvent,
    setConnectedDevice,
    setSessionId,
    handleSessionId,
    stopCast,
    setCastVolume,
    onTrackEnded,
  };
});

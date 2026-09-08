import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { api } from "../api/client";
import type { CastDevice, CastSessionStatus } from "@shared/api";

/**
 * Chromecast control for the web client.
 *
 * The desktop app gets pushed status through Tauri events; a browser has no
 * such channel, so this polls `GET /api/cast/status` while a session is live
 * and stops as soon as it is not. Everything else — discovery, transport,
 * volume — is the same set of `/api/cast/*` routes both other clients drive.
 *
 * The server owns the session: it connects to the device and streams the audio
 * itself, so nothing here touches the local `<audio>` element beyond pausing it
 * when a cast starts.
 */

/** How often to re-read the session while casting. */
const STATUS_POLL_MS = 1000;
/** How often to re-read the device list while the picker is open. */
const DISCOVERY_POLL_MS = 2000;

/** Statuses that mean a session exists and should keep being polled. */
const LIVE = ["connecting", "transcoding", "playing", "paused"] as const;

export const useCastStore = defineStore("cast", () => {
  const status = ref<CastSessionStatus>({ status: "idle" });
  const devices = ref<CastDevice[]>([]);
  const device = ref<CastDevice | null>(null);
  const volume = ref(1);
  const error = ref<string | null>(null);
  const discovering = ref(false);

  let statusTimer: ReturnType<typeof setInterval> | null = null;
  let discoveryTimer: ReturnType<typeof setInterval> | null = null;

  /** Callbacks fired when the device reports a track played to the end. */
  const trackEndedHandlers: (() => void)[] = [];

  const isCasting = computed(() =>
    (LIVE as readonly string[]).includes(status.value.status),
  );

  const isPlaying = computed(() => status.value.status === "playing");

  /** Position reported by the device, or null when it has not said yet. */
  const positionSecs = computed(() => {
    const s = status.value;
    return s.status === "playing" || s.status === "paused"
      ? (s.position_secs ?? null)
      : null;
  });

  const deviceName = computed(() => device.value?.name ?? null);

  function onTrackEnded(handler: () => void): () => void {
    trackEndedHandlers.push(handler);
    return () => {
      const i = trackEndedHandlers.indexOf(handler);
      if (i >= 0) trackEndedHandlers.splice(i, 1);
    };
  }

  // ── Discovery ───────────────────────────────────────────────────────────

  /** Begin an mDNS sweep and keep the device list fresh while the picker is open. */
  async function startDiscovery(): Promise<void> {
    error.value = null;
    discovering.value = true;
    try {
      await api.castStartDiscovery();
      await refreshDevices();
      discoveryTimer ??= setInterval(() => void refreshDevices(), DISCOVERY_POLL_MS);
    } catch (e) {
      discovering.value = false;
      error.value = (e as Error).message;
    }
  }

  async function stopDiscovery(): Promise<void> {
    discovering.value = false;
    if (discoveryTimer) {
      clearInterval(discoveryTimer);
      discoveryTimer = null;
    }
    // Best-effort: the sweep costs the server nothing much if this fails.
    await api.castStopDiscovery().catch(() => undefined);
  }

  async function refreshDevices(): Promise<void> {
    try {
      devices.value = await api.castDevices();
    } catch (e) {
      error.value = (e as Error).message;
    }
  }

  // ── Session ─────────────────────────────────────────────────────────────

  /** Start casting `trackId` to `target`. */
  async function play(target: CastDevice, trackId: number): Promise<void> {
    error.value = null;
    device.value = target;
    status.value = { status: "connecting" };
    try {
      await api.castPlay(target.address, target.port, trackId);
      startPolling();
    } catch (e) {
      error.value = (e as Error).message;
      status.value = { status: "idle" };
      device.value = null;
    }
  }

  async function pause(): Promise<void> {
    await run(() => api.castPause());
  }

  async function resume(): Promise<void> {
    await run(() => api.castResume());
  }

  async function seek(secs: number): Promise<void> {
    await run(() => api.castSeek(secs, isPlaying.value));
  }

  async function setVolume(level: number): Promise<void> {
    const clamped = Math.min(1, Math.max(0, level));
    volume.value = clamped;
    await run(() => api.castSetVolume(clamped));
  }

  /** Tear the session down and hand playback back to the browser. */
  async function stop(): Promise<void> {
    stopPolling();
    status.value = { status: "idle" };
    device.value = null;
    await api.castStop().catch(() => undefined);
  }

  async function run(action: () => Promise<unknown>): Promise<void> {
    try {
      await action();
      await refreshStatus();
    } catch (e) {
      error.value = (e as Error).message;
    }
  }

  // ── Status polling ──────────────────────────────────────────────────────

  function startPolling(): void {
    statusTimer ??= setInterval(() => void refreshStatus(), STATUS_POLL_MS);
  }

  function stopPolling(): void {
    if (statusTimer) {
      clearInterval(statusTimer);
      statusTimer = null;
    }
  }

  async function refreshStatus(): Promise<void> {
    let next: CastSessionStatus;
    try {
      const response = await api.castStatus();
      next = response.session;
      volume.value = response.volume;
    } catch (e) {
      // A server that went away mid-session should not leave the UI stuck
      // showing "casting" forever.
      error.value = (e as Error).message;
      stopPolling();
      status.value = { status: "idle" };
      device.value = null;
      return;
    }

    const previous = status.value;
    status.value = next;

    if (next.status === "error") {
      error.value = next.message;
      stopPolling();
      device.value = null;
      return;
    }

    if (next.status === "stopped") {
      stopPolling();
      device.value = null;
      status.value = { status: "idle" };
      // `finished` distinguishes "the track ended" from "the user stopped it";
      // only the former should advance the queue.
      if (next.finished && previous.status !== "idle") {
        for (const handler of trackEndedHandlers) handler();
      }
      return;
    }

    if (next.status === "idle") {
      stopPolling();
      device.value = null;
    }
  }

  /** Clear timers — called when the app tears down. */
  function dispose(): void {
    stopPolling();
    if (discoveryTimer) {
      clearInterval(discoveryTimer);
      discoveryTimer = null;
    }
  }

  return {
    status,
    devices,
    device,
    deviceName,
    volume,
    error,
    discovering,
    isCasting,
    isPlaying,
    positionSecs,
    onTrackEnded,
    startDiscovery,
    stopDiscovery,
    refreshDevices,
    play,
    pause,
    resume,
    seek,
    setVolume,
    stop,
    refreshStatus,
    dispose,
  };
});

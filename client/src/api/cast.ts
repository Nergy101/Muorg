import { invoke } from "@tauri-apps/api/core";
import { apiFetch } from "./client";
import type { CastSessionStatus, CastDevice } from "../stores/cast";

export interface CastStatusResponse {
  session: CastSessionStatus;
  volume: number;
}

// Discovery runs in the Tauri process (in-process mDNS has proper macOS entitlements).
export async function getDevices(): Promise<CastDevice[]> {
  return invoke<CastDevice[]>("cast_get_devices");
}

export async function startDiscovery(): Promise<void> {
  await invoke<void>("cast_start_discovery");
}

export async function stopDiscovery(): Promise<void> {
  await invoke<void>("cast_stop_discovery");
}

// Cast session (connect + stream) is handled by the server sidecar.
export async function getStatus(): Promise<CastStatusResponse> {
  return apiFetch<CastStatusResponse>("/api/cast/status");
}

export async function castPlay(
  deviceId: string,
  trackId: number,
  deviceAddress: string,
  devicePort: number,
): Promise<void> {
  await apiFetch("/api/cast/play", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      device_id: deviceId,
      track_id: trackId,
      device_address: deviceAddress,
      device_port: devicePort,
    }),
  });
}

export async function castPause(): Promise<void> {
  await apiFetch("/api/cast/pause", { method: "POST" });
}

export async function castResume(): Promise<void> {
  await apiFetch("/api/cast/resume", { method: "POST" });
}

export async function castStop(): Promise<void> {
  await apiFetch("/api/cast/stop", { method: "POST" });
}

export async function castSeek(positionSecs: number, wasPlaying: boolean): Promise<void> {
  await apiFetch("/api/cast/seek", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ position_secs: positionSecs, was_playing: wasPlaying }),
  });
}

export async function setCastVolume(level: number): Promise<void> {
  await apiFetch("/api/cast/volume", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ level }),
  });
}

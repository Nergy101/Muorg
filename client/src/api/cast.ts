/**
 * Chromecast control for the desktop app.
 *
 * Split brain by necessity: discovery runs in the Tauri process (in-process
 * mDNS is what carries the macOS entitlements), while the session itself —
 * connect, stream, transport — is driven by the server sidecar over HTTP. The
 * HTTP half goes through the shared spec-typed client; the discovery half
 * through `invoke`.
 */

import { invoke } from "@tauri-apps/api/core";
import { api } from "./client";
import type { CastSessionStatus, CastDevice } from "../stores/cast";

export interface CastStatusResponse {
  session: CastSessionStatus;
  volume: number;
}

// ── Discovery: Tauri commands ────────────────────────────────────────────────

export function getDevices(): Promise<CastDevice[]> {
  return invoke<CastDevice[]>("cast_get_devices");
}

export async function startDiscovery(): Promise<void> {
  await invoke<void>("cast_start_discovery");
}

export async function stopDiscovery(): Promise<void> {
  await invoke<void>("cast_stop_discovery");
}

// ── Session: the sidecar's /api/cast/* routes ────────────────────────────────

export function getStatus(): Promise<CastStatusResponse> {
  return api.castStatus() as Promise<CastStatusResponse>;
}

export function castPlay(
  trackId: number,
  deviceAddress: string,
  devicePort: number,
): Promise<void> {
  return api.castPlay(deviceAddress, devicePort, trackId);
}

export function castPause(): Promise<void> {
  return api.castPause();
}

export function castResume(): Promise<void> {
  return api.castResume();
}

export function castStop(): Promise<void> {
  return api.castStop();
}

export function castSeek(
  positionSecs: number,
  wasPlaying: boolean,
): Promise<void> {
  return api.castSeek(positionSecs, wasPlaying);
}

export function setCastVolume(level: number): Promise<void> {
  return api.castSetVolume(level);
}

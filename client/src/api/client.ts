/**
 * HTTP client for muorg-server. Reads server URL and API key from localStorage,
 * switching between local and online credentials based on backendMode.
 */

import { createApi, createTransport } from "@shared/api";

export const BACKEND_MODE_KEY = "muorg-backend-mode";
export const LOCAL_API_KEY_KEY = "muorg-local-api-key";
export const ONLINE_SERVER_URL_KEY = "muorg-server-url";
export const ONLINE_API_KEY_KEY = "muorg-api-key";

export const LOCAL_SERVER_URL = "http://localhost:7700";
export const LOCAL_API_KEY_DEFAULT = "dev-key";

export function getBackendMode(): "local" | "online" {
  return localStorage.getItem(BACKEND_MODE_KEY) === "online" ? "online" : "local";
}

export function setBackendMode(mode: "local" | "online") {
  localStorage.setItem(BACKEND_MODE_KEY, mode);
}

export function getLocalApiKey(): string {
  return localStorage.getItem(LOCAL_API_KEY_KEY) ?? LOCAL_API_KEY_DEFAULT;
}

export function setLocalApiKey(key: string) {
  localStorage.setItem(LOCAL_API_KEY_KEY, key);
}

export function getOnlineServerUrl(): string {
  return localStorage.getItem(ONLINE_SERVER_URL_KEY) ?? "";
}

export function setOnlineServerUrl(url: string) {
  localStorage.setItem(ONLINE_SERVER_URL_KEY, url.replace(/\/$/, ""));
}

export function getOnlineApiKey(): string {
  return localStorage.getItem(ONLINE_API_KEY_KEY) ?? "";
}

export function setOnlineApiKey(key: string) {
  localStorage.setItem(ONLINE_API_KEY_KEY, key);
}

export function getServerUrl(): string {
  if (getBackendMode() === "local") return LOCAL_SERVER_URL;
  return getOnlineServerUrl() || LOCAL_SERVER_URL;
}

export function getApiKey(): string {
  if (getBackendMode() === "local") return LOCAL_API_KEY_DEFAULT;
  return getOnlineApiKey();
}

/** @deprecated use getOnlineServerUrl/setOnlineServerUrl directly */
export function setServerUrl(url: string) { setOnlineServerUrl(url); }
/** @deprecated use getOnlineApiKey/setOnlineApiKey directly */
export function setApiKey(key: string) { setOnlineApiKey(key); }

/**
 * The typed client. Request plumbing and every endpoint signature live in
 * `src/api/` at the repo root, shared with the web client — see
 * `src/api/README.md`.
 *
 * The credentials are read per request, not captured, because Settings can flip
 * between the bundled local sidecar and a remote server while the app runs.
 */
export const transport = createTransport({
  baseUrl: getServerUrl,
  apiKey: getApiKey,
});

export const api = createApi(transport);

// Kept as named exports so existing call sites need no change.
export const apiFetch = transport.fetchJson;
export const apiFetchBlob = transport.fetchBlob;
export const streamUrl = transport.streamUrl;

export { ApiError } from "@shared/api";

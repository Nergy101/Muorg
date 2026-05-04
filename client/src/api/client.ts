/**
 * HTTP client for muorg-server. Reads server URL and API key from localStorage,
 * switching between local and online credentials based on backendMode.
 */

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

function authHeaders(): Record<string, string> {
  const key = getApiKey();
  return key ? { Authorization: `Bearer ${key}` } : {};
}

export async function apiFetch<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const url = `${getServerUrl()}${path}`;
  const headers = new Headers({ ...authHeaders(), ...(options.headers as Record<string, string> | undefined) });
  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const body = await res.json() as { error?: string };
      if (body.error) msg = body.error;
    } catch { /* ignore */ }
    throw new Error(msg);
  }
  if (res.status === 204 || res.headers.get("content-length") === "0") {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export async function apiFetchBlob(path: string): Promise<Blob> {
  const url = `${getServerUrl()}${path}`;
  const headers = new Headers(authHeaders());
  const res = await fetch(url, { headers });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.blob();
}

export function streamUrl(trackId: number, token: string): string {
  return `${getServerUrl()}/stream/${trackId}?token=${encodeURIComponent(token)}`;
}

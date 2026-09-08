import { createApi, createTransport } from "@shared/api";

const SERVER_URL_KEY = "muorg-web-url";
const API_KEY_KEY = "muorg-web-key";

export function getServerUrl(): string {
  return localStorage.getItem(SERVER_URL_KEY) ?? "";
}

export function setServerUrl(url: string): void {
  localStorage.setItem(SERVER_URL_KEY, url.replace(/\/$/, ""));
}

export function getApiKey(): string {
  return localStorage.getItem(API_KEY_KEY) ?? "";
}

export function setApiKey(key: string): void {
  localStorage.setItem(API_KEY_KEY, key);
}

export function isConnected(): boolean {
  return !!getServerUrl();
}

export function disconnect(): void {
  localStorage.removeItem(SERVER_URL_KEY);
  localStorage.removeItem(API_KEY_KEY);
}

/**
 * The typed client. Request plumbing and every endpoint signature live in
 * `src/api/` at the repo root, shared with the desktop app — see
 * `src/api/README.md`.
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

/**
 * Probe a server the user just typed in, before we commit it to localStorage —
 * so it needs its own transport rather than the ambient one.
 */
export async function testConnection(url: string, key: string): Promise<void> {
  const probe = createTransport({
    baseUrl: () => url.replace(/\/$/, ""),
    apiKey: () => key,
  });
  await createApi(probe).health();
}

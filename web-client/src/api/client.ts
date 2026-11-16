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

let unauthorizedHandler: (() => void) | null = null;

/**
 * Register what happens when the server rejects our API key with a 401 —
 * e.g. the key was changed server-side. Wired to a full logout in main.ts,
 * which owns the router and stores (client.ts must not import them: they
 * import this module).
 */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

/**
 * The typed client. Request plumbing and every endpoint signature live in
 * `src/api/` at the repo root, shared with the desktop app — see
 * `src/api/README.md`.
 */
export const transport = createTransport({
  baseUrl: getServerUrl,
  apiKey: getApiKey,
  onUnauthorized: () => unauthorizedHandler?.(),
});

export const api = createApi(transport);

// There is no untyped `apiFetch` any more: every request goes through the
// spec-typed client above, so a call to a path the server does not serve is a
// compile error. `streamUrl` stays because an <audio src> is a URL, not a fetch.
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

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

function authHeaders(): Record<string, string> {
  const key = getApiKey();
  return key ? { Authorization: `Bearer ${key}` } : {};
}

export async function apiFetch<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const url = `${getServerUrl()}${path}`;
  const headers = new Headers({
    ...authHeaders(),
    ...(options.headers as Record<string, string> | undefined),
  });
  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const body = (await res.json()) as { error?: string };
      if (body.error) msg = body.error;
    } catch {
      /* ignore */
    }
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

export function streamUrl(trackId: number, token: string, startSecs?: number): string {
  const base = `${getServerUrl()}/stream/${trackId}?token=${encodeURIComponent(token)}`;
  return startSecs != null && startSecs > 0
    ? `${base}&start=${startSecs.toFixed(2)}`
    : base;
}

export async function testConnection(url: string, key: string): Promise<void> {
  const cleanUrl = url.replace(/\/$/, "");
  const headers = new Headers(key ? { Authorization: `Bearer ${key}` } : {});
  const res = await fetch(`${cleanUrl}/api/health`, { headers });
  if (!res.ok) throw new Error(`Server responded with HTTP ${res.status}`);
}

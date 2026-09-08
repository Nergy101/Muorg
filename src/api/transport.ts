/**
 * The HTTP plumbing shared by the desktop and web clients.
 *
 * Both apps had their own copy of this — same `apiFetch`, same error unwrapping,
 * same `streamUrl` — differing only in where the base URL and API key come from.
 * That is now injected via {@link Credentials}, so the request handling itself
 * exists once.
 */

/**
 * How an app resolves its current server. Read on every request rather than
 * captured, because both apps let the user switch servers at runtime.
 */
export interface Credentials {
  /** Base URL with no trailing slash, e.g. `http://localhost:7700`. */
  baseUrl(): string;
  /** Bearer token, or `""` when the server is unauthenticated. */
  apiKey(): string;
}

/** An error carrying the HTTP status, so callers can branch on 404 vs 401. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly path: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface Transport {
  /** JSON request/response. Returns `undefined` for 204 and empty bodies. */
  fetchJson<T = unknown>(path: string, options?: RequestInit): Promise<T>;
  /** Same, but also hands back the response headers (for `X-Total-Count`). */
  fetchJsonWithHeaders<T = unknown>(
    path: string,
    options?: RequestInit,
  ): Promise<{ data: T; headers: Headers }>;
  fetchBlob(path: string): Promise<Blob>;
  /** Absolute, token-authorised `<audio src>` URL. */
  streamUrl(trackId: number, token: string, startSecs?: number): string;
  baseUrl(): string;
}

export function createTransport(credentials: Credentials): Transport {
  function authHeaders(): Record<string, string> {
    const key = credentials.apiKey();
    return key ? { Authorization: `Bearer ${key}` } : {};
  }

  function url(path: string): string {
    return `${credentials.baseUrl()}${path}`;
  }

  async function request(path: string, options: RequestInit = {}): Promise<Response> {
    const headers = new Headers({
      ...authHeaders(),
      ...(options.headers as Record<string, string> | undefined),
    });
    const res = await fetch(url(path), { ...options, headers });
    if (!res.ok) {
      // The server reports failures as {"error": "..."} (routes::dto::ErrorResponse);
      // fall back to the bare status when the body is not JSON.
      let message = `HTTP ${res.status}`;
      try {
        const body = (await res.json()) as { error?: string };
        if (body.error) message = body.error;
      } catch {
        /* not a JSON body */
      }
      throw new ApiError(message, res.status, path);
    }
    return res;
  }

  function isEmpty(res: Response): boolean {
    return res.status === 204 || res.headers.get("content-length") === "0";
  }

  return {
    baseUrl: () => credentials.baseUrl(),

    async fetchJson<T>(path: string, options: RequestInit = {}): Promise<T> {
      const res = await request(path, options);
      if (isEmpty(res)) return undefined as T;
      return (await res.json()) as T;
    },

    async fetchJsonWithHeaders<T>(path: string, options: RequestInit = {}) {
      const res = await request(path, options);
      const data = isEmpty(res) ? (undefined as T) : ((await res.json()) as T);
      return { data, headers: res.headers };
    },

    async fetchBlob(path: string): Promise<Blob> {
      const res = await request(path);
      return res.blob();
    },

    streamUrl(trackId: number, token: string, startSecs?: number): string {
      const base = `${credentials.baseUrl()}/stream/${trackId}?token=${encodeURIComponent(token)}`;
      return startSecs != null && startSecs > 0
        ? `${base}&start=${startSecs.toFixed(2)}`
        : base;
    },
  };
}

/** JSON body helper — every mutation in `endpoints.ts` posts the same way. */
export function jsonBody(value: unknown): RequestInit {
  return {
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(value),
  };
}

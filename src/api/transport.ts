/**
 * The HTTP plumbing shared by the desktop and web clients.
 *
 * This is `openapi-fetch` wired to the generated `paths` type, so the request
 * layer is checked against the spec end to end: a URL that is not a real path,
 * a method that path does not serve, a body of the wrong shape, or a missing
 * path parameter are all compile errors rather than 404s at runtime.
 *
 * The only thing each app does differently is where its base URL and API key
 * come from — injected as {@link Credentials} and read per request, because
 * both apps let the user change server while running.
 */

import createClient, { type Client } from "openapi-fetch";
import type { paths } from "./schema";
import type { Present } from "./types";

/**
 * How an app resolves its current server. Functions, not values: the desktop
 * app switches between the bundled local sidecar and a remote server from
 * Settings, and the web app can be re-pointed at any time.
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

export type MuorgClient = Client<paths>;

/**
 * Stand-in origin handed to openapi-fetch, swapped for the real server in the
 * custom `fetch` below.
 *
 * openapi-fetch needs *an* absolute base to build a `Request` — an empty base
 * yields a relative "/api/health", which only resolves where a document origin
 * exists (fine in a browser, throws in Node, a worker or a test). `.invalid` is
 * reserved by RFC 2606 and can never resolve, so if the rewrite were ever
 * skipped the request fails loudly instead of quietly reaching a real host.
 */
const PLACEHOLDER_ORIGIN = "http://muorg.invalid";

export interface Transport {
  /** The spec-typed client. Every call is checked against `paths`. */
  readonly client: MuorgClient;
  /**
   * Unwrap an openapi-fetch result: return `data`, or throw {@link ApiError}.
   *
   * The stores in both apps are written around exceptions (`try/catch` setting
   * `error`), so the `{ data, error }` result is unwrapped here rather than
   * pushed out to ~40 call sites.
   */
  unwrap<T>(
    result: { data?: T; error?: unknown; response: Response },
    path: string,
  ): Present<T>;
  /** Bytes rather than JSON — album art. */
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

  const client = createClient<paths>({
    // The real server is resolved per request instead of being baked in here,
    // because both apps can be re-pointed at another server while running.
    baseUrl: PLACEHOLDER_ORIGIN,
    async fetch(request) {
      const { pathname, search } = new URL(request.url);
      const target = `${credentials.baseUrl()}${pathname}${search}`;
      const next = new Request(target, request);
      for (const [name, value] of Object.entries(authHeaders())) {
        next.headers.set(name, value);
      }
      return globalThis.fetch(next);
    },
  });

  return {
    client,
    baseUrl: () => credentials.baseUrl(),

    unwrap<T>(
      result: { data?: T; error?: unknown; response: Response },
      path: string,
    ): Present<T> {
      // Test the response, not `error`. openapi-fetch only populates `error`
      // when the failure has a parseable body, and the auth middleware answers
      // a bad API key with a bare 401 and content-length: 0 — which would
      // otherwise fall through as `data: undefined` and read to the caller as
      // an empty library rather than an auth failure.
      if (!result.response.ok) {
        // Failures normally carry {"error": "..."} (routes::dto::ErrorResponse);
        // fall back to the bare status when the body is empty or another shape.
        const body = result.error as { error?: string } | undefined;
        const message =
          typeof body?.error === "string"
            ? body.error
            : `HTTP ${result.response.status}`;
        throw new ApiError(message, result.response.status, path);
      }
      // 204 and empty bodies parse to undefined; callers of those return void.
      return result.data as Present<T>;
    },

    async fetchBlob(path: string): Promise<Blob> {
      const headers = new Headers(authHeaders());
      const res = await globalThis.fetch(`${credentials.baseUrl()}${path}`, {
        headers,
      });
      if (!res.ok) throw new ApiError(`HTTP ${res.status}`, res.status, path);
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

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
  /**
   * Called when the server rejects the API key with a 401 on an `/api/*`
   * request — e.g. after the key was changed server-side. Apps use it to
   * drop their stored credentials and send the user back to log in again.
   * Not fired for `/stream/*` 401s: those carry short-lived stream tokens,
   * not the API key.
   */
  onUnauthorized?(): void;
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
  /**
   * Absolute, token-authorised URL for the track's original FLAC bytes
   * (`/stream/flac/{token}/{id}`). Only served when the server advertises
   * `flac: true`; no `?start=` — FLAC seeks natively via byte ranges.
   */
  flacStreamUrl(trackId: number, token: string): string;
  /**
   * Absolute, token-authorised URL for the track's original WAV bytes
   * (`/stream/wav/{token}/{id}`). Only served when the server advertises
   * `wav: true`; no `?start=` — WAV seeks natively via byte ranges.
   */
  wavStreamUrl(trackId: number, token: string): string;
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
      // Read the body as text and rebuild the Request from scratch. Cloning
      // via `new Request(target, request)` transfers the body as a
      // ReadableStream, which some browsers silently drop on cross-origin
      // POST/PATCH/DELETE — the server then sees an empty body and rejects
      // the call. Every API body is JSON text, so reading it as text is safe.
      // Note: `request.body` is null in Firefox even when a body is present,
      // so the body must be read via clone().text() unconditionally — gating
      // on request.body sends an empty body there (server: 400 EOF).
      const bodyText = await request.clone().text();
      const next = new Request(target, {
        method: request.method,
        headers: request.headers,
        body: bodyText.length > 0 ? bodyText : undefined,
      });
      for (const [name, value] of Object.entries(authHeaders())) {
        next.headers.set(name, value);
      }
      const response = await globalThis.fetch(next);
      if (response.status === 401 && pathname.startsWith("/api/")) {
        credentials.onUnauthorized?.();
      }
      return response;
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
        // Album art is mutable (the metadata editor rewrites it), so every
        // fetch must revalidate with the server instead of trusting the HTTP
        // cache. Older servers sent covers with `max-age=86400`; those cached
        // entries stay "fresh" for a day and would keep serving the OLD art
        // after a cover write without the server ever being asked. An
        // unchanged cover still costs only a 304 thanks to the server's ETag.
        cache: "no-cache",
      });
      if (!res.ok) {
        if (res.status === 401) credentials.onUnauthorized?.();
        throw new ApiError(`HTTP ${res.status}`, res.status, path);
      }
      return res.blob();
    },

    streamUrl(trackId: number, token: string, startSecs?: number): string {
      // Token in the path, not the query string: query strings land in proxy
      // access logs, browser history and Referer headers.
      const base = `${credentials.baseUrl()}/stream/${encodeURIComponent(token)}/${trackId}`;
      return startSecs != null && startSecs > 0
        ? `${base}?start=${startSecs.toFixed(2)}`
        : base;
    },

    flacStreamUrl(trackId: number, token: string): string {
      return `${credentials.baseUrl()}/stream/flac/${encodeURIComponent(token)}/${trackId}`;
    },

    wavStreamUrl(trackId: number, token: string): string {
      return `${credentials.baseUrl()}/stream/wav/${encodeURIComponent(token)}/${trackId}`;
    },
  };
}

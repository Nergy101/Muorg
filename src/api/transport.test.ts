import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createTransport } from "./transport";

/**
 * Records what the client actually put on the wire and replies with whatever
 * the test asks for. The point of most of these is the request: the URL the
 * client builds is the thing that silently 404s in production.
 *
 * Calls arrive in two shapes — openapi-fetch hands over a `Request`, while
 * `fetchBlob` passes a URL string plus init — so both are normalised to
 * `{ url, headers }`.
 */
interface SeenRequest {
  url: string;
  headers: Headers;
}

function stubFetch(reply: (url: string) => Response) {
  const seen: SeenRequest[] = [];
  const spy = vi.fn(async (input: Request | string, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.url;
    const headers =
      typeof input === "string"
        ? new Headers(init?.headers)
        : input.headers;
    seen.push({ url, headers });
    return reply(url);
  });
  vi.stubGlobal("fetch", spy);
  return seen;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const creds = { baseUrl: () => "http://server.test:7700", apiKey: () => "k" };

afterEach(() => vi.unstubAllGlobals());

describe("request building", () => {
  it("re-points the placeholder origin at the configured server", async () => {
    const seen = stubFetch(() => json([]));
    await createTransport(creds).client.GET("/api/roots");

    expect(seen).toHaveLength(1);
    expect(seen[0].url).toBe("http://server.test:7700/api/roots");
    // The placeholder must never survive into a real request.
    expect(seen[0].url).not.toContain("muorg.invalid");
  });

  it("resolves the server per request, not at construction", async () => {
    let current = "http://first.test";
    const seen = stubFetch(() => json([]));
    const transport = createTransport({ baseUrl: () => current, apiKey: () => "" });

    await transport.client.GET("/api/roots");
    current = "http://second.test";
    await transport.client.GET("/api/roots");

    expect(seen.map((r) => new URL(r.url).host)).toEqual([
      "first.test",
      "second.test",
    ]);
  });

  it("expands path parameters and query strings", async () => {
    const seen = stubFetch(() => json({}));
    await createTransport(creds).client.GET("/api/tracks/{id}/metadata", {
      params: { path: { id: 42 } },
    });
    await createTransport(creds).client.GET("/api/tracks", {
      params: { query: { offset: 500, limit: 250 } },
    });

    expect(seen[0].url).toBe("http://server.test:7700/api/tracks/42/metadata");
    const second = new URL(seen[1].url);
    expect(second.pathname).toBe("/api/tracks");
    expect(second.searchParams.get("offset")).toBe("500");
    expect(second.searchParams.get("limit")).toBe("250");
  });

  it("sends the API key as a bearer token", async () => {
    const seen = stubFetch(() => json([]));
    await createTransport(creds).client.GET("/api/roots");
    expect(seen[0].headers.get("Authorization")).toBe("Bearer k");
  });

  it("omits the header entirely when there is no key", async () => {
    const seen = stubFetch(() => json([]));
    await createTransport({ baseUrl: () => "http://s.test", apiKey: () => "" })
      .client.GET("/api/roots");
    expect(seen[0].headers.get("Authorization")).toBeNull();
  });
});

describe("unwrap", () => {
  const t = () => createTransport(creds);

  it("returns the body on success", () => {
    const result = { data: { ok: true }, response: new Response(null, { status: 200 }) };
    expect(t().unwrap(result, "/x")).toEqual({ ok: true });
  });

  it("throws ApiError carrying the status and path", () => {
    const result = {
      error: { error: "Track 9 not found" },
      response: new Response(null, { status: 404 }),
    };
    expect(() => t().unwrap(result, "/api/tracks/9")).toThrowError(ApiError);
    try {
      t().unwrap(result, "/api/tracks/9");
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).status).toBe(404);
      expect((e as ApiError).path).toBe("/api/tracks/9");
      expect((e as ApiError).message).toBe("Track 9 not found");
    }
  });

  it("throws on a failure with an empty body", () => {
    // Regression: the auth middleware answers a bad API key with a bare 401 and
    // content-length: 0. openapi-fetch leaves both `data` and `error` unset for
    // that, so testing `error` let it through as `undefined` — which every
    // caller would have rendered as an empty library rather than an auth error.
    const result = { response: new Response(null, { status: 401 }) };
    expect(() => t().unwrap(result, "/api/roots")).toThrowError(ApiError);
    try {
      t().unwrap(result, "/api/roots");
    } catch (e) {
      expect((e as ApiError).status).toBe(401);
      expect((e as ApiError).message).toBe("HTTP 401");
    }
  });

  it("falls back to the status when the error body is not the expected shape", () => {
    const result = {
      error: "<html>502 Bad Gateway</html>",
      response: new Response(null, { status: 502 }),
    };
    try {
      t().unwrap(result, "/api/stats");
    } catch (e) {
      expect((e as ApiError).message).toBe("HTTP 502");
    }
  });

  it("passes an empty 204 body through as undefined", () => {
    const result = { response: new Response(null, { status: 204 }) };
    expect(t().unwrap(result, "/api/cast/pause")).toBeUndefined();
  });
});

describe("fetchBlob", () => {
  it("hits the configured server with auth and returns bytes", async () => {
    const seen = stubFetch(() => new Response(new Blob(["img"])));
    const blob = await createTransport(creds).fetchBlob("/api/tracks/1/cover");

    expect(seen[0].url).toBe("http://server.test:7700/api/tracks/1/cover");
    expect(await blob.text()).toBe("img");
  });

  it("throws ApiError on a non-2xx", async () => {
    stubFetch(() => new Response(null, { status: 404 }));
    await expect(
      createTransport(creds).fetchBlob("/api/tracks/1/cover"),
    ).rejects.toThrowError(ApiError);
  });
});

describe("streamUrl", () => {
  const t = createTransport(creds);

  it("builds an absolute, token-authorised URL", () => {
    expect(t.streamUrl(7, "tok")).toBe(
      "http://server.test:7700/stream/7?token=tok",
    );
  });

  it("escapes a token with URL-unsafe characters", () => {
    expect(t.streamUrl(7, "a+b/c=")).toContain("token=a%2Bb%2Fc%3D");
  });

  it("appends a seek offset only when it is past the start", () => {
    expect(t.streamUrl(7, "tok", 0)).not.toContain("start=");
    expect(t.streamUrl(7, "tok", 12.345)).toContain("&start=12.35");
  });
});

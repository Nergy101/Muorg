import { afterEach, describe, expect, it, vi } from "vitest";
import { createApi } from "./endpoints";
import { createTransport } from "./transport";
import { TRACKS_PAGE_SIZE } from "./types";

/**
 * A fake catalog served through the real transport, so these exercise the
 * actual URL building and response handling rather than a mock of them.
 */
function serveCatalog(total: number, opts: { pageCap?: number } = {}) {
  const requests: URL[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (req: Request) => {
      const url = new URL(req.url);
      requests.push(url);
      const offset = Number(url.searchParams.get("offset") ?? 0);
      const limit = Math.min(
        Number(url.searchParams.get("limit") ?? TRACKS_PAGE_SIZE),
        opts.pageCap ?? Number.MAX_SAFE_INTEGER,
      );
      const rows = Array.from(
        { length: Math.max(0, Math.min(limit, total - offset)) },
        (_, i) => ({ id: offset + i, path: `/m/${offset + i}.mp3`, format: "mp3" }),
      );
      return new Response(JSON.stringify(rows), {
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "X-Total-Count": String(total),
        },
      });
    }),
  );
  return requests;
}

const api = () =>
  createApi(
    createTransport({ baseUrl: () => "http://s.test", apiKey: () => "k" }),
  );

afterEach(() => vi.unstubAllGlobals());

describe("getTracksPage", () => {
  it("reports the catalog total from X-Total-Count, not the page length", async () => {
    serveCatalog(3015);
    const page = await api().getTracksPage(0, 500);
    expect(page.tracks).toHaveLength(500);
    expect(page.total).toBe(3015);
  });

  it("falls back to the page length when the header is missing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify([{ id: 1 }]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })),
    );
    expect((await api().getTracksPage()).total).toBe(1);
  });
});

describe("streamTracks", () => {
  it("yields the first page before fetching the rest", async () => {
    // The whole point of streaming: the UI paints page one while later pages
    // are still in flight. Before this, the desktop app awaited all seven
    // round-trips behind a spinner.
    const requests = serveCatalog(3015);
    const iterator = api().streamTracks()[Symbol.asyncIterator]();

    const first = await iterator.next();
    expect(first.value?.tracks).toHaveLength(500);
    expect(requests).toHaveLength(1);
  });

  it("walks the whole catalog when the total is not a page multiple", async () => {
    serveCatalog(3015);
    const pages = [];
    for await (const page of api().streamTracks()) pages.push(page);

    expect(pages.map((p) => p.tracks.length)).toEqual([
      500, 500, 500, 500, 500, 500, 15,
    ]);
    expect(pages.reduce((n, p) => n + p.tracks.length, 0)).toBe(3015);
  });

  it("stops cleanly on an exact page multiple instead of looping", async () => {
    serveCatalog(1000);
    const pages = [];
    for await (const page of api().streamTracks()) pages.push(page);
    expect(pages).toHaveLength(2);
  });

  it("makes one request for an empty catalog", async () => {
    const requests = serveCatalog(0);
    const pages = [];
    for await (const page of api().streamTracks()) pages.push(page);

    expect(pages).toEqual([{ tracks: [], total: 0 }]);
    expect(requests).toHaveLength(1);
  });

  it("stops when the server runs out of rows early", async () => {
    // A concurrent rescan can shrink the library after the first page reported
    // a larger total; the loop must not spin on empty responses.
    let total = 2000;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (req: Request) => {
        const offset = Number(new URL(req.url).searchParams.get("offset") ?? 0);
        const rows = offset === 0 ? [{ id: 1 }] : [];
        const body = JSON.stringify(rows);
        total = 2000;
        return new Response(body, {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "X-Total-Count": String(total),
          },
        });
      }),
    );

    const pages = [];
    for await (const page of api().streamTracks()) pages.push(page);
    expect(pages).toHaveLength(1);
  });

  it("honours a smaller page size", async () => {
    const requests = serveCatalog(25, { pageCap: 10 });
    const pages = [];
    for await (const page of api().streamTracks(10)) pages.push(page);

    expect(pages.map((p) => p.tracks.length)).toEqual([10, 10, 5]);
    expect(requests.every((u) => u.searchParams.get("limit") === "10")).toBe(true);
  });
});

describe("fetchAllTracks", () => {
  it("flattens every page into one array", async () => {
    serveCatalog(1200);
    const all = await api().fetchAllTracks();
    expect(all).toHaveLength(1200);
    expect(all[0].id).toBe(0);
    expect(all[1199].id).toBe(1199);
  });
});

describe("endpoints that reshape the response", () => {
  function replyOnce(body: unknown) {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })),
    );
  }

  it("getStreamToken returns the bare token", async () => {
    replyOnce({ token: "abc123" });
    expect(await api().getStreamToken(3)).toBe("abc123");
  });

  it("rescan returns the added count", async () => {
    replyOnce({ tracks_added: 42 });
    expect(await api().rescan("/music")).toBe(42);
  });

  it("getBackupDirectory returns the bare path", async () => {
    replyOnce({ path: "/var/backups" });
    expect(await api().getBackupDirectory()).toBe("/var/backups");
  });

  it("autoTagSuggestions returns the candidates array", async () => {
    replyOnce({ candidates: [{ mbid: "x", title: "t", artist: "a", confidence: 1 }] });
    const candidates = await api().autoTagSuggestions(1);
    expect(candidates).toHaveLength(1);
    expect(candidates[0].mbid).toBe("x");
  });

  it("getLyrics resolves to null rather than throwing on a 404", async () => {
    // Most tracks have no lyrics; a missing one is not an error worth a catch
    // at every call site.
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 404 })));
    expect(await api().getLyrics(1)).toBeNull();
  });
});

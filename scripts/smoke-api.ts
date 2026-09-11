/**
 * Exercises the shared API client against a running muorg-server, and checks
 * every response against the schema the spec declares for it.
 *
 * The three layers each prove something different:
 *   - TypeScript proves the client matches the spec;
 *   - the call-surface tests prove each wrapper hits the route it claims;
 *   - this proves the *server* matches the spec, and that requests assemble at
 *     runtime.
 *
 * It has already earned its keep: it caught a base URL that only resolved
 * inside a browser, a bare 401 being read as an empty library, and a plain-text
 * health probe parsed as JSON — none of which the compiler could see.
 *
 * The runner seeds the server's library from the audio fixtures before starting
 * it. That matters: against an empty library every list response validates as
 * `[]`, which satisfies any item schema, so `CatalogTrack`, `TrackMetadata`,
 * the cover bytes and the stream were all nominally checked and actually
 * untouched. The cases below work from a real row.
 *
 * Usage — `./scripts/smoke-api.sh` starts a throwaway server and runs this, or
 * point it at your own:
 *
 *   MUORG_URL=http://127.0.0.1:7700 MUORG_KEY=dev-key pnpm smoke:api
 */
import { readFileSync } from "node:fs";
import Ajv, { type ValidateFunction } from "ajv";
import { createApi, createTransport, ApiError } from "../src/api/index";

const BASE = process.env.MUORG_URL ?? "http://127.0.0.1:7700";
const KEY = process.env.MUORG_KEY ?? "dev-key";

const api = createApi(
  createTransport({ baseUrl: () => BASE, apiKey: () => KEY }),
);

// ── Schema validation ───────────────────────────────────────────────────────

const spec = JSON.parse(
  readFileSync(new URL("../server/openapi.json", import.meta.url), "utf8"),
) as {
  paths: Record<string, Record<string, unknown>>;
  components: { schemas: Record<string, unknown> };
};

// utoipa emits OpenAPI 3.1, which is JSON Schema 2020-12.
const ajv = new Ajv({ strict: false, allErrors: true });
// OpenAPI's numeric formats are annotations, not constraints Ajv knows; declare
// them so it validates the type and stays quiet about the format.
for (const format of ["int32", "int64", "float", "double"]) {
  ajv.addFormat(format, true);
}
for (const [name, schema] of Object.entries(spec.components.schemas)) {
  ajv.addSchema(schema as object, `#/components/schemas/${name}`);
}

const validators = new Map<string, ValidateFunction>();

/** The 200-response schema for an operation, compiled and cached. */
function validatorFor(path: string, method: string): ValidateFunction | null {
  const key = `${method} ${path}`;
  const cached = validators.get(key);
  if (cached) return cached;

  const operation = spec.paths[path]?.[method] as
    | { responses?: Record<string, { content?: Record<string, { schema?: object }> }> }
    | undefined;
  const schema = operation?.responses?.["200"]?.content?.["application/json"]?.schema;
  if (!schema) return null;

  const validate = ajv.compile(schema);
  validators.set(key, validate);
  return validate;
}

// ── Harness ─────────────────────────────────────────────────────────────────

let failures = 0;

function fail(name: string, detail: string) {
  failures++;
  console.log(`  FAIL ${name}\n       ${detail}`);
}

/** Record a failure when `condition` does not hold, without aborting the run. */
function expect(name: string, condition: boolean, detail: string) {
  if (condition) {
    console.log(`  ok   ${name}`);
  } else {
    fail(name, detail);
  }
}

/**
 * Run one call and, when `spec` names the operation it came from, check the
 * response body against that operation's declared schema.
 */
async function check(
  name: string,
  call: () => Promise<unknown>,
  operation?: { path: string; method: string },
) {
  let value: unknown;
  try {
    value = await call();
  } catch (e) {
    fail(name, e instanceof Error ? e.message : String(e));
    return;
  }

  if (operation && value !== undefined) {
    const validate = validatorFor(operation.path, operation.method);
    if (validate && !validate(value)) {
      const errors = (validate.errors ?? [])
        .map((e) => `${e.instancePath || "<root>"} ${e.message}`)
        .join("; ");
      fail(name, `response does not match its schema: ${errors}`);
      return;
    }
  }

  const preview = JSON.stringify(value)?.slice(0, 80) ?? "undefined";
  console.log(`  ok   ${name} -> ${preview}`);
}

// ── Cases ───────────────────────────────────────────────────────────────────

console.log(`-- against ${BASE}`);

console.log("\n-- plain GET, no params");
await check("health", () => api.health());
await check("getRoots", () => api.getRoots(), { path: "/api/roots", method: "get" });
await check("getStats", () => api.getStats(), { path: "/api/stats", method: "get" });

console.log("\n-- GET with query params (the paginated one)");
await check("getTracksPage", () => api.getTracksPage(0, 5));
await check("searchTracks", () => api.searchTracks("nothing"), {
  path: "/api/search",
  method: "get",
});
await check("getRecentlyAdded", () => api.getRecentlyAdded(5), {
  path: "/api/tracks/recently-added",
  method: "get",
});
await check("getRecentlyPlayed", () => api.getRecentlyPlayed(5), {
  path: "/api/play-history/recent",
  method: "get",
});
await check("getMostPlayed", () => api.getMostPlayed(5), {
  path: "/api/play-history/top",
  method: "get",
});

// ── The seeded library ──────────────────────────────────────────────────────
//
// Everything below works from whatever the server actually scanned, so the
// fixtures can change without editing assertions here.

console.log("\n-- the catalog has real rows");
const page = await api.getTracksPage(0, 50);
expect(
  "the seeded library was scanned",
  page.tracks.length >= 3 && page.total >= 3,
  `expected at least 3 tracks, got ${page.tracks.length} of ${page.total}`,
);

// Validate each row against the component schema rather than the list's — an
// empty library satisfies the list schema without ever touching an item.
const trackValidator = ajv.getSchema("#/components/schemas/CatalogTrack");
if (trackValidator) {
  const bad = page.tracks.find((t) => !trackValidator(t));
  expect(
    "every track matches CatalogTrack",
    bad === undefined,
    `row ${JSON.stringify(bad)?.slice(0, 120)} failed: ${(trackValidator.errors ?? [])
      .map((e) => `${e.instancePath || "<root>"} ${e.message}`)
      .join("; ")}`,
  );
} else {
  fail("every track matches CatalogTrack", "CatalogTrack is not in the spec's schemas");
}

expect(
  "pagination reports a total beyond the page",
  (await api.getTracksPage(0, 1)).total === page.total,
  "X-Total-Count should be the library size, not the page size",
);

const track = page.tracks[0];
const withCover = page.tracks.find((t) => t.has_cover);
const withoutCover = page.tracks.find((t) => !t.has_cover);

if (!track) {
  fail("seeded library", "no tracks to exercise the per-track routes with");
} else {
  console.log("\n-- per-track routes against a real track");
  await check("getMetadata", () => api.getMetadata(track.id), {
    path: "/api/tracks/{id}/metadata",
    method: "get",
  });
  await check("getLyrics (none embedded -> null)", async () => {
    const lyrics = await api.getLyrics(track.id);
    // The fixtures carry no lyrics, so this is the 404-becomes-null path —
    // the one that would otherwise throw and break a track sheet.
    if (lyrics !== null) throw new Error(`expected null, got ${JSON.stringify(lyrics)}`);
    return null;
  });

  console.log("\n-- cover bytes");
  if (withCover) {
    await check("getCoverBlob", async () => {
      const blob = await api.getCoverBlob(withCover.id);
      if (blob.size === 0) throw new Error("cover was empty");
      if (!blob.type.startsWith("image/")) throw new Error(`cover type was ${blob.type}`);
      return `${blob.type} ${blob.size}B`;
    });
  } else {
    fail("getCoverBlob", "no fixture reported has_cover, so the cover route is untested");
  }
  if (withoutCover) {
    await check("a track with no art 404s rather than serving empty bytes", async () => {
      try {
        await api.getCoverBlob(withoutCover.id);
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return "ApiError status=404";
        throw e;
      }
      throw new Error("expected a 404");
    });
  }

  console.log("\n-- streaming");
  let token: string | null = null;
  await check("getStreamToken", async () => {
    token = await api.getStreamToken(track.id);
    if (!token) throw new Error("empty token");
    return `${token.slice(0, 8)}…`;
  });
  if (token) {
    const url = api.streamUrl(track.id, token);
    await check("the stream serves audio for that token", async () => {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const bytes = await res.arrayBuffer();
      if (bytes.byteLength === 0) throw new Error("stream was empty");
      return `${res.headers.get("content-type")} ${bytes.byteLength}B`;
    });
    await check("the stream honours a Range request", async () => {
      // Seeking in the browser depends on this; a server that ignores Range
      // and returns 200 makes the player restart instead of jumping.
      const res = await fetch(url, { headers: { Range: "bytes=0-99" } });
      if (res.status !== 206) throw new Error(`expected 206, got ${res.status}`);
      const bytes = await res.arrayBuffer();
      if (bytes.byteLength !== 100) throw new Error(`got ${bytes.byteLength} bytes, wanted 100`);
      return `206 ${res.headers.get("content-range")}`;
    });
    await check("another track's id cannot ride the same token", async () => {
      const other = page.tracks.find((t) => t.id !== track.id);
      if (!other) return "only one track; skipped";
      const res = await fetch(api.streamUrl(other.id, token!));
      if (res.status !== 401) throw new Error(`expected 401, got ${res.status}`);
      return "401";
    });
  }

  console.log("\n-- writes: tags, rating, plays");
  // A title unique to this run, so the search below cannot match a leftover.
  const marker = `Smoke ${Date.now()}`;
  await check("patchMetadata", () =>
    api.patchMetadata(track.id, {
      title: marker,
      artist: "Smoke Artist",
      album: "Smoke Album",
    }),
  );
  await check("the write is visible in the catalog", async () => {
    const after = (await api.getTracksPage(0, 50)).tracks.find((t) => t.id === track.id);
    if (after?.title !== marker) throw new Error(`title is ${after?.title}`);
    return after.title;
  });
  await check("search finds the tag that was just written", async () => {
    // Also proves the FTS index is maintained on update, not only on scan.
    const hits = await api.searchTracks("Smoke Artist");
    if (!hits.some((t) => t.id === track.id)) throw new Error("the updated track was not found");
    return `${hits.length} hit(s)`;
  });
  await check("patchMetadataBatch", () =>
    api.patchMetadataBatch(
      page.tracks.map((t) => ({ id: t.id, album_artist: "Smoke Compilation" })),
    ),
  );
  await check("setRating", () => api.setRating(track.id, 4));
  await check("recordPlay", () => api.recordPlay(track.id));
  await check("the play shows up in recently played", async () => {
    const recent = await api.getRecentlyPlayed(5);
    if (!recent.some((t) => t.id === track.id)) throw new Error("not in recently played");
    return `${recent.length} entry(s)`;
  });
  await check("the play shows up in most played", async () => {
    const top = await api.getMostPlayed(5);
    if (!top.some((t) => t.id === track.id)) throw new Error("not in most played");
    return `${top.length} entry(s)`;
  });
  await check("stats reflect the seeded library", async () => {
    const stats = await api.getStats();
    if (stats.track_count < 3) throw new Error(`track_count is ${stats.track_count}`);
    return JSON.stringify(stats);
  });

  console.log("\n-- backups");
  await check("a backup is taken when asked for one", async () => {
    await api.patchMetadata(track.id, { genre: "Smoke" }, true);
    const backup = await api.getLatestBackup(track.id);
    if (!backup?.backup_path) throw new Error("no backup was recorded");
    return backup.backup_path.split("/").pop() ?? "";
  });
  await check("restoreFromBackup", () => api.restoreFromBackup(track.id));
}

console.log("\n-- POST with a body, and the path-param form");
const playlistName = `smoke-${Date.now()}`;
let playlistId: number | null = null;
await check(
  "createPlaylist",
  async () => {
    const created = await api.createPlaylist(playlistName);
    playlistId = created.id;
    return created;
  },
  { path: "/api/playlists", method: "post" },
);
await check("getPlaylists", () => api.getPlaylists(), {
  path: "/api/playlists",
  method: "get",
});
if (playlistId != null) {
  const id = playlistId;
  await check("getPlaylistTracks", () => api.getPlaylistTracks(id), {
    path: "/api/playlists/{id}/tracks",
    method: "get",
  });
  await check("getPlaylistEntries", () => api.getPlaylistEntries(id), {
    path: "/api/playlists/{id}/entries",
    method: "get",
  });
  await check("updatePlaylist", () => api.updatePlaylist(id, { name: `${playlistName}-renamed` }));

  // With a seeded library the membership routes can carry real ids, which is
  // what exercises the join back to `tracks`.
  const ids = page.tracks.map((t) => t.id);
  if (ids.length > 0) {
    await check("addTracksToPlaylist", () => api.addTracksToPlaylist(id, ids));
    await check("the tracks are in the playlist", async () => {
      const inPlaylist = await api.getPlaylistTracks(id);
      if (inPlaylist.length !== ids.length) {
        throw new Error(`expected ${ids.length}, got ${inPlaylist.length}`);
      }
      return `${inPlaylist.length} track(s)`;
    });
    await check("reorderPlaylistTracks", () => api.reorderPlaylistTracks(id, [...ids].reverse()));
    await check("the new order stuck", async () => {
      // This route returns bare track ids, not rows.
      const ordered = await api.getPlaylistTracks(id);
      const expected = [...ids].reverse();
      if (ordered.join(",") !== expected.join(",")) {
        throw new Error(`got ${ordered.join(",")}, wanted ${expected.join(",")}`);
      }
      return expected.join(",");
    });
    await check("removePlaylistEntry", async () => {
      const entries = await api.getPlaylistEntries(id);
      const first = entries[0];
      if (!first) throw new Error("no entries to remove");
      await api.removePlaylistEntry(id, first.entry_id);
      const left = await api.getPlaylistEntries(id);
      if (left.length !== entries.length - 1) throw new Error("nothing was removed");
      return `${left.length} left`;
    });
    await check("removeTracksFromPlaylist", () => api.removeTracksFromPlaylist(id, ids));
  }

  await check("deletePlaylist", () => api.deletePlaylist(id));
}

console.log("\n-- smart playlists resolve against the library");
await check("createSmartPlaylist and resolve it", async () => {
  const rules = JSON.stringify([
    { field: "album_artist", op: "eq", value: "Smoke Compilation" },
  ]);
  const smart = await api.createSmartPlaylist(`smoke-smart-${Date.now()}`, rules);
  try {
    const resolved = await api.getSmartPlaylistTracks(smart.id);
    if (resolved.length === 0) throw new Error("the rule matched nothing");
    return `${resolved.length} track(s)`;
  } finally {
    await api.deletePlaylist(smart.id);
  }
});

console.log("\n-- admin");
await check("getBackupDirectory", () => api.getBackupDirectory());
await check("clearCache", () => api.clearCache());

console.log("\n-- cast discovery (no device needed)");
await check("castStartDiscovery", () => api.castStartDiscovery());
await check("castDevices", () => api.castDevices(), {
  path: "/api/cast/devices",
  method: "get",
});
await check("castStatus", () => api.castStatus(), {
  path: "/api/cast/status",
  method: "get",
});
await check("castStopDiscovery", () => api.castStopDiscovery());

console.log("\n-- fetch-image refuses to be used as a proxy");
await check("an internal address is refused", async () => {
  try {
    await api.fetchImage("http://169.254.169.254/latest/meta-data/");
  } catch (e) {
    if (e instanceof ApiError && e.status === 400) return "ApiError status=400";
    throw e;
  }
  throw new Error("the server fetched an internal address");
});
await check("a host outside the allowlist is refused", async () => {
  try {
    await api.fetchImage("https://example.com/cover.jpg");
  } catch (e) {
    if (e instanceof ApiError && e.status === 400) return "ApiError status=400";
    throw e;
  }
  throw new Error("the server fetched an unlisted host");
});

console.log("\n-- errors surface as ApiError with the right status");
await check("404 -> throws", async () => {
  try {
    await api.getMetadata(999999);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return "ApiError status=404";
    throw e;
  }
  throw new Error("expected a 404 to throw");
});
await check("bad key -> 401", async () => {
  const bad = createApi(
    createTransport({ baseUrl: () => BASE, apiKey: () => "definitely-not-the-key" }),
  );
  try {
    await bad.getRoots();
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return "ApiError status=401";
    throw e;
  }
  // The regression this exists for: an empty-bodied 401 read as an empty list.
  throw new Error("a wrong API key was not rejected");
});

console.log(failures === 0 ? "\nALL PASSED" : `\n${failures} FAILED`);
process.exit(failures === 0 ? 0 : 1);

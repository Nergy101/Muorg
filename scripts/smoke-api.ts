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
  await check("deletePlaylist", () => api.deletePlaylist(id));
}

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

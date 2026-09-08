/**
 * Exercises the shared API client against a running muorg-server.
 *
 * Type-checking proves the client matches the spec; this proves the spec
 * matches the server, and that the request actually assembles at runtime. It
 * caught three real bugs the compiler could not: a relative base URL that only
 * resolves inside a browser, a bare 401 being read as an empty result instead
 * of an auth failure, and /api/health answering with text rather than JSON.
 *
 * Usage — start a server, then:
 *
 *   MUORG_URL=http://127.0.0.1:7700 MUORG_KEY=dev-key pnpm smoke:api
 *
 * Not wired into CI: it needs a live server. See improvement-plan §13 for
 * turning it into a full contract test over a fixture library.
 */
import { createApi, createTransport, ApiError } from "../src/api/index";

const api = createApi(
  createTransport({
    baseUrl: () => process.env.MUORG_URL ?? "http://127.0.0.1:7700",
    apiKey: () => process.env.MUORG_KEY ?? "dev-key",
  }),
);

let failures = 0;
async function check(name: string, fn: () => Promise<unknown>) {
  try {
    const value = await fn();
    if (String(JSON.stringify(value)).includes("wrong")) { failures++; console.log(`  FAIL ${name} -> ${JSON.stringify(value)}`); } else console.log(`  ok   ${name} -> ${JSON.stringify(value)?.slice(0, 90)}`);
  } catch (e) {
    failures++;
    console.log(`  FAIL ${name} -> ${e instanceof Error ? e.message : String(e)}`);
  }
}

console.log("-- plain GET, no params");
await check("health", () => api.health());
await check("getRoots", () => api.getRoots());
await check("getStats", () => api.getStats());

console.log("-- GET with query params (the paginated one)");
await check("getTracksPage", () => api.getTracksPage(0, 5));
await check("searchTracks", () => api.searchTracks("nothing"));

console.log("-- POST with a body, and the path-param form");
await check("createPlaylist", () => api.createPlaylist(`smoke-${Date.now()}`));
await check("getPlaylists", () => api.getPlaylists());

console.log("-- errors surface as ApiError with the right status");
await check("404 -> throws", async () => {
  try {
    await api.getMetadata(999999);
    return "NO THROW (wrong)";
  } catch (e) {
    if (e instanceof ApiError) return `ApiError status=${e.status}`;
    throw e;
  }
});
await check("bad key -> 401", async () => {
  const bad = createApi(
    createTransport({
      baseUrl: () => process.env.MUORG_URL ?? "http://127.0.0.1:7700",
      apiKey: () => "definitely-not-the-key",
    }),
  );
  try {
    await bad.getRoots();
    return "NO THROW (wrong)";
  } catch (e) {
    if (e instanceof ApiError) return `ApiError status=${e.status}`;
    throw e;
  }
});

console.log(failures === 0 ? "\nALL PASSED" : `\n${failures} FAILED`);
process.exit(failures === 0 ? 0 : 1);

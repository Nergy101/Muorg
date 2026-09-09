import { afterEach, describe, expect, it, vi } from "vitest";
import { createApi, type MuorgApi } from "./endpoints";
import { createTransport } from "./transport";

/**
 * Every wrapper in `endpoints.ts`, checked against the method and URL it is
 * supposed to produce.
 *
 * TypeScript already guarantees the path literal exists in the spec — that is
 * what `openapi-fetch` typed on `paths` buys. What it cannot catch is a wrapper
 * wired to the *wrong* real path: `deletePlaylist` calling `DELETE
 * /api/playlists/{id}/tracks` type-checks perfectly and destroys the wrong
 * thing. This table is the check for that.
 */

interface Seen {
  method: string;
  url: string;
  body?: string;
}

function record(): Seen[] {
  const seen: Seen[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: Request | string, init?: RequestInit) => {
      const req = typeof input === "string" ? null : input;
      seen.push({
        method: req?.method ?? init?.method ?? "GET",
        url: typeof input === "string" ? input : input.url,
        body: req ? await req.clone().text() : undefined,
      });
      // A shape permissive enough for every wrapper that reshapes its response.
      return new Response(
        JSON.stringify({
          ok: true,
          token: "t",
          path: "/p",
          tracks_added: 0,
          candidates: [],
          count: 0,
          updated: 0,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }),
  );
  return seen;
}

const api = (): MuorgApi =>
  createApi(createTransport({ baseUrl: () => "http://s.test", apiKey: () => "k" }));

afterEach(() => vi.unstubAllGlobals());

/** [description, call, expected method, expected path, expected body?] */
const CALLS: [string, (a: MuorgApi) => Promise<unknown>, string, string, unknown?][] = [
  // Catalog
  ["getRoots", (a) => a.getRoots(), "GET", "/api/roots"],
  ["getStats", (a) => a.getStats(), "GET", "/api/stats"],
  ["searchTracks", (a) => a.searchTracks("a b"), "GET", "/api/search?q=a%20b"],
  ["getRecentlyAdded", (a) => a.getRecentlyAdded(7), "GET", "/api/tracks/recently-added?limit=7"],
  ["getRecentlyPlayed", (a) => a.getRecentlyPlayed(7), "GET", "/api/play-history/recent?limit=7"],
  ["getMostPlayed", (a) => a.getMostPlayed(7, 14), "GET", "/api/play-history/top?limit=7&days=14"],

  // Tracks
  ["getMetadata", (a) => a.getMetadata(3), "GET", "/api/tracks/3/metadata"],
  ["getLyrics", (a) => a.getLyrics(3), "GET", "/api/tracks/3/lyrics"],
  ["getLatestBackup", (a) => a.getLatestBackup(3), "GET", "/api/tracks/3/backup"],
  ["getStreamToken", (a) => a.getStreamToken(3), "GET", "/api/tracks/3/stream-token"],
  ["recordPlay", (a) => a.recordPlay(3), "POST", "/api/tracks/3/play"],
  ["restoreFromBackup", (a) => a.restoreFromBackup(3), "POST", "/api/tracks/3/restore"],
  [
    "setRating",
    (a) => a.setRating(3, 4),
    "POST",
    "/api/tracks/3/rating",
    { rating: 4 },
  ],
  [
    "setRating (clearing)",
    (a) => a.setRating(3, null),
    "POST",
    "/api/tracks/3/rating",
    { rating: null },
  ],
  [
    "renameTrackFile",
    (a) => a.renameTrackFile(3, "/new.mp3"),
    "POST",
    "/api/tracks/3/rename",
    { new_path: "/new.mp3" },
  ],
  [
    "patchMetadata",
    (a) => a.patchMetadata(3, { title: "T" }, true),
    "PATCH",
    "/api/tracks/3/metadata",
    { title: "T", backup_before_write: true },
  ],
  [
    "patchMetadataBatch",
    (a) => a.patchMetadataBatch([{ id: 1, title: "X" }]),
    "POST",
    "/api/tracks/metadata/batch",
    [{ id: 1, title: "X" }],
  ],
  [
    "autoTagSuggestions",
    (a) => a.autoTagSuggestions(3, { artist: "A" }),
    "POST",
    "/api/tracks/3/auto-tag-suggestions",
    { artist: "A" },
  ],

  // Playlists
  ["getPlaylists", (a) => a.getPlaylists(), "GET", "/api/playlists"],
  ["getPlaylistTracks", (a) => a.getPlaylistTracks(5), "GET", "/api/playlists/5/tracks"],
  ["getPlaylistEntries", (a) => a.getPlaylistEntries(5), "GET", "/api/playlists/5/entries"],
  ["getSmartPlaylistTracks", (a) => a.getSmartPlaylistTracks(5), "GET", "/api/playlists/smart/5/tracks"],
  ["deletePlaylist", (a) => a.deletePlaylist(5), "DELETE", "/api/playlists/5"],
  [
    "createPlaylist",
    (a) => a.createPlaylist("Mine"),
    "POST",
    "/api/playlists",
    { name: "Mine" },
  ],
  [
    "updatePlaylist",
    (a) => a.updatePlaylist(5, { name: "New", icon: null }),
    "PATCH",
    "/api/playlists/5",
    { name: "New", icon: null },
  ],
  [
    "addTracksToPlaylist",
    (a) => a.addTracksToPlaylist(5, [1, 2]),
    "POST",
    "/api/playlists/5/tracks",
    { track_ids: [1, 2] },
  ],
  [
    "removeTracksFromPlaylist",
    (a) => a.removeTracksFromPlaylist(5, [1]),
    "DELETE",
    "/api/playlists/5/tracks",
    { track_ids: [1] },
  ],
  [
    "removePlaylistEntry",
    (a) => a.removePlaylistEntry(5, 9),
    "DELETE",
    "/api/playlists/5/entries/9",
  ],
  [
    "reorderPlaylists",
    (a) => a.reorderPlaylists([3, 1]),
    "PUT",
    "/api/playlists/order",
    { ids: [3, 1] },
  ],
  [
    "reorderPlaylistTracks",
    (a) => a.reorderPlaylistTracks(5, [2, 1]),
    "PUT",
    "/api/playlists/5/tracks/order",
    { ids: [2, 1] },
  ],
  [
    "createSmartPlaylist",
    (a) => a.createSmartPlaylist("S", "[]"),
    "POST",
    "/api/playlists/smart",
    { name: "S", rules_json: "[]" },
  ],
  [
    "updateSmartPlaylistRules",
    (a) => a.updateSmartPlaylistRules(5, "[]"),
    "PATCH",
    "/api/playlists/smart/5/rules",
    { rules_json: "[]" },
  ],

  // Cast
  ["castDevices", (a) => a.castDevices(), "GET", "/api/cast/devices"],
  ["castStatus", (a) => a.castStatus(), "GET", "/api/cast/status"],
  ["castStartDiscovery", (a) => a.castStartDiscovery(), "POST", "/api/cast/discovery/start"],
  ["castStopDiscovery", (a) => a.castStopDiscovery(), "POST", "/api/cast/discovery/stop"],
  ["castPause", (a) => a.castPause(), "POST", "/api/cast/pause"],
  ["castResume", (a) => a.castResume(), "POST", "/api/cast/resume"],
  ["castStop", (a) => a.castStop(), "POST", "/api/cast/stop"],
  [
    "castPlay",
    (a) => a.castPlay("10.0.0.5", 8009, 3),
    "POST",
    "/api/cast/play",
    { device_address: "10.0.0.5", device_port: 8009, track_id: 3 },
  ],
  [
    "castSeek",
    (a) => a.castSeek(30.5, true),
    "POST",
    "/api/cast/seek",
    { position_secs: 30.5, was_playing: true },
  ],
  [
    "castSetVolume",
    (a) => a.castSetVolume(0.4),
    "POST",
    "/api/cast/volume",
    { level: 0.4 },
  ],

  // Admin and system
  ["health", (a) => a.health(), "GET", "/api/health"],
  ["clearCache", (a) => a.clearCache(), "POST", "/api/admin/clear-cache"],
  ["getBackupDirectory", (a) => a.getBackupDirectory(), "GET", "/api/admin/backup-directory"],
  [
    "rescan (one root)",
    (a) => a.rescan("/music"),
    "POST",
    "/api/admin/rescan",
    { root_path: "/music" },
  ],
  ["rescan (all roots)", (a) => a.rescan(), "POST", "/api/admin/rescan", {}],
  [
    "removeFolder",
    (a) => a.removeFolder("/music"),
    "POST",
    "/api/admin/remove-folder",
    { root_path: "/music" },
  ],
  [
    "fetchImage",
    (a) => a.fetchImage("http://img.test/a.jpg"),
    "POST",
    "/api/fetch-image",
    { url: "http://img.test/a.jpg" },
  ],
];

describe("call surface", () => {
  it.each(CALLS)("%s", async (_name, call, method, path, body) => {
    const seen = record();
    await call(api());

    expect(seen).toHaveLength(1);
    expect(seen[0].method).toBe(method);
    expect(seen[0].url).toBe(`http://s.test${path}`);
    if (body !== undefined) {
      expect(JSON.parse(seen[0].body ?? "null")).toEqual(body);
    }
  });

  it("covers every wrapper that talks to the server", () => {
    // Guards against a new endpoint being added without a row above.
    const exercised = new Set(
      CALLS.map(([name]) => name.replace(/ \(.*\)$/, "")),
    );
    const notHttp = new Set(["streamTracks", "fetchAllTracks", "getTracksPage", "getCoverBlob", "streamUrl"]);
    const missing = Object.keys(api()).filter(
      (k) => !exercised.has(k) && !notHttp.has(k),
    );
    expect(missing, `untested endpoints: ${missing.join(", ")}`).toEqual([]);
  });
});

describe("cover art", () => {
  it("requests a downscaled cover only when a size is given", async () => {
    const seen = record();
    await api().getCoverBlob(3);
    await api().getCoverBlob(3, 256);

    expect(seen[0].url).toBe("http://s.test/api/tracks/3/cover");
    expect(seen[1].url).toBe("http://s.test/api/tracks/3/cover?size=256");
  });
});

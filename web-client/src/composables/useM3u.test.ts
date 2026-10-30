import { describe, expect, it, vi } from "vitest";
import type { CatalogTrack } from "../types";

const issueStreamToken = vi.fn();
vi.mock("../api/catalog", () => ({
  issueStreamToken: (id: number) => issueStreamToken(id),
}));
vi.mock("../api/client", () => ({
  getServerUrl: () => "http://server.test:7700",
  streamUrl: (id: number, token: string) => `http://server.test:7700/stream/${id}?token=${token}`,
}));

// Dynamic import so the module under test resolves its ../api/catalog and
// ../api/client imports after vi.mock above has replaced them (same pattern
// as stores/playlists.test.ts) — a static import would run before the mocks exist.
const {
  computeRelativePath,
  previewRelativePath,
  buildRelativeM3u,
  buildStreamM3u,
  sanitizeM3uFilename,
} = await import("./useM3u");

function track(p: Partial<CatalogTrack> & { id: number; path: string }): CatalogTrack {
  return {
    album: null,
    album_artist: null,
    artist: null,
    disc_number: null,
    duration_secs: null,
    featuring: null,
    format: "mp3",
    genre: null,
    has_cover: false,
    last_played_at: null,
    mtime_secs: 0,
    play_count: 0,
    rating: null,
    root_id: 1,
    title: null,
    track_number: null,
    year: null,
    ...p,
  };
}

describe("computeRelativePath", () => {
  it("keeps everything from the root segment on, prefixed with ../, forward-slashed", () => {
    expect(computeRelativePath("/Users/me/Music/Artist/Album/Song.mp3", "Music")).toBe(
      "../Music/Artist/Album/Song.mp3",
    );
  });

  it("handles backslash paths", () => {
    expect(computeRelativePath("C:\\Users\\me\\Music\\Artist\\Song.mp3", "Music")).toBe(
      "../Music/Artist/Song.mp3",
    );
  });

  it("returns null when the root segment isn't found", () => {
    expect(computeRelativePath("/Users/me/Elsewhere/Song.mp3", "Music")).toBeNull();
  });

  it("matches the root as an exact path segment, not a substring", () => {
    expect(computeRelativePath("/Users/me/MyMusicLibrary/Song.mp3", "Music")).toBeNull();
  });
});

describe("buildRelativeM3u", () => {
  it("emits #EXTM3U, #EXTINF and the relative path for each in-root track", () => {
    const tracks = [
      track({ id: 1, path: "/m/Music/A/Song1.mp3", artist: "A", title: "Song1", duration_secs: 180 }),
      track({ id: 2, path: "/m/Music/B/Song2.mp3", artist: "B", title: "Song2", duration_secs: 200 }),
    ];
    const { content, skippedCount } = buildRelativeM3u(tracks, "Music");
    expect(skippedCount).toBe(0);
    const lines = content.trim().split("\n");
    expect(lines[0]).toBe("#EXTM3U");
    expect(lines[1]).toBe("#EXTINF:180,A - Song1");
    expect(lines[2]).toBe("../Music/A/Song1.mp3");
    expect(lines[3]).toBe("#EXTINF:200,B - Song2");
    expect(lines[4]).toBe("../Music/B/Song2.mp3");
  });

  it("skips and counts tracks outside the music root instead of writing an absolute path", () => {
    const tracks = [
      track({ id: 1, path: "/m/Music/A/Song1.mp3", title: "In root" }),
      track({ id: 2, path: "/m/Other/Song2.mp3", title: "Outside root" }),
    ];
    const { content, skippedCount } = buildRelativeM3u(tracks, "Music");
    expect(skippedCount).toBe(1);
    expect(content).not.toContain("Outside root");
    expect(content).toContain("In root");
  });

  it("skips every track when no music root is configured", () => {
    const tracks = [track({ id: 1, path: "/m/Music/A/Song1.mp3" })];
    const { content, skippedCount } = buildRelativeM3u(tracks, "");
    expect(skippedCount).toBe(1);
    expect(content.trim()).toBe("#EXTM3U");
  });

  it("falls back to the file name when artist and title are both missing", () => {
    const tracks = [track({ id: 1, path: "/m/Music/A/mystery.mp3" })];
    const { content } = buildRelativeM3u(tracks, "Music");
    expect(content).toContain("#EXTINF:-1,Track 1");
  });
});

describe("previewRelativePath", () => {
  it("previews the first in-root track", () => {
    const tracks = [
      track({ id: 1, path: "/m/Other/Song1.mp3" }),
      track({ id: 2, path: "/m/Music/B/Song2.mp3" }),
    ];
    expect(previewRelativePath(tracks, "Music")).toBe("../Music/B/Song2.mp3");
  });

  it("returns null when the root is empty", () => {
    expect(previewRelativePath([track({ id: 1, path: "/m/Music/Song.mp3" })], "")).toBeNull();
  });
});

describe("buildStreamM3u", () => {
  it("issues a token per track and writes an authorised stream URL", async () => {
    issueStreamToken.mockImplementation((id: number) => Promise.resolve(`tok${id}`));
    const tracks = [track({ id: 7, path: "/m/Music/A/Song.mp3", artist: "A", title: "Song", duration_secs: 42 })];
    const { content, skippedCount } = await buildStreamM3u(tracks);
    expect(skippedCount).toBe(0);
    expect(content).toContain("#EXTM3U");
    expect(content).toContain("#EXTINF:42,A - Song");
    expect(content).toContain("http://server.test:7700/stream/7?token=tok7");
  });

  it("falls back to an unauthenticated stream URL when token issuance fails", async () => {
    issueStreamToken.mockRejectedValue(new Error("offline"));
    const tracks = [track({ id: 9, path: "/m/Music/B/Other.mp3" })];
    const { content } = await buildStreamM3u(tracks);
    expect(content).toContain("http://server.test:7700/stream/9");
    expect(content).not.toContain("token=");
  });
});

describe("sanitizeM3uFilename", () => {
  it("strips filesystem-unsafe characters", () => {
    expect(sanitizeM3uFilename('My: "Great" Mix / 2024')).toBe("My_ _Great_ Mix _ 2024");
  });

  it("falls back to 'playlist' for an empty/whitespace name", () => {
    expect(sanitizeM3uFilename("   ")).toBe("playlist");
  });
});

import { describe, expect, it } from "vitest";
import { computeTransformMatches, planSelectedRenames } from "./smartTransform";
import type { CatalogTrack } from "./types";

function track(id: number, path: string, partial: Partial<CatalogTrack> = {}): CatalogTrack {
  return {
    id,
    path,
    format: "mp3",
    has_cover: false,
    mtime_secs: 0,
    play_count: 0,
    root_id: 1,
    title: null,
    artist: null,
    album: null,
    album_artist: null,
    disc_number: null,
    duration_secs: null,
    featuring: null,
    genre: null,
    last_played_at: null,
    rating: null,
    track_number: null,
    year: null,
    ...partial,
  };
}

describe("computeTransformMatches", () => {
  const ORIGIN = "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>";
  // Every placeholder in DEST (Artist, Album, TrackNumber, TrackTitle, Format)
  // is one the origin template can actually extract from the path itself —
  // buildTransformPath has no access to track metadata, only the path text.
  const DEST = "<Artist>/<Album> (Remastered)/<TrackNumber> - <TrackTitle>.<Format>";

  it("matches tracks whose path fits the origin template and rewrites them", () => {
    const tracks = [
      track(1, "/music/Zed/Greatest Hits/01 - Opener.mp3", { artist: "Zed" }),
      track(2, "/music/Amy/Debut/02 - Second.flac", { artist: "Amy" }),
    ];
    const matches = computeTransformMatches(tracks, ORIGIN, DEST);
    expect(matches).toHaveLength(2);
    expect(matches[0].newPath).toBe("/music/Zed/Greatest Hits (Remastered)/01 - Opener.mp3");
    expect(matches[1].newPath).toBe("/music/Amy/Debut (Remastered)/02 - Second.flac");
  });

  it("skips tracks whose path does not fit the origin template", () => {
    const tracks = [track(1, "/music/not/enough/segments.mp3")];
    expect(computeTransformMatches(tracks, ORIGIN, DEST)).toHaveLength(0);
  });

  it("skips a track whose rewritten path is identical to its current path", () => {
    const tracks = [track(1, "/music/Zed/1999 - Greatest Hits/01 - Opener.mp3", { artist: "Zed", album_artist: "Zed", year: 1999 })];
    // Origin == dest template here, so nothing actually changes.
    expect(computeTransformMatches(tracks, DEST, DEST)).toHaveLength(0);
  });

  it("returns nothing until both templates are non-empty", () => {
    const tracks = [track(1, "/music/Zed/Greatest Hits/01 - Opener.mp3", { artist: "Zed" })];
    expect(computeTransformMatches(tracks, "", DEST)).toHaveLength(0);
    expect(computeTransformMatches(tracks, ORIGIN, "  ")).toHaveLength(0);
    expect(computeTransformMatches(tracks, "", "")).toHaveLength(0);
  });
});

describe("planSelectedRenames", () => {
  it("keeps only the selected ids, in match order", () => {
    const matches = [
      { track: track(1, "/a"), newPath: "/a2" },
      { track: track(2, "/b"), newPath: "/b2" },
      { track: track(3, "/c"), newPath: "/c2" },
    ];
    expect(planSelectedRenames(matches, new Set([3, 1]))).toEqual([
      { id: 1, newPath: "/a2" },
      { id: 3, newPath: "/c2" },
    ]);
  });

  it("returns an empty plan when nothing is selected", () => {
    const matches = [{ track: track(1, "/a"), newPath: "/a2" }];
    expect(planSelectedRenames(matches, new Set())).toEqual([]);
  });
});

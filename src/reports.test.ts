import { describe, it, expect } from "vitest";
import type { CatalogTrack } from "./api";
import {
  duplicateCount,
  duplicateGroups,
  duplicateKey,
  isFieldMissing,
  missingFieldsFor,
  mostPlayedTracks,
  recentlyPlayedTracks,
  reportCount,
  reportCounts,
  runReport,
  tracksMissingAlbumCover,
  tracksMissingMetadata,
} from "./reports";

let nextId = 1;

function track(overrides: Partial<CatalogTrack> = {}): CatalogTrack {
  return {
    id: nextId++,
    path: `/music/track${nextId}.mp3`,
    root_id: 1,
    title: "Title",
    artist: "Artist",
    album: "Album",
    album_artist: "Artist",
    featuring: null,
    year: 2000,
    genre: "Rock",
    track_number: 1,
    disc_number: 1,
    duration_secs: 180,
    format: "mp3",
    mtime_secs: 1_700_000_000,
    has_cover: true,
    rating: 3,
    play_count: 0,
    last_played_at: null,
    ...overrides,
  } as CatalogTrack;
}

describe("isFieldMissing", () => {
  it("treats an absent text tag as missing", () => {
    expect(isFieldMissing(track({ title: null }), "title")).toBe(true);
    expect(isFieldMissing(track({ title: "Something" }), "title")).toBe(false);
  });

  it("treats a whitespace-only tag as missing", () => {
    // A file tagged "   " is untagged; counting it as present is exactly how
    // those files stay invisible to the report meant to find them.
    expect(isFieldMissing(track({ artist: "   " }), "artist")).toBe(true);
    expect(isFieldMissing(track({ album: "" }), "album")).toBe(true);
  });

  it("does not treat a zero as a missing number", () => {
    // Track 0 is a legitimate (if unusual) tag, and a year of 0 is a real
    // value someone typed. Neither is the same as nothing.
    expect(isFieldMissing(track({ track_number: 0 }), "track_number")).toBe(false);
    expect(isFieldMissing(track({ disc_number: 0 }), "disc_number")).toBe(false);
    expect(isFieldMissing(track({ year: 0 }), "year")).toBe(false);
    expect(isFieldMissing(track({ rating: 0 }), "rating")).toBe(false);
  });

  it("reads has_cover as a flag rather than a value", () => {
    expect(isFieldMissing(track({ has_cover: false }), "has_cover")).toBe(true);
    expect(isFieldMissing(track({ has_cover: true }), "has_cover")).toBe(false);
  });

  it("reports an unset rating as missing", () => {
    expect(isFieldMissing(track({ rating: null }), "rating")).toBe(true);
  });
});

describe("tracksMissingMetadata", () => {
  it("matches a track missing any one of the requested fields", () => {
    const complete = track();
    const noArtist = track({ artist: null });
    const noYear = track({ year: null });

    const found = tracksMissingMetadata([complete, noArtist, noYear], ["artist", "year"]);
    expect(found.map((t) => t.id)).toEqual([noArtist.id, noYear.id]);
  });

  it("matches nothing when no fields are being looked for", () => {
    // An empty selection means "I am not checking anything", not "everything
    // is missing" — the latter would flag the whole library.
    expect(tracksMissingMetadata([track({ title: null })], [])).toEqual([]);
  });

  it("defaults to title, artist and album", () => {
    const bad = track({ album: null });
    const fineButUnrated = track({ rating: null });
    expect(tracksMissingMetadata([bad, fineButUnrated]).map((t) => t.id)).toEqual([bad.id]);
  });

  it("lists which fields a track is short of", () => {
    const t = track({ title: null, artist: "  ", album: "Album" });
    expect(missingFieldsFor(t, ["title", "artist", "album"])).toEqual(["title", "artist"]);
  });
});

describe("duplicates", () => {
  it("keys on artist, album and title, ignoring case and padding", () => {
    const a = track({ artist: "Radiohead", album: "OK Computer", title: "Creep" });
    const b = track({ artist: " radiohead ", album: "ok computer", title: "CREEP" });
    expect(duplicateKey(a)).toBe(duplicateKey(b));
  });

  it("does not key on the path, so two copies in two folders match", () => {
    const a = track({ path: "/music/a/song.mp3" });
    const b = track({ path: "/music/b/song.mp3" });
    expect(duplicateGroups([a, b])).toHaveLength(1);
  });

  it("counts copies beyond the first, not tracks involved", () => {
    // Two copies of one song is one thing to fix. Reporting "2" next to a
    // two-row list reads as two problems when there is one.
    const same = { artist: "A", album: "B", title: "C" };
    expect(duplicateCount([track(same), track(same)])).toBe(1);
    expect(duplicateCount([track(same), track(same), track(same)])).toBe(2);
  });

  it("counts each group separately", () => {
    const one = { artist: "A", album: "B", title: "C" };
    const two = { artist: "D", album: "E", title: "F" };
    const tracks = [track(one), track(one), track(two), track(two), track(two)];
    expect(duplicateCount(tracks)).toBe(3);
    expect(duplicateGroups(tracks).map((g) => g.length)).toEqual([3, 2]);
  });

  it("reports nothing when every track is distinct", () => {
    const tracks = [track({ title: "One" }), track({ title: "Two" })];
    expect(duplicateCount(tracks)).toBe(0);
    expect(duplicateGroups(tracks)).toEqual([]);
  });

  it("ignores tracks with no artist, album or title at all", () => {
    // Otherwise every untagged file in the library is a duplicate of every
    // other one, which buries the real duplicates under noise.
    const blank = { artist: null, album: null, title: null };
    expect(duplicateCount([track(blank), track(blank), track(blank)])).toBe(0);
  });

  it("still groups tracks that share a title but have no album", () => {
    const partial = { artist: "A", album: null, title: "C" };
    expect(duplicateCount([track(partial), track(partial)])).toBe(1);
  });

  it("keeps copies of the same song adjacent in the listing", () => {
    const one = { artist: "A", album: "B", title: "C" };
    const two = { artist: "D", album: "E", title: "F" };
    const listed = runReport("duplicates", [
      track(one),
      track(two),
      track(one),
      track(two),
    ]);
    const keys = listed.map(duplicateKey);
    expect(keys[0]).toBe(keys[1]);
    expect(keys[2]).toBe(keys[3]);
    expect(keys[0]).not.toBe(keys[2]);
  });
});

describe("missing album cover", () => {
  it("lists only tracks with no embedded art", () => {
    const without = track({ has_cover: false });
    expect(tracksMissingAlbumCover([track(), without]).map((t) => t.id)).toEqual([without.id]);
  });
});

describe("play history reports", () => {
  it("lists recently played newest first and skips never-played tracks", () => {
    const old = track({ last_played_at: 100, play_count: 1 });
    const recent = track({ last_played_at: 900, play_count: 1 });
    const never = track({ last_played_at: null, play_count: 0 });

    expect(recentlyPlayedTracks([old, never, recent]).map((t) => t.id)).toEqual([
      recent.id,
      old.id,
    ]);
  });

  it("lists most played by count, skipping tracks never played", () => {
    const once = track({ play_count: 1, last_played_at: 1 });
    const often = track({ play_count: 12, last_played_at: 1 });
    const never = track({ play_count: 0 });

    expect(mostPlayedTracks([once, often, never]).map((t) => t.id)).toEqual([
      often.id,
      once.id,
    ]);
  });

  it("does not mutate the list it was given", () => {
    // These sort, and sorting in place would reorder the caller's catalog.
    const tracks = [
      track({ play_count: 1, last_played_at: 1 }),
      track({ play_count: 9, last_played_at: 9 }),
    ];
    const before = tracks.map((t) => t.id);
    mostPlayedTracks(tracks);
    recentlyPlayedTracks(tracks);
    expect(tracks.map((t) => t.id)).toEqual(before);
  });
});

describe("report counts", () => {
  it("counts rows for every report except duplicates", () => {
    const same = { artist: "A", album: "B", title: "C" };
    const tracks = [
      track(same),
      track(same),
      track({ title: null, has_cover: false }),
      track({ play_count: 3, last_played_at: 500 }),
    ];

    expect(reportCounts(tracks)).toEqual({
      missing_metadata: 1,
      duplicates: 1,
      missing_album_cover: 1,
      recently_played: 1,
      most_played: 1,
    });
  });

  it("agrees with the list each report produces, duplicates aside", () => {
    const tracks = [
      track({ title: null }),
      track({ has_cover: false }),
      track({ play_count: 2, last_played_at: 5 }),
    ];
    for (const kind of ["missing_metadata", "missing_album_cover", "recently_played", "most_played"] as const) {
      expect(reportCount(kind, tracks)).toBe(runReport(kind, tracks).length);
    }
  });

  it("reports zero for everything on an empty library", () => {
    expect(reportCounts([])).toEqual({
      missing_metadata: 0,
      duplicates: 0,
      missing_album_cover: 0,
      recently_played: 0,
      most_played: 0,
    });
  });
});

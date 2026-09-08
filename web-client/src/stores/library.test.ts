import { describe, expect, it } from "vitest";
import {
  albumKeyFor,
  compareTrackOrder,
  formatDuration,
  groupAlbums,
} from "./library";
import type { CatalogTrack } from "../types";

function track(p: Partial<CatalogTrack> & { id: number }): CatalogTrack {
  return {
    path: `/m/${p.id}.mp3`,
    root_id: 1,
    title: `t${p.id}`,
    artist: null,
    album: null,
    album_artist: null,
    featuring: null,
    year: null,
    genre: null,
    track_number: null,
    disc_number: null,
    duration_secs: null,
    format: "mp3",
    mtime_secs: 0,
    has_cover: false,
    rating: null,
    play_count: 0,
    last_played_at: null,
    ...p,
  };
}

describe("formatDuration", () => {
  it("uses m:ss under an hour", () => {
    expect(formatDuration(0)).toBe("0:00");
    expect(formatDuration(9)).toBe("0:09");
    expect(formatDuration(65)).toBe("1:05");
    expect(formatDuration(599)).toBe("9:59");
  });

  it("switches to h:mm:ss past an hour", () => {
    expect(formatDuration(3600)).toBe("1:00:00");
    expect(formatDuration(3661)).toBe("1:01:01");
    expect(formatDuration(7325)).toBe("2:02:05");
  });

  it("truncates fractional seconds rather than rounding up", () => {
    expect(formatDuration(59.9)).toBe("0:59");
  });
});

describe("albumKeyFor", () => {
  it("keys on album plus album artist, case-insensitively", () => {
    expect(albumKeyFor(track({ id: 1, album: "Ride", album_artist: "Boards" })))
      .toBe(albumKeyFor(track({ id: 2, album: "RIDE", album_artist: "BOARDS" })));
  });

  it("ignores the per-track artist", () => {
    // A compilation has a different artist on every track but is one album;
    // folding `artist` into the key would scatter it across the grid.
    const a = track({ id: 1, album: "Comp", album_artist: "Various", artist: "One" });
    const b = track({ id: 2, album: "Comp", album_artist: "Various", artist: "Two" });
    expect(albumKeyFor(a)).toBe(albumKeyFor(b));
  });

  it("falls back to Unknown Album with no album tag", () => {
    expect(albumKeyFor(track({ id: 1 }))).toBe("unknown album|||");
  });
});

describe("compareTrackOrder", () => {
  it("orders by disc, then track number", () => {
    const a = track({ id: 1, disc_number: 1, track_number: 5 });
    const b = track({ id: 2, disc_number: 2, track_number: 1 });
    expect(compareTrackOrder(a, b)).toBeLessThan(0);
  });

  it("treats a missing disc number as disc 1", () => {
    const untagged = track({ id: 1, track_number: 2 });
    const discTwo = track({ id: 2, disc_number: 2, track_number: 1 });
    expect(compareTrackOrder(untagged, discTwo)).toBeLessThan(0);
  });

  it("sorts a disc by track number", () => {
    const list = [
      track({ id: 3, track_number: 3 }),
      track({ id: 1, track_number: 1 }),
      track({ id: 2, track_number: 2 }),
    ];
    expect([...list].sort(compareTrackOrder).map((t) => t.id)).toEqual([1, 2, 3]);
  });
});

describe("groupAlbums", () => {
  it("groups one album under its single album artist", () => {
    const albums = groupAlbums([
      track({ id: 1, album: "Ride", album_artist: "Boards", artist: "Boards" }),
      track({ id: 2, album: "Ride", album_artist: "Boards", artist: "Boards" }),
    ]);
    expect(albums.size).toBe(1);
    const [item] = [...albums.values()];
    expect(item.albumArtist).toBe("Boards");
    expect(item.trackIds).toHaveLength(2);
  });

  it("keeps a compilation together and calls it Various Artists", () => {
    const albums = groupAlbums([
      track({ id: 1, album: "Mix", artist: "One" }),
      track({ id: 2, album: "Mix", artist: "Two" }),
    ]);
    expect(albums.size).toBe(1);
    expect([...albums.values()][0].albumArtist).toBe("Various Artists");
  });

  it("splits two different albums that share a title", () => {
    // "Greatest Hits" by two bands is two albums, not one.
    const albums = groupAlbums([
      track({ id: 1, album: "Greatest Hits", album_artist: "A", artist: "A" }),
      track({ id: 2, album: "Greatest Hits", album_artist: "B", artist: "B" }),
    ]);
    expect(albums.size).toBe(2);
    expect([...albums.values()].map((i) => i.albumArtist).sort()).toEqual(["A", "B"]);
  });

  it("keeps one album together when a featured artist leaked into album_artist", () => {
    // Several album-artist tags, none matching a track artist: the tag is
    // polluted, the album is still one album.
    const albums = groupAlbums([
      track({ id: 1, album: "Split", album_artist: "Main feat. X", artist: "Main" }),
      track({ id: 2, album: "Split", album_artist: "Main feat. Y", artist: "Main" }),
    ]);
    expect(albums.size).toBe(1);
    expect([...albums.values()][0].trackIds).toHaveLength(2);
  });

  it("adopts an untagged track into the one album artist present", () => {
    const albums = groupAlbums([
      track({ id: 1, album: "Ride", album_artist: "Boards", artist: "Boards" }),
      track({ id: 2, album: "Ride", artist: "Boards" }),
    ]);
    expect(albums.size).toBe(1);
    expect([...albums.values()][0].trackIds).toHaveLength(2);
  });

  it("sends an untagged track to the sub-album matching its own artist", () => {
    const albums = groupAlbums([
      track({ id: 1, album: "Hits", album_artist: "A", artist: "A" }),
      track({ id: 2, album: "Hits", album_artist: "B", artist: "B" }),
      track({ id: 3, album: "Hits", artist: "B" }),
    ]);
    const b = [...albums.values()].find((i) => i.albumArtist === "B");
    expect(b?.trackIds).toContain(3);
  });

  it("takes the earliest year and sums the durations", () => {
    const albums = groupAlbums([
      track({ id: 1, album: "X", album_artist: "A", year: 2005, duration_secs: 100 }),
      track({ id: 2, album: "X", album_artist: "A", year: 1999, duration_secs: 200 }),
    ]);
    const [item] = [...albums.values()];
    expect(item.year).toBe(1999);
    expect(item.totalDurationSecs).toBe(300);
  });

  it("picks the first track that actually has art as the cover", () => {
    const albums = groupAlbums([
      track({ id: 1, album: "X", album_artist: "A", has_cover: false }),
      track({ id: 2, album: "X", album_artist: "A", has_cover: true }),
    ]);
    const [item] = [...albums.values()];
    expect(item.hasCover).toBe(true);
    expect(item.coverTrackId).toBe(2);
  });

  it("matches album names case-insensitively", () => {
    const albums = groupAlbums([
      track({ id: 1, album: "Ride", album_artist: "A" }),
      track({ id: 2, album: "RIDE", album_artist: "A" }),
    ]);
    expect(albums.size).toBe(1);
  });

  it("returns nothing for an empty library", () => {
    expect(groupAlbums([]).size).toBe(0);
  });
});

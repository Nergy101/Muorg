import { describe, expect, it } from "vitest";
import { sortPlaylistTracks } from "./playlistSort";
import type { CatalogTrack } from "./types";

function track(id: number, partial: Partial<CatalogTrack> = {}): CatalogTrack {
  return {
    id,
    path: `/music/${id}.mp3`,
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

const tracks = [
  track(1, { title: "Beta", artist: "Zed" }),
  track(2, { title: "alpha", artist: "Amy" }),
  track(3, { title: "Gamma", artist: "Amy" }),
];

describe("sortPlaylistTracks", () => {
  it("keeps the playlist order under the saved-order sort", () => {
    expect(sortPlaylistTracks(tracks, "order", true).map((t) => t.id)).toEqual([1, 2, 3]);
    expect(sortPlaylistTracks(tracks, "order", false).map((t) => t.id)).toEqual([3, 2, 1]);
  });

  it("sorts by title case-insensitively", () => {
    expect(sortPlaylistTracks(tracks, "title", true).map((t) => t.id)).toEqual([2, 1, 3]);
    expect(sortPlaylistTracks(tracks, "title", false).map((t) => t.id)).toEqual([3, 1, 2]);
  });

  it("sorts by artist, then title", () => {
    expect(sortPlaylistTracks(tracks, "artist", true).map((t) => t.id)).toEqual([2, 3, 1]);
    expect(sortPlaylistTracks(tracks, "artist", false).map((t) => t.id)).toEqual([1, 3, 2]);
  });

  it("sorts by the entry ids under the added-date sort", () => {
    // The entry id grows with every insert, so it is the added order.
    const addedKeys = new Map([
      [3, 11],
      [1, 12],
      [2, 13],
    ]);
    expect(sortPlaylistTracks(tracks, "added", true, addedKeys).map((t) => t.id)).toEqual([
      3, 1, 2,
    ]);
    expect(sortPlaylistTracks(tracks, "added", false, addedKeys).map((t) => t.id)).toEqual([
      2, 1, 3,
    ]);
  });

  it("falls back to the natural order for the added-date sort without keys (smart playlists, mixes)", () => {
    expect(sortPlaylistTracks(tracks, "added", true).map((t) => t.id)).toEqual([1, 2, 3]);
    expect(sortPlaylistTracks(tracks, "added", false).map((t) => t.id)).toEqual([3, 2, 1]);
  });

  it("falls back to the file name and unknown artist when metadata is missing", () => {
    const untitled = [
      track(1, { path: "/music/b-song.mp3", artist: null, album_artist: null }),
      track(2, { path: "/music/a-song.mp3", artist: "Zed" }),
    ];
    // Titles fall back to the file name: a-song before b-song.
    expect(sortPlaylistTracks(untitled, "title", true).map((t) => t.id)).toEqual([2, 1]);
    // Both artists resolve ("Unknown Artist" sorts before "zed"), then title
    // breaks the tie the other way round.
    expect(sortPlaylistTracks(untitled, "artist", true).map((t) => t.id)).toEqual([1, 2]);
  });

  it("does not mutate the input list", () => {
    const before = tracks.map((t) => t.id);
    sortPlaylistTracks(tracks, "title", true);
    sortPlaylistTracks(tracks, "order", false);
    expect(tracks.map((t) => t.id)).toEqual(before);
  });
});

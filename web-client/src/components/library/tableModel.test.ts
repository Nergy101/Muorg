import { describe, expect, it } from "vitest";
import {
  buildTableRows,
  cycleSortDirection,
  filterByMinRating,
  groupAvgRating,
  groupTracks,
  sortGroups,
  sortTracks,
  type SortDirection,
  type TableSortColumn,
} from "./tableModel";
import type { CatalogTrack } from "../../types";

function track(overrides: Partial<CatalogTrack> & { id: number }): CatalogTrack {
  return {
    title: null,
    artist: null,
    album: null,
    album_artist: null,
    featuring: null,
    genre: null,
    year: null,
    track_number: null,
    disc_number: null,
    duration_secs: null,
    format: "flac",
    rating: null,
    play_count: 0,
    last_played_at: null,
    path: `/music/${overrides.id}.flac`,
    has_cover: false,
    ...overrides,
  } as CatalogTrack;
}

describe("sortTracks", () => {
  it("is stable: equal keys keep their relative input order", () => {
    const tracks = [
      track({ id: 1, title: "Same" }),
      track({ id: 2, title: "Same" }),
      track({ id: 3, title: "Same" }),
    ];
    const sorted = sortTracks(tracks, "title", "asc");
    expect(sorted.map((t) => t.id)).toEqual([1, 2, 3]);
  });

  it("puts nulls last in both directions", () => {
    const tracks = [
      track({ id: 1, year: 2000 }),
      track({ id: 2, year: null }),
      track({ id: 3, year: 1990 }),
    ];
    expect(sortTracks(tracks, "year", "asc").map((t) => t.id)).toEqual([3, 1, 2]);
    expect(sortTracks(tracks, "year", "desc").map((t) => t.id)).toEqual([1, 3, 2]);
  });

  it("returns a copy, unsorted, when no column is selected", () => {
    const tracks = [track({ id: 2 }), track({ id: 1 })];
    const result = sortTracks(tracks, null, "asc");
    expect(result).not.toBe(tracks);
    expect(result.map((t) => t.id)).toEqual([2, 1]);
  });

  it("sorts strings case-insensitively", () => {
    const tracks = [track({ id: 1, artist: "banana" }), track({ id: 2, artist: "Apple" })];
    expect(sortTracks(tracks, "artist", "asc").map((t) => t.id)).toEqual([2, 1]);
  });

  it("sorts numeric columns like rating and duration", () => {
    const tracks = [
      track({ id: 1, duration_secs: 300 }),
      track({ id: 2, duration_secs: 120 }),
      track({ id: 3, duration_secs: 200 }),
    ];
    expect(sortTracks(tracks, "duration", "asc").map((t) => t.id)).toEqual([2, 3, 1]);
  });
});

describe("filterByMinRating", () => {
  const tracks = [
    track({ id: 1, rating: 5 }),
    track({ id: 2, rating: 3 }),
    track({ id: 3, rating: null }),
    track({ id: 4, rating: 1 }),
  ];

  it("returns everything (as a copy) when minRating is 0", () => {
    const result = filterByMinRating(tracks, 0);
    expect(result).not.toBe(tracks);
    expect(result.map((t) => t.id)).toEqual([1, 2, 3, 4]);
  });

  it("excludes unrated tracks and anything below the threshold, inclusive of the threshold itself", () => {
    expect(filterByMinRating(tracks, 3).map((t) => t.id)).toEqual([1, 2]);
  });

  it("negative minRating behaves like 0 (no filter)", () => {
    expect(filterByMinRating(tracks, -1).map((t) => t.id)).toEqual([1, 2, 3, 4]);
  });
});

describe("groupTracks", () => {
  it("returns null for groupBy none or an empty list", () => {
    const tracks = [track({ id: 1 })];
    expect(groupTracks(tracks, "none")).toBeNull();
    expect(groupTracks([], "artist")).toBeNull();
  });

  it("groups by artist, falling back to — for missing values", () => {
    const tracks = [
      track({ id: 1, artist: "A" }),
      track({ id: 2, artist: "B" }),
      track({ id: 3, artist: "A" }),
      track({ id: 4, artist: null }),
    ];
    const groups = groupTracks(tracks, "artist")!;
    const byKey = new Map(groups.map((g) => [g.key, g]));
    expect(byKey.get("A")!.tracks.map((t) => t.id)).toEqual([1, 3]);
    expect(byKey.get("B")!.tracks.map((t) => t.id)).toEqual([2]);
    expect(byKey.get("—")!.tracks.map((t) => t.id)).toEqual([4]);
  });

  it("groups by album and carries the album's artist", () => {
    const tracks = [
      track({ id: 1, album: "X", artist: "Artist" }),
      track({ id: 2, album: "X", artist: "Artist" }),
    ];
    const groups = groupTracks(tracks, "album")!;
    expect(groups).toHaveLength(1);
    expect(groups[0].artist).toBe("Artist");
    expect(groups[0].tracks).toHaveLength(2);
  });
});

describe("groupAvgRating", () => {
  it("averages only rated tracks, or null when none are rated", () => {
    expect(groupAvgRating([track({ id: 1, rating: 4 }), track({ id: 2, rating: 2 })])).toBe(3);
    expect(groupAvgRating([track({ id: 1, rating: null })])).toBeNull();
  });
});

describe("sortGroups", () => {
  it("ties on the sorted column fall back to alphabetical by label", () => {
    const groups = groupTracks(
      [
        track({ id: 1, album: "Zebra", year: 2000 }),
        track({ id: 2, album: "Apple", year: 2000 }),
      ],
      "album",
    )!;
    const sorted = sortGroups(groups, "year", "asc");
    expect(sorted.map((g) => g.label)).toEqual(["Apple", "Zebra"]);
  });

  it("orders by earliest year per group when sorting by year", () => {
    const groups = groupTracks(
      [
        track({ id: 1, album: "Late", year: 2010 }),
        track({ id: 2, album: "Early", year: 1990 }),
      ],
      "album",
    )!;
    expect(sortGroups(groups, "year", "asc").map((g) => g.label)).toEqual(["Early", "Late"]);
    expect(sortGroups(groups, "year", "desc").map((g) => g.label)).toEqual(["Late", "Early"]);
  });

  it("groups with no year sort after groups that have one", () => {
    const groups = groupTracks(
      [
        track({ id: 1, album: "Dated", year: 2000 }),
        track({ id: 2, album: "Undated", year: null }),
      ],
      "album",
    )!;
    expect(sortGroups(groups, "year", "asc").map((g) => g.label)).toEqual(["Dated", "Undated"]);
  });
});

describe("buildTableRows", () => {
  it("flattens into plain track rows when ungrouped", () => {
    const tracks = [track({ id: 2, title: "B" }), track({ id: 1, title: "A" })];
    const { rows } = buildTableRows(tracks, {
      sortColumn: "title",
      sortDirection: "asc",
      groupBy: "none",
      minRating: 0,
      expandedGroups: new Set(),
    });
    expect(rows.map((r) => (r.type === "track" ? r.track.id : null))).toEqual([1, 2]);
  });

  it("only expands groups present in expandedGroups, and applies the rating filter before grouping", () => {
    const tracks = [
      track({ id: 1, artist: "A", rating: 5 }),
      track({ id: 2, artist: "A", rating: 1 }),
      track({ id: 3, artist: "B", rating: 4 }),
    ];
    const { rows } = buildTableRows(tracks, {
      sortColumn: null,
      sortDirection: "asc",
      groupBy: "artist",
      minRating: 3,
      expandedGroups: new Set(["A"]),
    });
    // Group B is collapsed (no track row); group A is expanded but only its
    // rated->=3 member survives the filter.
    expect(rows).toEqual([
      { type: "group", key: "A", group: expect.objectContaining({ key: "A" }) },
      { type: "track", track: expect.objectContaining({ id: 1 }), groupKey: "A" },
      { type: "group", key: "B", group: expect.objectContaining({ key: "B" }) },
    ]);
  });
});

describe("cycleSortDirection", () => {
  it("cycles asc -> desc -> none for the same column", () => {
    let state: { column: TableSortColumn | null; direction: SortDirection } = { column: null, direction: "asc" };
    state = cycleSortDirection(state, "title");
    expect(state).toEqual({ column: "title", direction: "asc" });
    state = cycleSortDirection(state, "title");
    expect(state).toEqual({ column: "title", direction: "desc" });
    state = cycleSortDirection(state, "title");
    expect(state).toEqual({ column: null, direction: "asc" });
  });

  it("switching to a different column always starts at asc", () => {
    const state = { column: "title" as const, direction: "desc" as const };
    expect(cycleSortDirection(state, "artist")).toEqual({ column: "artist", direction: "asc" });
  });
});

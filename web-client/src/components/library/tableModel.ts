/**
 * Pure sort/group/filter logic for the desktop-class library table
 * (`LibraryTrackTable.vue`). Kept free of Vue/Pinia so it is trivial to unit
 * test and so the component can stay a thin rendering layer over it.
 */
import type { CatalogTrack } from "../../types";

export type TableSortColumn = "title" | "artist" | "album" | "year" | "duration" | "rating" | "format";
export type SortDirection = "asc" | "desc";
export type TableGroupBy = "none" | "artist" | "album";

export interface TableGroup {
  key: string;
  label: string;
  /** Only set for album groups — the group's (uniform) artist, shown next to the album title. */
  artist?: string;
  tracks: CatalogTrack[];
}

export type TableRow =
  | { type: "group"; key: string; group: TableGroup }
  | { type: "track"; track: CatalogTrack; groupKey?: string };

const collator = new Intl.Collator(undefined, { sensitivity: "base" });

function compareStrings(a: string, b: string): number {
  return collator.compare(a, b);
}

/** Tracks rated at or above `minRating`. 0 (or anything <= 0) means no filter; unrated tracks never pass a positive threshold. */
export function filterByMinRating(tracks: readonly CatalogTrack[], minRating: number): CatalogTrack[] {
  if (!minRating || minRating <= 0) return [...tracks];
  return tracks.filter((t) => (t.rating ?? 0) >= minRating);
}

function sortValue(t: CatalogTrack, col: TableSortColumn): string | number | null {
  switch (col) {
    case "title":
      return t.title ?? null;
    case "artist":
      return t.artist ?? null;
    case "album":
      return t.album ?? null;
    case "year":
      return t.year ?? null;
    case "duration":
      return t.duration_secs ?? null;
    case "rating":
      return t.rating ?? null;
    case "format":
      return t.format ?? null;
  }
}

/**
 * Sorts tracks by one column. Stable (equal keys keep their relative input
 * order — `Array.prototype.sort` already guarantees this, this function just
 * never breaks it with an unstable comparator). Nulls always sort after every
 * real value regardless of direction, so an unrated/undated track never jumps
 * to the top on a descending sort.
 */
export function sortTracks(
  tracks: readonly CatalogTrack[],
  column: TableSortColumn | null,
  direction: SortDirection,
): CatalogTrack[] {
  if (!column) return [...tracks];
  const dir = direction === "desc" ? -1 : 1;
  return [...tracks].sort((a, b) => {
    const va = sortValue(a, column);
    const vb = sortValue(b, column);
    if (va == null && vb == null) return 0;
    if (va == null) return 1;
    if (vb == null) return -1;
    if (typeof va === "string" && typeof vb === "string") return dir * compareStrings(va, vb);
    return dir * ((va as number) - (vb as number));
  });
}

/** Average rating of a group's rated tracks, or null when none are rated. */
export function groupAvgRating(tracks: readonly CatalogTrack[]): number | null {
  const rated = tracks.filter((t) => t.rating != null);
  if (!rated.length) return null;
  return rated.reduce((s, t) => s + (t.rating ?? 0), 0) / rated.length;
}

/** Groups tracks by artist or album. Returns null for "none" or an empty input. */
export function groupTracks(tracks: readonly CatalogTrack[], groupBy: TableGroupBy): TableGroup[] | null {
  if (groupBy === "none" || tracks.length === 0) return null;
  const map = new Map<string, TableGroup>();
  for (const t of tracks) {
    if (groupBy === "artist") {
      const artist = t.artist ?? "—";
      let g = map.get(artist);
      if (!g) {
        g = { key: artist, label: artist, tracks: [] };
        map.set(artist, g);
      }
      g.tracks.push(t);
    } else {
      const album = t.album ?? "—";
      const artist = t.artist ?? "—";
      let g = map.get(album);
      if (!g) {
        g = { key: album, label: album, artist, tracks: [] };
        map.set(album, g);
      }
      g.tracks.push(t);
    }
  }
  return [...map.values()];
}

/**
 * Orders groups by the active sort column's aggregate (earliest year, total
 * duration, average rating, or artist name); title/album/format/no-sort fall
 * straight through to the alphabetical tie-break. Ties — and columns with no
 * group-level meaning — fall back to label, then artist.
 */
export function sortGroups(
  groups: readonly TableGroup[],
  column: TableSortColumn | null,
  direction: SortDirection,
): TableGroup[] {
  const dir = direction === "desc" ? -1 : 1;
  const out = [...groups];
  out.sort((a, b) => {
    let primary = 0;
    if (column === "year") {
      const ay = a.tracks.find((t) => t.year != null)?.year ?? null;
      const by = b.tracks.find((t) => t.year != null)?.year ?? null;
      primary = ay == null && by == null ? 0 : ay == null ? 1 : by == null ? -1 : dir * (ay - by);
    } else if (column === "duration") {
      const ad = a.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
      const bd = b.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
      primary = ad === bd ? 0 : dir * (ad - bd);
    } else if (column === "rating") {
      const ar = groupAvgRating(a.tracks);
      const br = groupAvgRating(b.tracks);
      primary = ar == null && br == null ? 0 : ar == null ? 1 : br == null ? -1 : dir * (ar - br);
    } else if (column === "artist") {
      primary = dir * compareStrings(a.artist ?? a.label, b.artist ?? b.label);
    }
    if (primary !== 0) return primary;
    const byLabel = compareStrings(a.label, b.label);
    if (byLabel !== 0) return byLabel;
    return compareStrings(a.artist ?? "", b.artist ?? "");
  });
  return out;
}

export interface BuildRowsOptions {
  sortColumn: TableSortColumn | null;
  sortDirection: SortDirection;
  groupBy: TableGroupBy;
  minRating: number;
  expandedGroups: ReadonlySet<string>;
}

/** Full pipeline: filter by rating, sort, group, order groups, flatten into display rows. */
export function buildTableRows(
  tracks: readonly CatalogTrack[],
  opts: BuildRowsOptions,
): { rows: TableRow[]; groups: TableGroup[] | null } {
  const filtered = filterByMinRating(tracks, opts.minRating);
  const sorted = sortTracks(filtered, opts.sortColumn, opts.sortDirection);
  const groups = groupTracks(sorted, opts.groupBy);
  if (!groups) {
    return { rows: sorted.map((track) => ({ type: "track", track })), groups: null };
  }
  const orderedGroups = sortGroups(groups, opts.sortColumn, opts.sortDirection);
  const rows: TableRow[] = [];
  for (const g of orderedGroups) {
    rows.push({ type: "group", key: g.key, group: g });
    if (opts.expandedGroups.has(g.key)) {
      for (const t of g.tracks) rows.push({ type: "track", track: t, groupKey: g.key });
    }
  }
  return { rows, groups: orderedGroups };
}

/** none → asc → desc → none (for the same column); a different column always starts at asc. */
export function cycleSortDirection(
  current: { column: TableSortColumn | null; direction: SortDirection },
  column: TableSortColumn,
): { column: TableSortColumn | null; direction: SortDirection } {
  if (current.column !== column) return { column, direction: "asc" };
  if (current.direction === "asc") return { column, direction: "desc" };
  return { column: null, direction: "asc" };
}

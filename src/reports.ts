/**
 * Library reports: the "what is wrong with my collection" views.
 *
 * These were computed inline in the desktop app's sidebar, which is why they
 * existed nowhere else — the web and Android apps could show a library but not
 * tell you what was missing from it. The logic is pure (a list of tracks in, a
 * filtered list out), so it belongs here rather than in any one app's
 * components, and the two TypeScript apps now share exactly this.
 *
 * Android has its own copy in `LibraryReports.kt`; it cannot import TypeScript.
 * The two are kept in step by the same cases being asserted on both sides.
 */
import type { CatalogTrack } from "./api";

/** The reports a user can open. */
export type ReportKind =
  | "missing_metadata"
  | "duplicates"
  | "missing_album_cover"
  | "recently_played"
  | "most_played";

export const REPORT_KINDS: readonly ReportKind[] = [
  "missing_metadata",
  "duplicates",
  "missing_album_cover",
  "recently_played",
  "most_played",
] as const;

/** Fields the "missing metadata" report can look for. */
export type MissingMetadataField =
  | "title"
  | "artist"
  | "album"
  | "album_artist"
  | "year"
  | "genre"
  | "track_number"
  | "disc_number"
  | "rating"
  | "has_cover";

export const MISSING_METADATA_FIELDS: readonly MissingMetadataField[] = [
  "title",
  "artist",
  "album",
  "album_artist",
  "year",
  "genre",
  "track_number",
  "disc_number",
  "rating",
  "has_cover",
] as const;

/** What the default "missing metadata" report looks for. */
export const DEFAULT_MISSING_METADATA_FIELDS: readonly MissingMetadataField[] = [
  "title",
  "artist",
  "album",
] as const;

export const REPORT_LABELS: Record<ReportKind, string> = {
  missing_metadata: "Missing metadata",
  duplicates: "Duplicates",
  missing_album_cover: "Missing album cover",
  recently_played: "Recently played",
  most_played: "Most played",
};

/**
 * Is `field` unset on this track?
 *
 * A whitespace-only tag counts as missing — a file tagged `"   "` is not tagged,
 * and treating it as present is how those files stay invisible to the report
 * that exists to find them. Numeric fields are only ever absent or present:
 * `0` is a legitimate track number, so it must not be swept up as "missing".
 */
export function isFieldMissing(
  track: CatalogTrack,
  field: MissingMetadataField,
): boolean {
  if (field === "has_cover") return !track.has_cover;
  if (field === "rating") return track.rating == null;
  if (field === "year" || field === "track_number" || field === "disc_number") {
    return track[field] == null;
  }
  const value = track[field];
  return value == null || String(value).trim() === "";
}

/** Tracks missing at least one of `fields`. An empty field list matches nothing. */
export function tracksMissingMetadata(
  tracks: readonly CatalogTrack[],
  fields: readonly MissingMetadataField[] = DEFAULT_MISSING_METADATA_FIELDS,
): CatalogTrack[] {
  if (fields.length === 0) return [];
  return tracks.filter((track) => fields.some((field) => isFieldMissing(track, field)));
}

/**
 * The key two files have to share to be considered the same recording:
 * artist, album and title, compared case- and padding-insensitively.
 *
 * Deliberately not the path or the duration — the point is to find the same
 * song filed twice, which is usually two rips at different bitrates in two
 * folders.
 */
export function duplicateKey(track: CatalogTrack): string {
  return duplicateKeyParts(track).join("|");
}

function duplicateKeyParts(track: CatalogTrack): string[] {
  const part = (v: string | null | undefined) => (v ?? "").trim().toLowerCase();
  return [part(track.artist), part(track.album), part(track.title)];
}

/**
 * Groups of tracks that share a duplicate key, largest group first.
 *
 * A track with no artist, album or title at all is skipped: those all collapse
 * to one key and would report every untagged file in the library as a duplicate
 * of every other, which is the "missing metadata" report's job instead.
 */
export function duplicateGroups(tracks: readonly CatalogTrack[]): CatalogTrack[][] {
  const groups = new Map<string, CatalogTrack[]>();
  for (const track of tracks) {
    if (duplicateKeyParts(track).every((part) => part === "")) continue;
    const key = duplicateKey(track);
    const existing = groups.get(key);
    if (existing) existing.push(track);
    else groups.set(key, [track]);
  }
  return [...groups.values()]
    .filter((group) => group.length > 1)
    .sort((a, b) => b.length - a.length);
}

/** Every track that is part of a duplicate group, grouped copies kept adjacent. */
export function duplicateTracks(tracks: readonly CatalogTrack[]): CatalogTrack[] {
  return duplicateGroups(tracks).flat();
}

/**
 * How many files could be deleted without losing a recording — the number of
 * copies beyond the first in each group, not the number of tracks involved.
 *
 * Two copies of one song is one duplicate, not two; showing "2" next to a
 * report that lists two rows reads as "two things to fix" when there is one.
 */
export function duplicateCount(tracks: readonly CatalogTrack[]): number {
  return duplicateGroups(tracks).reduce((total, group) => total + group.length - 1, 0);
}

export function tracksMissingAlbumCover(tracks: readonly CatalogTrack[]): CatalogTrack[] {
  return tracks.filter((track) => !track.has_cover);
}

/** Played at least once, most recent first. */
export function recentlyPlayedTracks(tracks: readonly CatalogTrack[]): CatalogTrack[] {
  return tracks
    .filter((track) => track.last_played_at != null)
    .sort((a, b) => (b.last_played_at ?? 0) - (a.last_played_at ?? 0));
}

/** Played at least once, most plays first. */
export function mostPlayedTracks(tracks: readonly CatalogTrack[]): CatalogTrack[] {
  return tracks
    .filter((track) => (track.play_count ?? 0) > 0)
    .sort((a, b) => (b.play_count ?? 0) - (a.play_count ?? 0));
}

/** The tracks one report lists. */
export function runReport(
  kind: ReportKind,
  tracks: readonly CatalogTrack[],
  missingFields: readonly MissingMetadataField[] = DEFAULT_MISSING_METADATA_FIELDS,
): CatalogTrack[] {
  switch (kind) {
    case "missing_metadata":
      return tracksMissingMetadata(tracks, missingFields);
    case "duplicates":
      return duplicateTracks(tracks);
    case "missing_album_cover":
      return tracksMissingAlbumCover(tracks);
    case "recently_played":
      return recentlyPlayedTracks(tracks);
    case "most_played":
      return mostPlayedTracks(tracks);
  }
}

/**
 * The number shown next to a report in the sidebar.
 *
 * Everything except duplicates counts rows; duplicates counts redundant copies,
 * which is the actionable number — see [`duplicateCount`].
 */
export function reportCount(
  kind: ReportKind,
  tracks: readonly CatalogTrack[],
  missingFields: readonly MissingMetadataField[] = DEFAULT_MISSING_METADATA_FIELDS,
): number {
  if (kind === "duplicates") return duplicateCount(tracks);
  return runReport(kind, tracks, missingFields).length;
}

/** Every report's count in one pass, for rendering the whole list. */
export function reportCounts(
  tracks: readonly CatalogTrack[],
  missingFields: readonly MissingMetadataField[] = DEFAULT_MISSING_METADATA_FIELDS,
): Record<ReportKind, number> {
  return {
    missing_metadata: reportCount("missing_metadata", tracks, missingFields),
    duplicates: duplicateCount(tracks),
    missing_album_cover: reportCount("missing_album_cover", tracks),
    recently_played: reportCount("recently_played", tracks),
    most_played: reportCount("most_played", tracks),
  };
}

/** Which of `fields` this track is missing — for a per-row explanation. */
export function missingFieldsFor(
  track: CatalogTrack,
  fields: readonly MissingMetadataField[] = DEFAULT_MISSING_METADATA_FIELDS,
): MissingMetadataField[] {
  return fields.filter((field) => isFieldMissing(track, field));
}

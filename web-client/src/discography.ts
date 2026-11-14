import type { AlbumGridItem, CatalogTrack } from "./types";

/**
 * An artist's discography, built client-side from the catalog.
 *
 * Muorg has no release-type tag, so "album / EP / single" is inferred the way
 * the streaming services do it (Spotify's published rule), with an explicit
 * "EP" / "Single" in the title winning over the counts:
 *
 * - 7+ tracks, or more than 30 minutes → album
 * - 4–6 tracks → EP
 * - 1–3 tracks → single, or an EP when one of them runs 10 minutes or more
 *
 * The counts are what the library holds, not what the release has: an album
 * with only two of its tracks present reads as a single.
 *
 * Mirrored by `android-client/.../util/Discography.kt` — keep the two in step.
 */

export type ReleaseKind = "album" | "ep" | "single";

export interface Discography {
  /** Every track that credits the artist (artist, album artist or featuring). */
  tracks: CatalogTrack[];
  albums: AlbumGridItem[];
  eps: AlbumGridItem[];
  singles: AlbumGridItem[];
  /** Releases by someone else (compilations, other artists' albums) that the
   *  artist is on. */
  appearsOn: AlbumGridItem[];
  /** Credited tracks with no album tag at all. */
  looseTracks: CatalogTrack[];
}

const ALBUM_MIN_TRACKS = 7;
const EP_MIN_TRACKS = 4;
const ALBUM_MIN_SECS = 30 * 60;
const LONG_TRACK_SECS = 10 * 60;

/** "Night Drive EP", "Remixes (EP)", "EP2", "Volume EP 3". */
const EP_TITLE = /\bEP\s?\d*\b/i;
/** "Track - Single", "Track (Single)", "Track [single]" — only at the end, so
 *  "Single Ladies" stays an album name. */
const SINGLE_TITLE = /(?:^|[\s([-])single\s*[)\]]?\s*$/i;

/** Placeholder credits that must never become an artist page. */
const PLACEHOLDER_ARTISTS = new Set(["", "—", "unknown artist", "various artists"]);

export function normalizeArtist(name: string | null | undefined): string {
  return (name ?? "").trim().toLowerCase();
}

/** Whether a credit names a real artist worth linking to. */
export function isLinkableArtist(name: string | null | undefined): name is string {
  return !PLACEHOLDER_ARTISTS.has(normalizeArtist(name));
}

/** The names in a `featuring` tag: "A, B & C", "A; B", "A / B", "A and B". */
export function splitCredits(value: string | null | undefined): string[] {
  if (!value) return [];
  return value
    .split(/\s*(?:,|;|&|\/|\band\b|\bfeat\.?|\bft\.?)\s*/i)
    .map((s) => normalizeArtist(s))
    .filter((s) => s.length > 0);
}

/** Whether a track credits the artist (already normalized). */
export function creditsArtist(t: CatalogTrack, norm: string): boolean {
  if (!norm) return false;
  return (
    normalizeArtist(t.artist) === norm ||
    normalizeArtist(t.album_artist) === norm ||
    splitCredits(t.featuring).includes(norm)
  );
}

export function classifyRelease(title: string, durationsSecs: readonly number[]): ReleaseKind {
  if (SINGLE_TITLE.test(title)) return "single";
  if (EP_TITLE.test(title)) return "ep";
  const n = durationsSecs.length;
  const total = durationsSecs.reduce((s, d) => s + d, 0);
  if (n >= ALBUM_MIN_TRACKS || total > ALBUM_MIN_SECS) return "album";
  if (n >= EP_MIN_TRACKS) return "ep";
  return durationsSecs.some((d) => d >= LONG_TRACK_SECS) ? "ep" : "single";
}

/** Newest first; undated releases last, then by title. */
function compareReleases(a: AlbumGridItem, b: AlbumGridItem): number {
  if (a.year !== b.year) {
    if (a.year == null) return 1;
    if (b.year == null) return -1;
    return b.year - a.year;
  }
  return a.album.toLowerCase().localeCompare(b.album.toLowerCase());
}

function hasAlbum(t: CatalogTrack): boolean {
  return (t.album ?? "").trim().length > 0;
}

/**
 * Builds an artist's discography from the whole catalog.
 *
 * `keyForTrack` / `albumByKey` are the library store's own album identity, so
 * a release here is exactly the album the library grid shows (all of its
 * tracks, not just the ones that credit this artist). `durationOf` resolves a
 * track id to its duration for the classification.
 */
export function buildDiscography(
  artist: string,
  allTracks: readonly CatalogTrack[],
  keyForTrack: (t: CatalogTrack) => string,
  albumByKey: (key: string) => AlbumGridItem | null,
  durationOf: (trackId: number) => number,
): Discography {
  const norm = normalizeArtist(artist);
  const tracks = allTracks.filter((t) => creditsArtist(t, norm));

  const looseTracks: CatalogTrack[] = [];
  const releases = new Map<string, AlbumGridItem>();
  for (const t of tracks) {
    if (!hasAlbum(t)) {
      looseTracks.push(t);
      continue;
    }
    const key = keyForTrack(t);
    if (releases.has(key)) continue;
    const item = albumByKey(key);
    if (item) releases.set(key, item);
  }

  const out: Discography = {
    tracks,
    albums: [],
    eps: [],
    singles: [],
    appearsOn: [],
    looseTracks,
  };
  for (const item of releases.values()) {
    if (normalizeArtist(item.albumArtist) !== norm) {
      out.appearsOn.push(item);
      continue;
    }
    const kind = classifyRelease(item.album, item.trackIds.map(durationOf));
    if (kind === "album") out.albums.push(item);
    else if (kind === "ep") out.eps.push(item);
    else out.singles.push(item);
  }
  out.albums.sort(compareReleases);
  out.eps.sort(compareReleases);
  out.singles.sort(compareReleases);
  out.appearsOn.sort(compareReleases);
  return out;
}

/**
 * Wire types come from the shared, generated API contract — see
 * `src/api/README.md`. Re-exported here so the existing `../types` imports keep
 * working; a field renamed on the server now breaks this app's build instead of
 * failing silently at runtime.
 */
import type { CatalogTrack } from "@shared/api";

export type {
  CatalogTrack,
  LibraryStats,
  MatchCandidate,
  Playlist,
  PlaylistTrackEntry,
} from "@shared/api";

/** One row of a smart playlist's rules editor. Serialized as
 *  { field, op, value } per the server's rules_json format. */
export interface SmartRule {
  field: string;
  op: string;
  value: string;
}

export interface AlbumGridItem {
  key: string;
  album: string;
  albumArtist: string;
  year: number | null;
  trackCount: number;
  totalDurationSecs: number;
  coverTrackId: number | null;
  hasCover: boolean;
  trackIds: number[];
}

/** One search-result card: an album-grid item built from a single track,
 *  plus the track itself for opening/playing. */
export interface SearchResultCard {
  item: AlbumGridItem;
  track: CatalogTrack;
}

export type AlbumViewStyle = "grid" | "list" | "tracks";
/** The Library's layouts: album covers (grid), the album list, or the
 *  tracks table — same three as the Android app. */
export type LibraryViewStyle = AlbumViewStyle;
export type SortMode = "album" | "artist" | "year";

/**
 * The order-by fields for a playlist's tracks (mirrors the Android app's
 * `PlaylistSortMode`). `"order"` is the saved playlist order — what
 * drag-reorder writes; the rest are view transforms.
 */
export type PlaylistSortMode = "order" | "added" | "title" | "artist";
export type ThemeMode = "dark" | "light" | "system";
export type AccentColor = "green" | "blue" | "purple" | "orange" | "red" | "teal" | "dynamic";
export type RepeatMode = "off" | "all" | "one";

/** One recent-search entry: a typed query (shown as a chip) or an artist
 *  picked from the artist matches (shown as a large artist card). */
export type SearchHistoryEntry =
  | { type: "query"; value: string }
  | { type: "artist"; value: string };

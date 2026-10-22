/**
 * Wire types come from the shared, generated API contract — see
 * `src/api/README.md`. Re-exported here so the existing `../types` imports keep
 * working; a field renamed on the server now breaks this app's build instead of
 * failing silently at runtime.
 */
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

export type AlbumViewStyle = "grid" | "list" | "tracks";
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

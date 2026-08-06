export interface CatalogTrack {
  id: number;
  path: string;
  root_id: number;
  title: string | null;
  artist: string | null;
  album: string | null;
  album_artist: string | null;
  featuring: string | null;
  year: number | null;
  genre: string | null;
  track_number: number | null;
  disc_number: number | null;
  duration_secs: number | null;
  format: string;
  mtime_secs: number;
  has_cover: boolean;
  rating: number | null;
  play_count: number;
  last_played_at: number | null;
}

export interface LibraryStats {
  track_count: number;
  artist_count: number;
  album_count: number;
  total_duration_secs: number;
}

export interface Playlist {
  id: number;
  name: string;
  track_count: number;
  icon: string | null;
  smart_rules: string | null;
}

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
export type ThemeMode = "dark" | "light" | "system";
export type RepeatMode = "off" | "all" | "one";

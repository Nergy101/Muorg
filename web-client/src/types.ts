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

export type ViewMode = "grid" | "table";
export type GridSortBy = "album" | "artist" | "year";
export type TableSortCol = "title" | "artist" | "album" | "year" | "duration";
export type SortDir = "asc" | "desc";
export type GroupBy = "none" | "album" | "artist";

export interface TableGroupRow {
  type: "group";
  key: string;
  label: string;
  coverTrackId: number | null;
  hasCover: boolean;
  trackCount: number;
  totalDurationSecs: number;
  year: number | null;
  collapsed: boolean;
}

export interface TableTrackRow {
  type: "track";
  track: CatalogTrack;
  groupKey: string;
}

export type TableRow = TableGroupRow | TableTrackRow;

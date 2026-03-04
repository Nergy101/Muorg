export interface CatalogTrack {
  id: number;
  path: string;
  root_id: number;
  title: string | null;
  artist: string | null;
  album: string | null;
  album_artist: string | null;
  year: number | null;
  genre: string | null;
  track_number: number | null;
  disc_number: number | null;
  duration_secs: number | null;
  format: string;
  mtime_secs: number;
  /** True if the track has embedded album cover art. */
  has_cover: boolean;
}

export interface MetadataUpdate {
  title?: string | null;
  artist?: string | null;
  album?: string | null;
  album_artist?: string | null;
  year?: number | null;
  genre?: string | null;
  track_number?: number | null;
  disc_number?: number | null;
  picture_base64?: string | null;
}

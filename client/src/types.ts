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
  /** True if the track has embedded album cover art. */
  has_cover: boolean;
  /** User rating 1–5, or null if unrated. */
  rating: number | null;
  /** Number of times this track has been played past 30 s. */
  play_count: number;
  /** Unix timestamp (seconds) of the most recent play, or null if never played. */
  last_played_at: number | null;
}

export interface TrackMetadataRead {
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
  picture_base64: string | null;
  picture_mime: string | null;
  picture_size_bytes: number | null;
  replaygain_track_gain_db: number | null;
  replaygain_track_peak: number | null;
  replaygain_album_gain_db: number | null;
  replaygain_album_peak: number | null;
}

export interface TrackBackupRecord {
  id: number;
  track_path: string;
  backup_path: string;
  created_at: number;
}

export interface Playlist {
  id: number;
  name: string;
  track_count: number;
  icon: string | null;
  /** JSON rule array for smart playlists; null for regular playlists. */
  smart_rules: string | null;
}

export interface LibraryStats {
  track_count: number;
  artist_count: number;
  album_count: number;
  total_duration_secs: number;
}

export interface MetadataUpdate {
  title?: string | null;
  artist?: string | null;
  album?: string | null;
  album_artist?: string | null;
  featuring?: string | null;
  year?: number | null;
  genre?: string | null;
  track_number?: number | null;
  disc_number?: number | null;
  picture_base64?: string | null;
}

/** A snapshot of a single track's metadata state before a save operation. */
export interface UndoSnapshot {
  trackId: number;
  path: string;
  /** The metadata values at the time of the snapshot (all explicitly set). */
  metadata: MetadataUpdate;
}

/** An undo/redo entry representing one user-facing save operation. */
export interface UndoEntry {
  description: string;
  snapshots: UndoSnapshot[];
}

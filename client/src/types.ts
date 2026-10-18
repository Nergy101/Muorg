/**
 * Wire types come from the shared, generated API contract — see
 * `src/api/README.md`. Re-exported here so the existing `@/types` imports keep
 * working; a field renamed on the server now breaks this app's build instead of
 * failing silently at runtime.
 *
 * `TrackMetadataRead` keeps its local name: the server calls the same struct
 * `TrackMetadata`.
 */
export type {
  CatalogTrack,
  LibraryStats,
  MetadataUpdate,
  Playlist,
  TrackBackupRecord,
  TrackMetadata as TrackMetadataRead,
} from "@shared/api";

import type { MetadataUpdate } from "@shared/api";

/** A snapshot of a single track's state before a save operation. */
export interface UndoSnapshot {
  trackId: number;
  path: string;
  /** The metadata values at the time of the snapshot (all explicitly set). */
  metadata: MetadataUpdate;
  /** The star rating at the time of the snapshot (null = unrated). */
  rating: number | null;
}

/** An undo/redo entry representing one user-facing save operation. */
export interface UndoEntry {
  description: string;
  /**
   * What the entry changes: "metadata" rewrites file tags, "rating" only
   * touches the DB rating. Drives how the entry is restored on undo/redo.
   */
  kind: "metadata" | "rating";
  snapshots: UndoSnapshot[];
}

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

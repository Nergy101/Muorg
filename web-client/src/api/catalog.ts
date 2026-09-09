/**
 * Catalog calls, delegated to the shared typed client in `src/api/`.
 *
 * The signatures here are the ones this app already used; the request building
 * and the wire types now come from the generated contract, so a server change
 * shows up as a compile error rather than a runtime shrug.
 */

import { api } from "./client";
import type { CatalogTrack, LibraryStats } from "../types";
import type { MetadataUpdate, TracksPage, TrackLyrics } from "@shared/api";

export type { TracksPage, TrackLyrics, MetadataUpdate };

/** One page of tracks plus the total count (from `X-Total-Count`). */
export function getTracks(offset = 0, limit?: number): Promise<TracksPage> {
  return api.getTracksPage(offset, limit);
}

/**
 * The catalog, one page at a time. Render each page as it arrives instead of
 * blocking on the whole library — `/api/tracks` is paginated at 500 rows.
 */
export function streamTracks(): AsyncGenerator<TracksPage, void, void> {
  return api.streamTracks();
}

export function getStats(): Promise<LibraryStats> {
  return api.getStats();
}

/** Most recently scanned tracks (newest first). */
export function getRecentlyAdded(limit = 20): Promise<CatalogTrack[]> {
  return api.getRecentlyAdded(limit);
}

/** Most recently played tracks (newest first). */
export function getRecentPlayHistory(limit = 20): Promise<CatalogTrack[]> {
  return api.getRecentlyPlayed(limit);
}

/** Most played tracks within the last `days` days (highest count first). */
export function getTopPlayHistory(limit = 20, days = 30): Promise<CatalogTrack[]> {
  return api.getMostPlayed(limit, days);
}

export async function getCoverBlob(trackId: number): Promise<Blob | null> {
  try {
    return await api.getCoverBlob(trackId);
  } catch {
    return null;
  }
}

export function issueStreamToken(trackId: number): Promise<string> {
  return api.getStreamToken(trackId);
}

/** Embedded lyrics for a track, or null when it has none (404). */
export function getTrackLyrics(trackId: number): Promise<TrackLyrics | null> {
  return api.getLyrics(trackId);
}

export async function recordPlay(trackId: number): Promise<void> {
  await api.recordPlay(trackId).catch(() => {
    /* fire-and-forget */
  });
}

export async function patchMetadata(
  trackId: number,
  update: MetadataUpdate,
  backupBeforeWrite: boolean,
): Promise<void> {
  await api.patchMetadata(trackId, update, backupBeforeWrite);
}

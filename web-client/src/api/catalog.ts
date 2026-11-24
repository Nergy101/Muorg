/**
 * Catalog calls, delegated to the shared typed client in `src/api/`.
 *
 * The signatures here are the ones this app already used; the request building
 * and the wire types now come from the generated contract, so a server change
 * shows up as a compile error rather than a runtime shrug.
 */

import { api } from "./client";
import type {
  CatalogTrack,
  ClockBucket,
  LibraryStats,
  ListeningSummary,
  ListeningWeek,
  TopAlbumStat,
  TopArtistStat,
  TopTrackStat,
} from "../types";
import type { MetadataUpdate, StreamTokenInfo, TracksPage, TrackLyrics, TrackMetadata } from "@shared/api";

export type { TracksPage, TrackLyrics, MetadataUpdate, TrackMetadata };

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

// ------------------------------------------------------- listening stats ---

/**
 * Listening totals over the window: plays, seconds listened, and how many
 * distinct tracks, artists and albums that covered (NER-325).
 */
export function getListeningSummary(days = 30): Promise<ListeningSummary> {
  return api.getListeningSummary(days);
}

/** Top tracks inside the window, ranked by seconds listened. */
export function getTopTracksByTime(limit = 10, days = 30): Promise<TopTrackStat[]> {
  return api.getTopTracksByTime(limit, days);
}

/** Top artists inside the window, ranked by seconds listened. */
export function getTopArtistsByTime(limit = 10, days = 30): Promise<TopArtistStat[]> {
  return api.getTopArtistsByTime(limit, days);
}

/** Top albums inside the window, ranked by seconds listened. */
export function getTopAlbumsByTime(limit = 10, days = 30): Promise<TopAlbumStat[]> {
  return api.getTopAlbumsByTime(limit, days);
}

/**
 * Listening time per week, read from the server's daily rollup so pruning
 * play history never eats the graph. Empty weeks included, oldest first.
 */
export function getListeningTime(weeks = 12): Promise<ListeningWeek[]> {
  return api.getListeningTime(weeks);
}

/** Plays and listening time per hour of the day, server-local timezone. */
export function getPlayClock(days = 30): Promise<ClockBucket[]> {
  return api.getPlayClock(days);
}

export async function getCoverBlob(trackId: number, size?: number): Promise<Blob | null> {
  try {
    return await api.getCoverBlob(trackId, size);
  } catch {
    return null;
  }
}

export function issueStreamToken(trackId: number): Promise<string> {
  return api.getStreamToken(trackId);
}

/**
 * Stream token plus the server's `flac`/`wav` capability flags — use this
 * (with `api.streamUrlForTrack`) when the caller needs to know whether the
 * server can serve the track's original lossless file.
 */
export function issueStreamTokenInfo(trackId: number): Promise<StreamTokenInfo> {
  return api.getStreamTokenInfo(trackId);
}

/** Embedded lyrics for a track, or null when it has none (404). */
export function getTrackLyrics(trackId: number): Promise<TrackLyrics | null> {
  return api.getLyrics(trackId);
}

/** Full tag read (incl. ReplayGain), or null on failure — a missing read
 *  must not break playback. */
export async function getMetadata(trackId: number): Promise<TrackMetadata | null> {
  try {
    return await api.getMetadata(trackId);
  } catch {
    return null;
  }
}

/**
 * Record a play, attributed to this device's name (Q19) and — when the caller
 * knows it — the seconds actually listened (NER-325). Fire-and-forget on
 * purpose; playback must never block or break on this.
 */
export async function recordPlay(trackId: number, deviceName?: string, durationSecs?: number): Promise<void> {
  await api.recordPlay(trackId, deviceName, durationSecs).catch(() => {
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

/**
 * Delete a track's file from the server and drop its catalog row.
 * Destructive — callers must confirm with the user first.
 */
export async function deleteTrack(trackId: number): Promise<void> {
  await api.deleteTrack(trackId);
}

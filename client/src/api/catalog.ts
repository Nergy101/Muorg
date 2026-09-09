/**
 * Catalog calls, delegated to the shared typed client in `src/api/`.
 *
 * The signatures here are the ones this app already used; the request building,
 * the wire types and — importantly — the pagination now come from the shared
 * contract, so a server change shows up as a compile error rather than a
 * runtime shrug.
 */

import { api } from "./client";
import type { CatalogTrack, MetadataUpdate, TrackMetadataRead } from "../types";
import type { MatchCandidate, TracksPage } from "@shared/api";

export type { TrackMetadataRead, TracksPage };

export interface CoverInfo {
  base64: string;
  mime: string;
  size_bytes: number;
}

export interface LibraryStats {
  track_count: number;
  artist_count: number;
  album_count: number;
  total_duration_secs: number;
}

export function getRoots(): Promise<string[]> {
  return api.getRoots();
}

/**
 * The catalog, one page at a time.
 *
 * `/api/tracks` is paginated at 500 rows. This used to be a loop here that
 * collected every page before returning, which meant a spinner until the whole
 * library had landed — seven round-trips for a 3k-track collection. Yielding
 * per page lets the table paint the first 500 immediately, which is what the
 * web and Android clients already do.
 */
export function streamTracks(): AsyncGenerator<TracksPage, void, void> {
  return api.streamTracks();
}

/** The whole catalog in one array, for callers that cannot render as it loads. */
export function getTracks(): Promise<CatalogTrack[]> {
  return api.fetchAllTracks();
}

export function searchTracks(query: string): Promise<CatalogTrack[]> {
  return api.searchTracks(query);
}

export function getStats(): Promise<LibraryStats> {
  return api.getStats();
}

export function rescan(rootPath?: string): Promise<number> {
  return api.rescan(rootPath);
}

export async function addFolder(
  path: string,
): Promise<{ roots: string[]; tracks_added: number }> {
  const tracksAdded = await rescan(path);
  const roots = await getRoots();
  return { roots, tracks_added: tracksAdded };
}

export async function removeFolder(rootPath: string): Promise<void> {
  await api.removeFolder(rootPath);
}

export async function clearCache(): Promise<void> {
  await api.clearCache();
}

export async function getCover(trackId: number): Promise<CoverInfo | null> {
  try {
    const blob = await api.getCoverBlob(trackId);
    const base64 = await blobToBase64(blob);
    return {
      base64: base64.split(",")[1] ?? base64,
      mime: blob.type,
      size_bytes: blob.size,
    };
  } catch {
    return null;
  }
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

export async function getMetadata(
  trackId: number,
): Promise<TrackMetadataRead | null> {
  try {
    return await api.getMetadata(trackId);
  } catch {
    return null;
  }
}

export async function patchMetadata(
  trackId: number,
  update: MetadataUpdate,
  backupBeforeWrite: boolean,
): Promise<void> {
  await api.patchMetadata(trackId, update, backupBeforeWrite);
}

export function patchMetadataBatch(
  items: { id: number; update: MetadataUpdate }[],
): Promise<{ ok: boolean; updated: number }> {
  // The server flattens the patch onto the item (`#[serde(flatten)]`), so the
  // wire shape is `{ id, title?, artist?, ... }`, not `{ id, update }`.
  return api.patchMetadataBatch(items.map(({ id, update }) => ({ id, ...update })));
}

export async function recordPlay(trackId: number): Promise<void> {
  await api.recordPlay(trackId);
}

export async function setRating(
  trackId: number,
  rating: number | null,
): Promise<void> {
  await api.setRating(trackId, rating);
}

export function issueStreamToken(trackId: number): Promise<string> {
  return api.getStreamToken(trackId);
}

export async function fetchImageUrl(url: string): Promise<CoverInfo | null> {
  try {
    const image = await api.fetchImage(url);
    return {
      base64: image.base64,
      mime: image.mime,
      // The endpoint returns base64 only; derive the decoded size so the cover
      // comparison in MetadataEditor has a real number to work with.
      size_bytes: base64ByteLength(image.base64),
    };
  } catch {
    return null;
  }
}

function base64ByteLength(base64: string): number {
  const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
  return Math.max(0, (base64.length * 3) / 4 - padding);
}

export async function getLatestBackup(
  trackId: number,
): Promise<{ path: string } | null> {
  try {
    const record = await api.getLatestBackup(trackId);
    return record ? { path: record.backup_path } : null;
  } catch {
    return null;
  }
}

export async function restoreFromLatestBackup(trackId: number): Promise<void> {
  await api.restoreFromBackup(trackId);
}

export function getBackupDir(): Promise<string> {
  return api.getBackupDirectory();
}

export async function renameTrackFile(
  trackId: number,
  newPath: string,
): Promise<void> {
  await api.renameTrackFile(trackId, newPath);
}

/** Embedded lyrics for a track, or null when it has none. */
export function getTrackLyrics(trackId: number) {
  return api.getLyrics(trackId);
}

// ── Auto-tagging (MusicBrainz) ────────────────────────────────────────────────

export type AutoTagCandidate = MatchCandidate;

export interface AutoTagResponse {
  candidates: AutoTagCandidate[];
}

export async function getAutoTagSuggestions(
  trackId: number,
  query?: { artist?: string | null; title?: string | null; album?: string | null },
): Promise<AutoTagResponse> {
  return { candidates: await api.autoTagSuggestions(trackId, query) };
}

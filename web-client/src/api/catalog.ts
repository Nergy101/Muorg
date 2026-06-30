import { apiFetch, apiFetchBlob } from "./client";
import type { CatalogTrack, LibraryStats } from "../types";

export async function getTracks(): Promise<CatalogTrack[]> {
  return apiFetch<CatalogTrack[]>("/api/tracks");
}

export async function getStats(): Promise<LibraryStats> {
  return apiFetch<LibraryStats>("/api/stats");
}

export async function getCoverBlob(trackId: number): Promise<Blob | null> {
  try {
    return await apiFetchBlob(`/api/tracks/${trackId}/cover`);
  } catch {
    return null;
  }
}

export async function issueStreamToken(trackId: number): Promise<string> {
  const result = await apiFetch<{ token: string }>(
    `/api/tracks/${trackId}/stream-token`,
  );
  return result.token;
}

export async function recordPlay(trackId: number): Promise<void> {
  apiFetch(`/api/tracks/${trackId}/play`, { method: "POST" }).catch(() => {
    /* fire-and-forget */
  });
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
}

export async function patchMetadata(
  trackId: number,
  update: MetadataUpdate,
  backupBeforeWrite: boolean,
): Promise<void> {
  await apiFetch(`/api/tracks/${trackId}/metadata`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...update, backup_before_write: backupBeforeWrite }),
  });
}

export async function patchMetadataBatch(
  items: { id: number; update: MetadataUpdate }[],
): Promise<{ ok: boolean; updated: number }> {
  return apiFetch("/api/tracks/metadata/batch", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(items),
  });
}

export async function setRating(trackId: number, rating: number | null): Promise<void> {
  await apiFetch(`/api/tracks/${trackId}/rating`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ rating }),
  });
}

/** Set rating for multiple tracks in parallel. */
export async function batchSetRating(trackIds: number[], rating: number | null): Promise<void> {
  await Promise.all(trackIds.map((id) => setRating(id, rating)));
}

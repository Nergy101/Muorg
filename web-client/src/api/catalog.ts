import { apiFetch, apiFetchBlob, getApiKey, getServerUrl } from "./client";
import type { CatalogTrack, LibraryStats } from "../types";

export interface TracksPage {
  tracks: CatalogTrack[];
  total: number;
}

/** Fetch a page of tracks plus the total count (via X-Total-Count header). */
export async function getTracks(offset = 0, limit = 500): Promise<TracksPage> {
  const url = `${getServerUrl()}/api/tracks?offset=${offset}&limit=${limit}`;
  const headers = new Headers();
  const key = getApiKey();
  if (key) headers.set("Authorization", `Bearer ${key}`);
  const res = await fetch(url, { headers });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  const total = Number(res.headers.get("X-Total-Count") ?? 0);
  const tracks = (await res.json()) as CatalogTrack[];
  return { tracks, total };
}

export async function getStats(): Promise<LibraryStats> {
  return apiFetch<LibraryStats>("/api/stats");
}

/** Most recently scanned tracks (newest first). */
export async function getRecentlyAdded(limit = 20): Promise<CatalogTrack[]> {
  return apiFetch<CatalogTrack[]>(`/api/tracks/recently-added?limit=${limit}`);
}

/** Most recently played tracks (newest first). */
export async function getRecentPlayHistory(limit = 20): Promise<CatalogTrack[]> {
  return apiFetch<CatalogTrack[]>(`/api/play-history/recent?limit=${limit}`);
}

/** Most played tracks within the last `days` days (highest count first). */
export async function getTopPlayHistory(limit = 20, days = 30): Promise<CatalogTrack[]> {
  return apiFetch<CatalogTrack[]>(`/api/play-history/top?limit=${limit}&days=${days}`);
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

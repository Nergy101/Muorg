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

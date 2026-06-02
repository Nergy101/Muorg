import { apiFetch, apiFetchBlob } from "./client";
import type { CatalogTrack, MetadataUpdate, TrackMetadataRead } from "../types";

export type { TrackMetadataRead };

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

export async function getRoots(): Promise<string[]> {
  return apiFetch<string[]>("/api/roots");
}

export async function getTracks(): Promise<CatalogTrack[]> {
  return apiFetch<CatalogTrack[]>("/api/tracks");
}

export async function searchTracks(query: string): Promise<CatalogTrack[]> {
  return apiFetch<CatalogTrack[]>(`/api/search?q=${encodeURIComponent(query)}`);
}

export async function getStats(): Promise<LibraryStats> {
  return apiFetch<LibraryStats>("/api/stats");
}

export async function rescan(rootPath?: string): Promise<number> {
  const body = rootPath ? { root_path: rootPath } : undefined;
  const result = await apiFetch<{ tracks_added: number }>("/api/admin/rescan", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  return result.tracks_added;
}

export async function addFolder(path: string): Promise<{ roots: string[]; tracks_added: number }> {
  const tracksAdded = await rescan(path);
  const roots = await getRoots();
  return { roots, tracks_added: tracksAdded };
}

export async function removeFolder(rootPath: string): Promise<void> {
  await apiFetch("/api/admin/remove-folder", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ root_path: rootPath }),
  });
}

export async function clearCache(): Promise<void> {
  await apiFetch("/api/admin/clear-cache", { method: "POST" });
}

export async function getCover(trackId: number): Promise<CoverInfo | null> {
  try {
    const blob = await apiFetchBlob(`/api/tracks/${trackId}/cover`);
    const base64 = await blobToBase64(blob);
    return { base64: base64.split(",")[1] ?? base64, mime: blob.type, size_bytes: blob.size };
  } catch {
    return null;
  }
}

export async function getMetadata(trackId: number): Promise<TrackMetadataRead | null> {
  try {
    return await apiFetch<TrackMetadataRead>(`/api/tracks/${trackId}/metadata`);
  } catch {
    return null;
  }
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

export async function recordPlay(trackId: number): Promise<void> {
  await apiFetch(`/api/tracks/${trackId}/play`, { method: "POST" });
}

export async function setRating(trackId: number, rating: number | null): Promise<void> {
  await apiFetch(`/api/tracks/${trackId}/rating`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ rating }),
  });
}

export async function issueStreamToken(trackId: number): Promise<string> {
  const result = await apiFetch<{ token: string }>(`/api/tracks/${trackId}/stream-token`);
  return result.token;
}

export async function fetchImageUrl(url: string): Promise<CoverInfo | null> {
  try {
    return apiFetch<CoverInfo | null>("/api/fetch-image", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url }),
    });
  } catch {
    return null;
  }
}

export async function getLatestBackup(trackId: number): Promise<{ path: string } | null> {
  try {
    return apiFetch<{ path: string } | null>(`/api/tracks/${trackId}/backup`);
  } catch {
    return null;
  }
}

export async function restoreFromLatestBackup(trackId: number): Promise<void> {
  await apiFetch(`/api/tracks/${trackId}/restore`, { method: "POST" });
}

export async function renameTrackFile(trackId: number, newPath: string): Promise<void> {
  await apiFetch(`/api/tracks/${trackId}/rename`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ new_path: newPath }),
  });
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

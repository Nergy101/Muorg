import { apiFetch } from "./client";
import type { Playlist } from "../types";

export async function getPlaylists(): Promise<Playlist[]> {
  return apiFetch<Playlist[]>("/api/playlists");
}

export async function createPlaylist(name: string, icon?: string | null): Promise<Playlist> {
  const p = await apiFetch<Playlist>("/api/playlists", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (icon !== undefined && icon !== null) {
    await apiFetch(`/api/playlists/${p.id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ icon }),
    });
    p.icon = icon;
  }
  return p;
}

export async function renamePlaylist(id: number, name: string, icon?: string | null): Promise<void> {
  const body: Record<string, unknown> = { name };
  if (icon !== undefined) body.icon = icon;
  await apiFetch(`/api/playlists/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function deletePlaylist(id: number): Promise<void> {
  await apiFetch(`/api/playlists/${id}`, { method: "DELETE" });
}

export async function getPlaylistTracks(playlistId: number): Promise<number[]> {
  return apiFetch<number[]>(`/api/playlists/${playlistId}/tracks`);
}

export async function addTracksToPlaylist(
  playlistId: number,
  trackIds: number[],
): Promise<void> {
  await apiFetch(`/api/playlists/${playlistId}/tracks`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ track_ids: trackIds }),
  });
}

export async function removeTracksFromPlaylist(
  playlistId: number,
  trackIds: number[],
): Promise<void> {
  await apiFetch(`/api/playlists/${playlistId}/tracks`, {
    method: "DELETE",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ track_ids: trackIds }),
  });
}

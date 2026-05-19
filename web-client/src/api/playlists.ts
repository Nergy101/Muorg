import { apiFetch } from "./client";
import type { Playlist } from "../types";

export async function getPlaylists(): Promise<Playlist[]> {
  return apiFetch<Playlist[]>("/api/playlists");
}

export async function createPlaylist(name: string): Promise<Playlist> {
  return apiFetch<Playlist>("/api/playlists", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
}

export async function renamePlaylist(id: number, name: string): Promise<void> {
  await apiFetch(`/api/playlists/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
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

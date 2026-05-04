import { apiFetch } from "./client";
import type { Playlist } from "../types";

export interface PlaylistEntry {
  entry_id: number;
  track_id: number;
}

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

export async function setPlaylistIcon(id: number, icon: string | null): Promise<void> {
  await apiFetch(`/api/playlists/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ icon }),
  });
}

export async function deletePlaylist(id: number): Promise<void> {
  await apiFetch(`/api/playlists/${id}`, { method: "DELETE" });
}

export async function getPlaylistTracks(playlistId: number): Promise<number[]> {
  return apiFetch<number[]>(`/api/playlists/${playlistId}/tracks`);
}

export async function getPlaylistsForTrack(trackId: number): Promise<number[]> {
  // The server returns playlists; filter to those containing the track.
  const all = await apiFetch<Playlist[]>("/api/playlists");
  const matching: number[] = [];
  for (const p of all) {
    const ids = await getPlaylistTracks(p.id).catch(() => [] as number[]);
    if (ids.includes(trackId)) matching.push(p.id);
  }
  return matching;
}

export async function getPlaylistEntries(playlistId: number): Promise<PlaylistEntry[]> {
  return apiFetch<PlaylistEntry[]>(`/api/playlists/${playlistId}/entries`);
}

export async function addTracksToPlaylist(playlistId: number, trackIds: number[]): Promise<void> {
  await apiFetch(`/api/playlists/${playlistId}/tracks`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ track_ids: trackIds }),
  });
}

export async function removeTracksFromPlaylist(playlistId: number, trackIds: number[]): Promise<void> {
  await apiFetch(`/api/playlists/${playlistId}/tracks`, {
    method: "DELETE",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ track_ids: trackIds }),
  });
}

export async function removePlaylistEntry(entryId: number): Promise<void> {
  // We need to find the playlist that contains this entry. The server API
  // requires the playlist ID in the path. Fetch all playlists to find it.
  const all = await apiFetch<Playlist[]>("/api/playlists");
  for (const p of all) {
    const entries = await getPlaylistEntries(p.id).catch(() => [] as PlaylistEntry[]);
    const entry = entries.find((e) => e.entry_id === entryId);
    if (entry) {
      await apiFetch(`/api/playlists/${p.id}/entries/${entryId}`, { method: "DELETE" });
      return;
    }
  }
  throw new Error(`Playlist entry ${entryId} not found`);
}

export async function createSmartPlaylist(name: string, rulesJson: string): Promise<Playlist> {
  return apiFetch<Playlist>("/api/playlists/smart", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, rules_json: rulesJson }),
  });
}

export async function updateSmartPlaylistRules(id: number, rulesJson: string): Promise<void> {
  await apiFetch(`/api/playlists/smart/${id}/rules`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ rules_json: rulesJson }),
  });
}

export async function getSmartPlaylistTrackIds(playlistId: number): Promise<number[]> {
  return apiFetch<number[]>(`/api/playlists/smart/${playlistId}/tracks`);
}

export async function reorderPlaylists(ids: number[]): Promise<void> {
  await apiFetch("/api/playlists/order", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ids }),
  });
}

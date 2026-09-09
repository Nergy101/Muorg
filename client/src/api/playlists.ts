/**
 * Playlist calls, delegated to the shared typed client in `src/api/`.
 */

import { api } from "./client";
import type { Playlist } from "../types";
import type { PlaylistTrackEntry } from "@shared/api";

export type PlaylistEntry = PlaylistTrackEntry;

export function getPlaylists(): Promise<Playlist[]> {
  return api.getPlaylists();
}

export function createPlaylist(name: string): Promise<Playlist> {
  return api.createPlaylist(name);
}

export async function renamePlaylist(id: number, name: string): Promise<void> {
  await api.updatePlaylist(id, { name });
}

export async function setPlaylistIcon(
  id: number,
  icon: string | null,
): Promise<void> {
  await api.updatePlaylist(id, { icon });
}

export async function deletePlaylist(id: number): Promise<void> {
  await api.deletePlaylist(id);
}

export function getPlaylistTracks(playlistId: number): Promise<number[]> {
  return api.getPlaylistTracks(playlistId);
}

export async function getPlaylistsForTrack(trackId: number): Promise<number[]> {
  // There is no server-side "which playlists hold this track" endpoint, so this
  // fans out over every playlist.
  const all = await api.getPlaylists();
  const matching: number[] = [];
  for (const p of all) {
    const ids = await getPlaylistTracks(p.id).catch(() => [] as number[]);
    if (ids.includes(trackId)) matching.push(p.id);
  }
  return matching;
}

export function getPlaylistEntries(
  playlistId: number,
): Promise<PlaylistEntry[]> {
  return api.getPlaylistEntries(playlistId);
}

export async function addTracksToPlaylist(
  playlistId: number,
  trackIds: number[],
): Promise<void> {
  await api.addTracksToPlaylist(playlistId, trackIds);
}

export async function removeTracksFromPlaylist(
  playlistId: number,
  trackIds: number[],
): Promise<void> {
  await api.removeTracksFromPlaylist(playlistId, trackIds);
}

export async function removePlaylistEntry(entryId: number): Promise<void> {
  // The delete route needs the playlist id in the path and the caller only has
  // the entry id, so search for the owning playlist.
  const all = await api.getPlaylists();
  for (const p of all) {
    const entries = await getPlaylistEntries(p.id).catch(
      () => [] as PlaylistEntry[],
    );
    if (entries.some((e) => e.entry_id === entryId)) {
      await api.removePlaylistEntry(p.id, entryId);
      return;
    }
  }
  throw new Error(`Playlist entry ${entryId} not found`);
}

export function createSmartPlaylist(
  name: string,
  rulesJson: string,
): Promise<Playlist> {
  return api.createSmartPlaylist(name, rulesJson);
}

export async function updateSmartPlaylistRules(
  id: number,
  rulesJson: string,
): Promise<void> {
  await api.updateSmartPlaylistRules(id, rulesJson);
}

export function getSmartPlaylistTrackIds(playlistId: number): Promise<number[]> {
  return api.getSmartPlaylistTracks(playlistId);
}

export async function reorderPlaylists(ids: number[]): Promise<void> {
  await api.reorderPlaylists(ids);
}

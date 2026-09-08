/**
 * Playlist calls, delegated to the shared typed client in `src/api/`.
 */

import { api } from "./client";
import type { Playlist } from "../types";

export function getPlaylists(): Promise<Playlist[]> {
  return api.getPlaylists();
}

export async function createPlaylist(
  name: string,
  icon?: string | null,
): Promise<Playlist> {
  const p = await api.createPlaylist(name);
  // The create endpoint takes a name only; the icon is a follow-up patch.
  if (icon !== undefined && icon !== null) {
    await api.updatePlaylist(p.id, { icon });
    p.icon = icon;
  }
  return p;
}

export function getSmartTracks(playlistId: number): Promise<number[]> {
  return api.getSmartPlaylistTracks(playlistId);
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

export async function renamePlaylist(
  id: number,
  name: string,
  icon?: string | null,
): Promise<void> {
  await api.updatePlaylist(id, icon !== undefined ? { name, icon } : { name });
}

export async function deletePlaylist(id: number): Promise<void> {
  await api.deletePlaylist(id);
}

export function getPlaylistTracks(playlistId: number): Promise<number[]> {
  return api.getPlaylistTracks(playlistId);
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

export async function reorderPlaylistTracks(
  playlistId: number,
  trackIds: number[],
): Promise<void> {
  await api.reorderPlaylistTracks(playlistId, trackIds);
}

/**
 * Reads a playlist's track ids. Smart playlists must go through the
 * smart endpoint — GET /api/playlists/{id}/tracks does not resolve rules.
 */
export function getTracksForPlaylist(p: Playlist): Promise<number[]> {
  return p.smart_rules != null ? getSmartTracks(p.id) : getPlaylistTracks(p.id);
}

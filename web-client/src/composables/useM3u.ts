/**
 * M3U playlist export — two modes, mirroring the desktop client's
 * PlaylistExportDialog where possible:
 *
 *  - "stream": each line is a token-authorised `<server>/stream/<id>` URL.
 *    Works anywhere, but the token expires after ~8 hours (same limit the
 *    existing PlaylistCard download already carried).
 *  - "relative": each line is the track's library path made relative to a
 *    chosen music-root folder name, same algorithm as the desktop dialog's
 *    `computeRelativePath` — split the path on whichever separator it uses,
 *    find the root folder segment, and keep everything from there on,
 *    prefixed with "../" (the M3U is assumed to sit next to the root).
 *    Desktop falls back to the untouched absolute path when the root segment
 *    isn't found; the web export instead *skips* that track and reports a
 *    count, since a stream of untranslated absolute paths is useless to
 *    whatever external player opens the .m3u.
 *
 * The builders are pure (no network, no DOM) so they're unit-testable; only
 * `buildStreamM3u` (token issuance) and `downloadM3uFile` (DOM) are impure.
 */

import { issueStreamToken } from "../api/catalog";
import { getServerUrl, streamUrl } from "../api/client";
import type { CatalogTrack } from "../types";

export type M3uMode = "stream" | "relative";

export interface M3uResult {
  content: string;
  /** Relative mode only: tracks whose path didn't contain the root folder, skipped. */
  skippedCount: number;
}

function extinfTitle(t: CatalogTrack): string {
  return [t.artist, t.title].filter(Boolean).join(" - ") || t.title || `Track ${t.id}`;
}

function extinfLine(t: CatalogTrack): string {
  return `#EXTINF:${Math.round(t.duration_secs ?? -1)},${extinfTitle(t)}`;
}

/**
 * Relative path for one track against a music-root folder name, or null if
 * that folder name doesn't appear as a path segment (track lies outside it).
 * Always forward-slashed, regardless of the source path's separator — M3U
 * consumers handle "/" more consistently than a mix of separators.
 */
export function computeRelativePath(trackPath: string, rootName: string): string | null {
  const sep = trackPath.includes("/") ? "/" : "\\";
  const parts = trackPath.split(sep);
  const idx = parts.findIndex((p) => p === rootName);
  if (idx === -1) return null;
  return "../" + parts.slice(idx).join("/");
}

/** Pure: builds the "relative paths" .m3u content. Tracks outside the root are skipped and counted. */
export function buildRelativeM3u(tracks: CatalogTrack[], musicRoot: string): M3uResult {
  const root = musicRoot.trim();
  const lines: string[] = ["#EXTM3U"];
  let skippedCount = 0;
  for (const t of tracks) {
    const rel = root ? computeRelativePath(t.path, root) : null;
    if (!rel) {
      skippedCount++;
      continue;
    }
    lines.push(extinfLine(t));
    lines.push(rel);
  }
  return { content: lines.join("\n") + "\n", skippedCount };
}

/** Preview the first track's relative-path line, for the export dialog. */
export function previewRelativePath(tracks: CatalogTrack[], musicRoot: string): string | null {
  const root = musicRoot.trim();
  if (!root) return null;
  for (const t of tracks) {
    const rel = computeRelativePath(t.path, root);
    if (rel) return rel;
  }
  return null;
}

/**
 * Builds the "stream URLs" .m3u content — issues a short-lived playback
 * token per track. A track whose token fails to issue falls back to an
 * unauthenticated stream URL (same as the pre-existing PlaylistCard export).
 */
export async function buildStreamM3u(tracks: CatalogTrack[]): Promise<M3uResult> {
  const tokens = await Promise.all(tracks.map((t) => issueStreamToken(t.id).catch(() => null)));
  const lines: string[] = ["#EXTM3U"];
  tracks.forEach((t, i) => {
    lines.push(extinfLine(t));
    const tok = tokens[i];
    lines.push(tok ? streamUrl(t.id, tok) : `${getServerUrl()}/stream/${t.id}`);
  });
  return { content: lines.join("\n") + "\n", skippedCount: 0 };
}

/** Filesystem-safe playlist filename, without extension. */
export function sanitizeM3uFilename(name: string): string {
  return name.replace(/[/\\?*:|"<>]/g, "_").trim() || "playlist";
}

/** Triggers a client-side download of M3U content as `<name>.m3u`, UTF-8 encoded. */
export function downloadM3uFile(content: string, playlistName: string): void {
  const blob = new Blob([content], { type: "audio/x-mpegurl;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${sanitizeM3uFilename(playlistName)}.m3u`;
  a.click();
  URL.revokeObjectURL(url);
}

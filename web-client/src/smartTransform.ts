import { buildTransformPath } from "@shared/utils/pathFormat";
import type { CatalogTrack } from "./types";

/** One track whose path matches the origin pattern, with its rewritten path. */
export interface TransformMatch {
  track: CatalogTrack;
  newPath: string;
}

/**
 * Tracks whose path matches `originTemplate`, each paired with the path
 * `destTemplate` rewrites it to. A track whose rewritten path is identical to
 * its current one is not a match — there is nothing to apply.
 *
 * Mirrors the desktop app's Smart Transform match list (SmartTransformPanel),
 * built on the same shared `buildTransformPath`.
 */
export function computeTransformMatches(
  tracks: readonly CatalogTrack[],
  originTemplate: string,
  destTemplate: string,
): TransformMatch[] {
  const origin = originTemplate.trim();
  const dest = destTemplate.trim();
  if (!origin || !dest) return [];
  const matches: TransformMatch[] = [];
  for (const track of tracks) {
    const newPath = buildTransformPath(origin, dest, track.path);
    if (newPath && newPath !== track.path) matches.push({ track, newPath });
  }
  return matches;
}

/** The subset of `matches` whose track id is in `selectedIds`, as the plain
 *  `{ id, newPath }` shape the rename and collision-check endpoints take. */
export function planSelectedRenames(
  matches: readonly TransformMatch[],
  selectedIds: ReadonlySet<number>,
): { id: number; newPath: string }[] {
  return matches
    .filter((m) => selectedIds.has(m.track.id))
    .map((m) => ({ id: m.track.id, newPath: m.newPath }));
}

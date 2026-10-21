import type { CatalogTrack, PlaylistSortMode } from "./types";

/** The Android app's `displayTitle`: the title, or the file name when unset. */
function displayTitle(t: CatalogTrack): string {
  const fileName = t.path.split("/").pop() ?? t.path;
  return (t.title ?? fileName.replace(/\.[^.]+$/, "")).toLowerCase();
}

/** The Android app's `displayArtist`: artist, then album artist, then a fallback. */
function displayArtist(t: CatalogTrack): string {
  return (t.artist ?? t.album_artist ?? "Unknown Artist").toLowerCase();
}

/**
 * Orders a playlist's tracks under the active sort — the web twin of the
 * Android app's `PlaylistAlbumsViewModel.applySort`. `"order"` is the saved
 * playlist order (what drag-reorder writes); the rest are view transforms.
 *
 * `addedKeys` maps trackId to the entry's added-order key (the entry row's id,
 * which grows with every insert). A smart playlist or a mix has no entries, so
 * there is no added order to sort by — callers pass an empty map and "added"
 * keeps the server's natural order.
 */
export function sortPlaylistTracks(
  tracks: CatalogTrack[],
  mode: PlaylistSortMode,
  ascending: boolean,
  addedKeys: ReadonlyMap<number, number> = new Map(),
): CatalogTrack[] {
  let result: CatalogTrack[];
  switch (mode) {
    case "added":
      result =
        addedKeys.size === 0
          ? tracks
          : [...tracks].sort((a, b) => (addedKeys.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (addedKeys.get(b.id) ?? Number.MAX_SAFE_INTEGER));
      break;
    case "title":
      result = [...tracks].sort((a, b) => displayTitle(a).localeCompare(displayTitle(b)));
      break;
    case "artist":
      result = [...tracks].sort(
        (a, b) =>
          displayArtist(a).localeCompare(displayArtist(b)) ||
          displayTitle(a).localeCompare(displayTitle(b)),
      );
      break;
    case "order":
      result = tracks;
      break;
  }
  return ascending ? result : result.slice().reverse();
}

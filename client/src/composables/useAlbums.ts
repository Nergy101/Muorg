import { computed, type ComputedRef } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import { albumIdentityKey } from "@shared/utils/albumIdentity";
import type { CatalogTrack } from "../types";
import type { AlbumGridItem } from "../components/library/AlbumGridView.vue";

/**
 * Album artist with fallback to the track artist — many files (singles,
 * MP3s tagged with TPE1 only) have no album-artist tag, and grouping or
 * displaying them as "Unknown Artist" is wrong when the artist tag exists.
 * Matches the web-client's `t.album_artist ?? t.artist ?? "Unknown Artist"`.
 */
export function albumArtistOf(t: CatalogTrack): string {
  return (t.album_artist ?? t.artist ?? "").trim() || "Unknown Artist";
}

/**
 * Albums derived from the catalog, shared by the Albums grid and the Home
 * "Recommended" shelf. Mirrors the grouping logic the library view uses.
 */
export function useAlbums(source?: ComputedRef<CatalogTrack[]>): ComputedRef<AlbumGridItem[]> {
  const store = useCatalogStore();
  const settings = useSettingsStore();
  const { filteredTracks } = storeToRefs(store);
  const { albumGridSortBy } = storeToRefs(settings);

  return computed<AlbumGridItem[]>(() => {
    const list = source?.value ?? filteredTracks.value;
    const grouped = new Map<string, { album: string; albumArtist: string; tracks: CatalogTrack[] }>();
    for (const track of list) {
      const key = albumIdentityKey(track.album, albumArtistOf(track));
      const existing = grouped.get(key);
      if (existing) {
        existing.tracks.push(track);
        continue;
      }
      grouped.set(key, {
        album: (track.album ?? "Unknown Album").trim() || "Unknown Album",
        albumArtist: albumArtistOf(track),
        tracks: [track],
      });
    }
    const items = [...grouped.entries()].map(([key, data]) => {
      const firstWithCover = data.tracks.find((t) => t.has_cover);
      const years = data.tracks.map((t) => t.year).filter((y): y is number => y != null);
      const uniqueArtists = [...new Set(data.tracks.map(albumArtistOf))];
      return {
        key,
        album: data.album,
        albumArtist: uniqueArtists.length > 1 ? "Various Artists" : uniqueArtists[0],
        year: years.length ? Math.min(...years) : null,
        trackCount: data.tracks.length,
        totalDurationSecs: data.tracks.reduce((a, t) => a + (t.duration_secs ?? 0), 0),
        coverPath: firstWithCover?.path ?? data.tracks[0].path,
        hasCover: Boolean(firstWithCover),
        trackIds: data.tracks.map((t) => t.id),
      };
    });
    const by = albumGridSortBy.value;
    items.sort((a, b) => {
      if (by === "artist") return a.albumArtist.localeCompare(b.albumArtist) || a.album.localeCompare(b.album);
      if (by === "year") return (a.year ?? 0) - (b.year ?? 0) || a.album.localeCompare(b.album);
      return a.album.localeCompare(b.album);
    });
    return items;
  });
}

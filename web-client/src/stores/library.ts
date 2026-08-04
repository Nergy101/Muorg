import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getTracks, getStats, getCoverBlob } from "../api/catalog";
import { useSettingsStore } from "./settings";
import type { CatalogTrack, LibraryStats, AlbumGridItem } from "../types";

const MAX_COVER_CONCURRENT = 8;

function normalize(s: string | null | undefined): string {
  return (s ?? "").toLowerCase();
}

/** `h:mm:ss` past an hour, otherwise `m:ss`. */
export function formatDuration(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  const s = Math.floor(secs % 60);
  if (h > 0) return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  return `${m}:${String(s).padStart(2, "0")}`;
}

/** The grid key that disambiguates same-titled albums by different artists. */
export function albumKeyFor(t: CatalogTrack): string {
  const album = t.album ?? "Unknown Album";
  const artist = t.album_artist ?? t.artist ?? "Unknown Artist";
  return `${album.toLowerCase()}|||${artist.toLowerCase()}`;
}

function groupAlbums(source: CatalogTrack[]): Map<string, AlbumGridItem> {
  const map = new Map<string, AlbumGridItem>();
  for (const t of source) {
    const key = albumKeyFor(t);
    let item = map.get(key);
    if (!item) {
      item = {
        key,
        album: t.album ?? "Unknown Album",
        albumArtist: t.album_artist ?? t.artist ?? "Unknown Artist",
        year: null,
        trackCount: 0,
        totalDurationSecs: 0,
        coverTrackId: t.has_cover ? t.id : null,
        hasCover: t.has_cover,
        trackIds: [],
      };
      map.set(key, item);
    }
    item.trackCount++;
    item.totalDurationSecs += t.duration_secs ?? 0;
    item.trackIds.push(t.id);
    if (t.year && (item.year === null || t.year < item.year)) item.year = t.year;
    if (t.has_cover && !item.hasCover) {
      item.hasCover = true;
      item.coverTrackId = t.id;
    }
  }
  return map;
}

/** Album order within a disc set: (disc, track). */
export function compareTrackOrder(a: CatalogTrack, b: CatalogTrack): number {
  const dc = (a.disc_number ?? 1) - (b.disc_number ?? 1);
  if (dc !== 0) return dc;
  return (a.track_number ?? 0) - (b.track_number ?? 0);
}

export const useLibraryStore = defineStore("library", () => {
  const settings = useSettingsStore();

  const tracks = ref<CatalogTrack[]>([]);
  const stats = ref<LibraryStats | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const searchQuery = ref("");
  /** Set from the `?artist=` query on /library. */
  const artistFilter = ref<string | null>(null);

  // Cover cache: trackId -> object URL
  const coverCache = ref<Map<number, string>>(new Map());
  const coverPending = ref<Set<number>>(new Set());
  let inFlight = 0;
  const coverQueue: number[] = [];

  const filteredTracks = computed(() => {
    let result = tracks.value;

    const artist = artistFilter.value?.trim().toLowerCase();
    if (artist) {
      result = result.filter((t) => normalize(t.artist ?? t.album_artist) === artist);
    }

    const q = searchQuery.value.trim().toLowerCase();
    if (q.length > 0) {
      result = result.filter(
        (t) =>
          normalize(t.title).includes(q) ||
          normalize(t.artist).includes(q) ||
          normalize(t.album).includes(q) ||
          normalize(t.album_artist).includes(q),
      );
    }

    return result;
  });

  const albumGridItems = computed((): AlbumGridItem[] => {
    const items = Array.from(groupAlbums(filteredTracks.value).values());

    switch (settings.sortMode) {
      case "artist":
        items.sort(
          (a, b) =>
            a.albumArtist.toLowerCase().localeCompare(b.albumArtist.toLowerCase()) ||
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()),
        );
        break;
      case "year":
        items.sort(
          (a, b) =>
            (a.year ?? 0) - (b.year ?? 0) ||
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()),
        );
        break;
      default:
        items.sort(
          (a, b) =>
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()) ||
            a.albumArtist.toLowerCase().localeCompare(b.albumArtist.toLowerCase()),
        );
    }

    if (!settings.sortAscending) items.reverse();
    return items;
  });

  /**
   * Unfiltered album index — album detail must resolve its key regardless of
   * the search box or artist chip that happened to be active on the way in.
   */
  const albumIndex = computed(() => groupAlbums(tracks.value));

  function albumByKey(key: string): AlbumGridItem | null {
    return albumIndex.value.get(key) ?? null;
  }

  function tracksForAlbum(key: string): CatalogTrack[] {
    return tracks.value.filter((t) => albumKeyFor(t) === key).sort(compareTrackOrder);
  }

  async function loadLibrary(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      const [t, s] = await Promise.all([getTracks(), getStats()]);
      tracks.value = t;
      stats.value = s;
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  function requestCover(trackId: number): void {
    if (coverCache.value.has(trackId) || coverPending.value.has(trackId)) return;
    coverPending.value.add(trackId);
    coverQueue.unshift(trackId);
    drainCoverQueue();
  }

  function drainCoverQueue(): void {
    while (inFlight < MAX_COVER_CONCURRENT && coverQueue.length > 0) {
      const id = coverQueue.shift()!;
      if (coverCache.value.has(id)) {
        coverPending.value.delete(id);
        continue;
      }
      inFlight++;
      getCoverBlob(id)
        .then((blob) => {
          if (blob) {
            coverCache.value.set(id, URL.createObjectURL(blob));
            coverCache.value = new Map(coverCache.value);
          }
        })
        .finally(() => {
          coverPending.value.delete(id);
          inFlight--;
          drainCoverQueue();
        });
    }
  }

  /**
   * Clears everything on logout. Setup stores get no working `$reset()`, so
   * every store exposes this explicitly.
   */
  function reset(): void {
    tracks.value = [];
    stats.value = null;
    loading.value = false;
    error.value = null;
    searchQuery.value = "";
    artistFilter.value = null;
    for (const url of coverCache.value.values()) URL.revokeObjectURL(url);
    coverCache.value = new Map();
    coverPending.value = new Set();
    coverQueue.length = 0;
  }

  return {
    tracks,
    stats,
    loading,
    error,
    searchQuery,
    artistFilter,
    coverCache,
    filteredTracks,
    albumGridItems,
    albumByKey,
    tracksForAlbum,
    loadLibrary,
    requestCover,
    reset,
  };
});

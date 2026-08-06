import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getTracks, getStats, getCoverBlob } from "../api/catalog";
import { useSettingsStore } from "./settings";
import type { CatalogTrack, LibraryStats, AlbumGridItem } from "../types";

const MAX_COVER_CONCURRENT = 8;
/** Page size for `/api/tracks`; matches the server's default limit. */
const PAGE_SIZE = 500;

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

/**
 * Stateless fallback album key for a single track — mirrors the server's own
 * album identity (`album ||| COALESCE(album_artist, '')`). The per-track
 * `artist` is deliberately NOT part of the key: on a compilation or a
 * collaboration album the tracks carry different artists but still belong to
 * one album, so falling back to `artist` splits it across the grid.
 */
export function albumKeyFor(t: CatalogTrack): string {
  const album = normalize(t.album || "Unknown Album");
  const artist = normalize(t.album_artist || "");
  return `${album}|||${artist}`;
}

/** First non-null album artist (original casing); album artists are uniform per group. */
function pickAlbumArtist(tracks: CatalogTrack[]): string {
  for (const t of tracks) {
    if (t.album_artist) return t.album_artist;
  }
  return "Unknown Artist";
}

/** Display artist for a group that has no album-artist tag at all. */
function displayArtistFor(tracks: CatalogTrack[]): string {
  const seen = new Set<string>();
  let first: string | null = null;
  for (const t of tracks) {
    const a = t.artist;
    if (!a) continue;
    const key = a.toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      if (first === null) first = a;
    }
  }
  if (seen.size === 0) return "Unknown Artist";
  if (seen.size === 1) return first!;
  return "Various Artists";
}

function makeAlbumItem(
  key: string,
  album: string,
  albumArtist: string,
  tracks: CatalogTrack[],
): AlbumGridItem {
  let year: number | null = null;
  let totalDurationSecs = 0;
  let coverTrackId: number | null = null;
  let hasCover = false;
  const trackIds: number[] = [];
  for (const t of tracks) {
    trackIds.push(t.id);
    totalDurationSecs += t.duration_secs ?? 0;
    if (t.year && (year === null || t.year < year)) year = t.year;
    if (t.has_cover && !hasCover) {
      hasCover = true;
      coverTrackId = t.id;
    }
  }
  return {
    key,
    album,
    albumArtist,
    year,
    trackCount: tracks.length,
    totalDurationSecs,
    coverTrackId,
    hasCover,
    trackIds,
  };
}

/**
 * Groups tracks into albums, name-first: every track with the same album title
 * starts in one bucket, so per-track artist differences can never split an
 * album. The album artist only splits a bucket when the same title genuinely
 * maps to several different albums (two releases sharing a name, e.g.
 * "Greatest Hits" by different artists) — identified by every album-artist
 * value also appearing as a track artist. Album-artist tags that match NO
 * track artist (e.g. featured artists leaked into the tag on a deluxe
 * edition, while every track's artist is the main act) are treated as one
 * album. Tracks without an album-artist tag ride along with their title
 * bucket — and, in a split bucket, join the sub-group whose album artist
 * matches their track artist, else the largest sub-group.
 */
export function groupAlbums(source: CatalogTrack[]): Map<string, AlbumGridItem> {
  // Pass 1: bucket by normalized album name.
  const byName = new Map<string, CatalogTrack[]>();
  for (const t of source) {
    const name = normalize(t.album || "Unknown Album");
    let bucket = byName.get(name);
    if (!bucket) byName.set(name, (bucket = []));
    bucket.push(t);
  }

  const map = new Map<string, AlbumGridItem>();
  const albumName = (t: CatalogTrack) => t.album ?? "Unknown Album";

  for (const [name, bucket] of byName) {
    const albumArtists = new Set<string>();
    const trackArtists = new Set<string>();
    for (const t of bucket) {
      const aa = normalize(t.album_artist);
      if (aa) albumArtists.add(aa);
      const ar = normalize(t.artist);
      if (ar) trackArtists.add(ar);
    }

    if (albumArtists.size === 0) {
      // No album-artist tag anywhere: the whole title is one album.
      const key = `${name}|||`;
      map.set(key, makeAlbumItem(key, albumName(bucket[0]), displayArtistFor(bucket), bucket));
    } else if (albumArtists.size === 1) {
      // One album artist owns this title; untagged tracks join it.
      const key = `${name}|||${[...albumArtists][0]}`;
      map.set(key, makeAlbumItem(key, albumName(bucket[0]), pickAlbumArtist(bucket), bucket));
    } else if ([...albumArtists].every((a) => trackArtists.has(a))) {
      // Same title, several album artists that all match track artists:
      // genuinely different albums. Split by album artist; untagged tracks join
      // the sub-group whose album artist matches their track artist, else the
      // largest sub-group.
      const subs = new Map<string, CatalogTrack[]>();
      for (const t of bucket) {
        const a = normalize(t.album_artist);
        if (!a) continue;
        let sub = subs.get(a);
        if (!sub) subs.set(a, (sub = []));
        sub.push(t);
      }
      let dominant = "";
      let dominantSize = -1;
      for (const [a, sub] of subs) {
        if (sub.length > dominantSize) {
          dominant = a;
          dominantSize = sub.length;
        }
      }
      for (const t of bucket) {
        if (normalize(t.album_artist)) continue;
        (subs.get(normalize(t.artist)) ?? subs.get(dominant)!).push(t);
      }
      for (const [a, sub] of subs) {
        const key = `${name}|||${a}`;
        map.set(key, makeAlbumItem(key, albumName(bucket[0]), pickAlbumArtist(sub), sub));
      }
    } else {
      // Several album-artist tags that match no track artist — featured
      // artists leaked into the album-artist tag while every track's artist is
      // the main act. Still one album.
      const key = `${name}|||`;
      map.set(key, makeAlbumItem(key, albumName(bucket[0]), displayArtistFor(bucket), bucket));
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
  const loadingMore = ref(false);
  const totalTracks = ref(0);
  const error = ref<string | null>(null);

  const searchQuery = ref("");
  /** Set from the `?artist=` query on /library. */
  const artistFilter = ref<string | null>(null);
  /** Normalized genre value selected in the Library toolbar dropdown. */
  const genreFilter = ref<string | null>(null);

  // Cover cache: trackId -> object URL
  const coverCache = ref<Map<number, string>>(new Map());
  const coverPending = ref<Set<number>>(new Set());
  let inFlight = 0;
  const coverQueue: number[] = [];

  /** Distinct genres across the catalog, original casing, sorted A–Z. */
  const genres = computed(() => {
    const seen = new Map<string, string>();
    for (const t of tracks.value) {
      const g = t.genre;
      if (!g) continue;
      const key = g.toLowerCase();
      if (!seen.has(key)) seen.set(key, g);
    }
    return [...seen.entries()]
      .sort((a, b) => a[1].localeCompare(b[1]))
      .map(([value, label]) => ({ value, label }));
  });

  const filteredTracks = computed(() => {
    let result = tracks.value;

    const artist = artistFilter.value?.trim().toLowerCase();
    if (artist) {
      result = result.filter((t) => normalize(t.artist ?? t.album_artist) === artist);
    }

    if (genreFilter.value) {
      const genre = genreFilter.value;
      result = result.filter((t) => normalize(t.genre) === genre);
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
    // Group the filtered subset by each track's FULL-catalog key, so search
    // and artist filters can never re-split or rename an album (keys stay
    // stable for navigation), while counts reflect the filtered tracks.
    const items = new Map<string, AlbumGridItem>();
    for (const t of filteredTracks.value) {
      const key = keyForTrack(t);
      let item = items.get(key);
      if (!item) {
        const seed = albumIndex.value.get(key);
        item = seed
          ? { ...seed, trackCount: 0, totalDurationSecs: 0, coverTrackId: null, hasCover: false, trackIds: [] }
          : {
              key,
              album: t.album ?? "Unknown Album",
              albumArtist: t.album_artist ?? "Unknown Artist",
              year: null,
              trackCount: 0,
              totalDurationSecs: 0,
              coverTrackId: null,
              hasCover: false,
              trackIds: [],
            };
        items.set(key, item);
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

    const result = Array.from(items.values());

    switch (settings.sortMode) {
      case "artist":
        result.sort(
          (a, b) =>
            a.albumArtist.toLowerCase().localeCompare(b.albumArtist.toLowerCase()) ||
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()),
        );
        break;
      case "year":
        result.sort(
          (a, b) =>
            (a.year ?? 0) - (b.year ?? 0) ||
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()),
        );
        break;
      default:
        result.sort(
          (a, b) =>
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()) ||
            a.albumArtist.toLowerCase().localeCompare(b.albumArtist.toLowerCase()),
        );
    }

    if (!settings.sortAscending) result.reverse();
    return result;
  });

  /** id → track, used to resolve album members back to track objects. */
  const trackById = computed(() => new Map(tracks.value.map((t) => [t.id, t])));

  /**
   * Unfiltered album index — album detail must resolve its key regardless of
   * the search box or artist chip that happened to be active on the way in.
   */
  const albumIndex = computed(() => groupAlbums(tracks.value));

  /** Full-catalog album key for every track id — stable across filters. */
  const trackKeyById = computed(() => {
    const m = new Map<number, string>();
    for (const [key, item] of albumIndex.value) {
      for (const id of item.trackIds) m.set(id, key);
    }
    return m;
  });

  function albumByKey(key: string): AlbumGridItem | null {
    return albumIndex.value.get(key) ?? null;
  }

  /**
   * Resolve the album key a track belongs to via the full-catalog grouping.
   * Falls back to the stateless key for tracks outside the catalog.
   */
  function keyForTrack(t: CatalogTrack): string {
    return trackKeyById.value.get(t.id) ?? albumKeyFor(t);
  }

  function tracksForAlbum(key: string): CatalogTrack[] {
    const item = albumIndex.value.get(key);
    if (!item) return [];
    return item.trackIds
      .map((id) => trackById.value.get(id))
      .filter((t): t is CatalogTrack => t != null)
      .sort(compareTrackOrder);
  }

  /**
   * `/api/tracks` is paginated. The first page renders immediately and the
   * rest stream in behind it, so search and the album grid stay usable on a
   * large library instead of blocking on the whole catalog.
   */
  async function loadLibrary(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      const first = await getTracks(0, PAGE_SIZE);
      tracks.value = first.tracks;
      totalTracks.value = first.total;
      if (first.total > first.tracks.length) {
        void loadRemainingPages(first.tracks.length);
      }
      stats.value = await getStats();
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  async function loadRemainingPages(start: number): Promise<void> {
    if (loadingMore.value) return;
    loadingMore.value = true;
    try {
      const seen = new Set(tracks.value.map((t) => t.id));
      for (let offset = start; offset < totalTracks.value; offset += PAGE_SIZE) {
        const page = await getTracks(offset, PAGE_SIZE);
        totalTracks.value = page.total;
        const fresh = page.tracks.filter((t) => !seen.has(t.id));
        for (const t of fresh) seen.add(t.id);
        if (fresh.length > 0) tracks.value = [...tracks.value, ...fresh];
      }
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loadingMore.value = false;
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
    loadingMore.value = false;
    totalTracks.value = 0;
    error.value = null;
    searchQuery.value = "";
    artistFilter.value = null;
    genreFilter.value = null;
    for (const url of coverCache.value.values()) URL.revokeObjectURL(url);
    coverCache.value = new Map();
    coverPending.value = new Set();
    coverQueue.length = 0;
  }

  return {
    tracks,
    stats,
    loading,
    loadingMore,
    totalTracks,
    error,
    searchQuery,
    artistFilter,
    genreFilter,
    genres,
    coverCache,
    filteredTracks,
    albumGridItems,
    albumByKey,
    keyForTrack,
    tracksForAlbum,
    loadLibrary,
    requestCover,
    reset,
  };
});

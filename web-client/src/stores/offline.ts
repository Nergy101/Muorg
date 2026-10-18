import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { getRecentPlayHistory, issueStreamToken } from "../api/catalog";
import { getServerUrl, streamUrl } from "../api/client";
import { showToast } from "../composables/useToast";
import { useLibraryStore } from "./library";
import { usePlaylistStore } from "./playlists";
import { loadPref, savePref } from "./prefs";
import type { CatalogTrack } from "../types";

/**
 * Offline playback for the web client (Q11).
 *
 * Tracks are stored WHOLE in Cache Storage — the Cache API cannot reliably
 * store partial (206) range responses, so each download is a single complete
 * file. The service worker's runtime cache for /stream/ uses the same cache
 * name (see vite.config.ts) and matches with ignoreSearch, so a cached track
 * is served by the SW whether the page asks for it online (tokened URL) or
 * offline (?offline=1 marker). Playback responses are never cached by the SW
 * itself — only this store writes entries, via cache.put.
 */

/** Cache Storage cache holding whole track files. Must match the runtime
 *  cacheName the service worker is generated with in vite.config.ts. */
export const OFFLINE_CACHE_NAME = "muorg-offline-tracks";

/** Recent plays cached automatically, newest first (Q11). */
const RECENT_CACHE_LIMIT = 100;
/** Hard cap on the total bytes held in the offline cache (Q11). Libraries can
 *  be tens of gigabytes, so never download everything. */
const MAX_CACHE_BYTES = 2 * 1024 * 1024 * 1024; // 2 GiB

const RECENT_KEY = "muorg-offline-recent";
const PLAYLISTS_KEY = "muorg-offline-playlists";
/** playlistId -> track ids, persisted so eviction can protect them. */
const PLAYLIST_TRACKS_KEY = "muorg-offline-playlist-tracks";

/** Cache key for a track: the bare stream URL, no query. The SW matches with
 *  ignoreSearch, so both the tokened playback URL and the ?offline=1 marker
 *  resolve to this entry. */
function cacheKey(trackId: number): string {
  return `${getServerUrl()}/stream/${trackId}`;
}

/** Playback URL for a cached track when the server is unreachable. The
 *  ?offline=1 marker tells the SW's cacheWillUpdate plugin this response may
 *  be cached; playback responses without it never are. */
export function offlineStreamUrl(trackId: number): string {
  return `${getServerUrl()}/stream/${trackId}?offline=1`;
}

/** Track id encoded in a cache entry's URL, or null. */
function trackIdFromUrl(url: string): number | null {
  const m = /\/stream\/(\d+)$/.exec(new URL(url).pathname);
  return m ? Number(m[1]) : null;
}

export const useOfflineStore = defineStore("offline", () => {
  /** Track ids currently held in Cache Storage. */
  const cachedIds = ref<Set<number>>(new Set());
  /** Sum of content-length headers across cached entries. */
  const cachedBytes = ref(0);
  /** True once init() has scanned the cache. */
  const ready = ref(false);
  /** True while a download batch is running. */
  const downloading = ref(false);
  /** Progress of the current batch: {done, total}. */
  const downloadProgress = ref<{ done: number; total: number }>({ done: 0, total: 0 });
  /** Playlist id currently being downloaded, or null. */
  const activePlaylistId = ref<number | null>(null);

  /** Track ids cached automatically from recent plays (Q11). */
  const recentIds = ref<number[]>(loadPref<number[]>(RECENT_KEY, []));
  /** Playlist ids the user explicitly downloaded. */
  const downloadedPlaylists = ref<number[]>(loadPref<number[]>(PLAYLISTS_KEY, []));
  /** playlistId -> track ids, persisted so eviction can protect them. */
  const playlistTrackIds = ref<Record<number, number[]>>(
    loadPref<Record<number, number[]>>(PLAYLIST_TRACKS_KEY, {}),
  );

  let cache: Cache | null = null;

  const cachedTrackCount = computed(() => cachedIds.value.size);

  function isCached(trackId: number): boolean {
    return cachedIds.value.has(trackId);
  }

  function isPlaylistDownloaded(playlistId: number): boolean {
    return downloadedPlaylists.value.includes(playlistId);
  }

  /** Ids that must not be evicted: the union of every downloaded playlist. */
  function protectedIds(): Set<number> {
    const s = new Set<number>();
    for (const ids of Object.values(playlistTrackIds.value)) {
      for (const id of ids) s.add(id);
    }
    return s;
  }

  /** Opens the cache and rebuilds the in-memory index from what is stored. */
  async function init(): Promise<void> {
    if (!("caches" in window)) return;
    try {
      cache = await caches.open(OFFLINE_CACHE_NAME);
      const keys = await cache.keys();
      const ids = new Set<number>();
      let bytes = 0;
      for (const req of keys) {
        const id = trackIdFromUrl(req.url);
        if (id == null) continue;
        ids.add(id);
        const res = await cache.match(req);
        const len = Number(res?.headers.get("content-length") ?? 0);
        if (Number.isFinite(len)) bytes += len;
      }
      cachedIds.value = ids;
      cachedBytes.value = bytes;
    } catch {
      cache = null;
    } finally {
      ready.value = true;
    }
  }

  /** True when `bytes` more would fit under both the quota and the cap. */
  async function hasRoomFor(bytes: number): Promise<boolean> {
    if (cachedBytes.value + bytes > MAX_CACHE_BYTES) return false;
    try {
      const est = await navigator.storage.estimate();
      const quota = est.quota ?? Number.POSITIVE_INFINITY;
      const usage = est.usage ?? 0;
      return usage + bytes <= Math.min(quota, MAX_CACHE_BYTES);
    } catch {
      // No estimate API: fall back to the local cap only.
      return cachedBytes.value + bytes <= MAX_CACHE_BYTES;
    }
  }

  /** Downloads one track whole and stores it as a complete file (Q11: the
   *  Cache API cannot reliably store 206 partial responses). */
  async function downloadTrack(track: CatalogTrack): Promise<boolean> {
    if (!cache) return false;
    if (isCached(track.id)) return true;
    let token: string;
    try {
      token = await issueStreamToken(track.id);
    } catch (e) {
      showToast(`Offline download failed: ${(e as Error).message}`);
      return false;
    }
    let res: Response;
    try {
      res = await fetch(streamUrl(track.id, token));
    } catch (e) {
      showToast(`Offline download failed: ${(e as Error).message}`);
      return false;
    }
    if (!res.ok) {
      showToast(`Offline download failed (${res.status})`);
      return false;
    }
    const size = Number(res.headers.get("content-length") ?? 0);
    if (size > 0 && !(await hasRoomFor(size))) {
      showToast("Not enough storage for offline downloads");
      return false;
    }
    try {
      await cache.put(cacheKey(track.id), res);
    } catch (e) {
      showToast(`Offline download failed: ${(e as Error).message}`);
      return false;
    }
    cachedIds.value = new Set(cachedIds.value).add(track.id);
    if (Number.isFinite(size) && size > 0) cachedBytes.value += size;
    return true;
  }

  /** Downloads tracks sequentially, stopping at the first failure or when the
   *  storage quota is reached. */
  async function downloadTracks(tracks: CatalogTrack[]): Promise<void> {
    if (downloading.value || tracks.length === 0) return;
    downloading.value = true;
    downloadProgress.value = { done: 0, total: tracks.length };
    try {
      for (const t of tracks) {
        const ok = await downloadTrack(t);
        downloadProgress.value = {
          done: downloadProgress.value.done + 1,
          total: tracks.length,
        };
        if (!ok) break;
      }
    } finally {
      downloading.value = false;
    }
  }

  /** Downloads every track of a playlist for offline use (Q11). */
  async function downloadPlaylist(playlistId: number): Promise<void> {
    if (downloading.value) {
      showToast("A download is already running");
      return;
    }
    const playlistStore = usePlaylistStore();
    const playlist = playlistStore.playlists.find((p) => p.id === playlistId);
    if (!playlist) return;
    const order = await playlistStore.loadTrackOrderForPlaylist(playlistId, true);
    const lib = useLibraryStore();
    const tracks = order
      .map((id) => lib.trackById.get(id))
      .filter((t): t is CatalogTrack => t != null);
    if (tracks.length === 0) {
      showToast("This playlist has no tracks to download");
      return;
    }
    activePlaylistId.value = playlistId;
    try {
      await downloadTracks(tracks);
      // Mark downloaded only when every track made it into the cache.
      const allCached = tracks.every((t) => isCached(t.id));
      if (allCached) {
        downloadedPlaylists.value = [...new Set([...downloadedPlaylists.value, playlistId])];
        playlistTrackIds.value = { ...playlistTrackIds.value, [playlistId]: order };
        savePref(PLAYLISTS_KEY, downloadedPlaylists.value);
        savePref(PLAYLIST_TRACKS_KEY, playlistTrackIds.value);
        showToast(`Downloaded ${tracks.length} track(s) for offline use`);
      } else {
        showToast("Download stopped — not enough storage");
      }
    } finally {
      activePlaylistId.value = null;
    }
  }

  /** Caches the most recent plays automatically, capped at RECENT_CACHE_LIMIT
   *  (Q11). Evicts auto-cached tracks that fell out of the recent list, but
   *  never a track a downloaded playlist needs. */
  async function refreshRecentCache(): Promise<void> {
    if (!ready.value) return;
    let recent: CatalogTrack[];
    try {
      recent = await getRecentPlayHistory(RECENT_CACHE_LIMIT);
    } catch {
      return; // offline — keep whatever is already cached
    }
    const target = recent.map((t) => t.id);
    const keep = new Set([...target, ...protectedIds()]);
    // Evict auto-cached tracks that are no longer recent and not protected.
    for (const id of [...cachedIds.value]) {
      if (keep.has(id)) continue;
      if (recentIds.value.includes(id)) await removeTrack(id);
    }
    recentIds.value = target;
    savePref(RECENT_KEY, target);
    // Download the missing recent tracks (downloadTracks stops on quota).
    const missing = recent.filter((t) => !isCached(t.id));
    if (missing.length > 0) await downloadTracks(missing);
  }

  /** Removes one track from the offline cache. */
  async function removeTrack(trackId: number): Promise<void> {
    if (!cache) return;
    const key = cacheKey(trackId);
    const res = await cache.match(key);
    const size = Number(res?.headers.get("content-length") ?? 0);
    await cache.delete(key);
    cachedIds.value = new Set(cachedIds.value);
    cachedIds.value.delete(trackId);
    if (Number.isFinite(size) && size > 0) {
      cachedBytes.value = Math.max(0, cachedBytes.value - size);
    }
  }

  /** Deletes the whole offline cache and its bookkeeping. */
  async function clearAll(): Promise<void> {
    if ("caches" in window) {
      await caches.delete(OFFLINE_CACHE_NAME).catch(() => undefined);
    }
    cache = null;
    cachedIds.value = new Set();
    cachedBytes.value = 0;
    recentIds.value = [];
    downloadedPlaylists.value = [];
    playlistTrackIds.value = {};
    try {
      localStorage.removeItem(RECENT_KEY);
      localStorage.removeItem(PLAYLISTS_KEY);
      localStorage.removeItem(PLAYLIST_TRACKS_KEY);
    } catch {
      /* ignore */
    }
  }

  return {
    cachedIds,
    cachedBytes,
    ready,
    downloading,
    downloadProgress,
    activePlaylistId,
    recentIds,
    downloadedPlaylists,
    playlistTrackIds,
    cachedTrackCount,
    isCached,
    isPlaylistDownloaded,
    init,
    downloadTrack,
    downloadTracks,
    downloadPlaylist,
    refreshRecentCache,
    removeTrack,
    clearAll,
  };
});

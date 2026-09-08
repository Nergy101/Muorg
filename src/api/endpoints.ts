/**
 * Typed calls against the Muorg server API.
 *
 * Every signature is pinned to the generated `schema.d.ts`, so a route or field
 * that changes in Rust surfaces here as a TypeScript error rather than as a
 * runtime surprise in one client and not the others.
 *
 * Pagination in particular lives here and nowhere else — see
 * {@link streamTracks}.
 */

import type { Transport } from "./transport";
import { jsonBody } from "./transport";
import type {
  BatchMetadataItem,
  CastDevice,
  CastStatusResponse,
  CatalogTrack,
  FetchedImage,
  LibraryStats,
  MatchCandidate,
  MetadataUpdate,
  Playlist,
  PlaylistTrackEntry,
  RescanResult,
  TrackBackupRecord,
  TrackLyrics,
  TrackMetadata,
  TracksPage,
} from "./types";
import { TRACKS_PAGE_SIZE } from "./types";

export function createApi(t: Transport) {
  const api = {
    // ------------------------------------------------------------ catalog ---

    getRoots: () => t.fetchJson<string[]>("/api/roots"),

    /**
     * One page of the catalog. `total` comes from `X-Total-Count`, so the caller
     * knows how much is left without a second request.
     */
    async getTracksPage(
      offset = 0,
      limit = TRACKS_PAGE_SIZE,
    ): Promise<TracksPage> {
      const { data, headers } = await t.fetchJsonWithHeaders<CatalogTrack[]>(
        `/api/tracks?offset=${offset}&limit=${limit}`,
      );
      const tracks = data ?? [];
      const header = headers.get("X-Total-Count");
      const total = header != null ? Number(header) : tracks.length;
      return { tracks, total: Number.isFinite(total) ? total : tracks.length };
    },

    /**
     * The whole catalog, page by page, yielded as each page lands.
     *
     * This is the one place pagination is implemented. Every client previously
     * rolled its own loop and every client got it wrong at least once — the
     * desktop app, the web app and Android each shipped a release that showed
     * only the first 500 tracks.
     *
     * Consume it with `for await`, and render what you have as you go rather
     * than waiting for the last page:
     *
     * ```ts
     * for await (const page of api.streamTracks()) {
     *   tracks.push(...page.tracks);
     * }
     * ```
     */
    async *streamTracks(
      pageSize = TRACKS_PAGE_SIZE,
    ): AsyncGenerator<TracksPage, void, void> {
      const first = await api.getTracksPage(0, pageSize);
      yield first;

      let loaded = first.tracks.length;
      // `total` can move under us if a scan finishes mid-load; re-read it from
      // each page rather than trusting the first one.
      let total = first.total;
      while (loaded < total && first.tracks.length > 0) {
        const page = await api.getTracksPage(loaded, pageSize);
        if (page.tracks.length === 0) break;
        total = page.total;
        loaded += page.tracks.length;
        yield page;
      }
    },

    /** The whole catalog in one array. Prefer {@link streamTracks} for UI. */
    async fetchAllTracks(pageSize = TRACKS_PAGE_SIZE): Promise<CatalogTrack[]> {
      const out: CatalogTrack[] = [];
      for await (const page of api.streamTracks(pageSize)) out.push(...page.tracks);
      return out;
    },

    searchTracks: (query: string) =>
      t.fetchJson<CatalogTrack[]>(`/api/search?q=${encodeURIComponent(query)}`),

    getStats: () => t.fetchJson<LibraryStats>("/api/stats"),

    getRecentlyAdded: (limit = 50) =>
      t.fetchJson<CatalogTrack[]>(`/api/tracks/recently-added?limit=${limit}`),

    getRecentlyPlayed: (limit = 50) =>
      t.fetchJson<CatalogTrack[]>(`/api/play-history/recent?limit=${limit}`),

    getMostPlayed: (limit = 50, days = 30) =>
      t.fetchJson<CatalogTrack[]>(
        `/api/play-history/top?limit=${limit}&days=${days}`,
      ),

    // ------------------------------------------------------------- tracks ---

    getCoverBlob: (trackId: number, size?: number) =>
      t.fetchBlob(
        `/api/tracks/${trackId}/cover${size != null ? `?size=${size}` : ""}`,
      ),

    getMetadata: (trackId: number) =>
      t.fetchJson<TrackMetadata>(`/api/tracks/${trackId}/metadata`),

    /** Resolves to `null` when the track has no embedded lyrics (404). */
    getLyrics: (trackId: number) =>
      t
        .fetchJson<TrackLyrics>(`/api/tracks/${trackId}/lyrics`)
        .catch(() => null),

    patchMetadata: (
      trackId: number,
      update: MetadataUpdate,
      backupBeforeWrite = false,
    ) =>
      t.fetchJson<{ ok: boolean }>(`/api/tracks/${trackId}/metadata`, {
        method: "PATCH",
        ...jsonBody({ ...update, backup_before_write: backupBeforeWrite }),
      }),

    patchMetadataBatch: (items: BatchMetadataItem[]) =>
      t.fetchJson<{ ok: boolean; updated: number }>("/api/tracks/metadata/batch", {
        method: "POST",
        ...jsonBody(items),
      }),

    setRating: (trackId: number, rating: number | null) =>
      t.fetchJson<{ ok: boolean }>(`/api/tracks/${trackId}/rating`, {
        method: "POST",
        ...jsonBody({ rating }),
      }),

    recordPlay: (trackId: number) =>
      t.fetchJson<{ ok: boolean }>(`/api/tracks/${trackId}/play`, {
        method: "POST",
      }),

    getLatestBackup: (trackId: number) =>
      t.fetchJson<TrackBackupRecord | null>(`/api/tracks/${trackId}/backup`),

    restoreFromBackup: (trackId: number) =>
      t.fetchJson<{ ok: boolean }>(`/api/tracks/${trackId}/restore`, {
        method: "POST",
      }),

    renameTrackFile: (trackId: number, newPath: string) =>
      t.fetchJson<{ ok: boolean }>(`/api/tracks/${trackId}/rename`, {
        method: "POST",
        ...jsonBody({ new_path: newPath }),
      }),

    /**
     * MusicBrainz candidates. With no `query`, the server builds one from the
     * file's own tags.
     */
    autoTagSuggestions: (
      trackId: number,
      query?: {
        artist?: string | null;
        title?: string | null;
        album?: string | null;
        duration_secs?: number | null;
      },
    ) =>
      t
        .fetchJson<{ candidates: MatchCandidate[] }>(
          `/api/tracks/${trackId}/auto-tag-suggestions`,
          { method: "POST", ...(query ? jsonBody(query) : {}) },
        )
        .then((r) => r.candidates),

    getStreamToken: (trackId: number) =>
      t
        .fetchJson<{ token: string }>(`/api/tracks/${trackId}/stream-token`)
        .then((r) => r.token),

    streamUrl: t.streamUrl,

    // ---------------------------------------------------------- playlists ---

    getPlaylists: () => t.fetchJson<Playlist[]>("/api/playlists"),

    createPlaylist: (name: string) =>
      t.fetchJson<Playlist>("/api/playlists", {
        method: "POST",
        ...jsonBody({ name }),
      }),

    updatePlaylist: (id: number, patch: { name?: string; icon?: string | null }) =>
      t.fetchJson<{ ok: boolean }>(`/api/playlists/${id}`, {
        method: "PATCH",
        ...jsonBody(patch),
      }),

    deletePlaylist: (id: number) =>
      t.fetchJson<{ ok: boolean }>(`/api/playlists/${id}`, { method: "DELETE" }),

    getPlaylistTracks: (id: number) =>
      t.fetchJson<number[]>(`/api/playlists/${id}/tracks`),

    getPlaylistEntries: (id: number) =>
      t.fetchJson<PlaylistTrackEntry[]>(`/api/playlists/${id}/entries`),

    addTracksToPlaylist: (id: number, trackIds: number[]) =>
      t.fetchJson<{ ok: boolean }>(`/api/playlists/${id}/tracks`, {
        method: "POST",
        ...jsonBody({ track_ids: trackIds }),
      }),

    removeTracksFromPlaylist: (id: number, trackIds: number[]) =>
      t.fetchJson<{ ok: boolean }>(`/api/playlists/${id}/tracks`, {
        method: "DELETE",
        ...jsonBody({ track_ids: trackIds }),
      }),

    removePlaylistEntry: (playlistId: number, entryId: number) =>
      t.fetchJson<{ ok: boolean }>(
        `/api/playlists/${playlistId}/entries/${entryId}`,
        { method: "DELETE" },
      ),

    reorderPlaylists: (ids: number[]) =>
      t.fetchJson<{ ok: boolean }>("/api/playlists/order", {
        method: "PUT",
        ...jsonBody({ ids }),
      }),

    reorderPlaylistTracks: (id: number, ids: number[]) =>
      t.fetchJson<{ ok: boolean }>(`/api/playlists/${id}/tracks/order`, {
        method: "PUT",
        ...jsonBody({ ids }),
      }),

    createSmartPlaylist: (name: string, rulesJson: string) =>
      t.fetchJson<Playlist>("/api/playlists/smart", {
        method: "POST",
        ...jsonBody({ name, rules_json: rulesJson }),
      }),

    updateSmartPlaylistRules: (id: number, rulesJson: string) =>
      t.fetchJson<{ ok: boolean }>(`/api/playlists/smart/${id}/rules`, {
        method: "PATCH",
        ...jsonBody({ rules_json: rulesJson }),
      }),

    getSmartPlaylistTracks: (id: number) =>
      t.fetchJson<number[]>(`/api/playlists/smart/${id}/tracks`),

    // --------------------------------------------------------------- cast ---

    castDevices: () => t.fetchJson<CastDevice[]>("/api/cast/devices"),
    castStartDiscovery: () =>
      t.fetchJson<void>("/api/cast/discovery/start", { method: "POST" }),
    castStopDiscovery: () =>
      t.fetchJson<void>("/api/cast/discovery/stop", { method: "POST" }),
    castStatus: () => t.fetchJson<CastStatusResponse>("/api/cast/status"),
    castPlay: (deviceAddress: string, devicePort: number, trackId: number) =>
      t.fetchJson<void>("/api/cast/play", {
        method: "POST",
        ...jsonBody({
          device_address: deviceAddress,
          device_port: devicePort,
          track_id: trackId,
        }),
      }),
    castPause: () => t.fetchJson<void>("/api/cast/pause", { method: "POST" }),
    castResume: () => t.fetchJson<void>("/api/cast/resume", { method: "POST" }),
    castStop: () => t.fetchJson<void>("/api/cast/stop", { method: "POST" }),
    castSeek: (positionSecs: number, wasPlaying: boolean) =>
      t.fetchJson<void>("/api/cast/seek", {
        method: "POST",
        ...jsonBody({ position_secs: positionSecs, was_playing: wasPlaying }),
      }),
    castSetVolume: (level: number) =>
      t.fetchJson<void>("/api/cast/volume", {
        method: "POST",
        ...jsonBody({ level }),
      }),

    // -------------------------------------------------------------- admin ---

    rescan: (rootPath?: string) =>
      t
        .fetchJson<RescanResult>("/api/admin/rescan", {
          method: "POST",
          ...jsonBody(rootPath ? { root_path: rootPath } : {}),
        })
        .then((r) => r.tracks_added),

    removeFolder: (rootPath: string) =>
      t.fetchJson<{ ok: boolean }>("/api/admin/remove-folder", {
        method: "POST",
        ...jsonBody({ root_path: rootPath }),
      }),

    clearCache: () =>
      t.fetchJson<{ ok: boolean }>("/api/admin/clear-cache", { method: "POST" }),

    getBackupDirectory: () =>
      t
        .fetchJson<{ path: string }>("/api/admin/backup-directory")
        .then((r) => r.path),

    fetchImage: (url: string) =>
      t.fetchJson<FetchedImage>("/api/fetch-image", {
        method: "POST",
        ...jsonBody({ url }),
      }),

    /** Unauthenticated liveness probe; throws when the server is unreachable. */
    health: () => t.fetchJson<void>("/api/health"),
  };

  return api;
}

export type MuorgApi = ReturnType<typeof createApi>;

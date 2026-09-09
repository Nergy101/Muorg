/**
 * Named calls against the Muorg server API.
 *
 * Every one of these is a one-liner over the spec-typed `openapi-fetch` client
 * in `transport.ts`. The URL literal must be a real path in `server/openapi.json`,
 * the method must be one that path serves, and the body and response types come
 * from the operation — all three are compile errors otherwise, so this file
 * cannot describe an endpoint the server does not have.
 *
 * The wrappers exist so the ~40 call sites across the two apps keep their
 * current shape (`api.getStats()` rather than an inline
 * `client.GET("/api/stats")`); they add names, not behaviour. The one place
 * with real logic is pagination — see {@link streamTracks}.
 */

import type { Transport } from "./transport";
import type {
  BatchMetadataItem,
  CatalogTrack,
  MatchCandidate,
  MetadataUpdate,
  TracksPage,
} from "./types";
import { TRACKS_PAGE_SIZE } from "./types";

export function createApi(t: Transport) {
  const { client, unwrap } = t;

  const api = {
    // ------------------------------------------------------------ catalog ---

    async getRoots() {
      return unwrap(await client.GET("/api/roots"), "/api/roots");
    },

    /**
     * One page of the catalog. `total` comes from `X-Total-Count`, so the caller
     * knows how much is left without a second request.
     */
    async getTracksPage(
      offset = 0,
      limit = TRACKS_PAGE_SIZE,
    ): Promise<TracksPage> {
      const result = await client.GET("/api/tracks", {
        params: { query: { offset, limit } },
      });
      const tracks = unwrap(result, "/api/tracks") ?? [];
      const header = result.response.headers.get("X-Total-Count");
      const total = header != null ? Number(header) : tracks.length;
      return {
        tracks: tracks as CatalogTrack[],
        total: Number.isFinite(total) ? total : tracks.length,
      };
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

    async searchTracks(query: string) {
      return unwrap(
        await client.GET("/api/search", { params: { query: { q: query } } }),
        "/api/search",
      );
    },

    async getStats() {
      return unwrap(await client.GET("/api/stats"), "/api/stats");
    },

    async getRecentlyAdded(limit = 50) {
      return unwrap(
        await client.GET("/api/tracks/recently-added", {
          params: { query: { limit } },
        }),
        "/api/tracks/recently-added",
      );
    },

    async getRecentlyPlayed(limit = 50) {
      return unwrap(
        await client.GET("/api/play-history/recent", {
          params: { query: { limit } },
        }),
        "/api/play-history/recent",
      );
    },

    async getMostPlayed(limit = 50, days = 30) {
      return unwrap(
        await client.GET("/api/play-history/top", {
          params: { query: { limit, days } },
        }),
        "/api/play-history/top",
      );
    },

    // ------------------------------------------------------------- tracks ---

    /** Bytes, not JSON — goes straight through the transport. */
    getCoverBlob(trackId: number, size?: number) {
      return t.fetchBlob(
        `/api/tracks/${trackId}/cover${size != null ? `?size=${size}` : ""}`,
      );
    },

    async getMetadata(trackId: number) {
      return unwrap(
        await client.GET("/api/tracks/{id}/metadata", {
          params: { path: { id: trackId } },
        }),
        "/api/tracks/{id}/metadata",
      );
    },

    /** Resolves to `null` when the track has no embedded lyrics (404). */
    async getLyrics(trackId: number) {
      const result = await client.GET("/api/tracks/{id}/lyrics", {
        params: { path: { id: trackId } },
      });
      return result.error !== undefined ? null : (result.data ?? null);
    },

    async patchMetadata(
      trackId: number,
      update: MetadataUpdate,
      backupBeforeWrite = false,
    ) {
      return unwrap(
        await client.PATCH("/api/tracks/{id}/metadata", {
          params: { path: { id: trackId } },
          body: { ...update, backup_before_write: backupBeforeWrite },
        }),
        "/api/tracks/{id}/metadata",
      );
    },

    async patchMetadataBatch(items: BatchMetadataItem[]) {
      return unwrap(
        await client.POST("/api/tracks/metadata/batch", { body: items }),
        "/api/tracks/metadata/batch",
      );
    },

    async setRating(trackId: number, rating: number | null) {
      return unwrap(
        await client.POST("/api/tracks/{id}/rating", {
          params: { path: { id: trackId } },
          body: { rating },
        }),
        "/api/tracks/{id}/rating",
      );
    },

    async recordPlay(trackId: number) {
      return unwrap(
        await client.POST("/api/tracks/{id}/play", {
          params: { path: { id: trackId } },
        }),
        "/api/tracks/{id}/play",
      );
    },

    async getLatestBackup(trackId: number) {
      return unwrap(
        await client.GET("/api/tracks/{id}/backup", {
          params: { path: { id: trackId } },
        }),
        "/api/tracks/{id}/backup",
      );
    },

    async restoreFromBackup(trackId: number) {
      return unwrap(
        await client.POST("/api/tracks/{id}/restore", {
          params: { path: { id: trackId } },
        }),
        "/api/tracks/{id}/restore",
      );
    },

    async renameTrackFile(trackId: number, newPath: string) {
      return unwrap(
        await client.POST("/api/tracks/{id}/rename", {
          params: { path: { id: trackId } },
          body: { new_path: newPath },
        }),
        "/api/tracks/{id}/rename",
      );
    },

    /**
     * MusicBrainz candidates. With no `query`, the server builds one from the
     * file's own tags.
     */
    async autoTagSuggestions(
      trackId: number,
      query?: {
        artist?: string | null;
        title?: string | null;
        album?: string | null;
        duration_secs?: number | null;
      },
    ): Promise<MatchCandidate[]> {
      const result = await client.POST("/api/tracks/{id}/auto-tag-suggestions", {
        params: { path: { id: trackId } },
        body: query ?? {},
      });
      // `unwrap`'s normalisation is shallow, so it reaches the response object
      // but not the candidates inside it. Same reasoning applies to them: serde
      // always emits the keys, `null` when empty.
      return unwrap(result, "/api/tracks/{id}/auto-tag-suggestions")
        .candidates as MatchCandidate[];
    },

    async getStreamToken(trackId: number) {
      const result = await client.GET("/api/tracks/{id}/stream-token", {
        params: { path: { id: trackId } },
      });
      return unwrap(result, "/api/tracks/{id}/stream-token").token;
    },

    streamUrl: t.streamUrl,

    // ---------------------------------------------------------- playlists ---

    async getPlaylists() {
      return unwrap(await client.GET("/api/playlists"), "/api/playlists");
    },

    async createPlaylist(name: string) {
      return unwrap(
        await client.POST("/api/playlists", { body: { name } }),
        "/api/playlists",
      );
    },

    async updatePlaylist(
      id: number,
      patch: { name?: string; icon?: string | null },
    ) {
      return unwrap(
        await client.PATCH("/api/playlists/{id}", {
          params: { path: { id } },
          body: patch,
        }),
        "/api/playlists/{id}",
      );
    },

    async deletePlaylist(id: number) {
      return unwrap(
        await client.DELETE("/api/playlists/{id}", { params: { path: { id } } }),
        "/api/playlists/{id}",
      );
    },

    async getPlaylistTracks(id: number) {
      return unwrap(
        await client.GET("/api/playlists/{id}/tracks", {
          params: { path: { id } },
        }),
        "/api/playlists/{id}/tracks",
      );
    },

    async getPlaylistEntries(id: number) {
      return unwrap(
        await client.GET("/api/playlists/{id}/entries", {
          params: { path: { id } },
        }),
        "/api/playlists/{id}/entries",
      );
    },

    async addTracksToPlaylist(id: number, trackIds: number[]) {
      return unwrap(
        await client.POST("/api/playlists/{id}/tracks", {
          params: { path: { id } },
          body: { track_ids: trackIds },
        }),
        "/api/playlists/{id}/tracks",
      );
    },

    async removeTracksFromPlaylist(id: number, trackIds: number[]) {
      return unwrap(
        await client.DELETE("/api/playlists/{id}/tracks", {
          params: { path: { id } },
          body: { track_ids: trackIds },
        }),
        "/api/playlists/{id}/tracks",
      );
    },

    async removePlaylistEntry(playlistId: number, entryId: number) {
      return unwrap(
        await client.DELETE("/api/playlists/{id}/entries/{entry_id}", {
          params: { path: { id: playlistId, entry_id: entryId } },
        }),
        "/api/playlists/{id}/entries/{entry_id}",
      );
    },

    async reorderPlaylists(ids: number[]) {
      return unwrap(
        await client.PUT("/api/playlists/order", { body: { ids } }),
        "/api/playlists/order",
      );
    },

    async reorderPlaylistTracks(id: number, ids: number[]) {
      return unwrap(
        await client.PUT("/api/playlists/{id}/tracks/order", {
          params: { path: { id } },
          body: { ids },
        }),
        "/api/playlists/{id}/tracks/order",
      );
    },

    async createSmartPlaylist(name: string, rulesJson: string) {
      return unwrap(
        await client.POST("/api/playlists/smart", {
          body: { name, rules_json: rulesJson },
        }),
        "/api/playlists/smart",
      );
    },

    async updateSmartPlaylistRules(id: number, rulesJson: string) {
      return unwrap(
        await client.PATCH("/api/playlists/smart/{id}/rules", {
          params: { path: { id } },
          body: { rules_json: rulesJson },
        }),
        "/api/playlists/smart/{id}/rules",
      );
    },

    async getSmartPlaylistTracks(id: number) {
      return unwrap(
        await client.GET("/api/playlists/smart/{id}/tracks", {
          params: { path: { id } },
        }),
        "/api/playlists/smart/{id}/tracks",
      );
    },

    // --------------------------------------------------------------- cast ---

    async castDevices() {
      return unwrap(await client.GET("/api/cast/devices"), "/api/cast/devices");
    },

    async castStartDiscovery() {
      unwrap(
        await client.POST("/api/cast/discovery/start"),
        "/api/cast/discovery/start",
      );
    },

    async castStopDiscovery() {
      unwrap(
        await client.POST("/api/cast/discovery/stop"),
        "/api/cast/discovery/stop",
      );
    },

    async castStatus() {
      return unwrap(await client.GET("/api/cast/status"), "/api/cast/status");
    },

    async castPlay(deviceAddress: string, devicePort: number, trackId: number) {
      unwrap(
        await client.POST("/api/cast/play", {
          body: {
            device_address: deviceAddress,
            device_port: devicePort,
            track_id: trackId,
          },
        }),
        "/api/cast/play",
      );
    },

    async castPause() {
      unwrap(await client.POST("/api/cast/pause"), "/api/cast/pause");
    },

    async castResume() {
      unwrap(await client.POST("/api/cast/resume"), "/api/cast/resume");
    },

    async castStop() {
      unwrap(await client.POST("/api/cast/stop"), "/api/cast/stop");
    },

    async castSeek(positionSecs: number, wasPlaying: boolean) {
      unwrap(
        await client.POST("/api/cast/seek", {
          body: { position_secs: positionSecs, was_playing: wasPlaying },
        }),
        "/api/cast/seek",
      );
    },

    async castSetVolume(level: number) {
      unwrap(
        await client.POST("/api/cast/volume", { body: { level } }),
        "/api/cast/volume",
      );
    },

    // -------------------------------------------------------------- admin ---

    async rescan(rootPath?: string) {
      const result = await client.POST("/api/admin/rescan", {
        body: rootPath ? { root_path: rootPath } : {},
      });
      return unwrap(result, "/api/admin/rescan").tracks_added;
    },

    async removeFolder(rootPath: string) {
      return unwrap(
        await client.POST("/api/admin/remove-folder", {
          body: { root_path: rootPath },
        }),
        "/api/admin/remove-folder",
      );
    },

    async clearCache() {
      return unwrap(
        await client.POST("/api/admin/clear-cache"),
        "/api/admin/clear-cache",
      );
    },

    async getBackupDirectory() {
      const result = await client.GET("/api/admin/backup-directory");
      return unwrap(result, "/api/admin/backup-directory").path;
    },

    async fetchImage(url: string) {
      return unwrap(
        await client.POST("/api/fetch-image", { body: { url } }),
        "/api/fetch-image",
      );
    },

    /** Unauthenticated liveness probe; throws when the server is unreachable. */
    async health() {
      // Answers with the plain string "Healthy", not JSON.
      unwrap(await client.GET("/api/health", { parseAs: "text" }), "/api/health");
    },
  };

  return api;
}

export type MuorgApi = ReturnType<typeof createApi>;

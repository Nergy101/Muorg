// GENERATED FILE — do not edit.
//
// Source: server/openapi.json, itself derived from the muorg-server route
// handlers. Regenerate with ./scripts/generate-api-clients.sh after any API
// change; CI fails if this file is stale.
//
// One method per operation in the spec. Repositories map these wire types onto
// the app's own models — see WireMapping.kt in the data layer.

package nl.muorg.android.data.api.schema

import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The complete Muorg server API, generated from its OpenAPI document.
 */
interface MuorgApi {

    /**
     * Where pre-write backups are kept on the server.
     *
     * `GET /api/admin/backup-directory`
     */
    @GET("api/admin/backup-directory")
    suspend fun getBackupDirectory(): Response<BackupDirectoryResponse>

    /**
     * Purge soft-deleted rows immediately.
     *
     * `POST /api/admin/clear-cache`
     */
    @POST("api/admin/clear-cache")
    suspend fun clearCache(): Response<OkResponse>

    /**
     * Deeper health check than `/api/health`: also proves the catalog is readable.
     *
     * `GET /api/admin/health`
     */
    @GET("api/admin/health")
    suspend fun adminHealth(): Response<AdminHealthResponse>

    /**
     * Prometheus exposition format.
     *
     * `GET /api/admin/metrics`
     */
    @GET("api/admin/metrics")
    suspend fun adminMetrics(): Response<ResponseBody>

    /**
     * Drop a root and soft-delete the tracks under it.
     *
     * `POST /api/admin/remove-folder`
     */
    @POST("api/admin/remove-folder")
    suspend fun removeFolder(
        @Body body: RemoveFolderBody,
    ): Response<OkResponse>

    /**
     * Rescan one root, or every root when no body is sent.
     *
     * `POST /api/admin/rescan`
     */
    @POST("api/admin/rescan")
    suspend fun rescan(
        @Body body: RescanBody?,
    ): Response<RescanResult>

    /**
     * Chromecast devices seen by the current mDNS sweep.
     *
     * `GET /api/cast/devices`
     */
    @GET("api/cast/devices")
    suspend fun castDevices(): Response<List<CastDevice>>

    /**
     * Begin mDNS discovery.
     *
     * `POST /api/cast/discovery/start`
     */
    @POST("api/cast/discovery/start")
    suspend fun castStartDiscovery(): Response<Unit>

    /**
     * Stop mDNS discovery.
     *
     * `POST /api/cast/discovery/stop`
     */
    @POST("api/cast/discovery/stop")
    suspend fun castStopDiscovery(): Response<Unit>

    /**
     * Pause the cast session.
     *
     * `POST /api/cast/pause`
     */
    @POST("api/cast/pause")
    suspend fun castPause(): Response<Unit>

    /**
     * Start casting a track to a device.
     *
     * `POST /api/cast/play`
     */
    @POST("api/cast/play")
    suspend fun castPlay(
        @Body body: PlayBody,
    ): Response<Unit>

    /**
     * Resume the cast session.
     *
     * `POST /api/cast/resume`
     */
    @POST("api/cast/resume")
    suspend fun castResume(): Response<Unit>

    /**
     * Seek within the casting track.
     *
     * `POST /api/cast/seek`
     */
    @POST("api/cast/seek")
    suspend fun castSeek(
        @Body body: SeekBody,
    ): Response<Unit>

    /**
     * Current cast session state and device volume.
     *
     * `GET /api/cast/status`
     */
    @GET("api/cast/status")
    suspend fun castStatus(): Response<CastStatusResponse>

    /**
     * Tear down the cast session.
     *
     * `POST /api/cast/stop`
     */
    @POST("api/cast/stop")
    suspend fun castStop(): Response<Unit>

    /**
     * Set device volume, 0.0–1.0.
     *
     * `POST /api/cast/volume`
     */
    @POST("api/cast/volume")
    suspend fun castSetVolume(
        @Body body: VolumeBody,
    ): Response<Unit>

    /**
     * Proxy-fetch a remote image and return it base64-encoded, so the clients can
     * pull cover art from the web without tripping CORS.
     *
     * `POST /api/fetch-image`
     */
    @POST("api/fetch-image")
    suspend fun fetchImage(
        @Body body: FetchImageBody,
    ): Response<FetchedImage>

    /**
     * Unauthenticated liveness probe.
     *
     * `GET /api/health`
     */
    @GET("api/health")
    suspend fun health(): Response<ResponseBody>

    /** `GET /api/play-history/recent` */
    @GET("api/play-history/recent")
    suspend fun getRecentPlayHistory(
        @Query("limit") limit: Long? = null,
        @Query("days") days: Long? = null,
    ): Response<List<CatalogTrack>>

    /** `GET /api/play-history/top` */
    @GET("api/play-history/top")
    suspend fun getTopPlayHistory(
        @Query("limit") limit: Long? = null,
        @Query("days") days: Long? = null,
    ): Response<List<CatalogTrack>>

    /**
     * All playlists, in user-defined order.
     *
     * `GET /api/playlists`
     */
    @GET("api/playlists")
    suspend fun listPlaylists(): Response<List<Playlist>>

    /**
     * Create an empty playlist.
     *
     * `POST /api/playlists`
     */
    @POST("api/playlists")
    suspend fun createPlaylist(
        @Body body: CreateBody,
    ): Response<Playlist>

    /**
     * Reorder the playlists themselves.
     *
     * `PUT /api/playlists/order`
     */
    @PUT("api/playlists/order")
    suspend fun reorderPlaylists(
        @Body body: ReorderBody,
    ): Response<OkResponse>

    /**
     * Create a rule-driven smart playlist.
     *
     * `POST /api/playlists/smart`
     */
    @POST("api/playlists/smart")
    suspend fun createSmartPlaylist(
        @Body body: SmartCreateBody,
    ): Response<Playlist>

    /**
     * Replace a smart playlist's rule set.
     *
     * `PATCH /api/playlists/smart/{id}/rules`
     */
    @PATCH("api/playlists/smart/{id}/rules")
    suspend fun updateSmartPlaylistRules(
        @Path("id") id: Long,
        @Body body: SmartRulesBody,
    ): Response<OkResponse>

    /**
     * Evaluate the rules and return the matching track ids.
     *
     * `GET /api/playlists/smart/{id}/tracks`
     */
    @GET("api/playlists/smart/{id}/tracks")
    suspend fun getSmartPlaylistTracks(
        @Path("id") id: Long,
    ): Response<List<Long>>

    /**
     * Rename a playlist and/or set its icon.
     *
     * `PATCH /api/playlists/{id}`
     */
    @PATCH("api/playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") id: Long,
        @Body body: UpdateBody,
    ): Response<OkResponse>

    /**
     * Delete a playlist and its entries.
     *
     * `DELETE /api/playlists/{id}`
     */
    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(
        @Path("id") id: Long,
    ): Response<OkResponse>

    /**
     * Playlist entries, which carry their own id so the same track can appear twice.
     *
     * `GET /api/playlists/{id}/entries`
     */
    @GET("api/playlists/{id}/entries")
    suspend fun getPlaylistEntries(
        @Path("id") id: Long,
    ): Response<List<PlaylistTrackEntry>>

    /**
     * Remove one entry, leaving other copies of the same track in place.
     *
     * `DELETE /api/playlists/{id}/entries/{entry_id}`
     */
    @DELETE("api/playlists/{id}/entries/{entry_id}")
    suspend fun removePlaylistEntry(
        @Path("id") id: Long,
        @Path("entry_id") entryId: Long,
    ): Response<OkResponse>

    /**
     * Track ids in playlist order.
     *
     * `GET /api/playlists/{id}/tracks`
     */
    @GET("api/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id") id: Long,
    ): Response<List<Long>>

    /**
     * Append tracks to a playlist.
     *
     * `POST /api/playlists/{id}/tracks`
     */
    @POST("api/playlists/{id}/tracks")
    suspend fun addPlaylistTracks(
        @Path("id") id: Long,
        @Body body: TrackIdsBody,
    ): Response<OkResponse>

    /**
     * Remove every entry for the given tracks.
     *
     * `DELETE /api/playlists/{id}/tracks`
     */
    @HTTP(method = "DELETE", path = "api/playlists/{id}/tracks", hasBody = true)
    suspend fun removePlaylistTracks(
        @Path("id") id: Long,
        @Body body: TrackIdsBody,
    ): Response<OkResponse>

    /**
     * Reorder entries within a playlist.
     *
     * `PUT /api/playlists/{id}/tracks/order`
     */
    @PUT("api/playlists/{id}/tracks/order")
    suspend fun reorderPlaylistTracks(
        @Path("id") id: Long,
        @Body body: ReorderBody,
    ): Response<OkResponse>

    /** `GET /api/roots` */
    @GET("api/roots")
    suspend fun getRoots(): Response<List<String>>

    /** `GET /api/search` */
    @GET("api/search")
    suspend fun searchTracks(
        @Query("q") q: String,
    ): Response<List<CatalogTrack>>

    /** `GET /api/stats` */
    @GET("api/stats")
    suspend fun getStats(): Response<LibraryStats>

    /** `GET /api/tracks` */
    @GET("api/tracks")
    suspend fun getTracks(
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Long? = null,
    ): Response<List<CatalogTrack>>

    /**
     * Total track count without loading rows.
     *
     * `GET /api/tracks/count`
     */
    @GET("api/tracks/count")
    suspend fun getTracksCount(): Response<CountResponse>

    /**
     * Apply a metadata patch to many tracks in one call.
     *
     * `POST /api/tracks/metadata/batch`
     */
    @POST("api/tracks/metadata/batch")
    suspend fun batchPatchMetadata(
        @Body body: List<BatchMetadataItem>,
    ): Response<BatchUpdateResponse>

    /** `GET /api/tracks/recently-added` */
    @GET("api/tracks/recently-added")
    suspend fun getRecentlyAdded(
        @Query("limit") limit: Long? = null,
        @Query("days") days: Long? = null,
    ): Response<List<CatalogTrack>>

    /**
     * MusicBrainz candidates for this track. With no body, the query is built from the file\u2019s own tags.
     *
     * `POST /api/tracks/{id}/auto-tag-suggestions`
     */
    @POST("api/tracks/{id}/auto-tag-suggestions")
    suspend fun autoTagSuggestions(
        @Path("id") id: Long,
        @Body body: SearchQuery,
    ): Response<AutoTagSuggestionsResponse>

    /**
     * The most recent pre-write backup of this file, if one exists.
     *
     * `GET /api/tracks/{id}/backup`
     */
    @GET("api/tracks/{id}/backup")
    suspend fun getBackup(
        @Path("id") id: Long,
    ): Response<TrackBackupRecord?>

    /**
     * Embedded album art, optionally downscaled.
     *
     * `GET /api/tracks/{id}/cover`
     */
    @GET("api/tracks/{id}/cover")
    suspend fun getCover(
        @Path("id") id: Long,
        @Query("size") size: Int? = null,
    ): Response<ResponseBody>

    /** `GET /api/tracks/{id}/lyrics` */
    @GET("api/tracks/{id}/lyrics")
    suspend fun getLyrics(
        @Path("id") id: Long,
    ): Response<TrackLyrics>

    /**
     * Tags read straight off the file, including album art and ReplayGain.
     *
     * `GET /api/tracks/{id}/metadata`
     */
    @GET("api/tracks/{id}/metadata")
    suspend fun getMetadata(
        @Path("id") id: Long,
    ): Response<TrackMetadata>

    /**
     * Write tags back to the file. Absent fields are left alone, `null` clears the tag.
     *
     * `PATCH /api/tracks/{id}/metadata`
     */
    @PATCH("api/tracks/{id}/metadata")
    suspend fun patchMetadata(
        @Path("id") id: Long,
        @Body body: PatchMetadataBody,
    ): Response<OkResponse>

    /**
     * Record a play. Bumps `play_count`/`last_played_at` and appends to play history.
     *
     * `POST /api/tracks/{id}/play`
     */
    @POST("api/tracks/{id}/play")
    suspend fun recordPlay(
        @Path("id") id: Long,
    ): Response<OkResponse>

    /**
     * Set or clear the 1–5 star rating.
     *
     * `POST /api/tracks/{id}/rating`
     */
    @POST("api/tracks/{id}/rating")
    suspend fun setRating(
        @Path("id") id: Long,
        @Body body: RatingBody,
    ): Response<OkResponse>

    /**
     * Move the file on disk and repoint the catalog row.
     *
     * `POST /api/tracks/{id}/rename`
     */
    @POST("api/tracks/{id}/rename")
    suspend fun renameFile(
        @Path("id") id: Long,
        @Body body: RenameBody,
    ): Response<OkResponse>

    /**
     * Restore the file from its most recent backup.
     *
     * `POST /api/tracks/{id}/restore`
     */
    @POST("api/tracks/{id}/restore")
    suspend fun restoreBackup(
        @Path("id") id: Long,
    ): Response<OkResponse>

    /**
     * Mint a short-lived (8h) token for the public `/stream/{id}` URL, so an
     * `<audio src>` can play without carrying the API key.
     *
     * `GET /api/tracks/{id}/stream-token`
     */
    @GET("api/tracks/{id}/stream-token")
    suspend fun issueToken(
        @Path("id") id: Long,
    ): Response<TokenResponse>

    /**
     * Audio bytes, range-capable. Public: authorised by the `token` query
     * parameter rather than the Bearer header, so browsers and cast receivers can
     * fetch it directly.
     *
     * `GET /stream/{id}`
     */
    @GET("stream/{id}")
    suspend fun streamAudio(
        @Path("id") id: Long,
        @Query("token") token: String? = null,
        @Query("start") start: Float? = null,
    ): Response<ResponseBody>
}

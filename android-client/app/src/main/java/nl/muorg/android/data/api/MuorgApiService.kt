package nl.muorg.android.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MuorgApiService {

    @GET("api/health")
    suspend fun health(): Response<Unit>

    /**
     * One page of the catalog. The server CAPS this route at 500 rows by
     * default (and 5000 hard), and reports the true size in `X-Total-Count`,
     * so callers must page — a bare call silently returns a truncated
     * library while `/api/stats` keeps reporting the full counts.
     */
    @GET("api/tracks")
    suspend fun getTracks(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
    ): Response<List<CatalogTrack>>

    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<CatalogTrack>

    /** Most recently played tracks, newest first. */
    @GET("api/play-history/recent")
    suspend fun getRecentPlayHistory(@Query("limit") limit: Int = 20): List<CatalogTrack>

    /** Most played tracks within the last [days] days, highest count first. */
    @GET("api/play-history/top")
    suspend fun getTopPlayHistory(
        @Query("limit") limit: Int = 20,
        @Query("days") days: Int = 30,
    ): List<CatalogTrack>

    @GET("api/stats")
    suspend fun getStats(): Stats

    @POST("api/tracks/{id}/play")
    suspend fun recordPlay(@Path("id") trackId: Int): Response<Unit>

    @GET("api/tracks/{id}/stream-token")
    suspend fun getStreamToken(@Path("id") trackId: Int): StreamTokenResponse

    @GET("api/playlists")
    suspend fun getPlaylists(): List<Playlist>

    @POST("api/playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): Playlist

    @PATCH("api/playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") playlistId: Int,
        @Body request: UpdatePlaylistRequest,
    ): Playlist

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") playlistId: Int): Response<Unit>

    @GET("api/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(@Path("id") playlistId: Int): List<Int>

    /**
     * Smart playlists must go through this endpoint: the plain
     * `/tracks` route reads the join table and does not evaluate rules,
     * so it comes back empty for every smart playlist.
     */
    @POST("api/playlists/smart")
    suspend fun createSmartPlaylist(@Body request: CreateSmartPlaylistRequest): Playlist

    @GET("api/playlists/smart/{id}/tracks")
    suspend fun getSmartPlaylistTracks(@Path("id") playlistId: Int): List<Int>

    @POST("api/playlists/{id}/tracks")
    suspend fun addTracksToPlaylist(
        @Path("id") playlistId: Int,
        @Body request: PlaylistTracksRequest,
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "api/playlists/{id}/tracks", hasBody = true)
    suspend fun removeTracksFromPlaylist(
        @Path("id") playlistId: Int,
        @Body request: PlaylistTracksRequest,
    ): Response<Unit>

    @PUT("api/playlists/{id}/tracks/order")
    suspend fun reorderPlaylistTracks(
        @Path("id") playlistId: Int,
        @Body request: ReorderPlaylistTracksRequest,
    ): Response<Unit>

    @PATCH("api/tracks/{id}/metadata")
    suspend fun patchTrackMetadata(
        @Path("id") trackId: Int,
        @Body request: MetadataUpdateRequest,
    ): Response<Unit>
}

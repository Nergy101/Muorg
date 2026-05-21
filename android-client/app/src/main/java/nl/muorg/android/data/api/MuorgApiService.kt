package nl.muorg.android.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MuorgApiService {

    @GET("api/health")
    suspend fun health(): Response<Unit>

    @GET("api/tracks")
    suspend fun getTracks(): List<CatalogTrack>

    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<CatalogTrack>

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
}

package nl.muorg.android.data.repository

import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.api.bodyOrThrow
import nl.muorg.android.data.api.orThrow
import nl.muorg.android.data.api.schema.CreateBody
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.api.schema.ReorderBody
import nl.muorg.android.data.api.schema.SmartCreateBody
import nl.muorg.android.data.api.schema.TrackIdsBody
import nl.muorg.android.data.api.schema.UpdateBody
import nl.muorg.android.data.api.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val api: MuorgApi,
) {
    suspend fun getPlaylists(): Result<List<Playlist>> = runCatching {
        api.listPlaylists().bodyOrThrow("GET /api/playlists").toDomain()
    }

    suspend fun createPlaylist(name: String, icon: String): Result<Playlist> = runCatching {
        val created = api.createPlaylist(CreateBody(name = name))
            .bodyOrThrow("POST /api/playlists").toDomain()
        if (icon.isNotBlank()) {
            // The create route takes a name only; the icon is a follow-up patch.
            api.updatePlaylist(created.id.toLong(), UpdateBody(icon = icon))
                .orThrow("PATCH /api/playlists/${created.id}")
            created.copy(icon = icon)
        } else {
            created
        }
    }

    suspend fun updatePlaylist(id: Int, name: String?, icon: String?): Result<Unit> = runCatching {
        api.updatePlaylist(id.toLong(), UpdateBody(name = name, icon = icon))
            .orThrow("PATCH /api/playlists/$id")
    }

    suspend fun deletePlaylist(id: Int): Result<Unit> = runCatching {
        api.deletePlaylist(id.toLong()).orThrow("DELETE /api/playlists/$id")
    }

    /** Resolves a playlist's tracks, taking the smart route when it has rules. */
    suspend fun getTracksFor(playlist: Playlist): Result<List<Int>> =
        if (playlist.smartRules != null) getSmartTracks(playlist.id) else getPlaylistTracks(playlist.id)

    suspend fun createSmartPlaylist(name: String, rulesJson: String): Result<Playlist> = runCatching {
        api.createSmartPlaylist(SmartCreateBody(name = name, rulesJson = rulesJson))
            .bodyOrThrow("POST /api/playlists/smart").toDomain()
    }

    suspend fun getSmartTracks(id: Int): Result<List<Int>> = runCatching {
        api.getSmartPlaylistTracks(id.toLong())
            .bodyOrThrow("GET /api/playlists/smart/$id/tracks").map { it.toInt() }
    }

    suspend fun getPlaylistTracks(id: Int): Result<List<Int>> = runCatching {
        api.getPlaylistTracks(id.toLong())
            .bodyOrThrow("GET /api/playlists/$id/tracks").map { it.toInt() }
    }

    suspend fun addTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.addPlaylistTracks(playlistId.toLong(), TrackIdsBody(trackIds.map { it.toLong() }))
            .orThrow("POST /api/playlists/$playlistId/tracks")
    }

    suspend fun removeTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.removePlaylistTracks(playlistId.toLong(), TrackIdsBody(trackIds.map { it.toLong() }))
            .orThrow("DELETE /api/playlists/$playlistId/tracks")
    }

    suspend fun reorderTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.reorderPlaylistTracks(playlistId.toLong(), ReorderBody(trackIds.map { it.toLong() }))
            .orThrow("PUT /api/playlists/$playlistId/tracks/order")
    }
}

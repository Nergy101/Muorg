package nl.muorg.android.data.repository

import nl.muorg.android.data.api.CreatePlaylistRequest
import nl.muorg.android.data.api.MuorgApiService
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.api.PlaylistTracksRequest
import nl.muorg.android.data.api.ReorderPlaylistTracksRequest
import nl.muorg.android.data.api.UpdatePlaylistRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val api: MuorgApiService,
) {
    suspend fun getPlaylists(): Result<List<Playlist>> = runCatching {
        api.getPlaylists()
    }

    suspend fun createPlaylist(name: String, icon: String): Result<Playlist> = runCatching {
        val created = api.createPlaylist(CreatePlaylistRequest(name = name))
        if (icon.isNotBlank()) {
            api.updatePlaylist(created.id, UpdatePlaylistRequest(icon = icon))
        } else {
            created
        }
    }

    suspend fun updatePlaylist(id: Int, name: String?, icon: String?): Result<Playlist> = runCatching {
        api.updatePlaylist(id, UpdatePlaylistRequest(name = name, icon = icon))
    }

    suspend fun deletePlaylist(id: Int): Result<Unit> = runCatching {
        api.deletePlaylist(id)
        Unit
    }

    suspend fun getPlaylistTracks(id: Int): Result<List<Int>> = runCatching {
        api.getPlaylistTracks(id)
    }

    suspend fun addTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.addTracksToPlaylist(playlistId, PlaylistTracksRequest(trackIds))
        Unit
    }

    suspend fun removeTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.removeTracksFromPlaylist(playlistId, PlaylistTracksRequest(trackIds))
        Unit
    }

    suspend fun reorderTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.reorderPlaylistTracks(playlistId, ReorderPlaylistTracksRequest(trackIds))
        Unit
    }
}

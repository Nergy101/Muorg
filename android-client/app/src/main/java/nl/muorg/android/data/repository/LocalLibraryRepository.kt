package nl.muorg.android.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.api.Stats
import nl.muorg.android.data.db.LocalPlaylist
import nl.muorg.android.data.db.LocalPlaylistDao
import nl.muorg.android.data.db.LocalPlaylistEntry
import nl.muorg.android.data.db.LocalTrackDao
import nl.muorg.android.data.db.toCatalogTrack
import nl.muorg.android.data.local.LocalLibraryScanner
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: LocalTrackDao,
    private val playlistDao: LocalPlaylistDao,
    private val scanner: LocalLibraryScanner,
) {
    suspend fun getAllTracks(): List<CatalogTrack> = trackDao.getAllTracks().map { it.toCatalogTrack() }

    suspend fun search(query: String): List<CatalogTrack> = trackDao.search(query).map { it.toCatalogTrack() }

    suspend fun getStats() = Stats(
        trackCount = trackDao.count(),
        albumCount = trackDao.albumCount(),
        artistCount = trackDao.artistCount(),
        totalDurationSecs = trackDao.totalDuration()?.toLong() ?: 0L,
    )

    suspend fun scanAndSave(folderUris: Set<String>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val tracks = scanner.scan(folderUris, onProgress)
        trackDao.clearAll()
        trackDao.insertAll(tracks)
        return tracks.size
    }

    suspend fun getPlaylists(): List<Playlist> = playlistDao.getAll().map {
        Playlist(id = it.id, name = it.name, icon = it.icon, trackCount = 0, smartRules = null)
    }

    suspend fun createPlaylist(name: String): LocalPlaylist {
        val id = playlistDao.insert(LocalPlaylist(name = name))
        return LocalPlaylist(id = id.toInt(), name = name)
    }

    suspend fun deletePlaylist(id: Int) = playlistDao.delete(id)

    suspend fun getPlaylistTrackIds(playlistId: Int): List<Int> =
        playlistDao.getEntries(playlistId).map { it.trackId }

    suspend fun addTracksToPlaylist(playlistId: Int, localTrackIds: List<Int>) {
        val existingCount = playlistDao.getEntries(playlistId).size
        localTrackIds.forEachIndexed { i, trackId ->
            playlistDao.insertEntry(
                LocalPlaylistEntry(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = existingCount + i,
                )
            )
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, localTrackId: Int) =
        playlistDao.removeEntry(playlistId, localTrackId)

    suspend fun buildAlbumGroups(): List<AlbumGroup> {
        val localTracks = trackDao.getAllTracks()
        val artDir = File(context.cacheDir, "album_art")
        return localTracks
            .groupBy { it.album ?: "Unknown Album" }
            .map { (albumName, tracks) ->
                val rep = tracks.minByOrNull { (it.discNumber ?: 0) * 10000 + (it.trackNumber ?: 9999) }
                    ?: tracks.first()
                val artFile = File(artDir, "${albumName.hashCode()}.jpg")
                AlbumGroup(
                    albumName = albumName,
                    artist = rep.albumArtist ?: rep.artist ?: "Unknown Artist",
                    year = rep.year,
                    trackCount = tracks.size,
                    coverTrackId = -rep.id,
                    coverArtUri = if (artFile.exists()) artFile.absolutePath else null,
                )
            }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.albumName.lowercase() }))
    }

    fun getAlbumArtUri(albumName: String): String? {
        val artFile = File(context.cacheDir, "album_art/${albumName.hashCode()}.jpg")
        return if (artFile.exists()) artFile.absolutePath else null
    }
}

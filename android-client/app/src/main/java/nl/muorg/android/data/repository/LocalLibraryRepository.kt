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

data class PlaylistTrackEntry(
    val filePath: String,
    val storedTitle: String?,
    val storedArtist: String?,
    val track: CatalogTrack?,
)

data class AddConflictCheck(
    val newTracks: List<CatalogTrack>,
    val alreadyPresentCount: Int,
)

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

    suspend fun getPlaylists(): List<Playlist> = playlistDao.getAll().map { pl ->
        Playlist(
            id = pl.id,
            name = pl.name,
            icon = pl.icon,
            trackCount = playlistDao.getTrackCount(pl.id),
            smartRules = null,
        )
    }

    suspend fun createPlaylist(name: String, icon: String? = null): LocalPlaylist {
        val newPlaylist = LocalPlaylist(name = name, icon = icon)
        val id = playlistDao.insert(newPlaylist)
        return newPlaylist.copy(id = id.toInt())
    }

    suspend fun updatePlaylist(id: Int, name: String, icon: String?) {
        playlistDao.update(LocalPlaylist(id = id, name = name, icon = icon))
    }

    suspend fun deletePlaylist(id: Int) = playlistDao.delete(id)

    suspend fun checkAddConflict(playlistId: Int, tracks: List<CatalogTrack>): AddConflictCheck {
        val filePaths = tracks.map { it.path }
        val existing = playlistDao.getExistingFilePaths(playlistId, filePaths).toSet()
        val newTracks = tracks.filter { it.path !in existing }
        return AddConflictCheck(newTracks = newTracks, alreadyPresentCount = existing.size)
    }

    suspend fun addTracksToPlaylist(playlistId: Int, tracks: List<CatalogTrack>) {
        val currentCount = playlistDao.getTrackCount(playlistId)
        tracks.forEachIndexed { i, track ->
            playlistDao.insertEntry(
                LocalPlaylistEntry(
                    playlistId = playlistId,
                    filePath = track.path,
                    trackTitle = track.title,
                    trackArtist = track.artist ?: track.albumArtist,
                    position = currentCount + i,
                )
            )
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, filePath: String) =
        playlistDao.removeEntry(playlistId, filePath)

    suspend fun getPlaylistContents(playlistId: Int): List<PlaylistTrackEntry> {
        val entries = playlistDao.getEntries(playlistId)
        val allTracks = trackDao.getAllTracks().associateBy { it.path }
        return entries.map { entry ->
            PlaylistTrackEntry(
                filePath = entry.filePath,
                storedTitle = entry.trackTitle,
                storedArtist = entry.trackArtist,
                track = allTracks[entry.filePath]?.toCatalogTrack(),
            )
        }
    }

    suspend fun getPlaylistMembershipByPath(): Map<String, Set<Int>> =
        playlistDao.getAllEntries()
            .groupBy { it.filePath }
            .mapValues { (_, entries) -> entries.map { it.playlistId }.toSet() }

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

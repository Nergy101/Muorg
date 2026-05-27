package nl.muorg.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface LocalPlaylistDao {
    @Query("SELECT * FROM local_playlists ORDER BY createdAt")
    suspend fun getAll(): List<LocalPlaylist>

    @Insert
    suspend fun insert(playlist: LocalPlaylist): Long

    @Update
    suspend fun update(playlist: LocalPlaylist)

    @Query("DELETE FROM local_playlists WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM local_playlist_entries WHERE playlistId = :playlistId")
    suspend fun getTrackCount(playlistId: Int): Int

    @Query("SELECT * FROM local_playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getEntries(playlistId: Int): List<LocalPlaylistEntry>

    @Query("SELECT * FROM local_playlist_entries")
    suspend fun getAllEntries(): List<LocalPlaylistEntry>

    @Query("SELECT filePath FROM local_playlist_entries WHERE playlistId = :playlistId AND filePath IN (:filePaths)")
    suspend fun getExistingFilePaths(playlistId: Int, filePaths: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: LocalPlaylistEntry): Long

    @Query("DELETE FROM local_playlist_entries WHERE playlistId = :playlistId AND filePath = :filePath")
    suspend fun removeEntry(playlistId: Int, filePath: String)

    @Query("DELETE FROM local_playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Int)
}

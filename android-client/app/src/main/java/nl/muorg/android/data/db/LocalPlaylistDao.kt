package nl.muorg.android.data.db

import androidx.room.Dao
import androidx.room.Insert
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

    @Query("SELECT * FROM local_playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getEntries(playlistId: Int): List<LocalPlaylistEntry>

    @Insert
    suspend fun insertEntry(entry: LocalPlaylistEntry)

    @Query("DELETE FROM local_playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeEntry(playlistId: Int, trackId: Int)

    @Query("DELETE FROM local_playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Int)
}

package nl.muorg.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflinePlaylistDao {
    @Query("SELECT * FROM offline_playlists ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<OfflinePlaylist>

    @Query("SELECT * FROM offline_playlists WHERE playlistId = :playlistId")
    suspend fun get(playlistId: Int): OfflinePlaylist?

    /** Playlist ids with a download, for the tile's filled download button. */
    @Query("SELECT playlistId FROM offline_playlists")
    fun observeIds(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: OfflinePlaylist)

    @Query("DELETE FROM offline_playlists WHERE playlistId = :playlistId")
    suspend fun delete(playlistId: Int)

    @Query("DELETE FROM offline_playlists")
    suspend fun clearAll()
}

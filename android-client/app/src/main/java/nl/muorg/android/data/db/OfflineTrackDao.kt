package nl.muorg.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface OfflineTrackDao {
    @Query("SELECT * FROM offline_tracks WHERE status = 'ready'")
    suspend fun getDownloadedTracks(): List<OfflineTrack>

    @Query("SELECT * FROM offline_tracks WHERE playlistId = :playlistId AND status = 'ready'")
    suspend fun getDownloadedTracksForPlaylist(playlistId: Int): List<OfflineTrack>

    @Query("SELECT * FROM offline_tracks WHERE trackId = :trackId")
    suspend fun getTrack(trackId: Int): OfflineTrack?

    @Query("SELECT * FROM offline_tracks WHERE playlistId = :playlistId")
    suspend fun getTracksForPlaylist(playlistId: Int): List<OfflineTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: OfflineTrack)

    @Update
    suspend fun update(track: OfflineTrack)

    @Query("DELETE FROM offline_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: Int)

    @Query("DELETE FROM offline_tracks WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Int)

    @Query("SELECT COUNT(*) FROM offline_tracks WHERE playlistId = :playlistId AND status = 'ready'")
    suspend fun countReadyForPlaylist(playlistId: Int): Int

    @Query("SELECT COUNT(*) FROM offline_tracks WHERE playlistId = :playlistId")
    suspend fun countTotalForPlaylist(playlistId: Int): Int

    @Query("DELETE FROM offline_tracks")
    suspend fun clearAll()
}

package nl.muorg.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalTrackDao {
    @Query("SELECT * FROM local_tracks ORDER BY album, trackNumber")
    suspend fun getAllTracks(): List<LocalTrack>

    @Query("SELECT * FROM local_tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<LocalTrack>

    @Query("SELECT COUNT(*) FROM local_tracks")
    suspend fun count(): Int

    @Query("SELECT COUNT(DISTINCT album) FROM local_tracks")
    suspend fun albumCount(): Int

    @Query("SELECT COUNT(DISTINCT artist) FROM local_tracks")
    suspend fun artistCount(): Int

    @Query("SELECT SUM(durationSecs) FROM local_tracks")
    suspend fun totalDuration(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<LocalTrack>)

    @Query("DELETE FROM local_tracks")
    suspend fun clearAll()

    @Query("SELECT * FROM local_tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<LocalTrack>

    @Query("SELECT * FROM local_tracks WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): LocalTrack?

    @Query("DELETE FROM local_tracks WHERE path LIKE :prefix || '%'")
    suspend fun deleteByTreePrefix(prefix: String)

    @Query("""UPDATE local_tracks SET
        title = CASE WHEN :title IS NOT NULL THEN :title ELSE title END,
        artist = CASE WHEN :artist IS NOT NULL THEN :artist ELSE artist END,
        album = CASE WHEN :album IS NOT NULL THEN :album ELSE album END,
        albumArtist = CASE WHEN :albumArtist IS NOT NULL THEN :albumArtist ELSE albumArtist END,
        year = CASE WHEN :year IS NOT NULL THEN :year ELSE year END,
        genre = CASE WHEN :genre IS NOT NULL THEN :genre ELSE genre END,
        trackNumber = CASE WHEN :trackNumber IS NOT NULL THEN :trackNumber ELSE trackNumber END,
        discNumber = CASE WHEN :discNumber IS NOT NULL THEN :discNumber ELSE discNumber END
    WHERE id = :id""")
    suspend fun updateMetadata(
        id: Int,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        year: Int?,
        genre: String?,
        trackNumber: Int?,
        discNumber: Int?,
    )
}

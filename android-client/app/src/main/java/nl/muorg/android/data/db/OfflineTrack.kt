package nl.muorg.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_tracks")
data class OfflineTrack(
    @PrimaryKey val trackId: Int,
    val filePath: String,
    val playlistId: Int? = null,
    val status: String = "downloading", // downloading, ready, failed
    val progress: Int = 0,
    val totalBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
)

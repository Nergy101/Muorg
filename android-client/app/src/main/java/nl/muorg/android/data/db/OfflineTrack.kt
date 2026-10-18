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
    /**
     * The track's catalog metadata, serialized at download time. The row
     * alone cannot render a playlist offline — there is no title, artist or
     * album in it — so the download stores the whole [nl.muorg.android.data.api.CatalogTrack]
     * alongside the file. Null only for rows written before this column
     * existed; those fall back to a filename-only track.
     */
    val trackJson: String? = null,
)

package nl.muorg.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A playlist the user downloaded for offline use.
 *
 * The row is what marks a playlist "downloaded": the tile's download button
 * reads it, and the offline-mode playlist list is built from it. Track rows
 * in [OfflineTrack] carry the files; this carries the name and icon the
 * server would otherwise have to be asked for.
 */
@Entity(tableName = "offline_playlists")
data class OfflinePlaylist(
    @PrimaryKey val playlistId: Int,
    val name: String,
    val icon: String? = null,
    val downloadedAt: Long = System.currentTimeMillis(),
)

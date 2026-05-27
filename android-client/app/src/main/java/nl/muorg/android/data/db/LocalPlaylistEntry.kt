package nl.muorg.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "filePath"], unique = true),
    ],
)
data class LocalPlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val filePath: String,
    val trackTitle: String?,
    val trackArtist: String?,
    val position: Int,
)

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
        ForeignKey(
            entity = LocalTrack::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("trackId")],
)
data class LocalPlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val trackId: Int,
    val position: Int,
)

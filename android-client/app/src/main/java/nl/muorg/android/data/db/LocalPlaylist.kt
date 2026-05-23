package nl.muorg.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_playlists")
data class LocalPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val icon: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

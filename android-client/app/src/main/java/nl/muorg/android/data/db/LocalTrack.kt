package nl.muorg.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import nl.muorg.android.data.api.CatalogTrack

@Entity(tableName = "local_tracks")
data class LocalTrack(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val path: String,
    val contentUri: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val year: Int?,
    val genre: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationSecs: Double?,
    val format: String,
    val fileSize: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

fun LocalTrack.toCatalogTrack() = CatalogTrack(
    id = -id,
    path = path,
    rootId = -1,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    featuring = null,
    year = year,
    genre = genre,
    trackNumber = trackNumber,
    discNumber = discNumber,
    durationSecs = durationSecs,
    format = format,
    mtimeSecs = addedAt / 1000,
    hasCover = false,
    rating = null,
    playCount = 0,
    lastPlayedAt = null,
    localFilePath = contentUri,
)

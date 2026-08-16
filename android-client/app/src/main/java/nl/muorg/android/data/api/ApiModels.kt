package nl.muorg.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogTrack(
    val id: Int,
    val path: String,
    @SerialName("root_id") val rootId: Int,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val featuring: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    @SerialName("duration_secs") val durationSecs: Double? = null,
    val format: String,
    @SerialName("mtime_secs") val mtimeSecs: Long,
    @SerialName("has_cover") val hasCover: Boolean,
    val rating: Int? = null,
    @SerialName("play_count") val playCount: Int,
    @SerialName("last_played_at") val lastPlayedAt: Long? = null,
    val localFilePath: String? = null,
    val localCoverPath: String? = null,
) {
    val displayTitle: String get() = title ?: path.substringAfterLast("/").substringBeforeLast(".")
    val displayArtist: String get() = artist ?: albumArtist ?: "Unknown Artist"
    val displayAlbum: String get() = album ?: "Unknown Album"

    fun formattedDuration(): String {
        val totalSecs = durationSecs?.toInt() ?: return "--:--"
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

@Serializable
data class Playlist(
    val id: Int,
    val name: String,
    val icon: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("smart_rules") val smartRules: String? = null,
)

@Serializable
data class Stats(
    @SerialName("track_count") val trackCount: Int,
    @SerialName("album_count") val albumCount: Int,
    @SerialName("artist_count") val artistCount: Int,
    @SerialName("total_duration_secs") val totalDurationSecs: Long,
)

@Serializable
data class StreamTokenResponse(
    val token: String,
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
)

@Serializable
data class UpdatePlaylistRequest(
    val name: String? = null,
    val icon: String? = null,
)

/** `rules_json` is a flat array of these, matching the web client exactly. */
@Serializable
data class SmartRule(
    val field: String,
    val op: String,
    val value: String,
)

@Serializable
data class CreateSmartPlaylistRequest(
    val name: String,
    @SerialName("rules_json") val rulesJson: String,
)

@Serializable
data class PlaylistTracksRequest(
    @SerialName("track_ids") val trackIds: List<Int>,
)

@Serializable
data class ReorderPlaylistTracksRequest(
    @SerialName("ids") val ids: List<Int>,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)

@Serializable
data class MetadataUpdateRequest(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val featuring: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    @SerialName("backup_before_write") val backupBeforeWrite: Boolean = false,
) {
    fun hasAnyField() = title != null || artist != null || album != null ||
        albumArtist != null || featuring != null || year != null ||
        genre != null || trackNumber != null || discNumber != null
}

// Local model for grouping tracks by album
data class AlbumGroup(
    val albumName: String,
    val artist: String,
    val year: Int?,
    val trackCount: Int,
    val coverTrackId: Int,
    val coverArtUri: String? = null,
)

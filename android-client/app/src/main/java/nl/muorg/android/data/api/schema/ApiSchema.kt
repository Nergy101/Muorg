// GENERATED FILE — do not edit.
//
// Source: server/openapi.json, itself derived from the muorg-server route
// handlers. Regenerate with ./scripts/generate-api-clients.sh after any API
// change; CI fails if this file is stale.
//
// These are the wire shapes only; the Retrofit interface that uses them is
// hand-written in MuorgApiService.kt. They live in their own package because the
// app's own models in ../ApiModels.kt still shadow some of these names while
// they are migrated onto the generated ones.

package nl.muorg.android.data.api.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator

/** `GET /api/admin/health` */
@Serializable
data class AdminHealthResponse(
    val server: String,
    /** `"ok"` when the catalog is readable, `"degraded"` otherwise. */
    val status: String,
    val version: String
)

/** `POST /api/tracks/{id}/auto-tag-suggestions` */
@Serializable
data class AutoTagSuggestionsResponse(
    val candidates: List<MatchCandidate>
)

/** `GET /api/admin/backup-directory` */
@Serializable
data class BackupDirectoryResponse(
    val path: String
)

@Serializable
data class BatchMetadataItem(
    val id: Long,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val artist: String? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    val featuring: String? = null,
    val genre: String? = null,
    @SerialName("picture_base64") val pictureBase64: String? = null,
    val title: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val year: Int? = null
)

/** `POST /api/tracks/metadata/batch` */
@Serializable
data class BatchUpdateResponse(
    val ok: Boolean,
    /** Number of tracks whose tags were actually written. */
    val updated: Int
)

@Serializable
data class CastDevice(
    val address: String,
    val id: String,
    val name: String,
    val port: Int
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("status")
sealed interface CastSessionStatus {
    @Serializable
    @SerialName("idle")
    object Idle : CastSessionStatus

    @Serializable
    @SerialName("connecting")
    object Connecting : CastSessionStatus

    @Serializable
    @SerialName("transcoding")
    object Transcoding : CastSessionStatus

    @Serializable
    @SerialName("playing")
    data class Playing(@SerialName("position_secs") val positionSecs: Float? = null) : CastSessionStatus

    @Serializable
    @SerialName("paused")
    data class Paused(@SerialName("position_secs") val positionSecs: Float? = null) : CastSessionStatus

    @Serializable
    @SerialName("stopped")
    data class Stopped(val finished: Boolean) : CastSessionStatus

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : CastSessionStatus
}

@Serializable
data class CastStatusResponse(
    val session: CastSessionStatus,
    val volume: Float
)

@Serializable
data class CatalogTrack(
    val format: String,
    @SerialName("has_cover") val hasCover: Boolean,
    val id: Long,
    @SerialName("mtime_secs") val mtimeSecs: Long,
    val path: String,
    @SerialName("play_count") val playCount: Long,
    @SerialName("root_id") val rootId: Long,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val artist: String? = null,
    @SerialName("disc_number") val discNumber: Long? = null,
    @SerialName("duration_secs") val durationSecs: Long? = null,
    val featuring: String? = null,
    val genre: String? = null,
    @SerialName("last_played_at") val lastPlayedAt: Long? = null,
    val rating: Long? = null,
    val title: String? = null,
    @SerialName("track_number") val trackNumber: Long? = null,
    val year: Long? = null
)

/** `GET /api/tracks/count` */
@Serializable
data class CountResponse(
    val count: Long
)

@Serializable
data class CreateBody(
    val name: String
)

/** Body of every non-2xx response. */
@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class FetchImageBody(
    val url: String
)

@Serializable
data class FetchedImage(
    val base64: String,
    val mime: String
)

@Serializable
data class LibraryStats(
    @SerialName("album_count") val albumCount: Long,
    @SerialName("artist_count") val artistCount: Long,
    @SerialName("total_duration_secs") val totalDurationSecs: Long,
    @SerialName("track_count") val trackCount: Long
)

/** A single candidate match from MusicBrainz. */
@Serializable
data class MatchCandidate(
    val artist: String,
    /** Confidence score 0.0–1.0 computed by comparing query against results. */
    val confidence: Double,
    /** MusicBrainz recording MBID. */
    val mbid: String,
    val title: String,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val year: Int? = null
)

/**
 * Patch document for `PATCH /api/tracks/{id}/metadata`.
 *
 * Every field is `Option<Option<T>>`: absent means "leave this tag alone",
 * `null` means "clear it". `ToSchema` flattens that to a plain nullable
 * optional, which is the right shape for a JSON patch body.
 */
@Serializable
data class MetadataUpdate(
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val artist: String? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    val featuring: String? = null,
    val genre: String? = null,
    @SerialName("picture_base64") val pictureBase64: String? = null,
    val title: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val year: Int? = null
)

/** The uniform acknowledgement for mutations that return no data. */
@Serializable
data class OkResponse(
    /** Always `true`; failures come back as an [`ErrorResponse`] with a 4xx/5xx. */
    val ok: Boolean
)

@Serializable
data class PatchMetadataBody(
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val artist: String? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    val featuring: String? = null,
    val genre: String? = null,
    @SerialName("picture_base64") val pictureBase64: String? = null,
    val title: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val year: Int? = null,
    @SerialName("backup_before_write") val backupBeforeWrite: Boolean? = null
)

@Serializable
data class PlayBody(
    @SerialName("device_address") val deviceAddress: String,
    @SerialName("device_port") val devicePort: Int,
    @SerialName("track_id") val trackId: Long
)

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    @SerialName("track_count") val trackCount: Long,
    val icon: String? = null,
    @SerialName("smart_rules") val smartRules: String? = null
)

@Serializable
data class PlaylistTrackEntry(
    @SerialName("entry_id") val entryId: Long,
    @SerialName("track_id") val trackId: Long
)

@Serializable
data class RatingBody(
    val rating: Long? = null
)

@Serializable
data class RemoveFolderBody(
    @SerialName("root_path") val rootPath: String
)

@Serializable
data class RenameBody(
    @SerialName("new_path") val newPath: String
)

@Serializable
data class ReorderBody(
    val ids: List<Long>
)

@Serializable
data class RescanBody(
    @SerialName("root_path") val rootPath: String? = null
)

@Serializable
data class RescanResult(
    @SerialName("tracks_added") val tracksAdded: Long
)

/** Query parameters for searching MusicBrainz. */
@Serializable
data class SearchQuery(
    val album: String? = null,
    val artist: String? = null,
    @SerialName("duration_secs") val durationSecs: Int? = null,
    val title: String? = null
)

@Serializable
data class SeekBody(
    @SerialName("position_secs") val positionSecs: Float,
    @SerialName("was_playing") val wasPlaying: Boolean
)

@Serializable
data class SmartCreateBody(
    val name: String,
    @SerialName("rules_json") val rulesJson: String
)

@Serializable
data class SmartRulesBody(
    @SerialName("rules_json") val rulesJson: String
)

@Serializable
data class TokenResponse(
    val token: String
)

@Serializable
data class TrackBackupRecord(
    @SerialName("backup_path") val backupPath: String,
    @SerialName("created_at") val createdAt: Long,
    val id: Long,
    @SerialName("track_path") val trackPath: String
)

@Serializable
data class TrackIdsBody(
    @SerialName("track_ids") val trackIds: List<Long>
)

/** Stored lyrics for a track, if any. */
@Serializable
data class TrackLyrics(
    val lyrics: String,
    @SerialName("sync_format") val syncFormat: String,
    @SerialName("track_id") val trackId: Long
)

@Serializable
data class TrackMetadata(
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val artist: String? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    @SerialName("duration_secs") val durationSecs: Long? = null,
    val featuring: String? = null,
    val genre: String? = null,
    /** Embedded lyrics text (USLT / UNSYNCEDLYRICS, or synced when available). */
    val lyrics: String? = null,
    /**
     * `"lrc"` when the lyrics text carries `[mm:ss.xx]` timing lines, else
     * `"plain"`. Absent when there are no embedded lyrics.
     */
    @SerialName("lyrics_format") val lyricsFormat: String? = null,
    @SerialName("picture_base64") val pictureBase64: String? = null,
    @SerialName("picture_mime") val pictureMime: String? = null,
    @SerialName("picture_size_bytes") val pictureSizeBytes: Int? = null,
    @SerialName("replaygain_album_gain_db") val replaygainAlbumGainDb: Float? = null,
    @SerialName("replaygain_album_peak") val replaygainAlbumPeak: Float? = null,
    @SerialName("replaygain_track_gain_db") val replaygainTrackGainDb: Float? = null,
    @SerialName("replaygain_track_peak") val replaygainTrackPeak: Float? = null,
    val title: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val year: Int? = null
)

@Serializable
data class UpdateBody(
    val icon: String? = null,
    val name: String? = null
)

@Serializable
data class VolumeBody(
    val level: Float
)

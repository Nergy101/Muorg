package nl.muorg.android.data.api

import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.api.schema.PatchMetadataBody
import retrofit2.Response
import nl.muorg.android.data.api.schema.CatalogTrack as WireTrack
import nl.muorg.android.data.api.schema.LibraryStats as WireStats
import nl.muorg.android.data.api.schema.Playlist as WirePlaylist

/**
 * The seam between the generated API client and the app's own models.
 *
 * Every HTTP call now goes through [MuorgApi], which is generated from the
 * server's OpenAPI document — so a route or field that changes on the server
 * breaks the build here, in one file, instead of deserializing to null in
 * twenty screens.
 *
 * The app keeps its own [CatalogTrack] rather than using the wire type
 * directly, because it carries state the server knows nothing about:
 * `localFilePath` and `localCoverPath` are what make offline playback and
 * casting work, and on-device library tracks are represented as a CatalogTrack
 * with no server id at all. That is a domain model, not a wire model, and the
 * two are deliberately different types.
 *
 * Ids narrow from `Long` to `Int` here. The server's ids are SQLite rowids
 * (i64); the app, its Room entities and its Compose state have always used Int.
 * Truncation would need a library past 2.1 billion tracks, and keeping the
 * narrowing in this one place is cheaper than a migration that touches every
 * screen. If that ever stops being true, this is the function to change.
 */
fun WireTrack.toDomain(): CatalogTrack = CatalogTrack(
    id = id.toInt(),
    path = path,
    rootId = rootId.toInt(),
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    featuring = featuring,
    year = year?.toInt(),
    genre = genre,
    trackNumber = trackNumber?.toInt(),
    discNumber = discNumber?.toInt(),
    durationSecs = durationSecs?.toDouble(),
    format = format,
    mtimeSecs = mtimeSecs,
    hasCover = hasCover,
    rating = rating?.toInt(),
    playCount = playCount.toInt(),
    lastPlayedAt = lastPlayedAt,
)

fun List<WireTrack>.toDomain(): List<CatalogTrack> = map { it.toDomain() }

fun WirePlaylist.toDomain(): Playlist = Playlist(
    id = id.toInt(),
    name = name,
    icon = icon,
    trackCount = trackCount.toInt(),
    smartRules = smartRules,
)

@JvmName("playlistsToDomain")
fun List<WirePlaylist>.toDomain(): List<Playlist> = map { it.toDomain() }

fun WireStats.toDomain(): Stats = Stats(
    trackCount = trackCount.toInt(),
    albumCount = albumCount.toInt(),
    artistCount = artistCount.toInt(),
    totalDurationSecs = totalDurationSecs,
)

/**
 * The app's edit form -> the wire patch body.
 *
 * [MetadataUpdateRequest] stays a domain type because the metadata scanner uses
 * its `hasAnyField()` helper to decide whether an edit is worth sending.
 */
fun MetadataUpdateRequest.toWire(): PatchMetadataBody = PatchMetadataBody(
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    featuring = featuring,
    year = year,
    genre = genre,
    trackNumber = trackNumber,
    discNumber = discNumber,
    backupBeforeWrite = backupBeforeWrite,
)

/**
 * Unwrap a Retrofit [Response], or throw with the status.
 *
 * The generated interface returns `Response<T>` uniformly so callers can see
 * the status code; almost all of them only want the body, and want a failure to
 * surface as an exception their `runCatching` already handles.
 */
fun <T> Response<T>.bodyOrThrow(what: String): T {
    val body = body()
    if (!isSuccessful || body == null) {
        error("$what failed: HTTP ${code()}")
    }
    return body
}

/** Same, for endpoints whose success carries no body. */
fun Response<*>.orThrow(what: String) {
    if (!isSuccessful) error("$what failed: HTTP ${code()}")
}

package nl.muorg.android.data.repository

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.MetadataUpdateRequest
import nl.muorg.android.data.api.Stats
import nl.muorg.android.data.api.bodyOrThrow
import nl.muorg.android.data.api.orThrow
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.api.schema.MatchCandidate
import nl.muorg.android.data.api.schema.PatchMetadataBody
import nl.muorg.android.data.api.schema.RenameBody
import nl.muorg.android.data.api.schema.SearchQuery
import nl.muorg.android.data.api.schema.TrackBackupRecord
import nl.muorg.android.data.api.toDomain
import nl.muorg.android.data.api.toWire
import javax.inject.Inject
import javax.inject.Singleton

/** Matches the server's default `/api/tracks` limit. */
private const val PAGE_SIZE = 500

/**
 * How long a fetched catalog stays servable. Album detail, playlists, home and
 * the metadata scanner each want the whole catalog, so without this every
 * screen entry re-pages the whole library (7 requests / ~1.5 MB for 3000
 * tracks) over mobile data. Ten minutes covers a browsing session; the catalog
 * only changes when the server rescans, and metadata edits invalidate it
 * explicitly.
 */
private const val CACHE_TTL_MS = 10 * 60_000L

@Singleton
class LibraryRepository @Inject constructor(
    private val api: MuorgApi,
) {

    private val cacheLock = Mutex()
    private var cachedTracks: List<CatalogTrack>? = null
    private var cachedAtMs = 0L

    /**
     * Whole catalog, paged. `/api/tracks` is paginated (500 rows by default),
     * so a single call returns the alphabetical head of the library while
     * `/api/stats` still reports every track — which looked like "the counts
     * are right but the library is missing most of it".
     *
     * [onPage] receives the accumulated list after each page so a screen can
     * render the first 500 immediately instead of blocking on the whole
     * catalog. Ties in the server's `ORDER BY artist, album, track_number,
     * title` are not unique, so rows are de-duplicated by id.
     *
     * Results are cached for [CACHE_TTL_MS] so that album detail, playlists
     * and the metadata scanner reuse one fetch instead of re-paging.
     */
    suspend fun getAllTracks(
        onPage: (suspend (List<CatalogTrack>) -> Unit)? = null,
    ): Result<List<CatalogTrack>> = runCatching {
        cacheLock.withLock {
            val cached = cachedTracks
            if (cached != null && SystemClock.elapsedRealtime() - cachedAtMs < CACHE_TTL_MS) {
                return@withLock cached
            }
            fetchAllPages(onPage).also {
                cachedTracks = it
                cachedAtMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private suspend fun fetchAllPages(
        onPage: (suspend (List<CatalogTrack>) -> Unit)?,
    ): List<CatalogTrack> {
        val all = ArrayList<CatalogTrack>()
        val seen = HashSet<Int>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total) {
            val response = api.getTracks(offset.toLong(), PAGE_SIZE.toLong())
            val page = response.bodyOrThrow("GET /api/tracks?offset=$offset").toDomain()
            response.headers()["X-Total-Count"]?.toIntOrNull()?.let { total = it }
            for (track in page) if (seen.add(track.id)) all.add(track)
            offset += PAGE_SIZE
            // A short page means the server ran out of rows even if the header
            // was missing or stale (a concurrent rescan can shrink `total`).
            if (page.size < PAGE_SIZE) break
            onPage?.invoke(all.toList())
        }
        return all
    }

    suspend fun search(query: String): Result<List<CatalogTrack>> = runCatching {
        api.searchTracks(query).bodyOrThrow("GET /api/search").toDomain()
    }

    suspend fun getRecentPlayHistory(limit: Int = 20): Result<List<CatalogTrack>> = runCatching {
        api.getRecentPlayHistory(limit.toLong())
            .bodyOrThrow("GET /api/play-history/recent").toDomain()
    }

    suspend fun getTopPlayHistory(limit: Int = 20, days: Int = 30): Result<List<CatalogTrack>> = runCatching {
        api.getTopPlayHistory(limit.toLong(), days.toLong())
            .bodyOrThrow("GET /api/play-history/top").toDomain()
    }

    suspend fun getStats(): Result<Stats> = runCatching {
        api.getStats().bodyOrThrow("GET /api/stats").toDomain()
    }

    suspend fun recordPlay(trackId: Int): Result<Unit> = runCatching {
        api.recordPlay(trackId.toLong()).orThrow("POST /api/tracks/$trackId/play")
    }

    suspend fun getStreamToken(trackId: Int): Result<String> = runCatching {
        api.issueToken(trackId.toLong())
            .bodyOrThrow("GET /api/tracks/$trackId/stream-token").token
    }

    suspend fun patchTrackMetadata(trackId: Int, update: MetadataUpdateRequest): Result<Unit> = runCatching {
        api.patchMetadata(trackId.toLong(), update.toWire())
            .orThrow("PATCH /api/tracks/$trackId/metadata")
        // The edited row is now stale in the cached catalog.
        cacheLock.withLock { cachedTracks = null }
    }

    /**
     * MusicBrainz candidates for a track, best match first.
     *
     * With no query the server builds one from the file's own tags, which is
     * what makes this useful on a badly-tagged track: it matches on whatever is
     * there plus the duration.
     */
    suspend fun autoTagSuggestions(trackId: Int): Result<List<MatchCandidate>> = runCatching {
        api.autoTagSuggestions(trackId.toLong(), SearchQuery())
            .bodyOrThrow("POST /api/tracks/$trackId/auto-tag-suggestions")
            .candidates
    }

    /**
     * The most recent pre-write backup of this track, or null if there is none.
     *
     * The server takes one before each tag write when `backup_before_write` is
     * set, so this is the undo for a bad auto-tag.
     */
    suspend fun latestBackup(trackId: Int): Result<TrackBackupRecord?> = runCatching {
        val response = api.getBackup(trackId.toLong())
        // A null body is the answer here, not a failure: the endpoint returns
        // `null` for a track that has never been written to. `bodyOrThrow`
        // would read that as an error.
        response.orThrow("GET /api/tracks/$trackId/backup")
        response.body()
    }

    suspend fun restoreFromBackup(trackId: Int): Result<Unit> = runCatching {
        api.restoreBackup(trackId.toLong())
            .orThrow("POST /api/tracks/$trackId/restore")
        // The restored tags make the cached catalog stale.
        cacheLock.withLock { cachedTracks = null }
    }

    /** Move the file on disk and repoint the catalog row at its new path. */
    suspend fun renameTrackFile(trackId: Int, newPath: String): Result<Unit> = runCatching {
        api.renameFile(trackId.toLong(), RenameBody(newPath = newPath))
            .orThrow("POST /api/tracks/$trackId/rename")
        cacheLock.withLock { cachedTracks = null }
    }

    fun buildAlbumGroups(tracks: List<CatalogTrack>): List<AlbumGroup> {
        return tracks
            .groupBy { it.displayAlbum }
            .map { (albumName, albumTracks) ->
                val representative = albumTracks
                    .sortedWith(
                        compareBy(
                            { it.discNumber ?: 0 },
                            { it.trackNumber ?: 0 }
                        )
                    )
                    .first()
                AlbumGroup(
                    albumName = albumName,
                    artist = representative.albumArtist ?: representative.displayArtist,
                    year = representative.year,
                    trackCount = albumTracks.size,
                    coverTrackId = albumTracks.firstOrNull { it.hasCover }?.id ?: representative.id,
                )
            }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.albumName.lowercase() }))
    }
}

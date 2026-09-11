package nl.muorg.android.util

import nl.muorg.android.data.api.CatalogTrack

/**
 * Library reports — the "what is wrong with my collection" views.
 *
 * A port of the shared `src/reports.ts` the desktop and web apps run. Android
 * cannot import TypeScript, so this is the one place the definitions are
 * written twice; the two are kept in step by asserting the same cases on both
 * sides (`src/reports.test.ts` and `LibraryReportsTest.kt`). Anything changed
 * here has to be changed there, or the same library reports different numbers
 * depending on which device you are holding.
 */
object LibraryReports {

    /** The reports a user can open, in the order they are listed. */
    enum class Kind(val label: String) {
        MISSING_METADATA("Missing metadata"),
        DUPLICATES("Duplicates"),
        MISSING_ALBUM_COVER("Missing album cover"),
        RECENTLY_PLAYED("Recently played"),
        MOST_PLAYED("Most played"),
        ;

        companion object {
            fun fromRouteArg(value: String?): Kind =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MISSING_METADATA
        }
    }

    /** Fields the "missing metadata" report can look for. */
    enum class Field(val label: String) {
        TITLE("Title"),
        ARTIST("Artist"),
        ALBUM("Album"),
        ALBUM_ARTIST("Album artist"),
        YEAR("Year"),
        GENRE("Genre"),
        TRACK_NUMBER("Track #"),
        DISC_NUMBER("Disc #"),
        RATING("Rating"),
        HAS_COVER("Cover art"),
    }

    /** What the default "missing metadata" report looks for. */
    val DEFAULT_FIELDS: List<Field> = listOf(Field.TITLE, Field.ARTIST, Field.ALBUM)

    /**
     * Is [field] unset on this track?
     *
     * A whitespace-only tag counts as missing: a file tagged `"   "` is not
     * tagged, and treating it as present is how those files stay invisible to
     * the report that exists to find them. Numbers are only absent or present —
     * `0` is a real track number, not a gap.
     */
    fun isFieldMissing(track: CatalogTrack, field: Field): Boolean = when (field) {
        Field.HAS_COVER -> !track.hasCover
        Field.RATING -> track.rating == null
        Field.YEAR -> track.year == null
        Field.TRACK_NUMBER -> track.trackNumber == null
        Field.DISC_NUMBER -> track.discNumber == null
        Field.TITLE -> track.title.isNullOrBlank()
        Field.ARTIST -> track.artist.isNullOrBlank()
        Field.ALBUM -> track.album.isNullOrBlank()
        Field.ALBUM_ARTIST -> track.albumArtist.isNullOrBlank()
        Field.GENRE -> track.genre.isNullOrBlank()
    }

    /** Tracks missing at least one of [fields]. An empty list matches nothing. */
    fun tracksMissingMetadata(
        tracks: List<CatalogTrack>,
        fields: List<Field> = DEFAULT_FIELDS,
    ): List<CatalogTrack> {
        if (fields.isEmpty()) return emptyList()
        return tracks.filter { track -> fields.any { isFieldMissing(track, it) } }
    }

    /** Which of [fields] this track is short of, for a per-row explanation. */
    fun missingFieldsFor(
        track: CatalogTrack,
        fields: List<Field> = DEFAULT_FIELDS,
    ): List<Field> = fields.filter { isFieldMissing(track, it) }

    private fun keyParts(track: CatalogTrack): List<String> = listOf(
        track.artist.orEmpty().trim().lowercase(),
        track.album.orEmpty().trim().lowercase(),
        track.title.orEmpty().trim().lowercase(),
    )

    /**
     * The key two files share to count as the same recording: artist, album and
     * title, compared case- and padding-insensitively.
     *
     * Deliberately not the path or the duration — the point is to find the same
     * song filed twice, usually two rips in two folders.
     */
    fun duplicateKey(track: CatalogTrack): String = keyParts(track).joinToString("|")

    /**
     * Groups of tracks sharing a key, largest group first.
     *
     * A track with no artist, album or title at all is skipped: those all
     * collapse to one key, and reporting every untagged file as a duplicate of
     * every other buries the real ones. That is the missing-metadata report's
     * job instead.
     */
    fun duplicateGroups(tracks: List<CatalogTrack>): List<List<CatalogTrack>> {
        val groups = LinkedHashMap<String, MutableList<CatalogTrack>>()
        for (track in tracks) {
            if (keyParts(track).all { it.isEmpty() }) continue
            groups.getOrPut(duplicateKey(track)) { mutableListOf() }.add(track)
        }
        return groups.values
            .filter { it.size > 1 }
            .sortedByDescending { it.size }
    }

    /** Every track in a duplicate group, copies of one recording kept adjacent. */
    fun duplicateTracks(tracks: List<CatalogTrack>): List<CatalogTrack> =
        duplicateGroups(tracks).flatten()

    /**
     * How many files could be deleted without losing a recording — copies
     * beyond the first in each group, not tracks involved.
     *
     * Two copies of one song is one duplicate. Showing "2" beside a two-row
     * list reads as two problems when there is one.
     */
    fun duplicateCount(tracks: List<CatalogTrack>): Int =
        duplicateGroups(tracks).sumOf { it.size - 1 }

    fun tracksMissingAlbumCover(tracks: List<CatalogTrack>): List<CatalogTrack> =
        tracks.filter { !it.hasCover }

    /** Played at least once, most recent first. */
    fun recentlyPlayedTracks(tracks: List<CatalogTrack>): List<CatalogTrack> =
        tracks.filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt ?: 0L }

    /** Played at least once, most plays first. */
    fun mostPlayedTracks(tracks: List<CatalogTrack>): List<CatalogTrack> =
        tracks.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }

    /** The tracks one report lists. */
    fun run(
        kind: Kind,
        tracks: List<CatalogTrack>,
        fields: List<Field> = DEFAULT_FIELDS,
    ): List<CatalogTrack> = when (kind) {
        Kind.MISSING_METADATA -> tracksMissingMetadata(tracks, fields)
        Kind.DUPLICATES -> duplicateTracks(tracks)
        Kind.MISSING_ALBUM_COVER -> tracksMissingAlbumCover(tracks)
        Kind.RECENTLY_PLAYED -> recentlyPlayedTracks(tracks)
        Kind.MOST_PLAYED -> mostPlayedTracks(tracks)
    }

    /**
     * The number shown beside a report.
     *
     * Everything except duplicates counts rows; duplicates counts redundant
     * copies, which is the actionable number — see [duplicateCount].
     */
    fun count(
        kind: Kind,
        tracks: List<CatalogTrack>,
        fields: List<Field> = DEFAULT_FIELDS,
    ): Int = if (kind == Kind.DUPLICATES) duplicateCount(tracks) else run(kind, tracks, fields).size

    /** Every report's count in one pass, for rendering the whole list. */
    fun counts(
        tracks: List<CatalogTrack>,
        fields: List<Field> = DEFAULT_FIELDS,
    ): Map<Kind, Int> = Kind.entries.associateWith { count(it, tracks, fields) }

    /**
     * The badge on the Reports entry: only the reports that mean something is
     * wrong with the files. Play history is not a to-do list.
     */
    fun issueCount(tracks: List<CatalogTrack>, fields: List<Field> = DEFAULT_FIELDS): Int =
        count(Kind.MISSING_METADATA, tracks, fields) +
            duplicateCount(tracks) +
            count(Kind.MISSING_ALBUM_COVER, tracks)
}

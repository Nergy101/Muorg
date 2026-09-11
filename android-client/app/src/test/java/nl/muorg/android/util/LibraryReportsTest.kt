package nl.muorg.android.util

import nl.muorg.android.data.api.CatalogTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin half of the report definitions.
 *
 * Deliberately the same cases as `src/reports.test.ts`: the logic exists twice
 * because Android cannot import the shared TypeScript, so the tests are what
 * keep the two honest. A change to one that is not made to the other shows up
 * as a case that passes on one side and fails on the other.
 */
class LibraryReportsTest {

    private var nextId = 1

    private fun track(
        title: String? = "Title",
        artist: String? = "Artist",
        album: String? = "Album",
        albumArtist: String? = "Artist",
        year: Int? = 2000,
        genre: String? = "Rock",
        trackNumber: Int? = 1,
        discNumber: Int? = 1,
        hasCover: Boolean = true,
        rating: Int? = 3,
        playCount: Int = 0,
        lastPlayedAt: Long? = null,
    ): CatalogTrack = CatalogTrack(
        id = nextId++,
        path = "/music/track$nextId.mp3",
        rootId = 1,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        year = year,
        genre = genre,
        trackNumber = trackNumber,
        discNumber = discNumber,
        durationSecs = 180.0,
        format = "mp3",
        mtimeSecs = 1_700_000_000L,
        hasCover = hasCover,
        rating = rating,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
    )

    // -- missing metadata ---------------------------------------------------

    @Test
    fun `an absent text tag is missing`() {
        assertTrue(LibraryReports.isFieldMissing(track(title = null), LibraryReports.Field.TITLE))
        assertFalse(LibraryReports.isFieldMissing(track(title = "Song"), LibraryReports.Field.TITLE))
    }

    @Test
    fun `a whitespace-only tag is missing`() {
        // A file tagged "   " is untagged; counting it as present is how those
        // files stay invisible to the report meant to find them.
        assertTrue(LibraryReports.isFieldMissing(track(artist = "   "), LibraryReports.Field.ARTIST))
        assertTrue(LibraryReports.isFieldMissing(track(album = ""), LibraryReports.Field.ALBUM))
    }

    @Test
    fun `a zero is not a missing number`() {
        assertFalse(LibraryReports.isFieldMissing(track(trackNumber = 0), LibraryReports.Field.TRACK_NUMBER))
        assertFalse(LibraryReports.isFieldMissing(track(discNumber = 0), LibraryReports.Field.DISC_NUMBER))
        assertFalse(LibraryReports.isFieldMissing(track(year = 0), LibraryReports.Field.YEAR))
        assertFalse(LibraryReports.isFieldMissing(track(rating = 0), LibraryReports.Field.RATING))
    }

    @Test
    fun `has cover is read as a flag`() {
        assertTrue(LibraryReports.isFieldMissing(track(hasCover = false), LibraryReports.Field.HAS_COVER))
        assertFalse(LibraryReports.isFieldMissing(track(hasCover = true), LibraryReports.Field.HAS_COVER))
    }

    @Test
    fun `a track missing any one requested field matches`() {
        val complete = track()
        val noArtist = track(artist = null)
        val noYear = track(year = null)

        val found = LibraryReports.tracksMissingMetadata(
            listOf(complete, noArtist, noYear),
            listOf(LibraryReports.Field.ARTIST, LibraryReports.Field.YEAR),
        )
        assertEquals(listOf(noArtist.id, noYear.id), found.map { it.id })
    }

    @Test
    fun `no requested fields matches nothing`() {
        // An empty selection means "I am not checking anything", not
        // "everything is missing" — the latter flags the whole library.
        assertTrue(LibraryReports.tracksMissingMetadata(listOf(track(title = null)), emptyList()).isEmpty())
    }

    @Test
    fun `the default fields are title artist and album`() {
        val bad = track(album = null)
        val fineButUnrated = track(rating = null)
        val found = LibraryReports.tracksMissingMetadata(listOf(bad, fineButUnrated))
        assertEquals(listOf(bad.id), found.map { it.id })
    }

    @Test
    fun `a track reports which fields it is short of`() {
        val t = track(title = null, artist = "  ", album = "Album")
        assertEquals(
            listOf(LibraryReports.Field.TITLE, LibraryReports.Field.ARTIST),
            LibraryReports.missingFieldsFor(t),
        )
    }

    // -- duplicates ---------------------------------------------------------

    @Test
    fun `the duplicate key ignores case and padding`() {
        val a = track(artist = "Radiohead", album = "OK Computer", title = "Creep")
        val b = track(artist = " radiohead ", album = "ok computer", title = "CREEP")
        assertEquals(LibraryReports.duplicateKey(a), LibraryReports.duplicateKey(b))
    }

    @Test
    fun `two copies in two folders are one duplicate`() {
        // The key is not the path, which is the whole point: the same rip in
        // two places is what the report is for.
        val same = listOf(track(), track())
        assertEquals(1, LibraryReports.duplicateGroups(same).size)
        assertEquals(1, LibraryReports.duplicateCount(same))
    }

    @Test
    fun `the count is copies beyond the first`() {
        assertEquals(2, LibraryReports.duplicateCount(listOf(track(), track(), track())))
    }

    @Test
    fun `each group is counted separately`() {
        val tracks = listOf(
            track(title = "One"), track(title = "One"),
            track(title = "Two"), track(title = "Two"), track(title = "Two"),
        )
        assertEquals(3, LibraryReports.duplicateCount(tracks))
        assertEquals(listOf(3, 2), LibraryReports.duplicateGroups(tracks).map { it.size })
    }

    @Test
    fun `distinct tracks report nothing`() {
        val tracks = listOf(track(title = "One"), track(title = "Two"))
        assertEquals(0, LibraryReports.duplicateCount(tracks))
        assertTrue(LibraryReports.duplicateGroups(tracks).isEmpty())
    }

    @Test
    fun `completely untagged tracks are not duplicates of each other`() {
        // Otherwise every untagged file is a duplicate of every other one,
        // which buries the real duplicates. That is missing-metadata's job.
        val blank = { track(artist = null, album = null, title = null) }
        assertEquals(0, LibraryReports.duplicateCount(listOf(blank(), blank(), blank())))
    }

    @Test
    fun `a shared title with no album still groups`() {
        val partial = { track(artist = "A", album = null, title = "C") }
        assertEquals(1, LibraryReports.duplicateCount(listOf(partial(), partial())))
    }

    @Test
    fun `copies of one recording stay adjacent in the listing`() {
        val listed = LibraryReports.run(
            LibraryReports.Kind.DUPLICATES,
            listOf(
                track(title = "One"), track(title = "Two"),
                track(title = "One"), track(title = "Two"),
            ),
        )
        val keys = listed.map(LibraryReports::duplicateKey)
        assertEquals(keys[0], keys[1])
        assertEquals(keys[2], keys[3])
        assertTrue(keys[0] != keys[2])
    }

    // -- covers and play history -------------------------------------------

    @Test
    fun `only tracks with no art are listed`() {
        val without = track(hasCover = false)
        val found = LibraryReports.tracksMissingAlbumCover(listOf(track(), without))
        assertEquals(listOf(without.id), found.map { it.id })
    }

    @Test
    fun `recently played is newest first and skips never-played tracks`() {
        val old = track(lastPlayedAt = 100, playCount = 1)
        val recent = track(lastPlayedAt = 900, playCount = 1)
        val never = track(lastPlayedAt = null, playCount = 0)

        val found = LibraryReports.recentlyPlayedTracks(listOf(old, never, recent))
        assertEquals(listOf(recent.id, old.id), found.map { it.id })
    }

    @Test
    fun `most played is by count and skips never-played tracks`() {
        val once = track(playCount = 1, lastPlayedAt = 1)
        val often = track(playCount = 12, lastPlayedAt = 1)
        val never = track(playCount = 0)

        val found = LibraryReports.mostPlayedTracks(listOf(once, often, never))
        assertEquals(listOf(often.id, once.id), found.map { it.id })
    }

    // -- counts -------------------------------------------------------------

    @Test
    fun `every report except duplicates counts rows`() {
        val tracks = listOf(
            track(title = "Same"),
            track(title = "Same"),
            track(title = null, hasCover = false),
            track(title = "Played", playCount = 3, lastPlayedAt = 500),
        )
        val counts = LibraryReports.counts(tracks)
        assertEquals(1, counts[LibraryReports.Kind.MISSING_METADATA])
        assertEquals(1, counts[LibraryReports.Kind.DUPLICATES])
        assertEquals(1, counts[LibraryReports.Kind.MISSING_ALBUM_COVER])
        assertEquals(1, counts[LibraryReports.Kind.RECENTLY_PLAYED])
        assertEquals(1, counts[LibraryReports.Kind.MOST_PLAYED])
    }

    @Test
    fun `an empty library reports zero for everything`() {
        assertTrue(LibraryReports.counts(emptyList()).values.all { it == 0 })
        assertEquals(0, LibraryReports.issueCount(emptyList()))
    }

    @Test
    fun `the issue badge ignores play history`() {
        // Having played something is not a problem to fix; including it would
        // sit a permanent number next to the Reports entry.
        val played = listOf(track(playCount = 5, lastPlayedAt = 10))
        assertEquals(0, LibraryReports.issueCount(played))
    }

    @Test
    fun `an unknown route argument falls back instead of crashing`() {
        assertEquals(LibraryReports.Kind.MISSING_METADATA, LibraryReports.Kind.fromRouteArg(null))
        assertEquals(LibraryReports.Kind.MISSING_METADATA, LibraryReports.Kind.fromRouteArg("nonsense"))
        assertEquals(LibraryReports.Kind.DUPLICATES, LibraryReports.Kind.fromRouteArg("DUPLICATES"))
    }
}

package nl.muorg.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The metadata scanner reads tags out of a file's own path when the tags are
 * missing, using a user-supplied pattern like
 * `<AlbumArtist>/<Album>/<TrackNumber> - <TrackTitle>`. Getting this wrong
 * writes bad tags to real files, so the parsing is worth pinning down.
 */
class PathPatternMatcherTest {

    @Test
    fun `extracts every placeholder from a matching path`() {
        val fields = PathPatternMatcher.extractMetadataFromPath(
            "<AlbumArtist>/<Album>/<TrackNumber> - <TrackTitle>",
            "/music/Boards/Campfire/03 - Roygbiv.mp3",
        )
        assertEquals("Boards", fields?.get("albumartist"))
        assertEquals("Campfire", fields?.get("album"))
        assertEquals("03", fields?.get("tracknumber"))
        assertEquals("Roygbiv.mp3", fields?.get("tracktitle"))
    }

    @Test
    fun `matches only the tail of a longer path`() {
        // The pattern describes the last N directories, not the whole path.
        val fields = PathPatternMatcher.extractMetadataFromPath(
            "<Album>/<TrackTitle>",
            "/a/very/deep/tree/Campfire/Roygbiv.mp3",
        )
        assertEquals("Campfire", fields?.get("album"))
        assertEquals("Roygbiv.mp3", fields?.get("tracktitle"))
    }

    @Test
    fun `returns null when the path has fewer segments than the pattern`() {
        assertNull(
            PathPatternMatcher.extractMetadataFromPath(
                "<AlbumArtist>/<Album>/<TrackTitle>",
                "/Roygbiv.mp3",
            ),
        )
    }

    @Test
    fun `returns null when a literal separator does not appear`() {
        // The pattern wants "NN - Title"; this file has no " - ".
        assertNull(
            PathPatternMatcher.extractMetadataFromPath(
                "<TrackNumber> - <TrackTitle>",
                "/music/Roygbiv.mp3",
            ),
        )
    }

    @Test
    fun `is case-insensitive about literals`() {
        val fields = PathPatternMatcher.extractMetadataFromPath(
            "Disc <DiscNumber>/<TrackTitle>",
            "/music/DISC 2/Roygbiv.mp3",
        )
        assertEquals("2", fields?.get("discnumber"))
    }

    @Test
    fun `drops a placeholder that captured only whitespace`() {
        val fields = PathPatternMatcher.extractMetadataFromPath(
            "<Artist> - <TrackTitle>",
            "/music/   - Roygbiv.mp3",
        )
        assertEquals(null, fields?.get("artist"))
        assertEquals("Roygbiv.mp3", fields?.get("tracktitle"))
    }

    @Test
    fun `ignores trailing and repeated slashes`() {
        val fields = PathPatternMatcher.extractMetadataFromPath(
            "<Album>/<TrackTitle>",
            "//music//Campfire//Roygbiv.mp3",
        )
        assertEquals("Campfire", fields?.get("album"))
    }

    @Test
    fun `builds an update from the extracted fields`() {
        val update = PathPatternMatcher.buildMetadataUpdate(
            mapOf(
                "TrackTitle" to "Roygbiv",
                "Artist" to "Boards",
                "Album" to "Campfire",
                "Year" to "1998",
                "TrackNumber" to "3",
            ),
        )
        assertEquals("Roygbiv", update.title)
        assertEquals("Boards", update.artist)
        assertEquals("Campfire", update.album)
        assertEquals(1998, update.year)
        assertEquals(3, update.trackNumber)
    }

    @Test
    fun `accepts either spelling of a field name`() {
        val a = PathPatternMatcher.buildMetadataUpdate(mapOf("album_artist" to "X"))
        val b = PathPatternMatcher.buildMetadataUpdate(mapOf("albumartist" to "X"))
        assertEquals("X", a.albumArtist)
        assertEquals("X", b.albumArtist)
    }

    @Test
    fun `takes the numerator of an N-of-M track number`() {
        // "03/12" is a common tag shape; only the 3 is the track number.
        val update = PathPatternMatcher.buildMetadataUpdate(
            mapOf("TrackNumber" to "03/12", "DiscNumber" to "1/2"),
        )
        assertEquals(3, update.trackNumber)
        assertEquals(1, update.discNumber)
    }

    @Test
    fun `leaves a non-numeric year or track number unset rather than guessing`() {
        val update = PathPatternMatcher.buildMetadataUpdate(
            mapOf("Year" to "sometime", "TrackNumber" to "A-side"),
        )
        assertNull(update.year)
        assertNull(update.trackNumber)
    }

    @Test
    fun `hasAnyField is false for an update that would write nothing`() {
        assertEquals(false, PathPatternMatcher.buildMetadataUpdate(emptyMap()).hasAnyField())
        assertEquals(true, PathPatternMatcher.buildMetadataUpdate(mapOf("Artist" to "X")).hasAnyField())
    }

    @Test
    fun `gives every known field a readable display name`() {
        assertEquals("Album Artist", PathPatternMatcher.fieldDisplayName("albumartist"))
        assertEquals("Album Artist", PathPatternMatcher.fieldDisplayName("album_artist"))
        assertEquals("Track #", PathPatternMatcher.fieldDisplayName("TrackNumber"))
        assertEquals("Title", PathPatternMatcher.fieldDisplayName("tracktitle"))
        // Anything unrecognised is capitalised rather than dropped.
        assertEquals("Composer", PathPatternMatcher.fieldDisplayName("composer"))
    }
}

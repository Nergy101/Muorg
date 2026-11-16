package nl.muorg.android.util

import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.util.Discography.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin half of the discography rules — the same cases as the web's
 * `web-client/src/discography.test.ts`, so the two stay in step.
 */
class DiscographyTest {

    private var nextId = 1

    private fun t(
        title: String,
        artist: String?,
        album: String?,
        albumArtist: String? = null,
        featuring: String? = null,
        year: Int? = null,
        trackNumber: Int? = null,
        durationSecs: Double = 240.0,
    ) = CatalogTrack(
        id = nextId++,
        path = "/music/$title.flac",
        rootId = 1,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        featuring = featuring,
        year = year,
        trackNumber = trackNumber,
        durationSecs = durationSecs,
        format = "flac",
        mtimeSecs = 0,
        hasCover = false,
        playCount = 0,
    )

    @Test
    fun `classify follows the 7+ 4-6 1-3 track rule`() {
        assertEquals(Kind.ALBUM, Discography.classify("A", List(7) { 200 }))
        assertEquals(Kind.EP, Discography.classify("A", List(4) { 200 }))
        assertEquals(Kind.EP, Discography.classify("A", List(6) { 200 }))
        assertEquals(Kind.SINGLE, Discography.classify("A", List(3) { 200 }))
        assertEquals(Kind.SINGLE, Discography.classify("A", listOf(200)))
    }

    @Test
    fun `classify calls over 30 minutes an album and a 10 minute track an EP`() {
        assertEquals(Kind.ALBUM, Discography.classify("A", listOf(1000, 1000)))
        assertEquals(Kind.EP, Discography.classify("A", listOf(600, 300)))
    }

    @Test
    fun `an explicit EP or Single in the title wins`() {
        assertEquals(Kind.EP, Discography.classify("Night Drive EP", List(8) { 100 }))
        assertEquals(Kind.EP, Discography.classify("Remixes (EP)", listOf(100)))
        assertEquals(Kind.EP, Discography.classify("Vol. EP2", listOf(100)))
        assertEquals(Kind.SINGLE, Discography.classify("Blue Monday - Single", List(5) { 100 }))
        assertEquals(Kind.SINGLE, Discography.classify("Blue Monday (Single)", List(5) { 100 }))
        assertEquals(Kind.ALBUM, Discography.classify("Epic Tales", List(8) { 200 }))
        assertEquals(Kind.ALBUM, Discography.classify("Single Ladies Anthology", List(8) { 200 }))
    }

    @Test
    fun `credits split featuring tags and match case-insensitively`() {
        assertEquals(listOf("a", "b", "c"), Discography.splitCredits("A, B & C"))
        assertEquals(listOf("a", "b"), Discography.splitCredits("A feat. B"))
        assertEquals(listOf("a", "b", "c", "d"), Discography.splitCredits("A / B; C and D"))
        assertTrue(Discography.credits(t("x", "Miles Davis", null), "miles davis"))
        assertTrue(Discography.credits(t("x", "Other", null, albumArtist = "Miles Davis"), "miles davis"))
        assertTrue(Discography.credits(t("x", "Other", null, featuring = "Coltrane, Miles Davis"), "miles davis"))
        assertFalse(Discography.credits(t("x", "Miles Davis Quintet", null), "miles davis"))
    }

    @Test
    fun `placeholder artists are never linkable`() {
        assertTrue(Discography.isLinkable("Miles Davis"))
        assertFalse(Discography.isLinkable("Unknown Artist"))
        assertFalse(Discography.isLinkable("Various Artists"))
        assertFalse(Discography.isLinkable("  "))
        assertFalse(Discography.isLinkable(null))
    }

    @Test
    fun `build sorts releases into albums, EPs, singles, appears-on and loose tracks`() {
        val tracks = buildList {
            repeat(8) { add(t("LP $it", "Miles Davis", "Kind of Blue", albumArtist = "Miles Davis", year = 1959, trackNumber = it + 1)) }
            repeat(7) { add(t("LP2 $it", "Miles Davis", "Bitches Brew", year = 1970)) }
            repeat(4) { add(t("EP $it", "Miles Davis", "Four")) }
            add(t("So What", "Miles Davis", "So What - Single"))
            add(t("Comp A", "Miles Davis", "Jazz Hits"))
            add(t("Comp B", "Herbie Hancock", "Jazz Hits"))
            add(t("Comp C", "Thelonious Monk", "Jazz Hits"))
            add(t("Guest", "Herbie Hancock", "Empyrean", featuring = "Miles Davis"))
            add(t("Host", "Herbie Hancock", "Empyrean"))
            add(t("Loose", "Miles Davis", null))
            add(t("Blank", "Miles Davis", "  "))
            add(t("Nope", "Herbie Hancock", "Head Hunters"))
        }
        val d = Discography.build("miles davis", tracks)

        assertEquals(listOf("Bitches Brew", "Kind of Blue"), d.albums.map { it.albumName })
        assertEquals(listOf("Four"), d.eps.map { it.albumName })
        assertEquals(listOf("So What - Single"), d.singles.map { it.albumName })
        assertEquals(listOf("Empyrean", "Jazz Hits"), d.appearsOn.map { it.albumName })
        assertEquals(listOf("Loose", "Blank"), d.looseTracks.map { it.title })
        assertEquals(8 + 7 + 4 + 1 + 1 + 1 + 2, d.tracks.size)
        // A release is the whole album, not just the artist's tracks on it.
        assertEquals(3, d.appearsOn.first { it.albumName == "Jazz Hits" }.tracks.size)

        val order = Discography.playOrder(d, "Miles Davis").map { it.title }
        assertEquals(d.tracks.size, order.size)
        assertEquals("LP2 0", order.first())
        assertFalse("Comp B" in order)
        assertEquals("Blank", order.last())
    }

    @Test
    fun `build is empty for an unknown artist`() {
        val d = Discography.build("Nobody", listOf(t("x", "Somebody", "A")))
        assertTrue(d.isEmpty)
        assertTrue(d.albums.isEmpty() && d.eps.isEmpty() && d.singles.isEmpty() && d.appearsOn.isEmpty())
    }
}

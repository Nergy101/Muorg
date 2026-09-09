package nl.muorg.android.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import nl.muorg.android.data.api.schema.CatalogTrack as WireTrack
import nl.muorg.android.data.api.schema.LibraryStats as WireStats
import nl.muorg.android.data.api.schema.Playlist as WirePlaylist

/**
 * `WireMapping.kt` is the one place the generated wire types meet the app's own
 * models. A field added, renamed or retyped on the server lands here first, so
 * these lock down the conversion — particularly the Long-to-Int narrowing,
 * which is deliberate and easy to change by accident.
 */
class WireMappingTest {

    private fun wireTrack(
        id: Long = 1,
        durationSecs: Long? = 180,
        playCount: Long = 0,
        rating: Long? = null,
    ) = WireTrack(
        format = "mp3",
        hasCover = true,
        id = id,
        mtimeSecs = 1_700_000_000,
        path = "/music/a.mp3",
        playCount = playCount,
        rootId = 2,
        album = "Campfire",
        albumArtist = "Boards",
        artist = "Boards",
        discNumber = 1,
        durationSecs = durationSecs,
        featuring = null,
        genre = "Electronic",
        lastPlayedAt = 1_700_000_500,
        rating = rating,
        title = "Roygbiv",
        trackNumber = 3,
        year = 1998,
    )

    @Test
    fun `carries every track field across`() {
        val t = wireTrack().toDomain()
        assertEquals(1, t.id)
        assertEquals(2, t.rootId)
        assertEquals("/music/a.mp3", t.path)
        assertEquals("Roygbiv", t.title)
        assertEquals("Boards", t.artist)
        assertEquals("Campfire", t.album)
        assertEquals("Boards", t.albumArtist)
        assertEquals("Electronic", t.genre)
        assertEquals(1998, t.year)
        assertEquals(3, t.trackNumber)
        assertEquals(1, t.discNumber)
        assertEquals(180.0, t.durationSecs!!, 0.001)
        assertEquals("mp3", t.format)
        assertEquals(1_700_000_000L, t.mtimeSecs)
        assertEquals(true, t.hasCover)
        assertEquals(1_700_000_500L, t.lastPlayedAt)
    }

    @Test
    fun `keeps nulls null rather than defaulting them`() {
        val t = wireTrack(durationSecs = null, rating = null).toDomain()
        assertNull(t.durationSecs)
        assertNull(t.rating)
        // Local paths are app state; the server never supplies them.
        assertNull(t.localFilePath)
        assertNull(t.localCoverPath)
    }

    @Test
    fun `narrows ids to Int`() {
        // Deliberate: the server's ids are i64, the app and its Room entities
        // are Int. Documented in WireMapping.kt; this is the guard on it.
        assertEquals(2_000_000_000, wireTrack(id = 2_000_000_000L).toDomain().id)
    }

    @Test
    fun `maps a list of tracks`() {
        val tracks = listOf(wireTrack(id = 1), wireTrack(id = 2)).toDomain()
        assertEquals(listOf(1, 2), tracks.map { it.id })
    }

    @Test
    fun `maps a playlist`() {
        val p = WirePlaylist(id = 5, name = "Mine", trackCount = 12, icon = "🎧", smartRules = null)
            .toDomain()
        assertEquals(5, p.id)
        assertEquals("Mine", p.name)
        assertEquals(12, p.trackCount)
        assertEquals("🎧", p.icon)
        assertNull(p.smartRules)
    }

    @Test
    fun `keeps smart rules so the caller can pick the right tracks endpoint`() {
        // A smart playlist must go through /smart/{id}/tracks; losing this
        // field makes every smart playlist read as empty.
        val p = WirePlaylist(id = 5, name = "S", trackCount = 0, smartRules = "[]").toDomain()
        assertEquals("[]", p.smartRules)
    }

    @Test
    fun `maps stats`() {
        val s = WireStats(albumCount = 3, artistCount = 2, totalDurationSecs = 999, trackCount = 40)
            .toDomain()
        assertEquals(40, s.trackCount)
        assertEquals(3, s.albumCount)
        assertEquals(2, s.artistCount)
        assertEquals(999L, s.totalDurationSecs)
    }

    @Test
    fun `maps an edit form to the wire patch body`() {
        val wire = MetadataUpdateRequest(
            title = "T",
            artist = "A",
            album = "Al",
            albumArtist = "AA",
            year = 2001,
            genre = "G",
            trackNumber = 4,
            discNumber = 2,
            backupBeforeWrite = true,
        ).toWire()

        assertEquals("T", wire.title)
        assertEquals("A", wire.artist)
        assertEquals("Al", wire.album)
        assertEquals("AA", wire.albumArtist)
        assertEquals(2001, wire.year)
        assertEquals("G", wire.genre)
        assertEquals(4, wire.trackNumber)
        assertEquals(2, wire.discNumber)
        assertEquals(true, wire.backupBeforeWrite)
    }

    @Test
    fun `an empty edit form maps to an all-null body`() {
        val wire = MetadataUpdateRequest().toWire()
        assertNull(wire.title)
        assertNull(wire.year)
        assertEquals(false, wire.backupBeforeWrite)
    }

    // ── Response unwrapping ────────────────────────────────────────────────

    @Test
    fun `bodyOrThrow returns the body on success`() {
        assertEquals("x", Response.success("x").bodyOrThrow("GET /x"))
    }

    @Test
    fun `bodyOrThrow throws with the status on a failure`() {
        val response = Response.error<String>(
            404,
            "".toResponseBody("application/json".toMediaType()),
        )
        val error = assertThrows(IllegalStateException::class.java) {
            response.bodyOrThrow("GET /api/tracks/9")
        }
        assertTrue(error.message!!.contains("404"))
        assertTrue(error.message!!.contains("GET /api/tracks/9"))
    }

    @Test
    fun `bodyOrThrow throws on a 2xx with no body`() {
        // A successful status with a missing body would otherwise NPE later.
        val error = assertThrows(IllegalStateException::class.java) {
            Response.success<String>(null).bodyOrThrow("GET /x")
        }
        assertTrue(error.message!!.contains("GET /x"))
    }

    @Test
    fun `orThrow accepts a bodyless success and rejects a failure`() {
        Response.success(Unit).orThrow("POST /x")
        assertThrows(IllegalStateException::class.java) {
            Response.error<Unit>(500, "".toResponseBody(null)).orThrow("POST /x")
        }
    }
}

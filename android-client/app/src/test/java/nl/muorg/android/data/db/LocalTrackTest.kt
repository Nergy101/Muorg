package nl.muorg.android.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * On-device tracks are surfaced to the UI as [nl.muorg.android.data.api.CatalogTrack]
 * so one list can hold both local files and server rows. That only works
 * because of a convention encoded in `toCatalogTrack`: local tracks take the
 * *negative* of their Room id and a `rootId` of -1.
 *
 * If a local track ever produced a positive id it would collide with a server
 * track — playback, queueing and playlist membership all key on that id, so the
 * wrong file would play.
 */
class LocalTrackTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun localTrack(id: Int = 7, album: String? = "Campfire") = LocalTrack(
        id = id,
        path = "/storage/music/a.mp3",
        contentUri = "content://com.android.externalstorage/document/primary%3AMusic%2Fa.mp3",
        title = "Roygbiv",
        artist = "Boards",
        album = album,
        albumArtist = "Boards",
        year = 1998,
        genre = "Electronic",
        trackNumber = 3,
        discNumber = 1,
        durationSecs = 180.0,
        format = "mp3",
        fileSize = 4_000_000,
        addedAt = 1_700_000_000_000,
    )

    @Test
    fun `a local track gets a negative id so it cannot collide with a server track`() {
        assertEquals(-7, localTrack(id = 7).toCatalogTrack().id)
    }

    @Test
    fun `a local track is marked as belonging to no server root`() {
        assertEquals(-1, localTrack().toCatalogTrack().rootId)
    }

    @Test
    fun `the content uri becomes the local file path used for playback`() {
        // PlayerController, CastManager and LocalCastServer all branch on this
        // being non-null to play from disk instead of streaming.
        val track = localTrack().toCatalogTrack()
        assertEquals(
            "content://com.android.externalstorage/document/primary%3AMusic%2Fa.mp3",
            track.localFilePath,
        )
    }

    @Test
    fun `tags carry across unchanged`() {
        val track = localTrack().toCatalogTrack()
        assertEquals("Roygbiv", track.title)
        assertEquals("Boards", track.artist)
        assertEquals("Campfire", track.album)
        assertEquals("Boards", track.albumArtist)
        assertEquals(1998, track.year)
        assertEquals("Electronic", track.genre)
        assertEquals(3, track.trackNumber)
        assertEquals(1, track.discNumber)
        assertEquals(180.0, track.durationSecs!!, 0.001)
        assertEquals("mp3", track.format)
    }

    @Test
    fun `addedAt milliseconds become mtime seconds`() {
        assertEquals(1_700_000_000L, localTrack().toCatalogTrack().mtimeSecs)
    }

    @Test
    fun `server-only fields are left empty`() {
        val track = localTrack().toCatalogTrack()
        assertNull(track.rating)
        assertNull(track.lastPlayedAt)
        assertEquals(0, track.playCount)
    }

    @Test
    fun `with no cache directory there is no cover`() {
        val track = localTrack().toCatalogTrack(cacheDir = null)
        assertEquals(false, track.hasCover)
        assertNull(track.localCoverPath)
    }

    @Test
    fun `a cached album art file is found and reported as a cover`() {
        val cache = temp.newFolder()
        val artDir = java.io.File(cache, "album_art").apply { mkdirs() }
        java.io.File(artDir, "${"Campfire".hashCode()}.jpg").writeBytes(byteArrayOf(1, 2, 3))

        val track = localTrack(album = "Campfire").toCatalogTrack(cacheDir = cache)
        assertTrue(track.hasCover)
        assertNotNull(track.localCoverPath)
        assertTrue(track.localCoverPath!!.endsWith(".jpg"))
    }

    @Test
    fun `a missing art file is not reported as a cover`() {
        val cache = temp.newFolder()
        val track = localTrack(album = "Campfire").toCatalogTrack(cacheDir = cache)
        assertEquals(false, track.hasCover)
        assertNull(track.localCoverPath)
    }

    @Test
    fun `a track with no album never looks for art`() {
        val cache = temp.newFolder()
        val track = localTrack(album = null).toCatalogTrack(cacheDir = cache)
        assertEquals(false, track.hasCover)
        assertNull(track.localCoverPath)
    }

    @Test
    fun `display helpers fall back when tags are missing`() {
        val bare = localTrack().copy(title = null, artist = null, album = null, albumArtist = null)
            .toCatalogTrack()
        assertEquals("a", bare.displayTitle)
        assertEquals("Unknown Artist", bare.displayArtist)
        assertEquals("Unknown Album", bare.displayAlbum)
    }

    @Test
    fun `duration formats as minutes and seconds`() {
        assertEquals("3:00", localTrack().toCatalogTrack().formattedDuration())
        val unknown = localTrack().copy(durationSecs = null).toCatalogTrack()
        assertEquals("--:--", unknown.formattedDuration())
    }
}

package nl.muorg.android.ui.widget

import nl.muorg.android.data.api.CatalogTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recently-played stash is what lets a widget row tap resolve a track id
 * back to a full [CatalogTrack] while the app process may be cold — it has to
 * round-trip the whole model, local-file fields included.
 */
class RecentTracksStashTest {

    private fun serverTrack(id: Int) = CatalogTrack(
        id = id,
        path = "/music/album/song-$id.mp3",
        rootId = 1,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumArtist = "Album Artist",
        durationSecs = 123.4,
        format = "mp3",
        mtimeSecs = 1_700_000_000L,
        hasCover = true,
        playCount = 3,
        lastPlayedAt = 1_700_000_100L,
    )

    private fun localTrack(id: Int) = CatalogTrack(
        id = id,
        path = "/local/track-$id.flac",
        rootId = -1,
        title = null,
        format = "flac",
        mtimeSecs = 1_700_000_000L,
        hasCover = true,
        playCount = 0,
        localFilePath = "/local/track-$id.flac",
        localCoverPath = "/local/track-$id.jpg",
    )

    @Test
    fun `round trip preserves server and local tracks`() {
        val tracks = listOf(serverTrack(42), localTrack(-7))
        assertEquals(tracks, RecentTracksStash.decode(RecentTracksStash.encode(tracks)))
    }

    @Test
    fun `encoded form is json with the wire field names`() {
        val encoded = RecentTracksStash.encode(listOf(serverTrack(1)))
        assertTrue(encoded.contains("\"root_id\":1"))
        assertTrue(encoded.contains("\"has_cover\":true"))
        assertTrue(encoded.contains("\"play_count\":3"))
    }

    @Test
    fun `decode tolerates fields added by a newer server`() {
        val raw = """
            [{"id":5,"path":"/m/x.mp3","root_id":1,"format":"mp3","mtime_secs":0,
              "has_cover":false,"play_count":1,"some_future_field":"x"}]
        """.trimIndent()
        val decoded = RecentTracksStash.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(5, decoded[0].id)
    }

    @Test
    fun `empty list round trips`() {
        assertEquals(emptyList<CatalogTrack>(), RecentTracksStash.decode(RecentTracksStash.encode(emptyList())))
    }
}

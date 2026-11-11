package nl.muorg.android.ui.easteregg

import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DustReportBuilderTest {

    @Test
    fun `empty library falls back to the dust-only line`() {
        val stats = DustReportBuilder.build(DustInput())

        assertEquals(1, stats.size)
        assertEquals("Nothing to report.", stats[0].stat)
    }

    @Test
    fun `templates with missing or zero data are skipped`() {
        val stats = DustReportBuilder.build(DustInput(trackCount = 3, totalDurationSecs = 600))

        assertEquals(1, stats.size)
        assertTrue(stats[0].stat.startsWith("You've got 3 tracks."))
    }

    @Test
    fun `singular and plural track counts`() {
        val one = DustReportBuilder.build(DustInput(trackCount = 1, totalDurationSecs = 60))
        val two = DustReportBuilder.build(DustInput(trackCount = 2, totalDurationSecs = 120))

        assertTrue(one[0].stat.contains("1 track."))
        assertTrue(two[0].stat.contains("2 tracks."))
    }

    @Test
    fun `never-played line pluralises has and have`() {
        val one = DustReportBuilder.build(DustInput(neverPlayedCount = 1))
        val two = DustReportBuilder.build(DustInput(neverPlayedCount = 2))

        assertTrue(one[0].stat.contains("1 track has never been played."))
        assertTrue(two[0].stat.contains("2 tracks have never been played."))
    }

    @Test
    fun `duration text uses days and hours`() {
        // 1 day + 1 hour + 1 minute
        val stats = DustReportBuilder.build(DustInput(trackCount = 1, totalDurationSecs = 86400 + 3600 + 60))

        assertTrue(stats[0].quip.contains("1 day and 1 hour"))
    }

    @Test
    fun `inputFrom maps catalog tracks and playlists onto dust input`() {
        val now = 1_700_000_000L
        val tracks = listOf(
            track(id = 1, artist = "A", playCount = 5, lastPlayedAt = now - 100, mtime = now - 100),
            track(id = 2, artist = "A", playCount = 3, lastPlayedAt = now - 200, mtime = now - 100),
            track(id = 3, artist = "B", playCount = 0, lastPlayedAt = null, mtime = now - 10_000_000),
        )
        val playlists = listOf(Playlist(id = 1, name = "Big", trackCount = 42))

        val input = DustReportBuilder.inputFrom(tracks, playlists, nowSecs = now)

        assertEquals(3, input.trackCount)
        assertEquals(1, input.neverPlayedCount)
        assertEquals("A", input.topArtistThisMonth)
        assertEquals(2, input.addedThisWeek)
        assertEquals(listOf("Big" to 42), input.playlists)
    }

    private fun track(
        id: Int,
        artist: String,
        playCount: Int,
        lastPlayedAt: Long?,
        mtime: Long,
    ) = CatalogTrack(
        id = id,
        path = "/music/$id.mp3",
        rootId = 1,
        artist = artist,
        durationSecs = 180.0,
        format = "mp3",
        mtimeSecs = mtime,
        hasCover = false,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
    )
}

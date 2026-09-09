package nl.muorg.android.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.api.schema.MuorgApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import nl.muorg.android.data.api.schema.Playlist as WirePlaylist

/**
 * Which endpoint a playlist's tracks come from.
 *
 * A smart playlist stores *rules*, not rows. The plain
 * `/api/playlists/{id}/tracks` reads the join table, which for a smart playlist
 * is empty — so asking the wrong endpoint returns no songs, with no error. That
 * shipped: smart playlists opened and stayed blank.
 */
class PlaylistRepositoryTest {

    private fun wirePlaylist(id: Long, smartRules: String? = null) = WirePlaylist(
        id = id,
        name = "P$id",
        trackCount = 3,
        icon = null,
        smartRules = smartRules,
    )

    private fun domain(id: Int, smartRules: String? = null) = Playlist(
        id = id,
        name = "P$id",
        icon = null,
        trackCount = 3,
        smartRules = smartRules,
    )

    private fun repo(configure: MuorgApi.() -> Unit): PlaylistRepository {
        val api = mockk<MuorgApi>()
        api.configure()
        return PlaylistRepository(api)
    }

    private val RULES = """[{"field":"genre","op":"contains","value":"metal"}]"""

    @Test
    fun `a regular playlist reads the join table`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.getPlaylistTracks(1L) } returns Response.success(listOf(10L, 11L))
        val repository = PlaylistRepository(api)

        assertEquals(listOf(10, 11), repository.getTracksFor(domain(1)).getOrThrow())
        coVerify(exactly = 0) { api.getSmartPlaylistTracks(any()) }
    }

    @Test
    fun `a smart playlist evaluates its rules instead`() = runTest {
        // The regression: this used to go to getPlaylistTracks and come back
        // empty, so the playlist opened with no songs.
        val api = mockk<MuorgApi>()
        coEvery { api.getSmartPlaylistTracks(2L) } returns Response.success(listOf(20L, 21L))
        val repository = PlaylistRepository(api)

        assertEquals(listOf(20, 21), repository.getTracksFor(domain(2, RULES)).getOrThrow())
        coVerify(exactly = 0) { api.getPlaylistTracks(any()) }
    }

    @Test
    fun `an empty rule set still counts as smart`() = runTest {
        // `smart_rules` of "[]" is a smart playlist that currently matches
        // nothing — not a regular playlist. Testing truthiness rather than
        // nullness would send it to the wrong endpoint.
        val api = mockk<MuorgApi>()
        coEvery { api.getSmartPlaylistTracks(3L) } returns Response.success(emptyList())
        val repository = PlaylistRepository(api)

        assertTrue(repository.getTracksFor(domain(3, "[]")).getOrThrow().isEmpty())
        coVerify(exactly = 0) { api.getPlaylistTracks(any()) }
    }

    @Test
    fun `by id, a smart playlist is looked up first and then evaluated`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.listPlaylists() } returns
            Response.success(listOf(wirePlaylist(1), wirePlaylist(2, RULES)))
        coEvery { api.getSmartPlaylistTracks(2L) } returns Response.success(listOf(20L))
        val repository = PlaylistRepository(api)

        assertEquals(listOf(20), repository.getTracksForId(2).getOrThrow())
        coVerify(exactly = 0) { api.getPlaylistTracks(any()) }
    }

    @Test
    fun `by id, a regular playlist reads the join table`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.listPlaylists() } returns
            Response.success(listOf(wirePlaylist(1), wirePlaylist(2, RULES)))
        coEvery { api.getPlaylistTracks(1L) } returns Response.success(listOf(10L))
        val repository = PlaylistRepository(api)

        assertEquals(listOf(10), repository.getTracksForId(1).getOrThrow())
        coVerify(exactly = 0) { api.getSmartPlaylistTracks(any()) }
    }

    @Test
    fun `by id, an unknown playlist fails rather than reporting no tracks`() = runTest {
        // Silently returning an empty list here is how the original bug looked
        // to a user: a playlist that opens and shows nothing.
        val api = mockk<MuorgApi>()
        coEvery { api.listPlaylists() } returns Response.success(listOf(wirePlaylist(1)))
        val repository = PlaylistRepository(api)

        assertTrue(repository.getTracksForId(99).isFailure)
    }

    @Test
    fun `a failed smart lookup is reported, not swallowed`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.getSmartPlaylistTracks(2L) } returns
            Response.error(500, "".toResponseBody("application/json".toMediaType()))
        val repository = PlaylistRepository(api)

        assertTrue(repository.getTracksFor(domain(2, RULES)).isFailure)
    }

    @Test
    fun `a smart playlist refuses tracks instead of silently dropping them`() = runTest {
        // The server accepts this write and puts the row in the join table,
        // which is never read for a smart playlist — so the track vanishes with
        // no error anywhere. Refusing is the only way the user finds out.
        val api = mockk<MuorgApi>()
        val repository = PlaylistRepository(api)

        val result = repository.addTracks(domain(2, RULES), listOf(10, 11))

        assertTrue(result.exceptionOrNull() is SmartPlaylistNotEditable)
        coVerify(exactly = 0) { api.addPlaylistTracks(any(), any()) }
    }

    @Test
    fun `an empty rule set is still a smart playlist and still refuses`() = runTest {
        val api = mockk<MuorgApi>()
        val repository = PlaylistRepository(api)

        assertTrue(repository.addTracks(domain(3, "[]"), listOf(10)).isFailure)
        coVerify(exactly = 0) { api.addPlaylistTracks(any(), any()) }
    }

    @Test
    fun `a manual playlist still takes tracks`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.addPlaylistTracks(1L, any()) } returns
            Response.success(nl.muorg.android.data.api.schema.OkResponse(ok = true))
        val repository = PlaylistRepository(api)

        assertTrue(repository.addTracks(domain(1), listOf(10, 11)).isSuccess)
        coVerify { api.addPlaylistTracks(1L, any()) }
    }

    @Test
    fun `handed a playlist, no lookup request is made`() = runTest {
        // The overload exists to skip the round trip; if it fell through to the
        // id-based path it would work but cost a request per add.
        val api = mockk<MuorgApi>()
        coEvery { api.addPlaylistTracks(1L, any()) } returns
            Response.success(nl.muorg.android.data.api.schema.OkResponse(ok = true))
        val repository = PlaylistRepository(api)

        repository.addTracks(domain(1), listOf(10)).getOrThrow()
        coVerify(exactly = 0) { api.listPlaylists() }
    }

    @Test
    fun `by id, a smart playlist is looked up and refused`() = runTest {
        // Every caller that only has an id goes through here, so the guard has
        // to hold without the caller knowing the playlist is smart.
        val api = mockk<MuorgApi>()
        coEvery { api.listPlaylists() } returns
            Response.success(listOf(wirePlaylist(1), wirePlaylist(2, RULES)))
        val repository = PlaylistRepository(api)

        val result = repository.addTracks(2, listOf(10))

        assertTrue(result.exceptionOrNull() is SmartPlaylistNotEditable)
        coVerify(exactly = 0) { api.addPlaylistTracks(any(), any()) }
    }

    @Test
    fun `by id, a manual playlist still takes tracks`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.listPlaylists() } returns
            Response.success(listOf(wirePlaylist(1), wirePlaylist(2, RULES)))
        coEvery { api.addPlaylistTracks(1L, any()) } returns
            Response.success(nl.muorg.android.data.api.schema.OkResponse(ok = true))
        val repository = PlaylistRepository(api)

        assertTrue(repository.addTracks(1, listOf(10)).isSuccess)
        coVerify { api.addPlaylistTracks(1L, any()) }
    }

    @Test
    fun `by id, an unknown playlist fails rather than writing blind`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.listPlaylists() } returns Response.success(listOf(wirePlaylist(1)))
        val repository = PlaylistRepository(api)

        assertTrue(repository.addTracks(99, listOf(10)).isFailure)
        coVerify(exactly = 0) { api.addPlaylistTracks(any(), any()) }
    }

    @Test
    fun `the refusal names the playlist so the message is useful`() = runTest {
        val api = mockk<MuorgApi>()
        val repository = PlaylistRepository(api)

        val message = repository.addTracks(domain(2, RULES), listOf(10)).exceptionOrNull()?.message
        assertTrue(message.orEmpty().contains("P2"))
    }

    @Test
    fun `creating a playlist with an icon patches it afterwards`() = runTest {
        // The create endpoint takes a name only.
        val api = mockk<MuorgApi>()
        coEvery { api.createPlaylist(any()) } returns Response.success(wirePlaylist(5))
        coEvery { api.updatePlaylist(5L, any()) } returns
            Response.success(nl.muorg.android.data.api.schema.OkResponse(ok = true))
        val repository = PlaylistRepository(api)

        val created = repository.createPlaylist("Mine", "🎧").getOrThrow()
        assertEquals("🎧", created.icon)
        coVerify { api.updatePlaylist(5L, any()) }
    }

    @Test
    fun `creating without an icon does not patch`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.createPlaylist(any()) } returns Response.success(wirePlaylist(5))
        val repository = PlaylistRepository(api)

        repository.createPlaylist("Mine", "").getOrThrow()
        coVerify(exactly = 0) { api.updatePlaylist(any(), any()) }
    }
}

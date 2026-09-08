package nl.muorg.android.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.muorg.android.data.api.schema.CatalogTrack as WireTrack
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.api.toDomain
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * The paging loop in [LibraryRepository].
 *
 * `/api/tracks` caps at 500 rows and reports the real size in `X-Total-Count`,
 * so a single call returns the alphabetical head of the library while
 * `/api/stats` still reports every track. That mismatch shipped as a bug
 * (`b531618`): the counts were right and most of the library was missing.
 */
class LibraryRepositoryPagingTest {

    private fun wireTrack(id: Long) = WireTrack(
        format = "mp3",
        hasCover = false,
        id = id,
        mtimeSecs = 0,
        path = "/m/$id.mp3",
        playCount = 0,
        rootId = 1,
        title = "t$id",
    )

    /** A page response carrying the `X-Total-Count` header the loop reads. */
    private fun pageOf(ids: LongRange, total: Int): Response<List<WireTrack>> =
        Response.success(
            ids.map(::wireTrack),
            Headers.headersOf("X-Total-Count", total.toString()),
        )

    private fun repo(configure: MuorgApi.() -> Unit): LibraryRepository {
        val api = mockk<MuorgApi>()
        api.configure()
        return LibraryRepository(api)
    }

    @Test
    fun `pages through a catalog larger than one request`() = runTest {
        val repository = repo {
            coEvery { getTracks(0, 500) } returns pageOf(0L until 500L, 1200)
            coEvery { getTracks(500, 500) } returns pageOf(500L until 1000L, 1200)
            coEvery { getTracks(1000, 500) } returns pageOf(1000L until 1200L, 1200)
        }

        val tracks = repository.getAllTracks().getOrThrow()
        assertEquals(1200, tracks.size)
        assertEquals(0, tracks.first().id)
        assertEquals(1199, tracks.last().id)
    }

    @Test
    fun `makes a single request when the library fits in one page`() = runTest {
        val repository = repo {
            coEvery { getTracks(0, 500) } returns pageOf(0L until 42L, 42)
        }
        assertEquals(42, repository.getAllTracks().getOrThrow().size)
    }

    @Test
    fun `de-duplicates a track that straddles a page boundary`() = runTest {
        // The server orders by (artist, album, track_number, title), which has
        // ties, so a row can be returned on two consecutive pages.
        val repository = repo {
            coEvery { getTracks(0, 500) } returns pageOf(0L until 500L, 999)
            coEvery { getTracks(500, 500) } returns
                Response.success(
                    (499L until 998L).map(::wireTrack),
                    Headers.headersOf("X-Total-Count", "999"),
                )
        }

        val tracks = repository.getAllTracks().getOrThrow()
        assertEquals(tracks.map { it.id }.distinct().size, tracks.size)
    }

    @Test
    fun `stops on a short page even if the header disagrees`() = runTest {
        // A concurrent rescan can shrink the library mid-load; a short page is
        // the ground truth and must end the loop rather than spin.
        val repository = repo {
            coEvery { getTracks(0, 500) } returns pageOf(0L until 10L, 5000)
        }
        assertEquals(10, repository.getAllTracks().getOrThrow().size)
    }

    @Test
    fun `reports a failed page as a failed Result`() = runTest {
        val repository = repo {
            coEvery { getTracks(0, 500) } returns
                Response.error(500, "".toResponseBody("application/json".toMediaType()))
        }

        val result = repository.getAllTracks()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `hands each page to the callback so a screen can render as it loads`() = runTest {
        val repository = repo {
            coEvery { getTracks(0, 500) } returns pageOf(0L until 500L, 1000)
            coEvery { getTracks(500, 500) } returns pageOf(500L until 1000L, 1000)
        }

        val progress = mutableListOf<Int>()
        repository.getAllTracks(onPage = { progress.add(it.size) }).getOrThrow()

        // The first callback must arrive before the whole catalog is in hand.
        assertTrue(progress.isNotEmpty())
        assertEquals(500, progress.first())
    }

    @Test
    fun `serves a second read from cache instead of re-paging`() = runTest {
        var calls = 0
        val api = mockk<MuorgApi>()
        coEvery { api.getTracks(0, 500) } answers {
            calls++
            pageOf(0L until 10L, 10)
        }
        val repository = LibraryRepository(api)

        repository.getAllTracks().getOrThrow()
        repository.getAllTracks().getOrThrow()

        assertEquals(1, calls)
    }

    @Test
    fun `groups tracks into albums by album artist`() {
        val repository = LibraryRepository(mockk())
        val tracks = listOf(
            wireTrack(1).copy(album = "Campfire", albumArtist = "Boards", trackNumber = 2),
            wireTrack(2).copy(album = "Campfire", albumArtist = "Boards", trackNumber = 1),
            wireTrack(3).copy(album = "Other", albumArtist = "Someone"),
        ).map { it.toDomain() }

        val albums = repository.buildAlbumGroups(tracks)
        assertEquals(2, albums.size)
        val campfire = albums.first { it.albumName == "Campfire" }
        assertEquals("Boards", campfire.artist)
        assertEquals(2, campfire.trackCount)
    }
}

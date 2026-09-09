package nl.muorg.android.ui

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.ui.screen.playlist.PlaylistAlbumsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Opening a playlist.
 *
 * The regression this exists for: a smart playlist opened and showed nothing.
 * `loadRemote` asked `getPlaylistTracks`, the join-table endpoint, which is
 * empty for a playlist whose membership is computed from rules — so the screen
 * rendered successfully with zero tracks and no error to explain it.
 *
 * The repository always had `getTracksFor` to pick the right endpoint. Testing
 * only the repository would have missed this entirely: the bug was the caller.
 */
class PlaylistAlbumsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val SMART_RULES = """[{"field":"genre","op":"contains","value":"metal"}]"""

    private fun track(id: Int) = CatalogTrack(
        id = id,
        path = "/m/$id.mp3",
        rootId = 1,
        title = "t$id",
        artist = "A",
        album = "Album",
        albumArtist = "A",
        featuring = null,
        year = 2020,
        genre = "Metal",
        trackNumber = id,
        discNumber = 1,
        durationSecs = 180.0,
        format = "mp3",
        mtimeSecs = 0,
        hasCover = false,
        rating = null,
        playCount = 0,
        lastPlayedAt = null,
    )

    private fun playlist(id: Int, smartRules: String? = null) =
        Playlist(id = id, name = "P$id", icon = null, trackCount = 2, smartRules = smartRules)

    private fun viewModel(
        playlistId: Int,
        playlists: List<Playlist>,
        library: LibraryRepository,
        playlistRepo: PlaylistRepository,
    ): PlaylistAlbumsViewModel {
        val preferences = mockk<AppPreferences>(relaxed = true)
        every { preferences.musicMode } returns flowOf("remote")
        every { preferences.playlistViewStyle } returns flowOf("tracks")
        coEvery { playlistRepo.getPlaylists() } returns Result.success(playlists)
        return PlaylistAlbumsViewModel(
            SavedStateHandle(mapOf("playlistId" to playlistId)),
            preferences,
            library,
            mockk<LocalLibraryRepository>(relaxed = true),
            playlistRepo,
        )
    }

    private fun libraryWith(vararg ids: Int): LibraryRepository {
        val library = mockk<LibraryRepository>()
        val tracks = ids.map(::track)
        coEvery { library.getAllTracks(any()) } returns Result.success(tracks)
        every { library.buildAlbumGroups(any()) } returns emptyList()
        return library
    }

    @Test
    fun `a smart playlist shows the tracks its rules match`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(match { it.smartRules != null }) } returns
            Result.success(listOf(1, 2))

        val vm = viewModel(7, listOf(playlist(7, SMART_RULES)), libraryWith(1, 2, 3), playlistRepo)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), vm.uiState.value.allTracks.map { it.id })
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `a smart playlist never asks the join-table endpoint`() = runTest {
        // The exact call that produced an empty screen.
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(1))

        viewModel(7, listOf(playlist(7, SMART_RULES)), libraryWith(1), playlistRepo)
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepo.getPlaylistTracks(any()) }
    }

    @Test
    fun `a regular playlist still loads its tracks`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(match { it.smartRules == null }) } returns
            Result.success(listOf(2, 3))

        val vm = viewModel(4, listOf(playlist(4)), libraryWith(1, 2, 3), playlistRepo)
        advanceUntilIdle()

        assertEquals(listOf(2, 3), vm.uiState.value.allTracks.map { it.id })
    }

    @Test
    fun `the playlist itself is exposed so the screen can title itself`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(1))

        val vm = viewModel(7, listOf(playlist(7, SMART_RULES)), libraryWith(1), playlistRepo)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.playlist)
        assertEquals(7, vm.uiState.value.playlist?.id)
    }

    @Test
    fun `an id the playlist list does not contain falls back to the plain endpoint`() = runTest {
        // Nothing to read `smart_rules` from, so the join table is the only
        // thing that can be asked — but it must still be asked, not skipped.
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getPlaylistTracks(99) } returns Result.success(listOf(3))

        val vm = viewModel(99, listOf(playlist(1)), libraryWith(1, 2, 3), playlistRepo)
        advanceUntilIdle()

        assertEquals(listOf(3), vm.uiState.value.allTracks.map { it.id })
    }

    @Test
    fun `a failed track lookup surfaces an error rather than an empty playlist`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.failure(RuntimeException("boom"))

        val vm = viewModel(7, listOf(playlist(7, SMART_RULES)), libraryWith(1), playlistRepo)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isLoading)
    }
}

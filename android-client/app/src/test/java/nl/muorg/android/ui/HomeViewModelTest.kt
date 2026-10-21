package nl.muorg.android.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.MixRepository
import nl.muorg.android.ui.screen.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Home page in local mode.
 *
 * The regression this exists for: after setup picked a local folder, Home
 * stayed empty — nothing had scanned the folder yet, and only the Library
 * tab triggered a scan. Home now runs the first scan itself and reports
 * progress until it completes.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

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

    private fun album(name: String) = AlbumGroup(
        albumName = name,
        artist = "A",
        year = 2020,
        trackCount = 2,
        coverTrackId = -1,
    )

    private fun serverRepo(tracks: List<CatalogTrack> = emptyList()): LibraryRepository {
        val repository = mockk<LibraryRepository>()
        coEvery { repository.getAllTracks(any()) } returns Result.success(tracks)
        coEvery { repository.getTopPlayHistory(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.getRecentPlayHistory(any()) } returns Result.success(emptyList())
        every { repository.buildAlbumGroups(any()) } returns emptyList()
        return repository
    }

    private fun viewModel(
        mode: String = "local",
        folderUris: Set<String> = setOf("content://music"),
        localRepo: LocalLibraryRepository,
        repository: LibraryRepository = serverRepo(),
    ): HomeViewModel {
        val preferences = mockk<AppPreferences>(relaxed = true)
        every { preferences.musicMode } returns flowOf(mode)
        every { preferences.offlineMode } returns flowOf(false)
        every { preferences.localFolderUris } returns flowOf(folderUris)
        return HomeViewModel(repository, localRepo, preferences, MixRepository())
    }

    @Test
    fun `local mode with an empty library scans it and fills the shelves`() = runTest {
        var scanned = emptyList<CatalogTrack>()
        val localRepo = mockk<LocalLibraryRepository>()
        coEvery { localRepo.getAllTracks() } answers { scanned }
        coEvery { localRepo.buildAlbumGroups() } returns listOf(album("Album"))
        coEvery { localRepo.scanIfEmpty(any(), any()) } answers {
            val onProgress = secondArg<(Int, Int) -> Unit>()
            onProgress(1, 2)
            onProgress(2, 2)
            scanned = listOf(track(1), track(2))
            true
        }

        val vm = viewModel(localRepo = localRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isScanning)
        assertTrue(state.scanCompleted)
        assertFalse(state.loading)
        assertEquals(2, state.scanProgress)
        assertEquals(2, state.scanTotal)
        assertEquals(listOf("Album"), state.recommended.map { it.albumName })
        coVerify(exactly = 1) { localRepo.scanIfEmpty(any(), any()) }
    }

    @Test
    fun `local mode with a populated library does not scan`() = runTest {
        val localRepo = mockk<LocalLibraryRepository>()
        coEvery { localRepo.getAllTracks() } returns listOf(track(1), track(2))
        coEvery { localRepo.buildAlbumGroups() } returns listOf(album("Album"))

        val vm = viewModel(localRepo = localRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isScanning)
        assertFalse(vm.uiState.value.scanCompleted)
        assertEquals(listOf("Album"), vm.uiState.value.recommended.map { it.albumName })
        coVerify(exactly = 0) { localRepo.scanIfEmpty(any(), any()) }
    }

    @Test
    fun `local mode without folders does not scan`() = runTest {
        val localRepo = mockk<LocalLibraryRepository>()
        coEvery { localRepo.getAllTracks() } returns emptyList()
        coEvery { localRepo.buildAlbumGroups() } returns emptyList()

        val vm = viewModel(folderUris = emptySet(), localRepo = localRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isScanning)
        coVerify(exactly = 0) { localRepo.scanIfEmpty(any(), any()) }
    }

    @Test
    fun `remote mode does not scan the local library`() = runTest {
        val localRepo = mockk<LocalLibraryRepository>(relaxed = true)

        val vm = viewModel(
            mode = "remote",
            localRepo = localRepo,
            repository = serverRepo(listOf(track(1))),
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isScanning)
        coVerify(exactly = 0) { localRepo.scanIfEmpty(any(), any()) }
    }

    @Test
    fun `a reload after the first scan does not scan again`() = runTest {
        var scanned = emptyList<CatalogTrack>()
        val localRepo = mockk<LocalLibraryRepository>()
        coEvery { localRepo.getAllTracks() } answers { scanned }
        coEvery { localRepo.buildAlbumGroups() } returns listOf(album("Album"))
        coEvery { localRepo.scanIfEmpty(any(), any()) } answers {
            scanned = listOf(track(1), track(2))
            true
        }

        val vm = viewModel(localRepo = localRepo)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        coVerify(exactly = 1) { localRepo.scanIfEmpty(any(), any()) }
    }
}

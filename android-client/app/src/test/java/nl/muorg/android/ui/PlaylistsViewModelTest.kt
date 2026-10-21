package nl.muorg.android.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.DownloadProgress
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.OfflineDownloadManager
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.data.repository.PlaylistTrackEntry
import nl.muorg.android.ui.screen.playlists.PlaylistsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The playlist list under offline mode.
 *
 * The regression this exists for: with offline mode on, the screen asked the
 * server for playlists — a call the mode exists to make impossible — and the
 * list came back empty. The list must come from the download store instead.
 */
class PlaylistsViewModelTest {

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

    private fun playlist(id: Int) =
        Playlist(id = id, name = "P$id", icon = null, trackCount = 2, smartRules = null)

    private fun viewModel(
        offlineMode: Boolean,
        playlistRepo: PlaylistRepository,
        library: LibraryRepository,
        downloads: OfflineDownloadManager,
        downloadedIds: Set<Int> = emptySet(),
        mode: String = "remote",
        localRepo: LocalLibraryRepository = mockk(relaxed = true),
    ): PlaylistsViewModel {
        val preferences = mockk<AppPreferences>(relaxed = true)
        every { preferences.musicMode } returns flowOf(mode)
        every { preferences.offlineMode } returns flowOf(offlineMode)
        every { preferences.pinnedPlaylists } returns flowOf(emptySet())
        every { downloads.downloadProgress } returns MutableStateFlow(DownloadProgress())
        every { downloads.observeDownloadedPlaylistIds() } returns flowOf(downloadedIds)
        return PlaylistsViewModel(
            playlistRepo,
            localRepo,
            preferences,
            library,
            downloads,
        )
    }

    @Test
    fun `offline mode lists the downloaded playlists without asking the server`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val library = mockk<LibraryRepository>(relaxed = true)
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)
        coEvery { downloads.getDownloadedPlaylists() } returns listOf(playlist(7), playlist(8))
        coEvery { downloads.getDownloadedTracks(any()) } returns listOf(track(1), track(2))

        val vm = viewModel(offlineMode = true, playlistRepo, library, downloads)
        advanceUntilIdle()

        assertEquals(listOf(7, 8), vm.uiState.value.playlists.map { it.id })
        assertTrue(vm.uiState.value.isOfflineMode)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { playlistRepo.getPlaylists() }
        coVerify(exactly = 0) { library.getAllTracks(any()) }
    }

    @Test
    fun `online mode lists the server playlists without reading the download store`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getPlaylists() } returns Result.success(listOf(playlist(3)))
        coEvery { playlistRepo.getPins() } returns Result.success(emptyList())
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(1))
        val library = mockk<LibraryRepository>()
        coEvery { library.getAllTracks(any()) } returns Result.success(listOf(track(1)))
        every { library.buildAlbumGroups(any()) } returns emptyList()
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)

        val vm = viewModel(offlineMode = false, playlistRepo, library, downloads)
        advanceUntilIdle()

        assertEquals(listOf(3), vm.uiState.value.playlists.map { it.id })
        assertFalse(vm.uiState.value.isOfflineMode)
        coVerify(exactly = 0) { downloads.getDownloadedPlaylists() }
    }

    @Test
    fun `tapping download on a downloaded playlist asks before removing`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getPlaylists() } returns Result.success(listOf(playlist(7)))
        coEvery { playlistRepo.getPins() } returns Result.success(emptyList())
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(1))
        val library = mockk<LibraryRepository>()
        coEvery { library.getAllTracks(any()) } returns Result.success(listOf(track(1)))
        every { library.buildAlbumGroups(any()) } returns emptyList()
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)
        coEvery { downloads.getDownloadedPlaylists() } returns listOf(playlist(7))
        coEvery { downloads.getDownloadedTracks(any()) } returns listOf(track(1))

        val vm = viewModel(offlineMode = false, playlistRepo, library, downloads, downloadedIds = setOf(7))
        advanceUntilIdle()

        vm.onDownloadClicked(playlist(7))
        assertTrue(vm.uiState.value.showRemoveDownloadDialog)
        coVerify(exactly = 0) { downloads.removePlaylist(any()) }

        vm.confirmRemoveDownload()
        assertFalse(vm.uiState.value.showRemoveDownloadDialog)
        coVerify(exactly = 1) { downloads.removePlaylist(7) }
    }

    @Test
    fun `tapping download on a fresh playlist starts a download`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getPlaylists() } returns Result.success(listOf(playlist(7)))
        coEvery { playlistRepo.getPins() } returns Result.success(emptyList())
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(1, 2))
        val library = mockk<LibraryRepository>()
        coEvery { library.getAllTracks(any()) } returns Result.success(listOf(track(1), track(2)))
        every { library.buildAlbumGroups(any()) } returns emptyList()
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)

        val vm = viewModel(offlineMode = false, playlistRepo, library, downloads)
        advanceUntilIdle()

        vm.onDownloadClicked(playlist(7))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showRemoveDownloadDialog)
        coVerify(exactly = 1) { downloads.downloadPlaylist(playlist(7), any()) }
    }

    @Test
    fun `local mode never downloads — the playlist is already on the device`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val library = mockk<LibraryRepository>(relaxed = true)
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)
        val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
        coEvery { localRepo.getPlaylists() } returns listOf(playlist(7))

        val vm = viewModel(
            offlineMode = false,
            playlistRepo,
            library,
            downloads,
            mode = "local",
            localRepo = localRepo,
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isServerMode)
        vm.onDownloadClicked(playlist(7))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showRemoveDownloadDialog)
        coVerify(exactly = 0) { downloads.downloadPlaylist(any(), any()) }
        coVerify(exactly = 0) { playlistRepo.getTracksFor(any()) }
    }

    @Test
    fun `local mode builds mosaic covers from the device's own tracks`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val library = mockk<LibraryRepository>(relaxed = true)
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)
        val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
        val localTrack = track(-1).copy(hasCover = true, localCoverPath = "/cache/album_art/1.jpg")
        coEvery { localRepo.getPlaylists() } returns listOf(playlist(7))
        coEvery { localRepo.getAllTracks() } returns listOf(localTrack)
        coEvery { localRepo.getPlaylistContents(7) } returns listOf(
            PlaylistTrackEntry(
                entryId = 1,
                filePath = localTrack.path,
                storedTitle = null,
                storedArtist = null,
                track = localTrack,
            )
        )

        val vm = viewModel(
            offlineMode = false,
            playlistRepo,
            library,
            downloads,
            mode = "local",
            localRepo = localRepo,
        )
        advanceUntilIdle()

        assertEquals(listOf(-1), vm.uiState.value.covers[7])
        assertEquals("/cache/album_art/1.jpg", vm.uiState.value.coverPaths[-1])
        coVerify(exactly = 0) { library.getAllTracks(any()) }
    }
}

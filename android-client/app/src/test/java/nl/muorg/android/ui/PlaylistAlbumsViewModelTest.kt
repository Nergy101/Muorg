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
import nl.muorg.android.data.repository.OfflineDownloadManager
import nl.muorg.android.data.repository.PlaylistEntry
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.data.repository.PlaylistTrackEntry
import nl.muorg.android.ui.UiMessenger
import nl.muorg.android.ui.screen.playlist.PlaylistAlbumsViewModel
import nl.muorg.android.ui.screen.playlist.PlaylistSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        offlineMode: Boolean = false,
        offlineDownloadManager: OfflineDownloadManager = mockk(relaxed = true),
        mode: String = "remote",
        localRepo: LocalLibraryRepository = mockk(relaxed = true),
        persistedSort: String = "PLAYLIST_ORDER",
        entries: List<PlaylistEntry> = emptyList(),
    ): PlaylistAlbumsViewModel {
        val preferences = mockk<AppPreferences>(relaxed = true)
        every { preferences.musicMode } returns flowOf(mode)
        every { preferences.playlistViewStyle } returns flowOf("tracks")
        every { preferences.offlineMode } returns flowOf(offlineMode)
        every { preferences.playlistSort } returns flowOf(persistedSort)
        every { preferences.sortAscending } returns flowOf(true)
        coEvery { playlistRepo.getPlaylists() } returns Result.success(playlists)
        coEvery { playlistRepo.getPlaylistEntries(any()) } returns Result.success(entries)
        return PlaylistAlbumsViewModel(
            SavedStateHandle(mapOf("playlistId" to playlistId)),
            preferences,
            library,
            localRepo,
            playlistRepo,
            offlineDownloadManager,
            UiMessenger(),
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

    @Test
    fun `offline mode loads the playlist from the download store, not the server`() = runTest {
        // The regression this exists for: with offline mode on, the screen
        // asked the server for a playlist the server cannot be asked about,
        // and the playlist showed nothing at all.
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val library = mockk<LibraryRepository>(relaxed = true)
        every { library.buildAlbumGroups(any()) } returns emptyList()
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)
        coEvery { downloads.getDownloadedPlaylist(7) } returns playlist(7)
        coEvery { downloads.getDownloadedTracks(7) } returns listOf(track(1), track(2))
        coEvery { downloads.getDownloadedPlaylists() } returns listOf(playlist(7))

        val vm = viewModel(7, emptyList(), library, playlistRepo, offlineMode = true, offlineDownloadManager = downloads)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), vm.uiState.value.allTracks.map { it.id })
        assertEquals(7, vm.uiState.value.playlist?.id)
        assertEquals(false, vm.uiState.value.isLoading)
        assertEquals(null, vm.uiState.value.error)
        coVerify(exactly = 0) { playlistRepo.getPlaylists() }
        coVerify(exactly = 0) { library.getAllTracks(any()) }
    }

    @Test
    fun `offline mode reports a playlist that was never downloaded`() = runTest {
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)
        coEvery { downloads.getDownloadedPlaylist(7) } returns null

        val vm = viewModel(7, emptyList(), mockk(relaxed = true), mockk(relaxed = true),
            offlineMode = true, offlineDownloadManager = downloads)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.allTracks.isEmpty())
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `sorted tracks default to the playlist order`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(3, 1, 2))

        val vm = viewModel(4, listOf(playlist(4)), libraryWith(1, 2, 3), playlistRepo)
        advanceUntilIdle()

        assertEquals(listOf(3, 1, 2), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `sorting by title orders the tracks`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(3, 1, 2))

        val vm = viewModel(4, listOf(playlist(4)), libraryWith(1, 2, 3), playlistRepo)
        advanceUntilIdle()
        vm.setSortMode(PlaylistSortMode.BY_TITLE)
        advanceUntilIdle()

        // The fixture's titles are t1, t2, t3.
        assertEquals(listOf(1, 2, 3), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `the direction switch reverses the order`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(3, 1, 2))

        val vm = viewModel(4, listOf(playlist(4)), libraryWith(1, 2, 3), playlistRepo)
        advanceUntilIdle()
        vm.setSortMode(PlaylistSortMode.BY_TITLE)
        vm.toggleSortDirection()
        advanceUntilIdle()

        assertEquals(listOf(3, 2, 1), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `sorting by added date uses the entry ids`() = runTest {
        // Playlist order is 3, 1, 2; the entries say track 1 was added first.
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(3, 1, 2))

        val vm = viewModel(
            4, listOf(playlist(4)), libraryWith(1, 2, 3), playlistRepo,
            entries = listOf(
                PlaylistEntry(entryId = 30, trackId = 3),
                PlaylistEntry(entryId = 10, trackId = 1),
                PlaylistEntry(entryId = 20, trackId = 2),
            ),
        )
        advanceUntilIdle()
        vm.setSortMode(PlaylistSortMode.BY_ADDED)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `sorting by artist orders by artist then title`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(1, 2, 3))
        val library = mockk<LibraryRepository>()
        coEvery { library.getAllTracks(any()) } returns Result.success(
            listOf(
                track(1).copy(artist = "B", title = "b1"),
                track(2).copy(artist = "A", title = "a2"),
                track(3).copy(artist = "A", title = "a1"),
            )
        )
        every { library.buildAlbumGroups(any()) } returns emptyList()

        val vm = viewModel(4, listOf(playlist(4)), library, playlistRepo)
        advanceUntilIdle()
        vm.setSortMode(PlaylistSortMode.BY_ARTIST)
        advanceUntilIdle()

        assertEquals(listOf(3, 2, 1), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `a smart playlist has no added-date sort`() = runTest {
        // Membership comes from the rules, so there are no entries and the
        // added-date sort falls back to the server's natural order.
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(2, 1))

        val vm = viewModel(7, listOf(playlist(7, SMART_RULES)), libraryWith(1, 2), playlistRepo)
        advanceUntilIdle()
        vm.setSortMode(PlaylistSortMode.BY_ADDED)
        advanceUntilIdle()

        assertEquals(listOf(2, 1), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `an added-date sort left over from another playlist is dropped for a smart playlist`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>()
        coEvery { playlistRepo.getTracksFor(any()) } returns Result.success(listOf(1, 2))

        val vm = viewModel(
            7, listOf(playlist(7, SMART_RULES)), libraryWith(1, 2), playlistRepo,
            persistedSort = "BY_ADDED",
        )
        advanceUntilIdle()

        assertEquals(PlaylistSortMode.PLAYLIST_ORDER, vm.uiState.value.sortMode)
        assertEquals(listOf(1, 2), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `local mode sorts by the entry's insertion order`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val library = mockk<LibraryRepository>(relaxed = true)
        val localRepo = mockk<LocalLibraryRepository>()
        coEvery { localRepo.getPlaylists() } returns listOf(playlist(7))
        coEvery { localRepo.getPlaylistContents(7) } returns listOf(
            PlaylistTrackEntry(entryId = 3, filePath = "/m/1.mp3", storedTitle = null, storedArtist = null, track = track(1)),
            PlaylistTrackEntry(entryId = 1, filePath = "/m/2.mp3", storedTitle = null, storedArtist = null, track = track(2)),
            PlaylistTrackEntry(entryId = 2, filePath = "/m/3.mp3", storedTitle = null, storedArtist = null, track = track(3)),
        )
        every { localRepo.buildAlbumGroupsForTracks(any()) } returns emptyList()
        coEvery { localRepo.getPlaylistMembershipByPath() } returns emptyMap()

        val vm = viewModel(7, listOf(playlist(7)), library, playlistRepo, mode = "local", localRepo = localRepo)
        advanceUntilIdle()
        vm.setSortMode(PlaylistSortMode.BY_ADDED)
        advanceUntilIdle()

        assertEquals(listOf(2, 3, 1), vm.uiState.value.sortedTracks.map { it.id })
    }

    @Test
    fun `offline mode sorts by download time`() = runTest {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val library = mockk<LibraryRepository>(relaxed = true)
        every { library.buildAlbumGroups(any()) } returns emptyList()
        val downloads = mockk<OfflineDownloadManager>(relaxed = true)
        coEvery { downloads.getDownloadedPlaylist(7) } returns playlist(7)
        coEvery { downloads.getDownloadedTracks(7) } returns listOf(track(1), track(2), track(3))
        coEvery { downloads.getDownloadedAddedAts(7) } returns mapOf(1 to 30L, 2 to 10L, 3 to 20L)
        coEvery { downloads.getDownloadedPlaylists() } returns listOf(playlist(7))

        val vm = viewModel(7, emptyList(), library, playlistRepo,
            offlineMode = true, offlineDownloadManager = downloads)
        advanceUntilIdle()
        vm.setSortMode(PlaylistSortMode.BY_ADDED)
        advanceUntilIdle()

        assertEquals(listOf(2, 3, 1), vm.uiState.value.sortedTracks.map { it.id })
    }
}

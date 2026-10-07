package nl.muorg.android.ui

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.ui.screen.reports.ReportsViewModel
import nl.muorg.android.util.LibraryReports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun track() = CatalogTrack(
        id = 1,
        path = "/music/no-title.mp3",
        rootId = 1,
        artist = "Artist",
        album = "Album",
        format = "mp3",
        mtimeSecs = 1_700_000_000L,
        hasCover = true,
        playCount = 0,
    )

    @Test
    fun `failed refresh retains previous reports and exposes the refresh error`() = runTest {
        val repository = mockk<LibraryRepository>()
        coEvery { repository.getAllTracks(any()) } returnsMany listOf(
            Result.success(listOf(track())),
            Result.failure(IllegalStateException("offline")),
        )
        val preferences = mockk<AppPreferences>(relaxed = true)
        coEvery { preferences.musicMode } returns flowOf("remote")
        val viewModel = ReportsViewModel(repository, mockk<LocalLibraryRepository>(), preferences)

        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.tracks.size)
        assertEquals(1, viewModel.uiState.value.counts[LibraryReports.Kind.MISSING_METADATA])

        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.tracks.size)
        assertEquals(1, viewModel.uiState.value.counts[LibraryReports.Kind.MISSING_METADATA])
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.isNotBlank())
        assertEquals(false, viewModel.uiState.value.isLoading)
    }
}

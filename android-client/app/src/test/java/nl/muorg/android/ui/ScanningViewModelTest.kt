package nl.muorg.android.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.ui.screen.scanning.ScanKind
import nl.muorg.android.ui.screen.scanning.ScanPhase
import nl.muorg.android.ui.screen.scanning.ScanningViewModel
import nl.muorg.android.ui.screen.scanning.ScanningViewModel.Companion.DONE_LINGER_MS
import nl.muorg.android.ui.screen.scanning.ScanningViewModel.Companion.MIN_VISIBLE_MS
import nl.muorg.android.ui.screen.scanning.ScanningViewModel.Companion.POLL_MS
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * The first-run scanning screen. What matters: it never flashes past for a
 * source that is already ready, it does not finish while the server still says
 * `Scanning`, and the user can always leave.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanningViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun prefs(mode: String): AppPreferences {
        val preferences = mockk<AppPreferences>(relaxed = true)
        every { preferences.musicMode } returns flowOf(mode)
        every { preferences.localFolderUris } returns flowOf(setOf("content://tree/music"))
        return preferences
    }

    private fun health(body: String): Response<okhttp3.ResponseBody> = Response.success(body.toResponseBody())

    /** `health()` answers with each scripted reply in turn (the last one repeats); a Throwable is thrown. */
    private fun serverApi(vararg replies: Any): MuorgApi {
        val api = mockk<MuorgApi>()
        var call = 0
        coEvery { api.health() } answers {
            val next = replies[call.coerceAtMost(replies.lastIndex)]
            call++
            if (next is Throwable) throw next
            @Suppress("UNCHECKED_CAST")
            next as Response<okhttp3.ResponseBody>
        }
        return api
    }

    @Test
    fun `local scan reports progress and stays up for the minimum time even when instant`() = runTest {
        val repo = mockk<LocalLibraryRepository>()
        coEvery { repo.scanAndSave(any(), any()) } coAnswers {
            secondArg<(Int, Int) -> Unit>().invoke(5, 10)
            10
        }
        val vm = ScanningViewModel(prefs("local"), mockk(relaxed = true), repo)

        runCurrent()
        assertEquals(ScanKind.LOCAL, vm.uiState.value.kind)
        assertEquals(5, vm.uiState.value.scanned)
        assertEquals(10, vm.uiState.value.total)
        assertEquals(ScanPhase.WORKING, vm.uiState.value.phase)

        advanceTimeBy(MIN_VISIBLE_MS - 1)
        assertEquals(ScanPhase.WORKING, vm.uiState.value.phase)

        advanceTimeBy(2)
        assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
        assertEquals(10, vm.uiState.value.trackCount)
        assertFalse(vm.uiState.value.finished)

        advanceTimeBy(DONE_LINGER_MS + 1)
        assertTrue(vm.uiState.value.finished)
    }

    @Test
    fun `a failed local scan is reported and still lets the user through`() = runTest {
        val repo = mockk<LocalLibraryRepository>()
        coEvery { repo.scanAndSave(any(), any()) } throws IOException("unreadable")
        val vm = ScanningViewModel(prefs("local"), mockk(relaxed = true), repo)

        advanceTimeBy(MIN_VISIBLE_MS + 1)
        assertEquals(ScanPhase.FAILED, vm.uiState.value.phase)

        advanceTimeBy(DONE_LINGER_MS + 1)
        assertTrue(vm.uiState.value.finished)
    }

    @Test
    fun `server mode keeps waiting while the server reports Scanning`() = runTest {
        val api = serverApi(health("Scanning"), health("Scanning"), health("Healthy"))
        val vm = ScanningViewModel(prefs("remote"), api, mockk(relaxed = true))

        runCurrent()
        assertEquals(ScanKind.REMOTE, vm.uiState.value.kind)
        assertTrue(vm.uiState.value.serverBusy)

        // Past the minimum display time, but the server has still not said Healthy.
        advanceTimeBy(MIN_VISIBLE_MS + 500)
        assertEquals(ScanPhase.WORKING, vm.uiState.value.phase)

        advanceTimeBy(POLL_MS * 2)
        assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
        coVerify(exactly = 3) { api.health() }
    }

    @Test
    fun `a server that is already healthy still waits out the minimum display time`() = runTest {
        val api = serverApi(health("Healthy"))
        val vm = ScanningViewModel(prefs("remote"), api, mockk(relaxed = true))

        runCurrent()
        assertEquals(ScanPhase.WORKING, vm.uiState.value.phase)

        advanceTimeBy(MIN_VISIBLE_MS + 1)
        assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
        coVerify(exactly = 1) { api.health() }
    }

    @Test
    fun `an unreachable server is retried, not treated as ready`() = runTest {
        val api = serverApi(IOException("no route"), health("Healthy"))
        val vm = ScanningViewModel(prefs("remote"), api, mockk(relaxed = true))

        runCurrent()
        assertTrue(vm.uiState.value.serverUnreachable)

        advanceTimeBy(POLL_MS + MIN_VISIBLE_MS)
        assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
    }

    @Test
    fun `skipping leaves at once and stops polling the server`() = runTest {
        val api = serverApi(health("Scanning"))
        val vm = ScanningViewModel(prefs("remote"), api, mockk(relaxed = true))
        runCurrent()

        vm.skip()
        assertTrue(vm.uiState.value.finished)

        advanceTimeBy(POLL_MS * 5)
        coVerify(exactly = 1) { api.health() }
    }
}

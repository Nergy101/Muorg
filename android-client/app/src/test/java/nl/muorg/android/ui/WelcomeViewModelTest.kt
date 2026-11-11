package nl.muorg.android.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.ui.screen.welcome.WelcomeDestination
import nl.muorg.android.ui.screen.welcome.WelcomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `choosing the server replaces a previously chosen local source`() = runTest {
        // A user who tried "Local Library", backed out and picked the server
        // must not be left in local mode: the app would keep reading the
        // device's (empty) folders and ignore the server they just set up.
        val preferences = mockk<AppPreferences>(relaxed = true)
        coEvery { preferences.musicMode } returns flowOf("local")
        coEvery { preferences.localFolderUris } returns flowOf(emptySet())
        val vm = WelcomeViewModel(preferences, mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()

        vm.chooseRemote()
        advanceUntilIdle()

        coVerify { preferences.setMusicMode("remote") }
        assertEquals(WelcomeDestination.RemoteSetup, vm.uiState.value.destination)
    }
}

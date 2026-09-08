package nl.muorg.android.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.ui.screen.connect.ConnectEvent
import nl.muorg.android.ui.screen.connect.ConnectViewModel
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

/**
 * The connect flow is the first thing a new user meets, and it has one property
 * that matters more than the rest: credentials are saved *before* the health
 * probe, because the OkHttp interceptor reads them from preferences — so a
 * failed probe has to put them back.
 */
class ConnectViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun prefs(url: String = "", key: String = ""): AppPreferences {
        val preferences = mockk<AppPreferences>(relaxed = true)
        coEvery { preferences.serverUrl } returns flowOf(url)
        coEvery { preferences.apiKey } returns flowOf(key)
        return preferences
    }

    private fun viewModel(
        preferences: AppPreferences = prefs(),
        api: MuorgApi = mockk(relaxed = true),
    ) = ConnectViewModel(preferences, api)

    @Test
    fun `pre-fills the form from saved credentials`() = runTest {
        val vm = viewModel(prefs(url = "http://nas.local:7700", key = "abc"))
        advanceUntilIdle()

        assertEquals("http://nas.local:7700", vm.uiState.value.serverUrl)
        assertEquals("abc", vm.uiState.value.apiKey)
    }

    @Test
    fun `refuses to connect with an empty field, without calling the server`() = runTest {
        val api = mockk<MuorgApi>()
        val vm = viewModel(api = api)
        advanceUntilIdle()

        vm.connect()
        advanceUntilIdle()

        assertEquals("Please enter both server URL and API key", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { api.health() }
    }

    @Test
    fun `typing clears a previous error`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.connect()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)

        vm.onServerUrlChange("http://nas.local")
        assertNull(vm.uiState.value.error)

        vm.connect()
        advanceUntilIdle()
        vm.onApiKeyChange("k")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `saves credentials then emits Connected when the server answers`() = runTest {
        val preferences = prefs()
        val api = mockk<MuorgApi>()
        coEvery { api.health() } returns Response.success("Healthy".toResponseBody())

        val vm = viewModel(preferences, api)
        advanceUntilIdle()

        // `events` is a SharedFlow with no replay, so the collector has to be
        // running before connect() emits — subscribing afterwards waits forever.
        val seen = mutableListOf<ConnectEvent>()
        val collector = launch { vm.events.collect { seen.add(it) } }

        vm.onServerUrlChange("http://nas.local:7700")
        vm.onApiKeyChange("secret")
        vm.connect()
        advanceUntilIdle()

        coVerify { preferences.saveCredentials("http://nas.local:7700", "secret") }
        coVerify(exactly = 0) { preferences.clearCredentials() }
        assertEquals(listOf(ConnectEvent.Connected), seen)
        collector.cancel()
    }

    @Test
    fun `emits no Connected event when the server rejects the credentials`() = runTest {
        val api = mockk<MuorgApi>()
        coEvery { api.health() } returns Response.error(401, "".toResponseBody())

        val vm = viewModel(api = api)
        advanceUntilIdle()

        val seen = mutableListOf<ConnectEvent>()
        val collector = launch { vm.events.collect { seen.add(it) } }

        vm.onServerUrlChange("http://nas.local:7700")
        vm.onApiKeyChange("wrong")
        vm.connect()
        advanceUntilIdle()

        assertTrue(seen.isEmpty())
        collector.cancel()
    }

    @Test
    fun `reverts the saved credentials when the server rejects them`() = runTest {
        // Leaving a bad key in preferences would 401 every later request with
        // no obvious cause.
        val preferences = prefs()
        val api = mockk<MuorgApi>()
        coEvery { api.health() } returns Response.error(401, "".toResponseBody())

        val vm = viewModel(preferences, api)
        advanceUntilIdle()
        vm.onServerUrlChange("http://nas.local:7700")
        vm.onApiKeyChange("wrong")

        vm.connect()
        advanceUntilIdle()

        coVerify { preferences.clearCredentials() }
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.error!!.isNotBlank())
    }

    @Test
    fun `surfaces a network failure as a readable error`() = runTest {
        val preferences = prefs()
        val api = mockk<MuorgApi>()
        coEvery { api.health() } throws java.net.UnknownHostException("nas.local")

        val vm = viewModel(preferences, api)
        advanceUntilIdle()
        vm.onServerUrlChange("http://nas.local:7700")
        vm.onApiKeyChange("k")

        vm.connect()
        advanceUntilIdle()

        assertEquals("nas.local", vm.uiState.value.error)
        coVerify { preferences.clearCredentials() }
    }
}

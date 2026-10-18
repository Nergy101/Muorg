package nl.muorg.android.data.api

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import nl.muorg.android.data.preferences.AppPreferences
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * The hard divide: with offline mode on, no request leaves the device.
 *
 * The interceptor sits on the one OkHttpClient that backs Retrofit, the
 * stream downloader and Coil, so blocking here blocks every network path in
 * the app. The exception is an IOException so every caller's existing error
 * handling applies unchanged.
 */
class OfflineInterceptorTest {

    private fun client(offlineMode: Boolean) = buildAuthOkHttpClient(
        mockk<AppPreferences> {
            every { offlineModeState } returns MutableStateFlow(offlineMode)
            every { apiKeyState } returns MutableStateFlow("")
        }
    )

    @Test
    fun `offline mode blocks the call before it leaves the device`() {
        val request = Request.Builder().url("http://127.0.0.1:9/api/stats").build()
        try {
            client(offlineMode = true).newCall(request).execute()
            fail("expected OfflineException")
        } catch (e: OfflineException) {
            // The block: no socket was ever opened.
        }
    }

    @Test
    fun `online mode lets the call through`() {
        // Nothing listens on port 9, so a call that gets past the offline
        // check fails as a connection error — proof the interceptor did not
        // stop it.
        val request = Request.Builder().url("http://127.0.0.1:9/api/stats").build()
        try {
            client(offlineMode = false).newCall(request).execute()
            fail("expected a connection failure")
        } catch (e: IOException) {
            assertFalse(e is OfflineException)
        }
    }
}

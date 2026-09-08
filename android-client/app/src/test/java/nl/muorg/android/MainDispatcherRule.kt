package nl.muorg.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.coroutines.CoroutineContext

/**
 * Swaps `Dispatchers.Main` for a test dispatcher.
 *
 * `viewModelScope` runs on `Dispatchers.Main`, which does not exist off-device —
 * without this every ViewModel test fails at construction with "Module with the
 * Main dispatcher had failed to initialize".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: CoroutineContext = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher as kotlinx.coroutines.CoroutineDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

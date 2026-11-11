package nl.muorg.android.ui.easteregg

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.muorg.android.ui.OrcMood
import nl.muorg.android.ui.UiMessenger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-only unlock for the Janitor easter egg. Five taps on the Muorg
 * logo (Home or Settings, any mix) within one app process wake the Janitor.
 * The state lives in memory only — never persisted — so a cold start puts
 * the Janitor back to sleep and resets the counter.
 */
@Singleton
class JanitorEasterEgg @Inject constructor(private val uiMessenger: UiMessenger) {
    private var taps = 0
    private val _awake = MutableStateFlow(false)
    val awake: StateFlow<Boolean> = _awake.asStateFlow()

    fun onLogoTapped() {
        if (_awake.value) return
        taps++
        if (taps >= TAP_COUNT) {
            _awake.value = true
            uiMessenger.show("Awoken the Janitor", OrcMood.Happy)
        }
    }

    private companion object {
        const val TAP_COUNT = 5
    }
}

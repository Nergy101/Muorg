package nl.muorg.android

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_VOLUME_DOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nl.muorg.android.cast.CastManager
import nl.muorg.android.player.PlayerController
import nl.muorg.android.ui.navigation.NavGraph
import nl.muorg.android.ui.widget.RecentTracksWidget
import nl.muorg.android.ui.widget.WidgetStateStore
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var castManager: CastManager
    @Inject lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation =
            if (resources.configuration.smallestScreenWidthDp < 600) {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            NavGraph()
        }
        if (savedInstanceState == null) handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    /**
     * A Recently Played row was tapped: queue the whole recent list as the
     * widget last showed it, starting at the tapped track. A track missing
     * from the stash (stale list) just leaves the app open.
     */
    private fun handleWidgetIntent(intent: Intent) {
        if (intent.action != RecentTracksWidget.ACTION_PLAY_TRACK) return
        // Consumed: a later redelivery of this intent must not restart playback.
        intent.action = Intent.ACTION_MAIN
        val trackId = intent.getIntExtra(RecentTracksWidget.EXTRA_TRACK_ID, 0)
        val recent = WidgetStateStore.loadRecentTracks(this)
        val track = recent.firstOrNull { it.id == trackId } ?: return
        lifecycleScope.launch { playerController.playTrack(track, recent) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (castManager.isCasting.value) {
            when (keyCode) {
                KEYCODE_VOLUME_UP -> { castManager.adjustVolume(0.05); return true }
                KEYCODE_VOLUME_DOWN -> { castManager.adjustVolume(-0.05); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

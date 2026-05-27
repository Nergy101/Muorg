package nl.muorg.android

import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_VOLUME_DOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import nl.muorg.android.cast.CastManager
import nl.muorg.android.ui.navigation.NavGraph
import nl.muorg.android.ui.theme.MuorgTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var castManager: CastManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MuorgTheme {
                NavGraph()
            }
        }
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

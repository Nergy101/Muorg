package nl.muorg.android.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Album/playlist grid columns by available width. 2 on every phone, more on tablets. */
@Composable
fun rememberGridColumns(): Int {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp < 600 -> 2
            widthDp < 840 -> 3
            widthDp < 1200 -> 4
            else -> 5
        }
    }
}

/** Widest the bottom island is allowed to grow before it is centred instead. */
val IslandMaxWidth: Dp = 640.dp

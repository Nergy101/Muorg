package nl.muorg.android.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import nl.muorg.android.ui.component.BlurredCoverArt
import nl.muorg.android.ui.theme.Stone900

/**
 * The blurred album art behind the player.
 *
 * The cover is shown edge to edge at its own brightness. It is never dimmed as
 * a whole: light covers used to sit under a 70% black scrim (and the album
 * look faded the sides into Stone900), which turned a white sleeve into a
 * grey-black wash. The player's text and controls are white, so the only
 * darkening left is a soft gradient exactly where they sit.
 *
 * Split out of [PlayerScreen], which was a single 574-line composable.
 */
@Composable
fun PlayerBackdrop(
    displayedCoverUrl: Any?,
    imageLoader: ImageLoader,
    /** Art-fullscreen mode: sharper cover, and only the bottom bar needs a floor. */
    sharp: Boolean = false,
) {
    if (displayedCoverUrl == null) return
    Box(modifier = Modifier.fillMaxSize().background(Stone900)) {
        BlurredCoverArt(coverUrl = displayedCoverUrl, imageLoader = imageLoader, sharp = sharp)

        // Under the status bar and the cast button.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
        )

        // Under the title, seek bar and transport row (or, in art-fullscreen,
        // just the bottom bar).
        val floor = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .then(if (sharp) Modifier.height(240.dp) else Modifier.fillMaxHeight(0.5f))
        Box(
            modifier = floor.background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                ),
            ),
        )
    }
}

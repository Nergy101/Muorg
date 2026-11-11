package nl.muorg.android.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.ui.theme.Stone900

/**
 * The cover stretched and blurred across a pane, matching the web's
 * `.edge-blur-art` below lg: blur 16px at 0.9 opacity, dropping to 6px at
 * full opacity in art-fullscreen ("sharp") mode.
 */
@Composable
fun BlurredCoverArt(
    coverUrl: Any?,
    imageLoader: ImageLoader,
    sharp: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (coverUrl == null) return
    val blur = if (sharp) 6f else 16f
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                    alpha = if (sharp) 1f else 0.9f
                },
        )
    }
}

/**
 * The album screen's backdrop, matching the web album view's CoverBackdrop
 * (variant "album") in the mobile viewport: a flat #1C1917 base with the
 * cover blurred across it, faded into the base only at the left and right
 * edges — the web's sideways linear mask (transparent, black 35%, black 65%,
 * transparent), not the circular vignette. Only shown when the album
 * actually has art, so coverless albums keep the plain theme background.
 */
@Composable
fun CoverBackdrop(
    coverUrl: Any?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Stone900)) {
        BlurredCoverArt(coverUrl, imageLoader)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Stone900,
                    0.35f to Stone900.copy(alpha = 0f),
                    0.65f to Stone900.copy(alpha = 0f),
                    1.0f to Stone900,
                ),
            )
        }
    }
}

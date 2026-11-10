package nl.muorg.android.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 *
 * [edgeFadeColor], when set, fades the art's edges radially into that colour
 * (closest-side, fully opaque centre up to 30%, transparent at the edges) —
 * the web's album view keeps this vignette at every viewport size.
 */
@Composable
fun BlurredCoverArt(
    coverUrl: Any?,
    imageLoader: ImageLoader,
    sharp: Boolean = false,
    edgeFadeColor: Color? = null,
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
        if (edgeFadeColor != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        0.3f to edgeFadeColor.copy(alpha = 0f),
                        1f to edgeFadeColor,
                        center = center,
                        radius = size.minDimension / 2f,
                    ),
                )
            }
        }
    }
}

/**
 * The album screen's backdrop, matching the web album view's CoverBackdrop
 * (variant "album") in the mobile viewport: a flat #1C1917 base with the
 * cover blurred across it, edges fading into the base. Only shown when the
 * album actually has art, so coverless albums keep the plain theme
 * background.
 */
@Composable
fun CoverBackdrop(
    coverUrl: Any?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Stone900)) {
        BlurredCoverArt(coverUrl, imageLoader, edgeFadeColor = Stone900)
    }
}

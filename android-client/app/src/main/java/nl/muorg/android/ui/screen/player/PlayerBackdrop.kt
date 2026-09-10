package nl.muorg.android.ui.screen.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.ui.component.DominantColorState

/**
 * The blurred album art behind the player.
 *
 * Split out of [PlayerScreen], which was a single 574-line composable.
 */
@Composable
fun PlayerBackdrop(
    displayedCoverUrl: Any?,
    dominantColor: DominantColorState,
    imageLoader: ImageLoader,
    accentColor: Color,
) {
    // ── Backdrop ──────────────────────────────────────────────────────
    if (displayedCoverUrl != null) {
        Crossfade(targetState = dominantColor.isBland, label = "bgMode") { isBland ->
            if (isBland) {
                // Bland / white / black cover: a blown-up blur of the sleeve
                // is the only thing left to pull colour from.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = displayedCoverUrl,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                renderEffect = BlurEffect(72f, 72f, TileMode.Clamp)
                                scaleX = 1.9f
                                scaleY = 1.9f
                                alpha = 0.55f
                            },
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.70f)))
            } else {
                // The web paints soft radial blobs of the cover's dominant
                // colour over near-black, rather than a flat wash — it keeps
                // the backdrop from reading as a solid colour card.
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0A0A)))
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(280f, 700f),
                            radius = 1400f,
                        )
                    )
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.38f), Color.Transparent),
                            center = Offset(900f, 1900f),
                            radius = 1200f,
                        )
                    )
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.5f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.55f),
                        )
                    )
                )
            }
        }
    }
}

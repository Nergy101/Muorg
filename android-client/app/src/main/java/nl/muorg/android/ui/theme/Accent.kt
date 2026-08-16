package nl.muorg.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * The accent pulled out of the current artwork, shared by every surface that
 * tints itself with the cover: the bottom island, the maximised player and the
 * queue's now-playing card. One helper so those three never drift apart.
 *
 * The raw dominant colour is NOT usable directly — it is regularly darker, or
 * greyer, than the surface it has to sit on — so it is pushed to a saturation
 * and lightness that clears that surface, in whichever direction is needed.
 * Near-grey artwork keeps the theme accent rather than tinting everything mud.
 *
 * @param source the artwork's dominant colour
 * @param onDark whether it will be drawn on a dark surface; defaults to the
 *   theme's own surface, but the player's backdrop is always dark regardless
 *   of theme and passes `true` explicitly.
 */
@Composable
fun artworkAccent(
    source: Color,
    onDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    fallback: Color = MaterialTheme.colorScheme.primary,
): Color {
    if (source == Color.Unspecified || source.alpha < 0.05f) return fallback
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(source.toArgb(), hsl)
    if (hsl[1] < 0.18f) return fallback
    hsl[1] = hsl[1].coerceIn(0.45f, 0.85f)
    hsl[2] = if (onDark) hsl[2].coerceIn(0.58f, 0.74f) else hsl[2].coerceIn(0.28f, 0.42f)
    return Color(ColorUtils.HSLToColor(hsl))
}

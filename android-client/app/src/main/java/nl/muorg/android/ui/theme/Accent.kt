package nl.muorg.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * The theme's own primary, before the app recolours `colorScheme.primary` with
 * the playing album's accent. Artwork with no usable colour falls back to this,
 * not to whatever album happens to be playing.
 */
val LocalBasePrimary = compositionLocalOf<Color?> { null }

/**
 * Set when "Override dynamic colors with Material You" is on: artwork accents
 * are ignored and every surface uses the theme (Material You) colour instead.
 */
val LocalOverrideArtworkAccent = compositionLocalOf { false }

/** The theme's own primary — Muorg green, or the Material You colour — never the playing album's. */
@Composable
fun themePrimary(): Color = LocalBasePrimary.current ?: MaterialTheme.colorScheme.primary

/**
 * The accent pulled out of the current artwork, shared by every surface that
 * tints itself with the cover: the bottom island, the maximised player and the
 * queue's now-playing card. One helper so those three never drift apart.
 *
 * The artwork's own hue, saturation and lightness are kept as sampled, so the
 * accent tracks the cover literally; lightness is only clamped into a wide
 * band so a near-black or near-white cover cannot produce an invisible accent.
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
    fallback: Color = LocalBasePrimary.current ?: MaterialTheme.colorScheme.primary,
): Color {
    if (LocalOverrideArtworkAccent.current) return fallback
    if (source == Color.Unspecified || source.alpha < 0.05f) return fallback
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(source.toArgb(), hsl)
    if (hsl[1] < 0.18f) return fallback
    hsl[2] = if (onDark) hsl[2].coerceIn(0.30f, 0.78f) else hsl[2].coerceIn(0.22f, 0.70f)
    return Color(ColorUtils.HSLToColor(hsl))
}

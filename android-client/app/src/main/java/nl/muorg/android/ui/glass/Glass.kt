package nl.muorg.android.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* -------------------------------------------------------------------------
 * Frosted glass, ported from the web client's `style.css` component layer.
 *
 * Each web material is a translucent fill over `backdrop-filter: blur(30-36px)
 * saturate(180-200%)`, plus a hairline border and an inner top highlight.
 *
 * Compose has no `backdrop-filter`, and it cannot be faked by sampling either:
 * capturing what is behind into a `GraphicsLayer` works, but a `RenderEffect`
 * — whether set on the layer or on the node that replays it — does not reach a
 * nested `RenderNode`, so the replayed patch comes back sharp. Verified on API
 * 36 against both `GraphicsLayer.renderEffect` and
 * `Modifier.graphicsLayer { renderEffect = … }`.
 *
 * So the bloom is reconstructed instead of sampled. At 36px blur and 200%
 * saturation nothing of the artwork survives as detail — what you actually see
 * under the web's island is a soft, over-saturated colour field pulled from the
 * covers near it. `bloom` paints exactly that: two offset radial gradients in
 * the artwork's dominant colour over the translucent fill. Same percept, one
 * draw call, and no second rasterisation of the whole screen every frame.
 * ------------------------------------------------------------------------- */

@Immutable
data class GlassSpec(
    val fill: Color,
    val border: Color,
    val highlight: Color,
    val blur: Dp,
    val saturation: Float,
    val shadow: Dp,
    val shadowColor: Color,
    /** Vertical fill ramp (the card caption scrim); `fill` is ignored when set. */
    val ramp: List<Pair<Float, Color>> = emptyList(),
    /** Fade the blurred patch in from transparent up to this fraction of its height. */
    val maskTo: Float = 0f,
    /** Hairline along the top edge only, instead of around the whole outline. */
    val borderTopOnly: Boolean = false,
)

/** The materials, by the name of the CSS class each one ports. */
enum class GlassMaterial { Glass, Strong, Deep, Scrim }

@Composable
fun glassSpec(material: GlassMaterial): GlassSpec {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    return when (material) {
        GlassMaterial.Glass -> GlassSpec(
            fill = surface.copy(alpha = 0.80f),
            border = onSurface.copy(alpha = 0.14f),
            highlight = Color.White.copy(alpha = 0.14f),
            blur = 30.dp,
            saturation = 1.8f,
            shadow = 10.dp,
            shadowColor = Color.Black.copy(alpha = 0.30f),
        )
        GlassMaterial.Strong -> GlassSpec(
            fill = surface.copy(alpha = 0.92f),
            border = onSurface.copy(alpha = 0.16f),
            highlight = Color.White.copy(alpha = 0.16f),
            blur = 30.dp,
            saturation = 1.8f,
            shadow = 10.dp,
            shadowColor = Color.Black.copy(alpha = 0.34f),
        )
        // Denser than the web's 62%. There, the fill sits on a 36px blur that
        // has already destroyed every edge behind the bar; here it is the only
        // thing separating the island from sharp album art, so it has to carry
        // that separation itself.
        GlassMaterial.Deep -> GlassSpec(
            fill = surface.copy(alpha = 0.97f),
            border = onSurface.copy(alpha = 0.16f),
            highlight = Color.White.copy(alpha = 0.18f),
            blur = 36.dp,
            saturation = 2.0f,
            shadow = 14.dp,
            shadowColor = Color.Black.copy(alpha = 0.42f),
        )
        // The caption band ramps from near-clear where it meets the artwork to
        // dense where the label sits, and the blur fades in over the same span,
        // so it dissolves into the cover instead of ending in a hard line.
        GlassMaterial.Scrim -> GlassSpec(
            fill = Color.Unspecified,
            border = onSurface.copy(alpha = 0.12f),
            highlight = Color.Transparent,
            blur = 26.dp,
            saturation = 1.6f,
            shadow = 0.dp,
            shadowColor = Color.Transparent,
            ramp = listOf(
                0f to surface.copy(alpha = 0.08f),
                0.5f to surface.copy(alpha = 0.55f),
                1f to surface.copy(alpha = 0.78f),
            ),
            maskTo = 0.45f,
            borderTopOnly = true,
        )
    }
}

@Composable
fun GlassSurface(
    material: GlassMaterial,
    shape: Shape,
    modifier: Modifier = Modifier,
    /** Artwork colour bled through the panel, standing in for the saturated blur. */
    bloom: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = glassSpec(material)
    val bloomColor by animateColorAsState(
        targetValue = if (bloom == Color.Unspecified) Color.Transparent else bloom,
        animationSpec = tween(600),
        label = "glassBloom",
    )

    Box(
        modifier = modifier
            .then(
                if (spec.shadow > 0.dp) {
                    Modifier.shadow(
                        spec.shadow,
                        shape,
                        clip = false,
                        ambientColor = spec.shadowColor,
                        spotColor = spec.shadowColor,
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawBehind { drawFace(spec, shape, bloomColor) },
        content = content,
    )
}

/* --- the material face: fill, hairline, inner top highlight ---------------- */

private fun DrawScope.drawFace(spec: GlassSpec, shape: Shape, bloom: Color = Color.Transparent) {
    val outline = shape.createOutline(size, layoutDirection, this)
    if (spec.ramp.isNotEmpty()) {
        drawOutline(outline, brush = Brush.verticalGradient(*spec.ramp.toTypedArray()))
    } else if (spec.fill != Color.Unspecified && spec.fill.alpha > 0f) {
        drawOutline(outline, color = spec.fill)
    }
    // Two wide, offset radials so the wash is uneven the way a blurred row of
    // different covers is, rather than a single symmetric glow.
    if (bloom.alpha > 0.01f && spec.blur > 0.dp) {
        val strength = (spec.saturation - 1f).coerceIn(0.4f, 1f)
        drawOutline(
            outline,
            brush = Brush.radialGradient(
                colors = listOf(bloom.copy(alpha = 0.55f * strength), Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.9f),
                radius = size.width * 0.75f,
            ),
        )
        drawOutline(
            outline,
            brush = Brush.radialGradient(
                colors = listOf(bloom.copy(alpha = 0.40f * strength), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.35f),
                radius = size.width * 0.60f,
            ),
        )
    }

    val hairline = 1.dp.toPx()
    if (spec.border.alpha > 0f) {
        if (spec.borderTopOnly) {
            drawLine(spec.border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = hairline)
        } else {
            drawOutline(outline, color = spec.border, style = Stroke(hairline))
        }
    }
    if (spec.highlight.alpha > 0f && !spec.borderTopOnly) {
        // `inset 0 1px 0 0 white/x`: a highlight riding the inside of the top
        // edge, faded out down the sides so it does not ring the whole panel.
        drawOutline(
            outline,
            brush = Brush.verticalGradient(
                0f to spec.highlight,
                (hairline * 3f / size.height).coerceIn(0.01f, 0.5f) to Color.Transparent,
            ),
            style = Stroke(hairline),
        )
    }
}

/* --- materials that do not sample a backdrop ------------------------------ */

/**
 * `.glass-field` — a frosted pill INSIDE another glass panel. Tinted with
 * `onSurface` rather than `surface`: surface-over-surface reads as a hole
 * punched in the bar instead of a raised control.
 */
@Composable
fun Modifier.glassField(shape: Shape): Modifier {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = onSurface.copy(alpha = 0.10f))
        drawOutline(outline, color = onSurface.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))
    }
}

/**
 * `.glass-frost` — the thick white pane for controls over unpredictable
 * artwork. Laying enough white over the cover guarantees the pane is lighter
 * than whatever is behind it, which is what lets it carry a fixed dark glyph
 * over a black sleeve and a white one alike.
 */
@Composable
fun Modifier.glassFrost(shape: Shape, on: Boolean = false): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    val fill = if (on) primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.58f)
    val base = if (on) Color.White.copy(alpha = 0.92f) else Color.Transparent
    val border = if (on) primary.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.60f)
    return this
        .shadow(4.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            if (base.alpha > 0f) drawOutline(outline, color = base)
            drawOutline(outline, color = fill)
            drawOutline(outline, color = border, style = Stroke(1.dp.toPx()))
        }
}

/** Content colour that belongs on `.glass-frost` (fixed, not theme-derived). */
val GlassFrostContent = Color(0xFF1C1917)

/** `.glass-sheer` — the thin pane for controls on the player's dimmed backdrop. */
@Composable
fun Modifier.glassSheer(shape: Shape): Modifier = this
    .shadow(4.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = Color.White.copy(alpha = 0.16f))
        drawOutline(outline, color = Color.White.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
    }

/**
 * `.scrim-label` — the caption halo. Drawn in `surface`, which is by definition
 * the backdrop colour for `onSurface` text, so it darkens behind light text in
 * the dark themes and lightens behind dark text in the light one.
 */
@Composable
fun scrimLabelStyle(base: TextStyle): TextStyle {
    val halo = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    return base.copy(
        shadow = androidx.compose.ui.graphics.Shadow(color = halo, blurRadius = 10f),
    )
}

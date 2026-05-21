package nl.muorg.android.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    pixelsPerSecond: Float = 40f,
) {
    val textMeasurer = rememberTextMeasurer()
    var containerWidth by remember { mutableIntStateOf(0) }

    // Measure the natural (unconstrained) text width synchronously — no layout-phase
    // state mutation, no invisible probe nodes.
    val textWidth = remember(text, style) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(),   // no maxWidth → natural width
        ).size.width
    }

    val overflows = textWidth > containerWidth && containerWidth > 0

    val totalScroll = (textWidth + containerWidth * 0.5f).coerceAtLeast(1f)
    val durationMs = ((totalScroll / pixelsPerSecond) * 1000).toInt().coerceAtLeast(2000)

    // Always create the transition unconditionally (Compose rule).
    // When not overflowing the target stays at 0f so nothing moves.
    val transition = rememberInfiniteTransition(label = "marquee")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (overflows) -totalScroll else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "marqueeOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width },
    ) {
        if (overflows) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { translationX = offset },
            )
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { translationX = offset + totalScroll },
            )
        } else {
            Text(
                text = text,
                style = style,
                color = color,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

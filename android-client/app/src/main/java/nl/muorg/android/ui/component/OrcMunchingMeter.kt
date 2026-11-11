package nl.muorg.android.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.theme.MuorgGreenLight

private val MunchTrack = Color(0xFF1F3139)
private val MunchFillInset = Color(0xFF5E8F2C)

/**
 * Progress meter for library/metadata scans: the Orc spinner turning while
 * the job runs, a title and detail line, and a pill bar below. The fill is
 * moss green with a 4dp darker bottom inset; fill changes animate over
 * 300ms. `progress == null` means indeterminate: the spinner keeps turning
 * and the bar shows a 30% segment sliding across. Under reduced motion the
 * segment rests at the start.
 */
@Composable
fun OrcMunchingMeter(
    title: String,
    detail: String,
    progress: () -> Float?,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val raw = progress()
    val fill by animateFloatAsState(
        targetValue = (raw ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "munchFill",
    )
    val slideTransition = rememberInfiniteTransition(label = "munchSlide")
    val slide by slideTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "munchSlide",
    )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrcSpinner(size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val h = size.height
            val radius = CornerRadius(h / 2)
            drawRoundRect(color = MunchTrack, cornerRadius = radius)
            if (raw != null) {
                val w = size.width * fill
                drawRoundRect(color = MuorgGreenLight, size = Size(w, h), cornerRadius = radius)
                val inset = 4.dp.toPx()
                drawRect(
                    color = MunchFillInset,
                    topLeft = Offset(0f, h - inset),
                    size = Size(w, inset),
                )
            } else {
                val segW = size.width * 0.3f
                val x = if (reduced) 0f else (size.width - segW) * slide
                drawRoundRect(
                    color = MuorgGreenLight,
                    topLeft = Offset(x, 0f),
                    size = Size(segW, h),
                    cornerRadius = radius,
                )
            }
        }
    }
}

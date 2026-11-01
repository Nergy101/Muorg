package nl.muorg.android.ui.screen.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.theme.MuorgShapes

/**
 * Hand-drawn Canvas charts for the statistics screen, mirroring the web
 * client's stats charts (donut, line, bars) — no chart library on Android.
 */

/** Muted, earthy palette matching the web client's chart colors. */
private val PALETTE = listOf(
    Color(0xFF5b7c32), Color(0xFF4a7fa5), Color(0xFFb06a2a), Color(0xFF7a4d9e),
    Color(0xFF3a9a7a), Color(0xFFb04848), Color(0xFF3a7aaa), Color(0xFF9a7a2a),
    Color(0xFF5a8a5a), Color(0xFF8a4a6a), Color(0xFF4a6a9a), Color(0xFF7a6a3a),
)
private val OTHER_COLOR = Color(0xFF57534e)

/** Slices below this percent (past the first five) fold into "Other". */
private const val MIN_SLICE_PERCENT = 1.5

data class DonutSlice(val label: String, val count: Int, val fraction: Float, val color: Color)

/** Folds small slices into "Other" and assigns palette colors, like the web pie. */
fun donutSlices(data: List<Pair<String, Int>>): List<DonutSlice> {
    val total = data.sumOf { it.second }
    if (total == 0) return emptyList()
    val sorted = data.sortedByDescending { it.second }
    val main = mutableListOf<DonutSlice>()
    var otherCount = 0
    sorted.forEachIndexed { i, (label, count) ->
        val pct = count.toFloat() / total * 100f
        if (pct < MIN_SLICE_PERCENT && i >= 5) {
            otherCount += count
        } else {
            main.add(
                DonutSlice(
                    label = label,
                    count = count,
                    fraction = count.toFloat() / total,
                    color = PALETTE[main.size % PALETTE.size],
                )
            )
        }
    }
    if (otherCount > 0) {
        main.add(DonutSlice("Other", otherCount, otherCount.toFloat() / total, OTHER_COLOR))
    }
    return main
}

/** A donut chart with a legend underneath. [centerLabel] sits in the hole. */
@Composable
fun DonutChart(
    data: List<Pair<String, Int>>,
    centerLabel: String,
    modifier: Modifier = Modifier,
) {
    val slices = donutSlices(data)
    val total = data.sumOf { it.second }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val stroke = size.width * 0.18f
                val inset = stroke / 2f
                var start = -90f
                for (slice in slices) {
                    val sweep = 360f * slice.fraction
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep.coerceAtMost(359.99f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        slices.forEach { slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(slice.color),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${slice.count} · ${(slice.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (total == 0) {
            Text(
                text = "No data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A line chart with a filled area underneath, for tracks per year. */
@Composable
fun LineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            if (data.isEmpty()) return@Canvas
            val pad = 8.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2 - 16.dp.toPx()
            val step = if (data.size > 1) w / (data.size - 1) else 0f
            val points = data.mapIndexed { i, (_, count) ->
                Offset(
                    x = pad + i * step,
                    y = pad + h * (1f - count.toFloat() / max),
                )
            }
            // Filled area under the line.
            val area = Path().apply {
                moveTo(points.first().x, size.height - pad)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height - pad)
                close()
            }
            drawPath(area, color = color.copy(alpha = 0.15f), style = Fill)
            // The line itself.
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            // Dots on each point.
            points.forEach { drawCircle(color = color, radius = 4.dp.toPx(), center = it) }
        }
        // X labels: first, middle and last year keep the axis readable.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val labels = listOfNotNull(
                data.firstOrNull()?.first,
                data.getOrNull(data.size / 2)?.first,
                data.lastOrNull()?.first,
            ).distinct()
            labels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (i == 0) Modifier.weight(1f)
                    else if (i == labels.size - 1) Modifier.weight(1f)
                    else Modifier.weight(1f),
                )
            }
        }
    }
}

/** Vertical bars with the value on top and the label underneath. */
@Composable
fun BarChart(
    data: List<Pair<String, Int>>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { (label, count) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(110.dp * (count.toFloat() / max))
                            .clip(MuorgShapes.chip)
                            .background(color),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            data.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

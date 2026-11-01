package nl.muorg.android.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.icon.MageIcon

/**
 * The download icon with a circular progress ring around it. [progress] is
 * null when nothing is downloading; 0..1 while a download runs. A finished
 * download shows a check instead of the arrow.
 */
@Composable
fun DownloadStatusIcon(
    downloaded: Boolean,
    progress: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = tint.copy(alpha = 0.25f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (progress != null) {
            Canvas(Modifier.size(size)) {
                val stroke = size.toPx() * 0.125f
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        MageIcon(
            name = if (downloaded && progress == null) "check-circle" else "download",
            tint = if (downloaded && progress == null) ringColor else tint,
            modifier = Modifier.size(size * if (progress != null) 0.5f else 0.92f),
        )
    }
}

/** The round play and shuffle buttons that sit in a sheet's title row. */
@Composable
fun PlayShuffleButtons(
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onShuffle != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onShuffle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (onPlay != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                MageIcon(
                    name = "play",
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** One row of an action sheet, styled like the track sheet's rows. */
@Composable
fun SheetActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    leading: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leading,
        modifier = modifier.clickable(onClick = onClick),
    )
}

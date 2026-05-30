package nl.muorg.android.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.player.PlayerState

@Composable
fun PlayerBar(
    playerState: PlayerState,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentTrack = playerState.currentTrack

    if (currentTrack == null) {
        playerState.errorMessage?.let { error ->
            Surface(
                modifier = modifier.fillMaxWidth().wrapContentHeight(),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { playerState.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover art — full-height, flush to left edge, no rounded corners
                val coverModel: Any? = when {
                    currentTrack.localCoverPath != null -> java.io.File(currentTrack.localCoverPath)
                    currentTrack.hasCover -> "$baseUrl/api/tracks/${currentTrack.id}/cover"
                    else -> null
                }
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp).padding(12.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Track info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                ) {
                    MarqueeText(
                        text = currentTrack.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = currentTrack.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Queue
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Open queue",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Play/Pause
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause
                        else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Next
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

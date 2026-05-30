package nl.muorg.android.ui.screen.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.ui.component.EqualizerBars
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.PlayerBar
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgGreenLight

@Composable
fun QueueScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
    onPlayerBarClick: () -> Unit = {},
    showPlayerBar: Boolean = false,
) {
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentTrack = playerState.currentTrack
    val queue = playerState.queue
    val upNext = if (currentTrack != null) {
        val idx = queue.indexOfFirst { it.id == currentTrack.id }
        if (idx >= 0) queue.drop(idx + 1) else queue
    } else queue

    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val estimatedItemHeightPx = remember(density) { with(density) { 56.dp.toPx() } }

    var draggedId by remember { mutableStateOf<Int?>(null) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    val draggedFromIndex = remember(draggedId, upNext) {
        draggedId?.let { id -> upNext.indexOfFirst { it.id == id } } ?: -1
    }

    val dropTargetIndex = if (draggedFromIndex >= 0) {
        val rowsMoved = (dragAccumY / estimatedItemHeightPx).roundToInt()
        (draggedFromIndex + rowsMoved).coerceIn(0, (upNext.size - 1).coerceAtLeast(0))
    } else -1

    val displayList = remember(upNext, draggedFromIndex, dropTargetIndex) {
        if (draggedFromIndex < 0 || dropTargetIndex < 0 || draggedFromIndex == dropTargetIndex) {
            upNext
        } else {
            upNext.toMutableList().apply {
                val item = removeAt(draggedFromIndex)
                add(dropTargetIndex.coerceIn(0, size), item)
            }
        }
    }

    // Visual offset of the dragged item relative to its current display-list position
    val dragTranslationY = if (draggedFromIndex >= 0 && dropTargetIndex >= 0) {
        val itemDisplacement = (dropTargetIndex - draggedFromIndex) * estimatedItemHeightPx
        dragAccumY - itemDisplacement
    } else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
            }
            Text(
                "Queue",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = playerViewModel::clearQueue) {
                Text("Clear all", color = MaterialTheme.colorScheme.primary)
            }
        }

        LazyColumn(state = lazyListState, modifier = Modifier.weight(1f)) {
            if (currentTrack != null) {
                item {
                    Text(
                        "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 6.dp),
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                RoundedCornerShape(12.dp),
                            )
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverArt(track = currentTrack, baseUrl = baseUrl, imageLoader = imageLoader, size = 52, cornerDp = 6)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                MarqueeText(
                                    currentTrack.displayTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                MarqueeText(
                                    "${currentTrack.displayArtist} · ${currentTrack.displayAlbum}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            EqualizerBars(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "UP NEXT · ${upNext.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = playerViewModel::toggleShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (upNext.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Queue is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(displayList, key = { it.id }) { track ->
                    val isDragged = track.id == draggedId
                    QueueTrackRow(
                        track = track,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        isDragged = isDragged,
                        dragTranslationY = if (isDragged) dragTranslationY else 0f,
                        onSkipTo = { if (!isDragged) playerViewModel.skipTo(track) },
                        onRemove = { playerViewModel.removeFromQueue(track) },
                        onDragStart = {
                            draggedId = track.id
                            dragAccumY = 0f
                        },
                        onDragDelta = { dy -> dragAccumY += dy },
                        onDragEnd = {
                            val from = draggedFromIndex
                            val to = dropTargetIndex
                            if (from >= 0 && to >= 0 && from != to) {
                                val currentIdx = if (currentTrack != null) {
                                    queue.indexOfFirst { it.id == currentTrack.id }
                                } else -1
                                val offset = (currentIdx + 1).coerceAtLeast(0)
                                playerViewModel.reorderQueue(offset + from, offset + to)
                            }
                            draggedId = null
                            dragAccumY = 0f
                        },
                    )
                }
            }
        }

        if (showPlayerBar && currentTrack != null) {
            PlayerBar(
                playerState = playerState,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onClick = onPlayerBarClick,
                onPlayPause = playerViewModel::playPause,
                onNext = playerViewModel::skipNext,
            )
        }
    }
}

@Composable
private fun CoverArt(
    track: CatalogTrack,
    baseUrl: String,
    imageLoader: ImageLoader,
    size: Int,
    cornerDp: Int,
) {
    val coverModel: Any? = when {
        track.localCoverPath != null -> java.io.File(track.localCoverPath)
        track.hasCover -> "$baseUrl/api/tracks/${track.id}/cover"
        else -> null
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (coverModel != null) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: CatalogTrack,
    baseUrl: String,
    imageLoader: ImageLoader,
    onSkipTo: () -> Unit,
    onRemove: () -> Unit,
    isDragged: Boolean = false,
    dragTranslationY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer { translationY = dragTranslationY }
            .then(
                if (isDragged) Modifier.border(2.dp, MuorgGreenLight, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onSkipTo)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp, 36.dp)
                .pointerInput(track.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { latestOnDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            latestOnDragDelta(amount.y)
                        },
                        onDragEnd = { latestOnDragEnd() },
                        onDragCancel = { latestOnDragEnd() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = null,
                tint = if (isDragged) MuorgGreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        CoverArt(track = track, baseUrl = baseUrl, imageLoader = imageLoader, size = 40, cornerDp = 4)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isDragged) MuorgGreenLight else MaterialTheme.colorScheme.onSurface,
            )
            MarqueeText(
                track.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDragged) MuorgGreenLight.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        track.durationSecs?.let { secs ->
            val mins = (secs / 60).toInt()
            val s = (secs % 60).toInt()
            Text(
                "%d:%02d".format(mins, s),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDragged) MuorgGreenLight.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

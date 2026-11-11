package nl.muorg.android.ui.screen.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.player.PlaybackQueue
import nl.muorg.android.player.QueueEntry
import nl.muorg.android.player.QueueOrigin
import nl.muorg.android.ui.component.EmptyState
import nl.muorg.android.ui.component.EqualizerBars
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.TrackActionsSheet
import nl.muorg.android.ui.component.rememberDominantColor
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgGreenLight
import nl.muorg.android.ui.theme.artworkAccent

// Lazy-list keys. Track rows are keyed by track id (an Int); everything else
// gets a String, so no key can ever collide with a track.
private const val KEY_NOW_PLAYING = "now-playing"
private const val KEY_SUMMARY = "summary"
private const val KEY_EMPTY = "empty"
private const val KEY_HEADER_USER = "header-user"
private const val KEY_HEADER_SYSTEM = "header-system"

/**
 * Where a dragged track would land: [index] among the upcoming tracks (as the
 * list reads once it has been dropped) and the [section] it would belong to.
 */
internal data class DropSlot(val index: Int, val section: QueueOrigin)

/**
 * Reads the drop slot off the layout as it is right now.
 *
 * [otherOrigins] is every other upcoming track in display order, so the dragged
 * one is not counted; [visibleOtherCenters] are the centres of those that are
 * on screen (a contiguous run starting at [firstVisibleOther]). The track goes
 * after every other row whose centre it has passed.
 *
 * The section follows the index — before the last of your queue it is in your
 * queue, after the first of up next it is in up next. Where the two meet it is
 * the finger that decides: above the "Up next" header's centre joins your
 * queue, below stays in up next. That includes the very top of a queue that
 * has no your-queue yet — lifting a track above the "Up next" header starts
 * one. Past the end of the list there is no divider to read, so the track
 * keeps the section it is in; that also stops picking a track up from
 * flipping it.
 */
internal fun resolveDropSlot(
    otherOrigins: List<QueueOrigin>,
    firstVisibleOther: Int,
    visibleOtherCenters: List<Float>,
    dragCenter: Float,
    systemHeaderCenter: Float?,
    current: DropSlot,
): DropSlot {
    val index = (firstVisibleOther + visibleOtherCenters.count { it < dragCenter })
        .coerceIn(0, otherOrigins.size)
    val users = otherOrigins.count { it == QueueOrigin.USER }
    val system = otherOrigins.size - users
    val section = when {
        index < users -> QueueOrigin.USER
        index > users -> QueueOrigin.SYSTEM
        system == 0 || systemHeaderCenter == null -> current.section
        dragCenter < systemHeaderCenter -> QueueOrigin.USER
        else -> QueueOrigin.SYSTEM
    }
    return DropSlot(index, section)
}

/** What the list is doing while a row is held: who, where the finger is, and the slot it points at. */
@Stable
private class QueueDragState {
    var draggedId by mutableStateOf<Int?>(null)
        private set

    /** Finger travel since the grab, in px. */
    var fingerDelta by mutableFloatStateOf(0f)
        private set

    /** The row's top, in viewport px, at the grab. */
    var startTop by mutableFloatStateOf(0f)
        private set

    var slot by mutableStateOf(DropSlot(0, QueueOrigin.SYSTEM))

    val active: Boolean get() = draggedId != null

    fun begin(id: Int, top: Float, from: DropSlot) {
        draggedId = id
        startTop = top
        fingerDelta = 0f
        slot = from
    }

    fun dragBy(dy: Float) {
        fingerDelta += dy
    }

    fun end() {
        draggedId = null
        fingerDelta = 0f
    }
}

@Composable
fun QueueScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
) {
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentTrack = playerState.currentTrack
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val currentTrackMembership by playerViewModel.currentTrackMembership.collectAsStateWithLifecycle()
    var sheetTrack by remember { mutableStateOf<CatalogTrack?>(null) }

    LaunchedEffect(sheetTrack) {
        if (sheetTrack != null) playerViewModel.loadCurrentTrackMembership(sheetTrack!!)
    }
    // The queue model *is* the play order, so what is drawn here is what will
    // play — including which half each track belongs to.
    val playbackQueue = playerState.playbackQueue
    val upNextEntries = playbackQueue.upNext

    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val drag = remember { QueueDragState() }
    val draggedId = drag.draggedId
    val draggedFrom = draggedId?.let { id -> upNextEntries.indexOfFirst { it.track.id == id } } ?: -1

    // The queue as it will be once the held row is dropped where it is now. It
    // runs the same move the drop commits, so a track dragged across the
    // your-queue / up-next divider is shown in its destination section while
    // it is still in the finger, and the headers stay truthful.
    val dragPreview: PlaybackQueue? = if (draggedFrom >= 0) {
        val offset = playbackQueue.position + 1
        val to = drag.slot.index.coerceIn(0, upNextEntries.size - 1)
        playbackQueue.move(offset + draggedFrom, offset + to, drag.slot.section)
    } else null

    // After a drop the real queue takes a beat to come back through the
    // player; until it does, keep showing the drop so the row does not jump
    // back to where it started.
    var settlePreview by remember { mutableStateOf<PlaybackQueue?>(null) }
    LaunchedEffect(playbackQueue) { settlePreview = null }
    val displayQueue = dragPreview ?: settlePreview ?: playbackQueue
    val displayList = displayQueue.upNext
    val upNextCount = displayList.size

    // The row that was just dropped glides from under the finger into its slot.
    var settlingId by remember { mutableStateOf<Int?>(null) }
    val dropOffset = remember { Animatable(0f) }

    // Pulling the list down past its top drags the whole screen with it, and
    // letting go far enough (or flicking) goes back — the same gesture the
    // player has. Nothing happens while the list can still scroll up.
    var dismissY by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = with(density) { 120.dp.toPx() }
    val latestOnBack by rememberUpdatedState(onBack)
    val dismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissY > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -dismissY)
                    dismissY += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    dismissY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissY <= 0f) return Velocity.Zero
                if (dismissY > dismissThreshold || available.y > 1800f) {
                    latestOnBack()
                } else {
                    animate(dismissY, 0f, animationSpec = spring()) { value, _ -> dismissY = value }
                }
                return available
            }
        }
    }

    val latestDisplayList by rememberUpdatedState(displayList)
    val latestEntries by rememberUpdatedState(upNextEntries)
    val latestQueue by rememberUpdatedState(playbackQueue)
    val latestPreview by rememberUpdatedState(dragPreview)

    // The held row vanished from the queue (the track ended, was removed): drop the grab.
    LaunchedEffect(draggedId, draggedFrom) {
        if (draggedId != null && draggedFrom < 0) drag.end()
    }

    // While a row is held: scroll when it nears an edge, and keep reading where
    // it would land. Done per frame because both depend on the layout, which
    // moves under a stationary finger as the list scrolls.
    LaunchedEffect(draggedId) {
        val id = draggedId ?: return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val maxSpeed = with(density) { 640.dp.toPx() }
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
            last = now

            val info = lazyListState.layoutInfo
            val held = info.visibleItemsInfo.firstOrNull { it.key == id } ?: continue
            val top = drag.startTop + drag.fingerDelta
            val bottom = top + held.size
            val viewStart = (info.viewportStartOffset + info.beforeContentPadding).toFloat()
            val viewEnd = (info.viewportEndOffset - info.afterContentPadding).toFloat()
            val scrollBy = when {
                top < viewStart + edge -> -maxSpeed * dt * ((viewStart + edge - top) / edge).coerceIn(0f, 1f)
                bottom > viewEnd - edge -> maxSpeed * dt * ((bottom - (viewEnd - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (scrollBy != 0f) lazyListState.scrollBy(scrollBy)

            val others = latestDisplayList.filter { it.track.id != id }
            val visibleOthers = info.visibleItemsInfo.filter { it.key is Int && it.key != id }
            val firstVisible = visibleOthers.firstOrNull()
                ?.let { first -> others.indexOfFirst { it.track.id == first.key } }
                ?.coerceAtLeast(0) ?: 0
            val header = info.visibleItemsInfo.firstOrNull { it.key == KEY_HEADER_SYSTEM }
            val next = resolveDropSlot(
                otherOrigins = others.map { it.origin },
                firstVisibleOther = firstVisible,
                visibleOtherCenters = visibleOthers.map { it.offset + it.size / 2f },
                dragCenter = top + held.size / 2f,
                systemHeaderCenter = header?.let { it.offset + it.size / 2f },
                current = drag.slot,
            )
            if (next != drag.slot) drag.slot = next
        }
    }

    fun grab(entry: QueueEntry) {
        val id = entry.track.id
        val item = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val from = latestEntries.indexOfFirst { it.track.id == id }
        if (from < 0) return
        scope.launch { dropOffset.snapTo(0f) }
        settlingId = null
        drag.begin(id, item.offset.toFloat(), DropSlot(from, entry.origin))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun release(commit: Boolean) {
        val id = drag.draggedId ?: return
        val held = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
        val startOffset = held?.let { drag.startTop + drag.fingerDelta - it.offset } ?: 0f
        val from = latestEntries.indexOfFirst { it.track.id == id }
        val slot = drag.slot
        if (commit && from >= 0 && (slot.index != from || slot.section != latestEntries[from].origin)) {
            settlePreview = latestPreview
            val offset = latestQueue.position + 1
            playerViewModel.reorderQueue(offset + from, offset + slot.index, slot.section)
        }
        drag.end()
        settlingId = id
        scope.launch {
            dropOffset.snapTo(startOffset)
            dropOffset.animateTo(0f, tween(160))
            settlingId = null
        }
    }

    // The now-playing card tints to the artwork, same accent as the island
    // and the player, so the three do not disagree about "the current track".
    val accent = artworkAccent(
        rememberDominantColor(
            url = currentTrack?.let { t ->
                when {
                    t.localCoverPath != null -> java.io.File(t.localCoverPath!!)
                    t.hasCover -> "$baseUrl/api/tracks/${t.id}/cover?size=1200"
                    else -> null
                }
            },
            imageLoader = imageLoader,
            fallback = Color.Unspecified,
        ).color
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(dismissConnection)
            .graphicsLayer { translationY = dismissY }
            .statusBarsPadding(),
    ) {
        // No back chevron and no title: the mini player's queue glyph is the
        // way in and back out, and it lights up while this screen is open, so
        // a second affordance and a label are both redundant.
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = playerViewModel::clearQueueWithUndo) {
                Text("Clear all", color = accent)
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = LocalBottomInset.current),
        ) {
            if (currentTrack != null) {
                item(key = KEY_NOW_PLAYING) {
                    Text(
                        "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 6.dp),
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(
                                accent.copy(alpha = 0.18f),
                                RoundedCornerShape(12.dp),
                            )
                            .border(1.dp, accent, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverArt(track = currentTrack, baseUrl = baseUrl, imageLoader = imageLoader, size = 52, cornerDp = 6)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                MarqueeText(
                                    currentTrack.displayTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = accent,
                                )
                                MarqueeText(
                                    "${currentTrack.displayArtist} · ${currentTrack.displayAlbum}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accent.copy(alpha = 0.85f),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            EqualizerBars(
                                color = accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            item(key = KEY_SUMMARY) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$upNextCount track${if (upNextCount == 1) "" else "s"} queued",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = playerViewModel::toggleShuffle) {
                        Icon(
                            painter = painterResource(mageIconRes("exchange")),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (playerState.shuffleEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Shuffle",
                            color = if (playerState.shuffleEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (displayList.isEmpty()) {
                item(key = KEY_EMPTY) {
                    EmptyState(title = "Queue is empty", body = "Add something to play next.")
                }
            } else {
                displayList.forEachIndexed { index, entry ->
                    val track = entry.track
                    // Section labels come from the list being drawn rather than
                    // from a separate count, so they stay truthful mid-drag: a
                    // track dragged below the album is shown under "Up next"
                    // because that is when it will now play.
                    val previousOrigin = displayList.getOrNull(index - 1)?.origin
                    if (entry.origin == QueueOrigin.USER && previousOrigin != QueueOrigin.USER) {
                        item(key = KEY_HEADER_USER) {
                            QueueSectionHeader(
                                label = "YOUR QUEUE · ${displayList.count { it.origin == QueueOrigin.USER }}",
                                color = accent,
                                action = "Clear" to playerViewModel::clearUserQueue,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else if (entry.origin == QueueOrigin.SYSTEM && previousOrigin != QueueOrigin.SYSTEM) {
                        item(key = KEY_HEADER_SYSTEM) {
                            QueueSectionHeader(
                                label = "UP NEXT · ${displayList.count { it.origin == QueueOrigin.SYSTEM }}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    item(key = track.id) {
                        val isDragged = track.id == draggedId
                        val isSettling = !isDragged && track.id == settlingId
                        QueueTrackRow(
                            track = track,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            isLoading = playerState.pendingTrackId == track.id,
                            isDragged = isDragged,
                            // Keeps the row under the finger: where the finger
                            // says it is, minus where the list laid it out.
                            dragOffset = {
                                when {
                                    isDragged -> {
                                        val laidOutAt = lazyListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == track.id }?.offset
                                        if (laidOutAt == null) 0f else drag.startTop + drag.fingerDelta - laidOutAt
                                    }
                                    isSettling -> dropOffset.value
                                    else -> 0f
                                }
                            },
                            onSkipTo = { playerViewModel.skipTo(track) },
                            onMore = { sheetTrack = track },
                            onDragStart = { grab(entry) },
                            onDragDelta = drag::dragBy,
                            onDragEnd = { release(commit = true) },
                            onDragCancel = { release(commit = false) },
                            modifier = if (isDragged || isSettling) {
                                Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                            } else {
                                Modifier.animateItem()
                            },
                        )
                    }
                }
            }
        }

    }

    sheetTrack?.let { track ->
        TrackActionsSheet(
            track = track,
            playerViewModel = playerViewModel,
            playlists = playlists,
            isFavorite = track.id.toString() in playerState.favorites,
            baseUrl = baseUrl,
            imageLoader = imageLoader,
            onDismiss = { sheetTrack = null },
            onAddToPlaylist = { playlistId -> playerViewModel.addTrackToPlaylist(track, playlistId); sheetTrack = null },
            onRemoveFromPlaylist = { playlistId -> playerViewModel.removeTrackFromPlaylist(track, playlistId); sheetTrack = null },
            trackInPlaylistIds = currentTrackMembership,
            onViewArtist = { sheetTrack = null },
            onViewAlbum = { sheetTrack = null },
            onRemoveFromQueue = { playerViewModel.removeFromQueue(track) },
            onSaveMetadata = { title, artist, album, albumArtist, genre, year ->
                sheetTrack = null
                playerViewModel.saveMetadata(track, title, artist, album, albumArtist, genre, year)
            },
        )
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
        track.hasCover -> "$baseUrl/api/tracks/${track.id}/cover?size=1200"
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
                painter = painterResource(mageIconRes("music")),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

/**
 * One queued track. Hold it anywhere and drag to reorder, or grab the bars on
 * the left, which pick it up straight away; tap to play it; the dots open the
 * track's action sheet.
 */
@Composable
private fun QueueTrackRow(
    track: CatalogTrack,
    baseUrl: String,
    imageLoader: ImageLoader,
    onSkipTo: () -> Unit,
    onMore: () -> Unit,
    dragOffset: () -> Float,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isDragged: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)
    val raised = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset()
                if (isDragged) {
                    shadowElevation = 12.dp.toPx()
                    shape = RoundedCornerShape(10.dp)
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .background(if (isDragged) raised else MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left — the three bars: pick up at once, no long press.
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(44.dp, 52.dp)
                .pointerInput(track.id) {
                    detectDragGestures(
                        onDragStart = { latestOnDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            latestOnDragDelta(amount.y)
                        },
                        onDragEnd = { latestOnDragEnd() },
                        onDragCancel = { latestOnDragCancel() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            MageIcon(
                "dash-menu",
                contentDescription = "Drag to reorder",
                tint = if (isDragged) MuorgGreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        // Track info: tap to play, hold and drag to reorder.
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSkipTo)
                .pointerInput(track.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { latestOnDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            latestOnDragDelta(amount.y)
                        },
                        onDragEnd = { latestOnDragEnd() },
                        onDragCancel = { latestOnDragCancel() },
                    )
                }
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(6.dp))
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
        }
        // Right of the length — the track's action sheet.
        IconButton(onClick = onMore, modifier = Modifier.size(44.dp)) {
            MageIcon(
                "dots",
                contentDescription = "Track actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A "Your queue" / "Up next" divider.
 *
 * The queue is one ordered list — these only say where the tracks the listener
 * queued by hand end and the album or playlist resumes.
 */
@Composable
private fun QueueSectionHeader(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = action.second) {
                Text(action.first, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

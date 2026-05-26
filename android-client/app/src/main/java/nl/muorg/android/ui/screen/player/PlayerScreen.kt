package nl.muorg.android.ui.screen.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.TrackActionsSheet
import nl.muorg.android.ui.component.rememberDominantColor
import nl.muorg.android.ui.player.PlayerViewModel

private data class GlowBlob(val cx: Float, val cy: Float, val radius: Float, val opacity: Float)

private fun buildGlowBlobs(seed: Int): List<GlowBlob> {
    val rnd = java.util.Random(seed.toLong())
    return (0 until 14).map {
        GlowBlob(
            cx = rnd.nextFloat(),
            cy = rnd.nextFloat(),
            radius = 0.25f + rnd.nextFloat() * 0.25f,
            opacity = 0.40f + rnd.nextFloat() * 0.40f,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onViewArtist: (artistName: String) -> Unit = {},
    onViewAlbum: (albumName: String) -> Unit = {},
) {
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentTrack = playerState.currentTrack
    val coverUrl = if (currentTrack?.hasCover == true) "$baseUrl/api/tracks/${currentTrack.id}/cover" else null

    // Dominant color runs on coverUrl immediately so accent starts animating ahead of the image switch
    val dominantColor = rememberDominantColor(
        url = coverUrl,
        imageLoader = imageLoader,
        fallback = Color(0xFF1A1A1A),
    )

    // displayedCoverUrl only advances once the full image is in cache — no blank flash
    val context = LocalContext.current
    var displayedCoverUrl by remember { mutableStateOf(coverUrl) }
    LaunchedEffect(coverUrl) {
        if (coverUrl == null) {
            displayedCoverUrl = null
            return@LaunchedEffect
        }
        val result = imageLoader.execute(
            ImageRequest.Builder(context).data(coverUrl).build()
        )
        if (result is SuccessResult) displayedCoverUrl = coverUrl
    }
    val accentColor by animateColorAsState(
        targetValue = dominantColor.color,
        animationSpec = tween(durationMillis = 800),
        label = "accentColor",
    )

    // Blob positions interpolate between songs so the background morphs instead of jumping
    var fromBlobs by remember { mutableStateOf(buildGlowBlobs(coverUrl.hashCode())) }
    var toBlobs   by remember { mutableStateOf(buildGlowBlobs(coverUrl.hashCode())) }
    val blobTransition = remember { Animatable(1f) }
    val blobProgress by blobTransition.asState()

    LaunchedEffect(coverUrl) {
        val p = blobTransition.value
        // Snapshot current interpolated positions as the new starting point
        fromBlobs = fromBlobs.zip(toBlobs).map { (a, b) ->
            GlowBlob(
                cx     = a.cx     + (b.cx     - a.cx)     * p,
                cy     = a.cy     + (b.cy     - a.cy)     * p,
                radius = a.radius + (b.radius - a.radius) * p,
                opacity = a.opacity + (b.opacity - a.opacity) * p,
            )
        }
        toBlobs = buildGlowBlobs(coverUrl.hashCode())
        blobTransition.snapTo(0f)
        blobTransition.animateTo(1f, tween(durationMillis = 800))
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isSeeking) seekPosition else playerState.progress

    var sheetOpen by remember { mutableStateOf(false) }
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val favorites = playerState.favorites

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    "Now playing",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Open queue",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = { sheetOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Background ────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))

            if (displayedCoverUrl != null) {
                if (dominantColor.isBland) {
                    // Bland / white / black cover: fall back to blurred scaled image
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = displayedCoverUrl,
                            contentDescription = null,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    renderEffect = BlurEffect(72f, 72f, TileMode.Clamp)
                                    scaleX = 1.7f
                                    scaleY = 1.7f
                                    alpha = 0.65f
                                },
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                } else {
                    // Vibrant cover: multiple blobs of the dominant color scattered across screen
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                renderEffect = BlurEffect(100f, 100f, TileMode.Clamp)
                                alpha = 0.80f
                            },
                    ) {
                        for (i in toBlobs.indices) {
                            val a = fromBlobs[i]
                            val b = toBlobs[i]
                            val t = blobProgress
                            drawCircle(
                                color = accentColor.copy(
                                    alpha = a.opacity + (b.opacity - a.opacity) * t
                                ),
                                radius = size.minDimension * (a.radius + (b.radius - a.radius) * t),
                                center = Offset(
                                    size.width  * (a.cx + (b.cx - a.cx) * t),
                                    size.height * (a.cy + (b.cy - a.cy) * t),
                                ),
                            )
                        }
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.45f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.width * 0.72f,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Album,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxSize().padding(48.dp),
                            )
                            if (displayedCoverUrl != null) {
                                AsyncImage(
                                    model = displayedCoverUrl,
                                    contentDescription = "Album cover",
                                    imageLoader = imageLoader,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    MarqueeText(
                        text = currentTrack?.displayTitle ?: "Nothing playing",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    MarqueeText(
                        text = currentTrack?.displayArtist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )

                    MarqueeText(
                        text = currentTrack?.displayAlbum ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier.padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Slider(
                        value = displayProgress,
                        onValueChange = { value ->
                            isSeeking = true
                            seekPosition = value
                        },
                        onValueChangeFinished = {
                            playerViewModel.seekTo(seekPosition)
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatMs(playerState.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                        Text(
                            text = if (playerState.durationMs > 0) formatMs(playerState.durationMs) else "–:--",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = playerViewModel::toggleShuffle) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playerState.shuffleEnabled) accentColor
                                       else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        IconButton(
                            onClick = playerViewModel::skipPrevious,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        IconButton(
                            onClick = playerViewModel::playPause,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause
                                             else Icons.Filled.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = accentColor,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        IconButton(
                            onClick = playerViewModel::skipNext,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        IconButton(onClick = playerViewModel::cycleRepeatMode) {
                            Icon(
                                imageVector = if (playerState.repeatMode == Player.REPEAT_MODE_ONE)
                                    Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = "Repeat",
                                tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF)
                                    accentColor
                                else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (sheetOpen && currentTrack != null) {
        TrackActionsSheet(
            track = currentTrack,
            playerViewModel = playerViewModel,
            playlists = playlists,
            isFavorite = currentTrack.id.toString() in favorites,
            baseUrl = baseUrl,
            imageLoader = imageLoader,
            onDismiss = { sheetOpen = false },
            onAddToPlaylist = { sheetOpen = false },
            onViewArtist = {
                sheetOpen = false
                onViewArtist(currentTrack.displayArtist)
            },
            onViewAlbum = {
                sheetOpen = false
                onViewAlbum(currentTrack.displayAlbum)
            },
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return "%d:%02d".format(minutes, seconds)
}

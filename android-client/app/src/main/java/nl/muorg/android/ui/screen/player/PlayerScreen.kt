package nl.muorg.android.ui.screen.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
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
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.appcompat.R as AppCompatR
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
    val coverUrl: Any? = when {
        currentTrack?.localCoverPath != null -> java.io.File(currentTrack.localCoverPath)
        currentTrack?.hasCover == true -> "$baseUrl/api/tracks/${currentTrack.id}/cover"
        else -> null
    }

    // Dominant color runs on coverUrl immediately so accent starts animating ahead of the image switch
    val dominantColor = rememberDominantColor(
        url = coverUrl,
        imageLoader = imageLoader,
        fallback = Color(0xFF1A1A1A),
    )

    // displayedCoverUrl only advances once the full image is in cache — no blank flash
    val context = LocalContext.current
    var displayedCoverUrl by remember { mutableStateOf<Any?>(coverUrl) }
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

    val isCasting by playerViewModel.isCasting.collectAsStateWithLifecycle()
    val castSelector = remember { playerViewModel.buildCastRouteSelector() }

    val castPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
    val castPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val themedContext = ContextThemeWrapper(context, AppCompatR.style.Theme_AppCompat_DayNight_Dialog)
            MediaRouteChooserDialog(themedContext).apply { routeSelector = castSelector }.show()
        }
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isSeeking) seekPosition else playerState.progress

    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var localVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()) }
    val castVolume by playerViewModel.castVolume.collectAsStateWithLifecycle()

    var sheetOpen by remember { mutableStateOf(false) }
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val currentTrackMembership by playerViewModel.currentTrackMembership.collectAsStateWithLifecycle()
    val favorites = playerState.favorites

    LaunchedEffect(sheetOpen, currentTrack) {
        if (sheetOpen && currentTrack != null) {
            playerViewModel.loadCurrentTrackMembership(currentTrack)
        }
    }

    val window = (context as? Activity)?.window
    if (window != null) {
        DisposableEffect(window) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            val prevLightStatus = insetsController.isAppearanceLightStatusBars
            val prevLightNav = insetsController.isAppearanceLightNavigationBars
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            onDispose {
                insetsController.isAppearanceLightStatusBars = prevLightStatus
                insetsController.isAppearanceLightNavigationBars = prevLightNav
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 4.dp),
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
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val themedContext = ContextThemeWrapper(context, AppCompatR.style.Theme_AppCompat_DayNight_Dialog)
                    if (isCasting) {
                        MediaRouteControllerDialog(themedContext).show()
                    } else if (ContextCompat.checkSelfPermission(context, castPermission) == PackageManager.PERMISSION_GRANTED) {
                        MediaRouteChooserDialog(themedContext).apply { routeSelector = castSelector }.show()
                    } else {
                        castPermissionLauncher.launch(castPermission)
                    }
                }) {
                    Icon(
                        if (isCasting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                        contentDescription = if (isCasting) "Casting" else "Cast",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
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
                Crossfade(targetState = dominantColor.isBland, label = "bgMode") { isBland ->
                    if (isBland) {
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
                        // Vibrant cover: solid dominant color + dark gradient toward bottom
                        Box(modifier = Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.90f)))
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.7f to Color.Black.copy(alpha = 0.15f),
                                    1.0f to Color.Black.copy(alpha = 0.50f),
                                )
                            )
                        )
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            val controlColor = if (dominantColor.isBland) Color.White else accentColor

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
                                        controlColor.copy(alpha = 0.45f),
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
                            thumbColor = controlColor,
                            activeTrackColor = controlColor,
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
                        val displayDurationMs = if (playerState.durationMs > 0) playerState.durationMs
                            else ((currentTrack?.durationSecs ?: 0.0) * 1000).toLong()
                        Text(
                            text = if (displayDurationMs > 0) formatMs(displayDurationMs) else "–:--",
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
                                tint = if (playerState.shuffleEnabled) controlColor
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
                                tint = controlColor,
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
                                    controlColor
                                else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.VolumeDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                        val displayVolume = if (isCasting) (castVolume ?: 0f) else localVolume
                        Slider(
                            value = displayVolume,
                            onValueChange = { newVol ->
                                if (isCasting) {
                                    playerViewModel.setCastVolume(newVol)
                                } else {
                                    localVolume = newVol
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (newVol * maxVolume).toInt(),
                                        0,
                                    )
                                }
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = controlColor.copy(alpha = 0.7f),
                                activeTrackColor = controlColor.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
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
            onAddToPlaylist = { playlistId ->
                playerViewModel.addTrackToPlaylist(currentTrack, playlistId)
                sheetOpen = false
            },
            onRemoveFromPlaylist = { playlistId ->
                playerViewModel.removeTrackFromPlaylist(currentTrack, playlistId)
                sheetOpen = false
            },
            trackInPlaylistIds = currentTrackMembership,
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

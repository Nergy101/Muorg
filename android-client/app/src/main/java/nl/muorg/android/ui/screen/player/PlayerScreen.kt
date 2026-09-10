package nl.muorg.android.ui.screen.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.appcompat.R as AppCompatR
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.TrackActionsSheet
import nl.muorg.android.ui.component.rememberDominantColor
import nl.muorg.android.ui.glass.glassSheer
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes
import nl.muorg.android.ui.theme.artworkAccent


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

    // Every primary on this screen comes from the artwork, matching the island
    // and the queue's now-playing card. The backdrop here is always near-black
    // whatever the theme is, so the accent is resolved for a dark surface.
    val accent by animateColorAsState(
        targetValue = artworkAccent(dominantColor.color, onDark = true),
        animationSpec = tween(durationMillis = 600),
        label = "playerAccent",
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

    val isSeekingState = remember { mutableStateOf(false) }
    val seekPositionState = remember { mutableFloatStateOf(0f) }
    val isSeeking by isSeekingState
    val seekPosition by seekPositionState
    val displayProgress = if (isSeeking) seekPosition else playerState.progress

    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var localVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()) }
    val castVolume by playerViewModel.castVolume.collectAsStateWithLifecycle()

    val sleepTimerActive by playerViewModel.sleepTimerActive.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerBackdrop(
            displayedCoverUrl = displayedCoverUrl,
            dominantColor = dominantColor,
            imageLoader = imageLoader,
            accentColor = accentColor,
        )


        // ── Content ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top row: a bare dismiss chevron on the left, and every secondary
            // control gathered into ONE sheer glass pill on the right — the web
            // groups them, it does not scatter round buttons across the bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(mageIconRes("chevron-down")),
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.glassSheer(MuorgShapes.pill).height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (sleepTimerActive) showSleepTimerDialog = true
                            else showSleepTimerPicker = true
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(mageIconRes("clock")),
                            contentDescription = if (sleepTimerActive) "Sleep timer active" else "Set sleep timer",
                            tint = if (sleepTimerActive) Color.White else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    VerticalHairline()
                    IconButton(
                        onClick = {
                            val themedContext = ContextThemeWrapper(context, AppCompatR.style.Theme_AppCompat_DayNight_Dialog)
                            if (isCasting) {
                                MediaRouteControllerDialog(themedContext).show()
                            } else if (ContextCompat.checkSelfPermission(context, castPermission) == PackageManager.PERMISSION_GRANTED) {
                                MediaRouteChooserDialog(themedContext).apply { routeSelector = castSelector }.show()
                            } else {
                                castPermissionLauncher.launch(castPermission)
                            }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(mageIconRes("screencast")),
                            contentDescription = if (isCasting) "Casting" else "Cast",
                            tint = if (isCasting) accent else Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    VerticalHairline()
                    IconButton(onClick = { sheetOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(mageIconRes("dots")),
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1.0f))

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 30.dp,
                        shape = MuorgShapes.art,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    )
                    .clip(MuorgShapes.art)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(mageIconRes("compact-disk")),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxSize().padding(64.dp),
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

            Spacer(modifier = Modifier.weight(1.3f))

            // Metadata is LEFT aligned with the secondary actions on the same
            // row — the web never centres the title under the artwork.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = currentTrack?.displayTitle ?: "Nothing playing",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    MarqueeText(
                        text = currentTrack?.displayArtist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.80f),
                    )
                    MarqueeText(
                        text = currentTrack?.displayAlbum ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(mageIconRes("stack")),
                        contentDescription = "Open queue",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
                IconButton(onClick = { sheetOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(mageIconRes("playlist-add")),
                        contentDescription = "Add to playlist",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
                IconButton(
                    onClick = { currentTrack?.let(playerViewModel::toggleFavorite) },
                    modifier = Modifier.size(40.dp),
                ) {
                    val isFavorite = currentTrack != null && currentTrack.id.toString() in favorites
                    Icon(
                        painter = painterResource(mageIconRes("heart")),
                        contentDescription = "Favourite",
                        tint = if (isFavorite) accent else Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            PlayerControls(
                playerState = playerState,
                playerViewModel = playerViewModel,
                currentTrack = currentTrack,
                accent = accent,
                isCasting = isCasting,
                castVolume = castVolume,
                displayProgress = displayProgress,
                isSeekingState = isSeekingState,
                seekPositionState = seekPositionState,
            )

        }
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep timer") },
            text = { Text("Playback will stop in ${formatSleepTimer(sleepTimerRemainingMs)}.") },
            confirmButton = {
                TextButton(onClick = {
                    playerViewModel.cancelSleepTimer()
                    showSleepTimerDialog = false
                }) { Text("Turn off") }
            },
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Keep") }
            },
        )
    }

    if (showSleepTimerPicker) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerPicker = false },
            sheetState = sheetState,
        ) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
            )
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90)
                presets.forEach { mins ->
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            playerViewModel.startSleepTimer(mins * 60_000L)
                            showSleepTimerPicker = false
                        }
                    ) {
                        Text("${mins}m")
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
            onSaveMetadata = { title, artist, album, albumArtist, genre, year ->
                sheetOpen = false
                playerViewModel.saveMetadata(currentTrack, title, artist, album, albumArtist, genre, year)
            },
        )
    }
}

private fun formatSleepTimer(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

internal fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return "%d:%02d".format(minutes, seconds)
}

/** The 1px divider between the buttons inside the player's glass pill. */
@Composable
private fun VerticalHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color.White.copy(alpha = 0.22f)),
    )
}

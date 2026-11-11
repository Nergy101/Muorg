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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.PlaylistPickerSheet
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
        currentTrack?.hasCover == true -> "$baseUrl/api/tracks/${currentTrack.id}/cover?size=1200"
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
    val sleepTimerEnabled by playerViewModel.sleepTimerEnabled.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }

    var sheetOpen by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    // Art-fullscreen ("FS") mode, mirroring the web player: tapping the cover
    // enters it, tapping the backdrop exits it. The FS layout is a single
    // bottom bar over the sharpened backdrop — no top bar, no transport row.
    var artFullscreen by remember { mutableStateOf(false) }
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val currentTrackMembership by playerViewModel.currentTrackMembership.collectAsStateWithLifecycle()
    val favorites = playerState.favorites

    // The picker shows the same effective membership as the actions sheet: a
    // favourite counts as a member of the Favorites playlist even when the
    // join row was never written.
    val favPlaylistId = remember(playlists) { playlists.firstOrNull { it.name == "Favorites" }?.id }
    val pickerMembership = remember(currentTrackMembership, favPlaylistId, favorites, currentTrack) {
        val isFavorite = currentTrack != null && currentTrack.id.toString() in favorites
        when {
            favPlaylistId == null -> currentTrackMembership
            isFavorite -> currentTrackMembership + favPlaylistId
            else -> currentTrackMembership - favPlaylistId
        }
    }

    // Swipe down anywhere on the player to dismiss it, alongside the chevron
    // and the back gesture: the screen follows the finger and springs back, or
    // leaves once dragged past the threshold.
    val density = LocalDensity.current
    val swipeScope = rememberCoroutineScope()
    val swipeOffset = remember { Animatable(0f) }
    val swipeDismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }

    LaunchedEffect(sheetOpen, showPlaylistPicker, currentTrack) {
        if ((sheetOpen || showPlaylistPicker) && currentTrack != null) {
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
            .background(Color.Black)
            .graphicsLayer { translationY = swipeOffset.value }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        swipeScope.launch {
                            swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceAtLeast(0f))
                        }
                    },
                    onDragEnd = {
                        if (swipeOffset.value >= swipeDismissThresholdPx) {
                            onBack()
                        } else {
                            swipeScope.launch { swipeOffset.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = {
                        swipeScope.launch { swipeOffset.animateTo(0f, spring()) }
                    },
                )
            },
    ) {
        PlayerBackdrop(
            displayedCoverUrl = displayedCoverUrl,
            imageLoader = imageLoader,
            sharp = artFullscreen,
        )


        // ── Content ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (!artFullscreen) {
            // Top row: every secondary control gathered into ONE sheer glass
            // pill on the right — the web groups them, it does not scatter round
            // buttons across the bar. No dismiss chevron on mobile: swipe down
            // or the back gesture leaves the player.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.glassSheer(MuorgShapes.pill).height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (sleepTimerEnabled) {
                        IconButton(
                            onClick = {
                                if (sleepTimerActive) showSleepTimerDialog = true
                                else showSleepTimerPicker = true
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                painter = painterResource(mageIconRes("clock")),
                                contentDescription = if (sleepTimerActive) "Sleep timer active" else "Set sleep timer",
                                tint = if (sleepTimerActive) Color.White else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        VerticalHairline()
                    }
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
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(mageIconRes("screencast")),
                            contentDescription = if (isCasting) "Casting" else "Cast",
                            tint = if (isCasting) accent else Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth > maxHeight) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        PlayerArtwork(
                            displayedCoverUrl = displayedCoverUrl,
                            imageLoader = imageLoader,
                            onClick = { artFullscreen = true },
                            modifier = Modifier
                                .padding(start = 24.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxHeight(0.72f)
                                .aspectRatio(1f),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            PlayerTrackHeader(
                                currentTrack = currentTrack,
                                favorites = favorites,
                                accent = accent,
                                onOpenQueue = onOpenQueue,
                                onAddToPlaylist = { showPlaylistPicker = true },
                                onToggleFavorite = { currentTrack?.let(playerViewModel::toggleFavorite) },
                                onOpenActions = { sheetOpen = true },
                            )
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
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.weight(1.0f))

                        PlayerArtwork(
                            displayedCoverUrl = displayedCoverUrl,
                            imageLoader = imageLoader,
                            onClick = { artFullscreen = true },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(0.76f)
                                .aspectRatio(1f),
                        )

                        Spacer(modifier = Modifier.weight(1.3f))

                        // The title gets the full width on its own row — squeezed into a
                        // weighted column next to the action icons it was cut off halfway
                        // across the screen. Marquee still kicks in when it overflows.
                        PlayerTrackHeader(
                            currentTrack = currentTrack,
                            favorites = favorites,
                            accent = accent,
                            onOpenQueue = onOpenQueue,
                            onAddToPlaylist = { showPlaylistPicker = true },
                            onToggleFavorite = { currentTrack?.let(playerViewModel::toggleFavorite) },
                            onOpenActions = { sheetOpen = true },
                        )

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
            }
            } else {
                // FS mode: the whole backdrop is the exit — tap it to leave
                // art-fullscreen (the web hides the maximize button on mobile).
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { artFullscreen = false },
                )
                FsBottomBar(
                    currentTrack = currentTrack,
                    displayedCoverUrl = displayedCoverUrl,
                    imageLoader = imageLoader,
                    favorites = favorites,
                    accent = accent,
                    progress = displayProgress,
                    isPlaying = playerState.isPlaying,
                    onPlayPause = playerViewModel::playPause,
                    onToggleFavorite = { currentTrack?.let(playerViewModel::toggleFavorite) },
                    onOpenActions = { sheetOpen = true },
                    onNext = playerViewModel::skipNext,
                )
            }

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

    if (showPlaylistPicker && currentTrack != null) {
        PlaylistPickerSheet(
            playlists = playlists,
            membershipIds = pickerMembership,
            onAdd = { playlist -> playerViewModel.addTrackToPlaylist(currentTrack, playlist.id) },
            onRemove = { playlist -> playerViewModel.removeTrackFromPlaylist(currentTrack, playlist.id) },
            onCreatePlaylist = { name -> playerViewModel.createPlaylist(name) },
            onDismiss = { showPlaylistPicker = false },
        )
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

/**
 * The square artwork: shared by the portrait Column (full width, below the
 * top bar) and the wide/short Row branch (full height, beside the track
 * header), which pass the sizing and alignment through [modifier].
 */
@Composable
private fun PlayerArtwork(
    displayedCoverUrl: Any?,
    imageLoader: ImageLoader,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
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
}

/**
 * Title, then artist + album on the left with the queue/playlist/favourite
 * actions on the same row — shared by both [PlayerScreen] layout branches.
 */
/**
 * Favourite heart with a pop animation on toggle: quick grow, then a bouncy
 * settle back. The tint crossfades between white and the accent.
 */
@Composable
private fun FavoriteHeartButton(
    isFavorite: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 20.dp,
) {
    val scale = remember { Animatable(1f) }
    var initial by remember { mutableStateOf(true) }
    LaunchedEffect(isFavorite) {
        if (initial) {
            initial = false
            return@LaunchedEffect
        }
        scale.snapTo(1f)
        scale.animateTo(1.35f, animationSpec = tween(110, easing = FastOutSlowInEasing))
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    val tint by animateColorAsState(
        targetValue = if (isFavorite) accent else Color.White,
        label = "heartTint",
    )
    IconButton(onClick = onClick, modifier = modifier.size(buttonSize)) {
        Icon(
            // Same glyph as the web player: outline until favorited,
            // filled once it is.
            painter = painterResource(mageIconRes(if (isFavorite) "heart-fill" else "heart")),
            contentDescription = "Favourite",
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

@Composable
private fun PlayerTrackHeader(
    currentTrack: CatalogTrack?,
    favorites: Set<String>,
    accent: Color,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MarqueeText(
            text = currentTrack?.displayTitle ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        // Artist + album on the left, the secondary actions on the same
        // row — the web never centres the title under the artwork.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            // Favourite, add-to-playlist, queue, track menu.
            val isFavorite = currentTrack != null && currentTrack.id.toString() in favorites
            FavoriteHeartButton(
                isFavorite = isFavorite,
                accent = accent,
                onClick = onToggleFavorite,
                buttonSize = 32.dp,
                iconSize = 20.dp,
            )
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(mageIconRes("playlist-add")),
                    contentDescription = "Add to playlist",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(mageIconRes("stack")),
                    contentDescription = "Open queue",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onOpenActions, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(mageIconRes("dots")),
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The art-fullscreen bottom bar (mobile FS mode): small cover + title/artist
 * on the left, then favourite, the track menu, a circular progress ring and
 * next on the right. No maximize button on mobile — tapping the backdrop
 * exits FS.
 */
@Composable
private fun FsBottomBar(
    currentTrack: CatalogTrack?,
    displayedCoverUrl: Any?,
    imageLoader: ImageLoader,
    favorites: Set<String>,
    accent: Color,
    progress: Float,
    onToggleFavorite: () -> Unit,
    onOpenActions: () -> Unit,
    onNext: () -> Unit,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f), MuorgShapes.island)
            .glassSheer(MuorgShapes.island)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MuorgShapes.artSmall)
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            if (displayedCoverUrl != null) {
                AsyncImage(
                    model = displayedCoverUrl,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = currentTrack?.displayTitle ?: "Nothing playing",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            MarqueeText(
                text = currentTrack?.displayArtist ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.70f),
            )
        }
        val isFavorite = currentTrack != null && currentTrack.id.toString() in favorites
        FavoriteHeartButton(
            isFavorite = isFavorite,
            accent = accent,
            onClick = onToggleFavorite,
            buttonSize = 44.dp,
            iconSize = 28.dp,
        )
        // The progress ring doubles as the play/pause button.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(32.dp)) {
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(
                    color = Color.White.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
            Icon(
                painter = painterResource(mageIconRes(if (isPlaying) "pause" else "play")),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(mageIconRes("next")),
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onOpenActions, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(mageIconRes("dots")),
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
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

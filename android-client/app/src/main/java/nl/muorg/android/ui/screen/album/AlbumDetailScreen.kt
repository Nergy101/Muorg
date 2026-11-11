package nl.muorg.android.ui.screen.album

import nl.muorg.android.ui.component.OrcSpinner
import nl.muorg.android.ui.component.OrcLoading
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.icon.mageIconRes
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import androidx.compose.foundation.layout.statusBarsPadding
import nl.muorg.android.ui.theme.MuorgShapes
import nl.muorg.android.ui.theme.artworkAccent
import nl.muorg.android.ui.theme.themePrimary
import nl.muorg.android.ui.component.rememberDominantColor
import nl.muorg.android.ui.component.CoverBackdrop
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.EqualizerBars
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.AlbumActionsViewModel
import nl.muorg.android.ui.component.DownloadStatusIcon
import nl.muorg.android.ui.component.PlaylistPickerSheet
import nl.muorg.android.ui.component.SheetActionRow
import nl.muorg.android.ui.component.TrackContextSheet
import nl.muorg.android.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    filterPlaylistId: Int? = null,
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onOpenQueue: () -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumName, filterPlaylistId) {
        viewModel.loadAlbum(albumName, filterPlaylistId)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPlaylistState()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()

    // Same cover-derived backdrop as the web album view (mobile viewport):
    // shown only when the album actually has art, so coverless albums keep
    // the plain theme background.
    val coverModel: Any? = uiState.coverArtUri ?: uiState.coverTrackId?.let { "$baseUrl/api/tracks/$it/cover?size=1200" }
    val backdropActive = coverModel != null
    // The play button wears the accent the player will show for this album,
    // so pressing it does not change colour on arrival. Bland artwork falls
    // back to the theme primary, as in the player.
    val playAccent = artworkAccent(
        rememberDominantColor(url = coverModel, imageLoader = imageLoader, fallback = Color.Unspecified).color,
        onDark = true,
    )

    // The backdrop runs edge to edge, so over it the system bars need
    // dark-style icons even in the light theme.
    val window = LocalActivity.current?.window
    if (window != null && backdropActive) {
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
        topBar = {},
        containerColor = if (backdropActive) Color.Transparent else MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (backdropActive) {
                CoverBackdrop(coverUrl = coverModel, imageLoader = imageLoader)
            }
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        OrcLoading()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp),
                    ) {
                        item {
                            var showAlbumPlaylistSheet by remember { mutableStateOf(false) }
                            var showAlbumMenuSheet by remember { mutableStateOf(false) }
                            val albumFullIds = remember(uiState.trackPlaylistMembership, uiState.tracks, uiState.playlists) {
                                val allPaths = uiState.tracks.map { it.path }.toSet()
                                uiState.playlists
                                    .filter { pl -> allPaths.isNotEmpty() && allPaths.all { path -> pl.id in (uiState.trackPlaylistMembership[path] ?: emptySet()) } }
                                    .map { it.id }.toSet()
                            }
                            val albumPartialIds = remember(uiState.trackPlaylistMembership, uiState.tracks, uiState.playlists, albumFullIds) {
                                val allPaths = uiState.tracks.map { it.path }.toSet()
                                uiState.playlists
                                    .filter { pl -> pl.id !in albumFullIds && allPaths.any { path -> pl.id in (uiState.trackPlaylistMembership[path] ?: emptySet()) } }
                                    .map { it.id }.toSet()
                            }

                            val onHeader = if (backdropActive) Color.White else MaterialTheme.colorScheme.onSurface
                            val onHeaderMuted = if (backdropActive) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                            ) {
                                // Cover on the left, vertically centred against the
                                // text block: title, artist · year, then the actions.
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    coverModel?.let {
                                        AsyncImage(
                                            model = it,
                                            contentDescription = "Album cover",
                                            imageLoader = imageLoader,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(136.dp)
                                                .clip(MuorgShapes.art),
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = uiState.albumName,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = onHeader,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = buildString {
                                                if (uiState.artist.isNotBlank()) append(uiState.artist)
                                                uiState.year?.let {
                                                    if (isNotEmpty()) append(" · ")
                                                    append("$it")
                                                }
                                                if (isNotEmpty()) append(" · ")
                                                append("${uiState.tracks.size} tracks")
                                                val totalSecs = uiState.tracks.sumOf { it.durationSecs?.toInt() ?: 0 }
                                                if (totalSecs > 0) append(" · ${totalSecs / 60}m")
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = onHeaderMuted,
                                            maxLines = 3,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            androidx.compose.material3.FilledIconButton(
                                                onClick = {
                                                    uiState.tracks.firstOrNull()?.let { first ->
                                                        playerViewModel.playTrack(first, uiState.tracks)
                                                    }
                                                },
                                                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                                    containerColor = playAccent,
                                                    contentColor = Color.White,
                                                ),
                                            ) {
                                                Icon(
                                                    painter = painterResource(mageIconRes("play")),
                                                    contentDescription = "Play all",
                                                )
                                            }
                                            androidx.compose.material3.IconButton(onClick = { showAlbumPlaylistSheet = true }) {
                                                Icon(
                                                    painter = painterResource(mageIconRes("playlist-add")),
                                                    contentDescription = "Add album to playlist",
                                                    tint = if (backdropActive) Color.White.copy(alpha = 0.75f) else LocalContentColor.current,
                                                )
                                            }
                                            IconButton(onClick = { showAlbumMenuSheet = true }) {
                                                Icon(
                                                    painter = painterResource(mageIconRes("dots")),
                                                    contentDescription = "More album options",
                                                    tint = onHeaderMuted,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (backdropActive) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                            } else {
                                HorizontalDivider()
                            }

                            if (showAlbumPlaylistSheet) {
                                PlaylistPickerSheet(
                                    playlists = uiState.playlists,
                                    membershipIds = albumFullIds,
                                    partialMembershipIds = albumPartialIds,
                                    onAdd = { playlist -> viewModel.requestAddTracksToPlaylist(uiState.tracks, playlist.id) },
                                    onRemove = { playlist -> viewModel.removeAlbumFromPlaylist(playlist.id) },
                                    onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                    onDismiss = { showAlbumPlaylistSheet = false },
                                )
                            }
                            if (showAlbumMenuSheet) {
                                AlbumHeaderMenuSheet(
                                    albumName = uiState.albumName,
                                    artist = uiState.artist,
                                    onDismiss = { showAlbumMenuSheet = false },
                                    onViewArtist = { uiState.artist.takeIf { it.isNotBlank() }?.let(onViewArtist) },
                                )
                            }
                        }

                        items(
                            items = uiState.tracks,
                            key = { it.id },
                        ) { track ->
                            val membership = uiState.trackPlaylistMembership[track.path] ?: emptySet()
                            val coverModel: Any? = uiState.coverArtUri ?: uiState.coverTrackId?.let { "$baseUrl/api/tracks/$it/cover?size=1200" }
                            AlbumTrackRow(
                                track = track,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                coverModel = coverModel,
                                onDark = backdropActive,
                                isPlaying = playerState.currentTrack?.id == track.id &&
                                    playerState.isPlaying,
                                isLoading = playerState.pendingTrackId == track.id,
                                isFavorite = track.id.toString() in playerState.favorites,
                                playlists = uiState.playlists,
                                trackInPlaylistIds = membership,
                                onClick = { playerViewModel.playTrack(track, uiState.tracks) },
                                onAddToQueue = { playerViewModel.addToQueue(track) },
                                onToggleFavorite = { playerViewModel.toggleFavorite(track) },
                                onAddToPlaylist = { playlist ->
                                    viewModel.requestAddTracksToPlaylist(listOf(track), playlist.id)
                                },
                                onRemoveFromPlaylist = { playlist ->
                                    viewModel.removeTrackFromPlaylist(track, playlist.id)
                                },
                                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                onViewArtist = { track.artist?.let { onViewArtist(it) } },
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.addConflict?.let { conflict ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text("Some tracks already added") },
            text = {
                Text(
                    "${conflict.allTracks.size - conflict.newTracks.size} track(s) already in this playlist. " +
                        "Add the ${conflict.newTracks.size} new one(s), or add all?"
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = viewModel::confirmAddNewOnly) {
                    Text("Add ${conflict.newTracks.size} new")
                }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = viewModel::confirmAddAll) { Text("Add all") }
                    androidx.compose.material3.TextButton(onClick = viewModel::dismissConflict) { Text("Cancel") }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTrackRow(
    track: CatalogTrack,
    baseUrl: String,
    imageLoader: ImageLoader,
    coverModel: Any? = null,
    /** Render for the dark cover-derived backdrop (white text instead of theme). */
    onDark: Boolean = false,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    isFavorite: Boolean = false,
    playlists: List<Playlist>,
    trackInPlaylistIds: Set<Int> = emptySet(),
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (Playlist) -> Unit,
    onRemoveFromPlaylist: (Playlist) -> Unit = {},
    onCreatePlaylist: ((String) -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showTrackInfo by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    OrcSpinner(size = 20.dp)
                } else if (isPlaying) {
                    EqualizerBars(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(width = 20.dp, height = 14.dp),
                    )
                } else {
                    Text(
                        text = track.trackNumber?.toString() ?: "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (onDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Small album cover between the track number and the title.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (onDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Icon(
                        painter = painterResource(mageIconRes("music")),
                        contentDescription = null,
                        tint = if (onDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title over artist, as on the web — a bare title line reads as a
            // file list, not a track list.
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else if (onDark) Color.White
                    else MaterialTheme.colorScheme.onSurface,
                )
                MarqueeText(
                    text = track.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (onDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = track.format.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (track.isLossless) themePrimary()
                        else if (onDark) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = if (track.isLossless)
                            themePrimary().copy(alpha = 0.12f)
                        else if (onDark) Color.White.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                        shape = MuorgShapes.chip,
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = track.formattedDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = if (onDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(mageIconRes("dots")),
                    contentDescription = "Track actions",
                    tint = if (onDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

    }

    if (showMenu) {
        TrackContextSheet(
            track = track,
            baseUrl = baseUrl,
            imageLoader = imageLoader,
            isFavorite = isFavorite,
            onDismiss = { showMenu = false },
            onPlayNow = onClick,
            onAddToQueue = onAddToQueue,
            onToggleFavorite = { onToggleFavorite?.invoke() },
            onAddToPlaylist = { showPlaylistSheet = true },
            onTrackInfo = { showTrackInfo = true },
            onViewArtist = onViewArtist,
        )
    }

    if (showPlaylistSheet) {
        PlaylistPickerSheet(
            playlists = playlists,
            membershipIds = trackInPlaylistIds,
            onAdd = onAddToPlaylist,
            onRemove = onRemoveFromPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showPlaylistSheet = false },
        )
    }

    if (showTrackInfo) {
        AlertDialog(
            onDismissRequest = { showTrackInfo = false },
            title = { Text("Track info") },
            text = {
                Column {
                    AlbumTrackInfoField("Title", track.displayTitle)
                    AlbumTrackInfoField("Artist", track.displayArtist)
                    AlbumTrackInfoField("Album", track.displayAlbum)
                    track.year?.let { AlbumTrackInfoField("Year", it.toString()) }
                    AlbumTrackInfoField("Format", track.format.uppercase())
                    AlbumTrackInfoField("Duration", track.formattedDuration())
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackInfo = false }) { Text("Close") }
            },
        )
    }
}

/**
 * The album header's 3-dots menu: a bottom sheet with only the offline
 * download toggle and view-artist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumHeaderMenuSheet(
    albumName: String,
    artist: String,
    onDismiss: () -> Unit,
    onViewArtist: () -> Unit,
    viewModel: AlbumActionsViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloadedIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()

    LaunchedEffect(albumName) { viewModel.load(albumName) }

    val key = "$albumName|$artist"
    val downloading = progress.isActive && progress.albumKey == key
    val ringProgress = if (downloading && progress.totalTracks > 0) {
        progress.completedTracks.toFloat() / progress.totalTracks
    } else null
    val downloaded = tracks.isNotEmpty() && tracks.all { it.id in downloadedIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        if (canDownload) {
            SheetActionRow(
                title = when {
                    downloading -> "Downloading…"
                    downloaded -> "Remove offline download"
                    else -> "Download for offline"
                },
                subtitle = if (downloading) "${progress.completedTracks} of ${progress.totalTracks} tracks" else null,
                onClick = {
                    when {
                        downloading -> Unit
                        downloaded -> viewModel.removeDownload()
                        else -> viewModel.download(key)
                    }
                },
            ) {
                DownloadStatusIcon(downloaded = downloaded && !downloading, progress = ringProgress)
            }
        }
        SheetActionRow(
            "View artist",
            subtitle = artist,
            onClick = { onViewArtist(); onDismiss() },
        ) {
            MageIcon("user", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun AlbumTrackInfoField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

package nl.muorg.android.ui.screen.playlist

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import kotlin.math.roundToInt
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.AlbumDisplayMode
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgGreenLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistAlbumsScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    viewModel: PlaylistAlbumsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentAlbum = playerState.currentTrack?.displayAlbum
    val albumViewStyle = uiState.viewStyle

    // Reorder state
    var reorderMode by remember { mutableStateOf(false) }
    var hasUnsavedOrder by remember { mutableStateOf(false) }
    var reorderedPaths by remember { mutableStateOf<List<String>?>(null) }

    val density = LocalDensity.current
    val estimatedItemHeightPx = remember(density) { with(density) { 56.dp.toPx() } }
    var draggedPath by remember { mutableStateOf<String?>(null) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    val trackList = reorderedPaths?.let { paths ->
        val trackByPath = uiState.allTracks.associateBy { it.path }
        paths.mapNotNull { trackByPath[it] }
    } ?: uiState.allTracks

    val draggedFromIndex = remember(draggedPath, trackList) {
        draggedPath?.let { p -> trackList.indexOfFirst { it.path == p } } ?: -1
    }
    val dropTargetIndex = if (draggedFromIndex >= 0) {
        val rowsMoved = (dragAccumY / estimatedItemHeightPx).roundToInt()
        (draggedFromIndex + rowsMoved).coerceIn(0, (trackList.size - 1).coerceAtLeast(0))
    } else -1

    val displayList = remember(trackList, draggedFromIndex, dropTargetIndex) {
        if (draggedFromIndex < 0 || dropTargetIndex < 0 || draggedFromIndex == dropTargetIndex) {
            trackList
        } else {
            trackList.toMutableList().apply {
                val item = removeAt(draggedFromIndex)
                add(dropTargetIndex.coerceIn(0, size), item)
            }
        }
    }
    val dragTranslationY = if (draggedFromIndex >= 0 && dropTargetIndex >= 0) {
        val displacement = (dropTargetIndex - draggedFromIndex) * estimatedItemHeightPx
        dragAccumY - displacement
    } else 0f

    LaunchedEffect(albumViewStyle) {
        if (albumViewStyle != "tracks") {
            reorderMode = false
            hasUnsavedOrder = false
            reorderedPaths = null
        }
    }

    val sortedTracks = remember(uiState.allTracks) {
        uiState.allTracks.sortedWith(
            compareBy({ it.displayAlbum.lowercase() }, { it.discNumber ?: 0 }, { it.trackNumber ?: 0 })
        )
    }

    uiState.addToastMsg?.let { msg ->
        LaunchedEffect(msg) { viewModel.clearToast() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val playlist = uiState.playlist
                    if (playlist != null) {
                        val title = if (!playlist.icon.isNullOrBlank()) "${playlist.icon} ${playlist.name}" else playlist.name
                        Text(text = title, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                        painter = painterResource(mageIconRes("chevron-left")), contentDescription = "Back")
                    }
                },
                actions = {
                    if (albumViewStyle == "tracks") {
                        Column(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (hasUnsavedOrder) {
                                            val paths = displayList.map { it.path }
                                            reorderedPaths = null
                                            reorderMode = false
                                            hasUnsavedOrder = false
                                            viewModel.reorderTracks(paths)
                                        } else {
                                            reorderMode = !reorderMode
                                            if (!reorderMode) reorderedPaths = null
                                        }
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .widthIn(min = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = if (hasUnsavedOrder) Icons.Filled.Save else Icons.Filled.LowPriority,
                                contentDescription = null,
                                tint = if (reorderMode || hasUnsavedOrder)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (hasUnsavedOrder) "save" else "reorder",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reorderMode || hasUnsavedOrder)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    viewModel.setViewStyle(when (albumViewStyle) {
                                        "grid" -> "list"
                                        "list" -> "tracks"
                                        else -> "grid"
                                    })
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .widthIn(min = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = when (albumViewStyle) {
                                "grid" -> Icons.AutoMirrored.Filled.ViewList
                                "list" -> Icons.Filled.MusicNote
                                else -> Icons.Filled.GridView
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = albumViewStyle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.error!!.takeIf { it.isNotEmpty() } ?: "Failed to load playlist",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            albumViewStyle == "tracks" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomInset.current + 4.dp),
                ) {
                    items(displayList, key = { it.path }) { track ->
                        val isDragged = track.path == draggedPath
                        val membership = uiState.trackPlaylistMembership[track.path] ?: emptySet()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragged) dragTranslationY else 0f }
                                .then(
                                    if (isDragged) Modifier.border(2.dp, MuorgGreenLight, RoundedCornerShape(8.dp))
                                    else Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (reorderMode) {
                                PlaylistDragHandle(
                                    isDragged = isDragged,
                                    onDragStart = {
                                        draggedPath = track.path
                                        dragAccumY = 0f
                                    },
                                    onDragDelta = { dy -> dragAccumY += dy },
                                    onDragEnd = {
                                        val from = draggedFromIndex
                                        val to = dropTargetIndex
                                        if (from >= 0 && to >= 0 && from != to) {
                                            val newList = trackList.toMutableList().apply {
                                                val item = removeAt(from)
                                                add(to.coerceIn(0, size), item)
                                            }
                                            reorderedPaths = newList.map { it.path }
                                            hasUnsavedOrder = true
                                        }
                                        draggedPath = null
                                        dragAccumY = 0f
                                    },
                                )
                            }
                            TrackRow(
                                modifier = Modifier.weight(1f),
                                track = track,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                                isFavorite = track.id.toString() in playerState.favorites,
                                playlists = uiState.playlists,
                                trackInPlaylistIds = membership,
                                onTrackClick = { if (!reorderMode) playerViewModel.playTrack(track, displayList) },
                                onAddToQueue = { playerViewModel.addToQueue(track) },
                                onToggleFavorite = { playerViewModel.toggleFavorite(track) },
                                onAddToPlaylist = { playlist ->
                                    viewModel.requestAddTracksToPlaylist(listOf(track), playlist.id)
                                },
                                onRemoveFromPlaylist = { _ ->
                                    viewModel.removeTrackFromThisPlaylist(track.path)
                                },
                                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                onViewAlbum = { onAlbumClick(track.displayAlbum) },
                                onViewArtist = { onViewArtist(track.displayArtist) },
                            )
                        }
                    }
                }
            }
            uiState.albums.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = "No albums in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            albumViewStyle == "list" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomInset.current + 4.dp),
                ) {
                    items(uiState.albums, key = { it.albumName }) { album ->
                        val albumPaths = remember(album.albumName, uiState.allTracks) {
                            uiState.allTracks.filter { it.displayAlbum == album.albumName }.map { it.path }
                        }
                        val albumFullIds = remember(albumPaths, uiState.trackPlaylistMembership, uiState.playlists) {
                            uiState.playlists.filter { pl ->
                                albumPaths.isNotEmpty() && albumPaths.all { path -> pl.id in (uiState.trackPlaylistMembership[path] ?: emptySet()) }
                            }.map { it.id }.toSet()
                        }
                        val albumPartialIds = remember(albumPaths, uiState.trackPlaylistMembership, uiState.playlists, albumFullIds) {
                            uiState.playlists.filter { pl ->
                                pl.id !in albumFullIds && albumPaths.any { path -> pl.id in (uiState.trackPlaylistMembership[path] ?: emptySet()) }
                            }.map { it.id }.toSet()
                        }
                        AlbumCard(
                            album = album,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            isActive = album.albumName == currentAlbum,
                            onClick = { onAlbumClick(album.albumName) },
                            displayMode = AlbumDisplayMode.LIST,
                            playlists = uiState.playlists,
                            albumInPlaylistIds = albumFullIds,
                            albumPartialPlaylistIds = albumPartialIds,
                            onPlayNow = {
                                val tracks = viewModel.getTracksForAlbum(album.albumName)
                                tracks.firstOrNull()?.let { playerViewModel.playTrack(it, tracks) }
                            },
                            onAddToQueue = {
                                viewModel.getTracksForAlbum(album.albumName).forEach { playerViewModel.addToQueue(it) }
                            },
                            onViewArtist = { onViewArtist(album.artist) },
                            onAddToPlaylist = { playlist ->
                                viewModel.requestAddTracksToPlaylist(viewModel.getTracksForAlbum(album.albumName), playlist.id)
                            },
                            onRemoveFromPlaylist = { playlist ->
                                viewModel.removeAlbumFromPlaylist(album.albumName, playlist.id)
                            },
                            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = LocalBottomInset.current + 8.dp),
                ) {
                    items(uiState.albums, key = { it.albumName }) { album ->
                        val albumPaths = remember(album.albumName, uiState.allTracks) {
                            uiState.allTracks.filter { it.displayAlbum == album.albumName }.map { it.path }
                        }
                        val albumFullIds = remember(albumPaths, uiState.trackPlaylistMembership, uiState.playlists) {
                            uiState.playlists.filter { pl ->
                                albumPaths.isNotEmpty() && albumPaths.all { path -> pl.id in (uiState.trackPlaylistMembership[path] ?: emptySet()) }
                            }.map { it.id }.toSet()
                        }
                        val albumPartialIds = remember(albumPaths, uiState.trackPlaylistMembership, uiState.playlists, albumFullIds) {
                            uiState.playlists.filter { pl ->
                                pl.id !in albumFullIds && albumPaths.any { path -> pl.id in (uiState.trackPlaylistMembership[path] ?: emptySet()) }
                            }.map { it.id }.toSet()
                        }
                        AlbumCard(
                            album = album,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            isActive = album.albumName == currentAlbum,
                            onClick = { onAlbumClick(album.albumName) },
                            modifier = Modifier.padding(4.dp),
                            playlists = uiState.playlists,
                            albumInPlaylistIds = albumFullIds,
                            albumPartialPlaylistIds = albumPartialIds,
                            onPlayNow = {
                                val tracks = viewModel.getTracksForAlbum(album.albumName)
                                tracks.firstOrNull()?.let { playerViewModel.playTrack(it, tracks) }
                            },
                            onAddToQueue = {
                                viewModel.getTracksForAlbum(album.albumName).forEach { playerViewModel.addToQueue(it) }
                            },
                            onViewArtist = { onViewArtist(album.artist) },
                            onAddToPlaylist = { playlist ->
                                viewModel.requestAddTracksToPlaylist(viewModel.getTracksForAlbum(album.albumName), playlist.id)
                            },
                            onRemoveFromPlaylist = { playlist ->
                                viewModel.removeAlbumFromPlaylist(album.albumName, playlist.id)
                            },
                            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                        )
                    }
                }
            }
        }
    }

    uiState.addConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text("Some tracks already added") },
            text = {
                Text(
                    "${conflict.allTracks.size - conflict.newTracks.size} track(s) are already in this playlist. " +
                        "Add the ${conflict.newTracks.size} new one(s), or add all?"
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmAddNewOnly) { Text("Add ${conflict.newTracks.size} new") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::confirmAddAll) { Text("Add all") }
                    TextButton(onClick = viewModel::dismissConflict) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun PlaylistDragHandle(
    isDragged: Boolean,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(Unit) {
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
            contentDescription = "Drag",
            tint = if (isDragged) MuorgGreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

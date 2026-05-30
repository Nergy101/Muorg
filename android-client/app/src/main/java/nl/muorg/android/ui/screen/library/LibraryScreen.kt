package nl.muorg.android.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.AlbumDisplayMode
import nl.muorg.android.ui.component.PlayerBar
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onAlbumClick: (String) -> Unit,
    onPlayerBarClick: () -> Unit,
    showPlayerBar: Boolean,
    artistFilter: String? = null,
    onOpenQueue: () -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    scrollToActiveSignal: Flow<Unit>? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentAlbum = playerState.currentTrack?.displayAlbum
    var showSortMenu by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(viewModel.rawSearchQuery) }
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.sortMode, uiState.sortAscending) {
        delay(50L)
        lazyGridState.scrollToItem(0)
        lazyListState.scrollToItem(0)
    }

    LaunchedEffect(artistFilter) {
        if (artistFilter != null) viewModel.applyArtistFilter(artistFilter)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPlaylistState()
        }
    }

    val currentAlbumRef = rememberUpdatedState(playerState.currentTrack?.displayAlbum)
    val filteredAlbumsRef = rememberUpdatedState(uiState.filteredAlbums)
    val albumViewStyleRef = rememberUpdatedState(uiState.albumViewStyle)
    LaunchedEffect(scrollToActiveSignal) {
        scrollToActiveSignal?.collect {
            val currentAlbum = currentAlbumRef.value ?: return@collect
            val albums = filteredAlbumsRef.value
            val index = albums.indexOfFirst { it.albumName == currentAlbum }
            if (index >= 0) {
                if (albumViewStyleRef.value == "list") {
                    lazyListState.animateScrollToItem(index)
                } else {
                    lazyGridState.animateScrollToItem(index)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it; viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search albums, artists…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchText = ""
                        viewModel.onSearchQueryChange("")
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        AnimatedVisibility(visible = uiState.artistFilter != null) {
            Row(modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                AssistChip(
                    onClick = viewModel::clearArtistFilter,
                    label = { Text(uiState.artistFilter ?: "") },
                    leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Filled.Close, "Clear", Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        labelColor = MaterialTheme.colorScheme.primary,
                        leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        trailingIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        // Sort dropdown + shuffle button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(
                    onClick = { showSortMenu = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("Sort: ${uiState.sortMode.label}")
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                coroutineScope.launch {
                                    lazyGridState.scrollToItem(0)
                                    lazyListState.scrollToItem(0)
                                }
                                viewModel.setSortMode(mode)
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (uiState.sortMode == mode) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }

            IconButton(onClick = {
                coroutineScope.launch {
                    lazyGridState.scrollToItem(0)
                    lazyListState.scrollToItem(0)
                }
                viewModel.toggleSortDirection()
            }) {
                Icon(
                    imageVector = if (uiState.sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = if (uiState.sortAscending) "Sort ascending" else "Sort descending",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = viewModel::toggleAlbumViewStyle) {
                Icon(
                    imageVector = if (uiState.albumViewStyle == "list") Icons.Filled.GridView else Icons.Filled.ViewList,
                    contentDescription = if (uiState.albumViewStyle == "list") "Switch to grid" else "Switch to list",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = {
                    val tracks = uiState.filteredTracks.ifEmpty {
                        uiState.filteredAlbums.flatMap { viewModel.getTracksForAlbum(it.albumName) }
                    }
                    if (tracks.isNotEmpty()) {
                        playerViewModel.enableShuffle()
                        playerViewModel.playTrack(tracks.random(), tracks)
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                uiState.isInitialScanning -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (uiState.initialScanTotal > 0)
                                "Scanning library… ${uiState.initialScanProgress} / ${uiState.initialScanTotal}"
                            else "Scanning library…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (uiState.initialScanTotal > 0) {
                            LinearProgressIndicator(
                                progress = { uiState.initialScanProgress.toFloat() / uiState.initialScanTotal },
                                modifier = Modifier.fillMaxWidth(0.6f),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                }
                searchText.isNotEmpty() || uiState.viewMode == ViewMode.TRACKS -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(
                            items = uiState.filteredTracks,
                            key = { it.id },
                        ) { track ->
                            val membership = uiState.trackPlaylistMembership[track.path] ?: emptySet()
                            TrackRow(
                                track = track,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                isPlaying = playerState.currentTrack?.id == track.id &&
                                    playerState.isPlaying,
                                isFavorite = track.id.toString() in playerState.favorites,
                                playlists = uiState.playlists,
                                trackInPlaylistIds = membership,
                                onTrackClick = {
                                    playerViewModel.playTrack(track, uiState.filteredTracks)
                                },
                                onAddToQueue = { playerViewModel.addToQueue(track) },
                                onToggleFavorite = { playerViewModel.toggleFavorite(track) },
                                onAddToPlaylist = { playlist ->
                                    viewModel.requestAddTracksToPlaylist(listOf(track), playlist.id)
                                },
                                onRemoveFromPlaylist = { playlist ->
                                    viewModel.removeTrackFromPlaylist(track, playlist.id)
                                },
                                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                onViewAlbum = { onAlbumClick(track.displayAlbum) },
                                onViewArtist = { onViewArtist(track.displayArtist) },
                            )
                        }
                    }
                }
                uiState.albumViewStyle == "list" -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(
                            items = uiState.filteredAlbums,
                            key = { it.albumName },
                        ) { album ->
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
                                    tracks.firstOrNull()?.let {
                                        playerViewModel.playTrack(it, tracks)
                                    }
                                },
                                onAddToQueue = {
                                    playerViewModel.addTracksToQueue(
                                        viewModel.getTracksForAlbum(album.albumName)
                                    )
                                },
                                onViewArtist = { onViewArtist(album.artist) },
                                onAddToPlaylist = { playlist ->
                                    val tracks = viewModel.getTracksForAlbum(album.albumName)
                                    viewModel.requestAddTracksToPlaylist(tracks, playlist.id)
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
                        state = lazyGridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(
                            items = uiState.filteredAlbums,
                        ) { album ->
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
                                    tracks.firstOrNull()?.let {
                                        playerViewModel.playTrack(it, tracks)
                                    }
                                },
                                onAddToQueue = {
                                    playerViewModel.addTracksToQueue(
                                        viewModel.getTracksForAlbum(album.albumName)
                                    )
                                },
                                onViewArtist = { onViewArtist(album.artist) },
                                onAddToPlaylist = { playlist ->
                                    val tracks = viewModel.getTracksForAlbum(album.albumName)
                                    viewModel.requestAddTracksToPlaylist(tracks, playlist.id)
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

        if (showPlayerBar && playerState.currentTrack != null) {
            PlayerBar(
                playerState = playerState,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onClick = onPlayerBarClick,
                onPlayPause = playerViewModel::playPause,
                onNext = playerViewModel::skipNext,
                onOpenQueue = onOpenQueue,
            )
        }
    }

    uiState.addConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text("Some tracks already added") },
            text = {
                Text(
                    "${conflict.allTracks.size - conflict.newTracks.size} track(s) already in this playlist. " +
                        "Add the ${conflict.newTracks.size} new one(s), or add all?"
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmAddNewOnly) {
                    Text("Add ${conflict.newTracks.size} new")
                }
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

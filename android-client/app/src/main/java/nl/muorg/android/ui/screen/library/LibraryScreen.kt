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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.glass.glassField
import nl.muorg.android.ui.theme.MuorgShapes
import nl.muorg.android.ui.icon.mageIconRes
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.ProvideLibraryChrome
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.AlbumDisplayMode
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.data.preferences.SearchHistoryEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onAlbumClick: (String) -> Unit,
    artistFilter: String? = null,
    onOpenQueue: () -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    scrollToActiveSignal: Flow<Unit>? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val currentAlbum = playerState.currentTrack?.displayAlbum
    var showSortMenu by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(viewModel.rawSearchQuery) }
    var recentSearches by remember { mutableStateOf(viewModel.getRecentSearches()) }
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

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

    // The web keeps search and the sort/filter row INSIDE the bottom island,
    // directly under the mini player, not at the top of the view. This
    // publishes them there; nothing is rendered in place.
    ProvideLibraryChrome {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    viewModel.onSearchQueryChange(it)
                    if (it.isNotEmpty()) {
                        // Refresh recent searches to include the new one
                        recentSearches = viewModel.getRecentSearches()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .glassField(MuorgShapes.pill),
                placeholder = {
                    Text(
                        "Search albums, artists…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("search")), contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            viewModel.onSearchQueryChange("")
                            recentSearches = viewModel.getRecentSearches()
                        }) {
                            Icon(
                        painter = painterResource(mageIconRes("multiply")), contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MuorgShapes.pill,
                // The pill itself is the `glassField` material; the text field
                // must not draw a second outline on top of it.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )

            // Search history chips — show when search field is empty and there's history
            if (searchText.isEmpty() && recentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(mageIconRes("clock")),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            viewModel.clearSearchHistory()
                            recentSearches = emptyList()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Clear all", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    recentSearches.take(10).forEach { entry ->
                        AssistChip(
                            onClick = {
                                searchText = entry.query
                                viewModel.onSearchQueryChange(entry.query)
                                recentSearches = viewModel.getRecentSearches()
                            },
                            label = { Text(entry.query, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(
                        painter = painterResource(mageIconRes("clock")),
                                    contentDescription = null,
                                    Modifier.size(14.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            shape = RoundedCornerShape(20),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState.artistFilter != null) {
                Row(modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                    AssistChip(
                        onClick = viewModel::clearArtistFilter,
                        label = { Text(uiState.artistFilter ?: "") },
                        leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("user")), null, Modifier.size(16.dp)) },
                        trailingIcon = { Icon(
                        painter = painterResource(mageIconRes("multiply")), "Clear", Modifier.size(16.dp)) },
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
                        painter = painterResource(mageIconRes("chevron-down")),
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

                IconButton(onClick = {
                    val next = when (uiState.albumViewStyle) {
                        "grid" -> "list"
                        "list" -> "tracks"
                        else -> "grid"
                    }
                    viewModel.setAlbumViewStyle(next)
                }) {
                    Icon(
                        imageVector = when (uiState.albumViewStyle) {
                            "grid" -> Icons.Filled.GridView
                            "list" -> Icons.AutoMirrored.Filled.ViewList
                            else -> Icons.Filled.MusicNote
                        },
                        contentDescription = "Switch layout",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = {
                        val tracks = uiState.filteredTracks.ifEmpty {
                            uiState.filteredAlbums.flatMap { viewModel.getTracksForAlbum(it.albumName) }
                        }
                        playerViewModel.startShuffleAll(tracks)
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(mageIconRes("exchange")),
                        contentDescription = "Shuffle play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                uiState.filteredTracks.isEmpty() && uiState.filteredAlbums.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                        painter = painterResource(mageIconRes("music")),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            text = if (searchText.isNotEmpty()) "No results for \"$searchText\""
                                   else "No tracks in library",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                searchText.isNotEmpty() || uiState.viewMode == ViewMode.TRACKS || uiState.albumViewStyle == "tracks" -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomInset.current + 4.dp),
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
                        contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomInset.current + 4.dp),
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
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = LocalBottomInset.current + 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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

private fun formatSleepTimer(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

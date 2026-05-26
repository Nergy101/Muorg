package nl.muorg.android.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import nl.muorg.android.ui.component.AlbumCard
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
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(artistFilter) {
        if (artistFilter != null) viewModel.applyArtistFilter(artistFilter)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text("Search albums, artists…") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {}

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

            Spacer(modifier = Modifier.weight(1f))

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
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                }
                uiState.viewMode == ViewMode.TRACKS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(
                            items = uiState.filteredTracks,
                            key = { it.id },
                        ) { track ->
                            TrackRow(
                                track = track,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                isPlaying = playerState.currentTrack?.id == track.id &&
                                    playerState.isPlaying,
                                playlists = uiState.playlists,
                                onTrackClick = {
                                    playerViewModel.playTrack(track, uiState.filteredTracks)
                                },
                                onAddToPlaylist = { playlist ->
                                    viewModel.addTracksToPlaylist(listOf(track.id), playlist.id)
                                },
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(
                            items = uiState.filteredAlbums,
                            key = { it.albumName },
                        ) { album ->
                            AlbumCard(
                                album = album,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onClick = { onAlbumClick(album.albumName) },
                                modifier = Modifier.padding(4.dp),
                                playlists = uiState.playlists,
                                onPlayNow = {
                                    val tracks = viewModel.getTracksForAlbum(album.albumName)
                                    tracks.firstOrNull()?.let {
                                        playerViewModel.playTrack(it, tracks)
                                    }
                                },
                                onAddToPlaylist = { playlist ->
                                    val trackIds = viewModel.getTracksForAlbum(album.albumName).map { it.id }
                                    viewModel.addTracksToPlaylist(trackIds, playlist.id)
                                },
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
}

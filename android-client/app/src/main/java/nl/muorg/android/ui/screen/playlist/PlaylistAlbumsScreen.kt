package nl.muorg.android.ui.screen.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.PlayerBar
import nl.muorg.android.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistAlbumsScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    onPlayerBarClick: () -> Unit,
    showPlayerBar: Boolean,
    onOpenQueue: () -> Unit = {},
    viewModel: PlaylistAlbumsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentAlbum = playerState.currentTrack?.displayAlbum

    uiState.addToastMsg?.let { msg ->
        LaunchedEffect(msg) {
            viewModel.clearToast()
        }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
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
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.albums.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "No albums in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(uiState.albums, key = { it.albumName }) { album ->
                        AlbumCard(
                            album = album,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            isActive = album.albumName == currentAlbum,
                            onClick = { onAlbumClick(album.albumName) },
                            modifier = Modifier.padding(4.dp),
                            playlists = uiState.playlists,
                            onPlayNow = {
                                val tracks = viewModel.getTracksForAlbum(album.albumName)
                                tracks.firstOrNull()?.let { playerViewModel.playTrack(it, tracks) }
                            },
                            onAddToPlaylist = { playlist ->
                                val tracks = viewModel.getTracksForAlbum(album.albumName)
                                viewModel.requestAddTracksToPlaylist(tracks, playlist.id)
                            },
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


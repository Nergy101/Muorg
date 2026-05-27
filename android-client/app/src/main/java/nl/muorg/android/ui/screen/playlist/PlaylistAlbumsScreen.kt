package nl.muorg.android.ui.screen.playlist

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import nl.muorg.android.data.repository.PlaylistTrackEntry
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
            uiState.isLocalMode -> {
                LocalPlaylistContent(
                    entries = uiState.localEntries,
                    playerState = playerState,
                    innerPadding = innerPadding,
                    onPlayTrack = { track ->
                        val playableTracks = uiState.localEntries.mapNotNull { it.track }
                        playerViewModel.playTrack(track, playableTracks)
                    },
                    onRemoveEntry = { entry -> viewModel.removeTrackFromThisPlaylist(entry.filePath) },
                )
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

@Composable
private fun LocalPlaylistContent(
    entries: List<PlaylistTrackEntry>,
    playerState: nl.muorg.android.player.PlayerState,
    innerPadding: PaddingValues,
    onPlayTrack: (nl.muorg.android.data.api.CatalogTrack) -> Unit,
    onRemoveEntry: (PlaylistTrackEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("No tracks in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(entries, key = { it.filePath }) { entry ->
            val isAvailable = entry.track != null
            val isPlaying = entry.track != null && playerState.currentTrack?.id == entry.track.id && playerState.isPlaying

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isAvailable) Modifier.alpha(0.4f) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.storedTitle ?: "Unknown track",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (!isAvailable) TextDecoration.LineThrough else null,
                        ),
                        color = if (isPlaying) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (!isAvailable) {
                        Text(
                            text = "File not found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        )
                    } else if (entry.storedArtist != null) {
                        Text(
                            text = entry.storedArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onRemoveEntry(entry) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

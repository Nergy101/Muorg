package nl.muorg.android.ui.screen.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.ui.component.EqualizerBars
import nl.muorg.android.ui.component.PlayerBar
import nl.muorg.android.ui.player.PlayerViewModel

private enum class TrackMenuLevel { HIDDEN, MAIN, PLAYLISTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
    onPlayerBarClick: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumName) {
        viewModel.loadAlbum(albumName)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {},
        bottomBar = {
            if (playerState.currentTrack != null) {
                PlayerBar(
                    playerState = playerState,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onClick = onPlayerBarClick,
                    onPlayPause = playerViewModel::playPause,
                    onNext = playerViewModel::skipNext,
                )
            }
        }
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
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val coverModel: Any? = uiState.coverArtUri ?: uiState.coverTrackId?.let { "$baseUrl/api/tracks/$it/cover" }
                            coverModel?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = "Album cover",
                                    imageLoader = imageLoader,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.albumName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = uiState.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        uiState.year?.let { append("$it · ") }
                                        append("${uiState.tracks.size} tracks")
                                        val totalSecs = uiState.tracks.sumOf {
                                            it.durationSecs?.toInt() ?: 0
                                        }
                                        if (totalSecs > 0) {
                                            val mins = totalSecs / 60
                                            append(" · ${mins}m")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            androidx.compose.material3.FilledIconButton(
                                onClick = {
                                    uiState.tracks.firstOrNull()?.let { first ->
                                        playerViewModel.playTrack(first, uiState.tracks)
                                    }
                                },
                                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                            }
                        }
                        HorizontalDivider()
                    }

                    items(
                        items = uiState.tracks,
                        key = { it.id },
                    ) { track ->
                        AlbumTrackRow(
                            track = track,
                            isPlaying = playerState.currentTrack?.id == track.id &&
                                playerState.isPlaying,
                            playlists = uiState.playlists,
                            onClick = { playerViewModel.playTrack(track, uiState.tracks) },
                            onAddToPlaylist = { playlist ->
                                viewModel.addTracksToPlaylist(listOf(track.id), playlist.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTrackRow(
    track: CatalogTrack,
    isPlaying: Boolean,
    playlists: List<Playlist>,
    onClick: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
) {
    var menuLevel by remember { mutableStateOf(TrackMenuLevel.HIDDEN) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuLevel = TrackMenuLevel.MAIN },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    EqualizerBars(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(width = 20.dp, height = 14.dp),
                    )
                } else {
                    Text(
                        text = track.trackNumber?.toString() ?: "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!track.artist.isNullOrBlank() && track.artist != track.albumArtist) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = track.format.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (track.format == "flac") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = if (track.format == "flac")
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = track.formattedDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Level 1: main actions
        DropdownMenu(
            expanded = menuLevel == TrackMenuLevel.MAIN,
            onDismissRequest = { menuLevel = TrackMenuLevel.HIDDEN },
        ) {
            DropdownMenuItem(
                text = { Text("Play now") },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    onClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                trailingIcon = { Text("›", style = MaterialTheme.typography.titleMedium) },
                onClick = { menuLevel = TrackMenuLevel.PLAYLISTS },
            )
        }

        // Level 2: playlist selection
        DropdownMenu(
            expanded = menuLevel == TrackMenuLevel.PLAYLISTS,
            onDismissRequest = { menuLevel = TrackMenuLevel.HIDDEN },
        ) {
            DropdownMenuItem(
                text = { Text("Back") },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { menuLevel = TrackMenuLevel.MAIN },
            )
            if (playlists.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false,
                )
            } else {
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text("${playlist.icon ?: "🎵"}  ${playlist.name}") },
                        onClick = {
                            onAddToPlaylist(playlist)
                            menuLevel = TrackMenuLevel.HIDDEN
                        },
                    )
                }
            }
        }
    }
}

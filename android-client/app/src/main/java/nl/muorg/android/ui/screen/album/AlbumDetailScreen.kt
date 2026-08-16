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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.EqualizerBars
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.PlaylistPickerSheet
import nl.muorg.android.ui.player.PlayerViewModel

private enum class TrackMenuLevel { HIDDEN, MAIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    filterPlaylistId: Int? = null,
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {},
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
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
                    contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp),
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
                                MarqueeText(
                                    text = uiState.albumName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                MarqueeText(
                                    text = uiState.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Icon(
                        painter = painterResource(mageIconRes("play")), contentDescription = "Play all")
                            }

                            var showAlbumPlaylistSheet by remember { mutableStateOf(false) }
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
                            androidx.compose.material3.IconButton(onClick = { showAlbumPlaylistSheet = true }) {
                                Icon(
                        painter = painterResource(mageIconRes("playlist-add")), contentDescription = "Add album to playlist")
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
                        }
                        HorizontalDivider()
                    }

                    items(
                        items = uiState.tracks,
                        key = { it.id },
                    ) { track ->
                        val membership = uiState.trackPlaylistMembership[track.path] ?: emptySet()
                        AlbumTrackRow(
                            track = track,
                            isPlaying = playerState.currentTrack?.id == track.id &&
                                playerState.isPlaying,
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
    isPlaying: Boolean,
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
    var menuLevel by remember { mutableStateOf(TrackMenuLevel.HIDDEN) }
    var showTrackInfo by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

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
                MarqueeText(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
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
                leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("play")), contentDescription = null) },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    onClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("stack")), contentDescription = null) },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    onAddToQueue?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                leadingIcon = {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    onToggleFavorite?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("playlist-add")), contentDescription = null) },
                onClick = { menuLevel = TrackMenuLevel.HIDDEN; showPlaylistSheet = true },
            )
            DropdownMenuItem(
                text = { Text("Track info") },
                leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("information-circle")), contentDescription = null) },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    showTrackInfo = true
                },
            )
            if (onViewArtist != null) {
                DropdownMenuItem(
                    text = { Text("View artist") },
                    leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("user")), contentDescription = null) },
                    onClick = {
                        menuLevel = TrackMenuLevel.HIDDEN
                        onViewArtist()
                    },
                )
            }
        }

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

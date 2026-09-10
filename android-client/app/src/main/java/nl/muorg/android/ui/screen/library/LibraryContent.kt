package nl.muorg.android.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import nl.muorg.android.player.PlayerState
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.AlbumDisplayMode
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel

/**
 * What the library shows: a spinner, an empty state, or one of the three
 * layouts — a track list, an album list, an album grid.
 *
 * Split out of [LibraryScreen], which was a single 551-line composable holding
 * the search chrome, all four of these branches and the dialogs at once.
 */
@Composable
fun LibraryContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    playerState: PlayerState,
    searchText: String,
    baseUrl: String,
    imageLoader: ImageLoader,
    lazyListState: LazyListState,
    lazyGridState: LazyGridState,
    onAlbumClick: (String) -> Unit,
    onViewArtist: (String) -> Unit,
) {
    val currentAlbum = playerState.currentTrack?.displayAlbum

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            when {
                // Only block on the very first page: the remote catalog streams
                // in page by page, so later pages must grow the list in place
                // instead of replacing it with a spinner.
                uiState.isLoading && uiState.allTracks.isEmpty() -> {
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
}

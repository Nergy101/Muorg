package nl.muorg.android.ui.screen.artist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.EmptyState
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.OrcLoading
import nl.muorg.android.ui.component.OrcVariant
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.layout.rememberGridColumns
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.util.Discography

/**
 * The artist page — the web's `ArtistDetailView`: albums, EPs, singles and
 * "appears on" as cover shelves, then the loose (album-less) tracks.
 */
@Composable
fun ArtistScreen(
    artistName: String,
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    LaunchedEffect(artistName) { viewModel.load(artistName) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val currentAlbum = playerState.currentTrack?.displayAlbum

    fun play(tracks: List<CatalogTrack>) {
        tracks.firstOrNull()?.let { playerViewModel.playTrack(it, tracks) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                MageIcon(
                    name = "chevron-left",
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!uiState.isLoading && uiState.trackCount > 0) {
                    Text(
                        text = metaLine(uiState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (uiState.playOrder.isNotEmpty()) {
                IconButton(onClick = { playerViewModel.shuffleTracks(uiState.playOrder) }) {
                    MageIcon(
                        name = "exchange",
                        contentDescription = "Shuffle artist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                FilledIconButton(
                    onClick = { play(uiState.playOrder) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    MageIcon(name = "play", contentDescription = "Play artist", modifier = Modifier.size(22.dp))
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> OrcLoading(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    title = "Something went off-key.",
                    body = uiState.error,
                    illustration = OrcVariant.ServerError,
                )
                uiState.trackCount == 0 -> EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    title = "No tracks by this artist",
                    body = "Nothing in the library credits \"$artistName\".",
                )
                else -> {
                    val columns = rememberGridColumns()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = PAGE_MARGIN,
                            end = PAGE_MARGIN,
                            bottom = LocalBottomInset.current + 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (section in uiState.sections) {
                            item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(section.icon, section.title, section.albums.size)
                            }
                            items(section.albums, key = { "${section.id}-${it.albumName}" }) { album ->
                                val albumTracks = uiState.tracksByAlbum[album.albumName].orEmpty()
                                AlbumCard(
                                    album = album,
                                    baseUrl = baseUrl,
                                    imageLoader = imageLoader,
                                    isActive = album.albumName == currentAlbum,
                                    onClick = { onAlbumClick(album.albumName) },
                                    playlists = playlists,
                                    onPlayNow = { play(albumTracks) },
                                    onShuffle = { playerViewModel.shuffleTracks(albumTracks) },
                                    onAddToQueue = { playerViewModel.addTracksToQueue(albumTracks) },
                                    onAddToPlaylist = { playlist -> playerViewModel.addTracksToPlaylist(albumTracks, playlist.id) },
                                    onCreatePlaylist = { name -> playerViewModel.createPlaylist(name) },
                                    onViewArtist = album.artist
                                        .takeIf { Discography.isLinkable(it) && !it.equals(artistName, ignoreCase = true) }
                                        ?.let { name -> { onViewArtist(name) } },
                                )
                            }
                        }

                        if (uiState.looseTracks.isNotEmpty()) {
                            item(key = "header-loose", span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader("music", "Loose tracks", uiState.looseTracks.size)
                            }
                            items(
                                items = uiState.looseTracks,
                                key = { "loose-${it.id}" },
                                span = { GridItemSpan(maxLineSpan) },
                            ) { track ->
                                TrackRow(
                                    // Rows run edge to edge like every other track list.
                                    modifier = Modifier.bleed(PAGE_MARGIN),
                                    track = track,
                                    baseUrl = baseUrl,
                                    imageLoader = imageLoader,
                                    isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                                    isLoading = playerState.pendingTrackId == track.id,
                                    isFavorite = track.id.toString() in playerState.favorites,
                                    playlists = playlists,
                                    onTrackClick = { playerViewModel.playTrack(track, uiState.looseTracks) },
                                    onAddToQueue = { playerViewModel.addToQueue(track) },
                                    onToggleFavorite = { playerViewModel.toggleFavorite(track) },
                                    onAddToPlaylist = { playlist -> playerViewModel.addTrackToPlaylist(track, playlist.id) },
                                    onRemoveFromPlaylist = { playlist -> playerViewModel.removeTrackFromPlaylist(track, playlist.id) },
                                    onCreatePlaylist = { name -> playerViewModel.createPlaylist(name) },
                                    onViewArtist = track.displayArtist
                                        .takeIf { Discography.isLinkable(it) && !it.equals(artistName, ignoreCase = true) }
                                        ?.let { name -> { onViewArtist(name) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val PAGE_MARGIN = 16.dp

/** Widen a grid item by [margin] on both sides, past the grid's content padding. */
private fun Modifier.bleed(margin: Dp): Modifier = layout { measurable, constraints ->
    val px = margin.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + 2 * px,
            maxWidth = constraints.maxWidth + 2 * px,
        ),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-px, 0) }
}

@Composable
private fun SectionHeader(icon: String, title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MageIcon(name = icon, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = " · $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun metaLine(state: ArtistUiState): String = buildList {
    fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
    if (state.albumCount > 0) add(plural(state.albumCount, "album", "albums"))
    if (state.epCount > 0) add(plural(state.epCount, "EP", "EPs"))
    if (state.singleCount > 0) add(plural(state.singleCount, "single", "singles"))
    add(plural(state.trackCount, "track", "tracks"))
}.joinToString(" · ")

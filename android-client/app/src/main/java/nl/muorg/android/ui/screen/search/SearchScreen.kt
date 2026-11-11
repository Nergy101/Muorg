package nl.muorg.android.ui.screen.search

import nl.muorg.android.ui.component.OrcLoading
import nl.muorg.android.ui.component.EmptyState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import kotlinx.coroutines.flow.Flow
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.ui.component.CoverMosaic
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.component.coverModelFor
import nl.muorg.android.ui.glass.glassField
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.glass.scrimLabelStyle
import androidx.compose.ui.graphics.RectangleShape
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes

/**
 * The dedicated Search screen: a search bar pinned at the TOP with the
 * keyboard open, and simple track rows between the bar and the keyboard —
 * live results while typing, recent searches + recently selected results
 * when the query is empty.
 *
 * Re-tapping the Search tab in the island bumps [searchFocusSignal], which
 * refocuses the input so typing can start immediately.
 */
@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onAlbumClick: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    searchFocusSignal: Flow<Unit>? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()

    var searchText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun focusSearch() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // First arrival stays unfocused — the keyboard only opens when the Search
    // tab is tapped again while already on the screen.
    LaunchedEffect(searchFocusSignal) {
        searchFocusSignal?.collect { focusSearch() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // Search bar at the top of the screen.
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                viewModel.onSearchQueryChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .glassField(MuorgShapes.pill),
            placeholder = {
                Text(
                    "Search for tracks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(painter = painterResource(mageIconRes("search")), contentDescription = null)
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchText = ""
                        viewModel.onSearchQueryChange("")
                    }) {
                        Icon(
                            painter = painterResource(mageIconRes("multiply")),
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.submitSearch()
                // Searching is done typing: drop the keyboard (it slides
                // down) so the results get the whole screen.
                focusManager.clearFocus()
                keyboardController?.hide()
            }),
            shape = MuorgShapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )

        // Results / recents between the search bar and the keyboard.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomInset.current + 4.dp),
        ) {
            if (searchText.isBlank()) {
                if (uiState.recentSearches.isNotEmpty() || uiState.recentArtists.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
                                onClick = { viewModel.clearSearchHistory() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Clear all", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        // Artists opened from the results come back as the same
                        // cards, ahead of the plain query chips.
                        if (uiState.recentArtists.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                uiState.recentArtists.forEach { hit ->
                                    ArtistSearchCard(
                                        name = hit.name,
                                        coverTracks = hit.coverTracks,
                                        baseUrl = baseUrl,
                                        imageLoader = imageLoader,
                                        onClick = {
                                            viewModel.recordSelectedArtist(hit.name)
                                            onViewArtist(hit.name)
                                        },
                                        modifier = Modifier.width(104.dp),
                                    )
                                }
                            }
                        }
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            uiState.recentSearches.take(10).forEach { entry ->
                                AssistChip(
                                    onClick = {
                                        searchText = entry.query
                                        viewModel.selectHistoryChip(entry.query)
                                        focusSearch()
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
                }

                if (uiState.recentResultTracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(mageIconRes("search")),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "Recent results",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { viewModel.clearSearchResults() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Clear all", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(
                        items = uiState.recentResultTracks,
                        key = { it.id },
                    ) { track ->
                        TrackRow(
                            track = track,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                            isLoading = playerState.pendingTrackId == track.id,
                            isFavorite = track.id.toString() in playerState.favorites,
                            playlists = playlists,
                            onTrackClick = {
                                viewModel.recordSelectedResult(track.id)
                                playerViewModel.playTrack(track, uiState.recentResultTracks)
                            },
                            onAddToQueue = { playerViewModel.addToQueue(track) },
                            onToggleFavorite = { playerViewModel.toggleFavorite(track) },
                            onViewAlbum = { onAlbumClick(track.displayAlbum) },
                            onViewArtist = { onViewArtist(track.displayArtist) },
                        )
                    }
                }

                if (uiState.recentSearches.isEmpty() && uiState.recentArtists.isEmpty() && uiState.recentResultTracks.isEmpty()) {
                    item {
                        if (uiState.isLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OrcLoading()
                            }
                        } else if (uiState.error != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = uiState.error ?: "Unknown error",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            EmptyState(
                                title = "No searches yet",
                                body = "Type above to search your library",
                            )
                        }
                    }
                }
            } else {
                // Artist matches first: a 2x2 cover collage per artist with
                // the name underneath, above the normal track results.
                if (uiState.artistMatches.isNotEmpty()) {
                    item {
                        // Compact cards in a sideways strip, not two big squares
                        // that push the tracks off the screen.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            uiState.artistMatches.forEach { hit ->
                                ArtistSearchCard(
                                    name = hit.name,
                                    coverTracks = hit.coverTracks,
                                    baseUrl = baseUrl,
                                    imageLoader = imageLoader,
                                    onClick = {
                                        viewModel.recordSelectedArtist(hit.name)
                                        onViewArtist(hit.name)
                                    },
                                    modifier = Modifier.width(104.dp),
                                )
                            }
                        }
                    }
                }
                if (uiState.isLoading && uiState.allTracks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OrcLoading()
                        }
                    }
                } else if (uiState.results.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No results for \"$searchText\"",
                            body = "Try another spelling or fewer words.",
                        )
                    }
                } else {
                    items(
                        items = uiState.results,
                        key = { it.id },
                    ) { track ->
                        TrackRow(
                            track = track,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                            isLoading = playerState.pendingTrackId == track.id,
                            // Show the album too: an album-title match is
                            // otherwise invisible in a title+artist row.
                            subtitle = "${track.displayArtist} • ${track.displayAlbum}",
                            isFavorite = track.id.toString() in playerState.favorites,
                            playlists = playlists,
                            onTrackClick = {
                                viewModel.recordSelectedResult(track.id)
                                playerViewModel.playTrack(track, uiState.results)
                            },
                            onAddToQueue = { playerViewModel.addToQueue(track) },
                            onToggleFavorite = { playerViewModel.toggleFavorite(track) },
                            onViewAlbum = { onAlbumClick(track.displayAlbum) },
                            onViewArtist = { onViewArtist(track.displayArtist) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One artist match in the search results: a square 2x2 cover collage of up
 * to 4 distinct albums (single cover when only one has artwork, a user icon
 * when none do — [CoverMosaic] handles both fallbacks) with the artist name
 * underneath. Tapping opens the artist's library view.
 */
@Composable
private fun ArtistSearchCard(
    name: String,
    coverTracks: List<CatalogTrack>,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same treatment as an album in the grid: the name sits on the cover, on
    // the bottom scrim, rather than underneath it.
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MuorgShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CoverMosaic(
            covers = coverTracks.map { coverModelFor(it.localCoverPath, it.id, baseUrl) },
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                painter = painterResource(mageIconRes("user")),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        GlassSurface(
            material = GlassMaterial.Scrim,
            shape = RectangleShape,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Text(
                text = name,
                style = scrimLabelStyle(MaterialTheme.typography.titleSmall),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            )
        }
    }
}

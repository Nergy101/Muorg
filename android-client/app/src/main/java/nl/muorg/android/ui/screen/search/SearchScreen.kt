package nl.muorg.android.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import kotlinx.coroutines.flow.Flow
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.glass.glassField
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
                    "Search tracks, artists, albums…",
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
                if (uiState.recentSearches.isNotEmpty()) {
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

                if (uiState.recentSearches.isEmpty() && uiState.recentResultTracks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            } else if (uiState.error != null) {
                                Text(
                                    text = uiState.error ?: "Unknown error",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(mageIconRes("search")),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Text(
                                    text = "Type above to search your library",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                if (uiState.isLoading && uiState.allTracks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (uiState.results.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
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
                                text = "No results for \"$searchText\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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

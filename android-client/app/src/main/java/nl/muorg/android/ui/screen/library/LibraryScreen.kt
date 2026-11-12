package nl.muorg.android.ui.screen.library

import nl.muorg.android.ui.component.OrcPullToRefreshBox
import nl.muorg.android.ui.component.bottomInset
import nl.muorg.android.ui.easteregg.DustReportBubble
import nl.muorg.android.ui.easteregg.JanitorFigure
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import kotlinx.coroutines.flow.Flow
import nl.muorg.android.ui.component.LocalIslandAccent
import nl.muorg.android.ui.player.PlayerViewModel

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
    val janitorAwake by viewModel.janitorAwake.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentAlbum = playerState.currentTrack?.displayAlbum
    var searchText by remember { mutableStateOf(viewModel.rawSearchQuery) }
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

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
    val currentTrackIdRef = rememberUpdatedState(playerState.currentTrack?.id)
    val filteredAlbumsRef = rememberUpdatedState(uiState.filteredAlbums)
    val filteredTracksRef = rememberUpdatedState(uiState.filteredTracks)
    val albumViewStyleRef = rememberUpdatedState(uiState.albumViewStyle)
    // Matches the branch LibraryContent uses to decide what's on screen.
    val trackViewActiveRef = rememberUpdatedState(searchText.isNotEmpty())
    LaunchedEffect(scrollToActiveSignal) {
        scrollToActiveSignal?.collect {
            if (trackViewActiveRef.value) {
                val trackId = currentTrackIdRef.value ?: return@collect
                val index = filteredTracksRef.value.indexOfFirst { it.id == trackId }
                if (index >= 0) lazyListState.animateScrollToItem(index)
            } else {
                val currentAlbum = currentAlbumRef.value ?: return@collect
                val index = filteredAlbumsRef.value.indexOfFirst { it.albumName == currentAlbum }
                if (index >= 0) {
                    if (albumViewStyleRef.value == "list") {
                        lazyListState.animateScrollToItem(index)
                    } else {
                        lazyGridState.animateScrollToItem(index)
                    }
                }
            }
        }
    }

    // The web keeps the search field INSIDE the bottom island, directly
    // under the mini player; sort, genre, layout and shuffle-all live in a
    // top bar above the content instead. This publishes the search there;
    // nothing is rendered in place.
    // Same artwork accent the island paints itself with; the chrome is
    // hosted inside that glass, so theme green here would clash with it.
    val chromeAccent = LocalIslandAccent.current.takeOrElse { MaterialTheme.colorScheme.primary }

    LibraryChrome(
        uiState = uiState,
        viewModel = viewModel,
        searchText = searchText,
        onSearchTextChange = { searchText = it },
        chromeAccent = chromeAccent,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LibraryTopBar(
                uiState = uiState,
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                lazyListState = lazyListState,
                lazyGridState = lazyGridState,
            )

            OrcPullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::loadTracks,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Room for the Janitor when he's awake, as on Playlists.
                    .padding(bottom = if (janitorAwake) 110.dp else 0.dp),
            ) {
                LibraryContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    playerViewModel = playerViewModel,
                    playerState = playerState,
                    searchText = searchText,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    lazyListState = lazyListState,
                    lazyGridState = lazyGridState,
                    onAlbumClick = onAlbumClick,
                    onViewArtist = onViewArtist,
                )
            }
        }

        // The awakened Janitor sweeps the library too, with library-specific
        // dust report lines — same overlay pattern as the Playlists screen.
        if (janitorAwake) {
            val dustOpen = uiState.dustReportOpen
            BackHandler(enabled = dustOpen) { viewModel.closeDustReport() }
            val bubbleVisible = dustOpen && uiState.dustStats.isNotEmpty()
            val bubbleAnim by animateFloatAsState(
                targetValue = if (bubbleVisible) 1f else 0f,
                animationSpec = tween(180),
            )
            if (bubbleVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = bottomInset() + 124.dp)
                        .alpha(bubbleAnim)
                        .scale(0.96f + 0.04f * bubbleAnim),
                ) {
                    DustReportBubble(
                        stats = uiState.dustStats,
                        index = uiState.dustIndex,
                        onNext = viewModel::nextDustStat,
                        onClose = viewModel::closeDustReport,
                    )
                }
            }
            JanitorFigure(
                bubbleOpen = dustOpen,
                onTap = viewModel::onJanitorTapped,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = bottomInset() + 12.dp),
            )
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


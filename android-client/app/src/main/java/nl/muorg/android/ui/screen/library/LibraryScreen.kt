package nl.muorg.android.ui.screen.library

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.takeOrElse
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
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val currentAlbum = playerState.currentTrack?.displayAlbum
    var searchText by remember { mutableStateOf(viewModel.rawSearchQuery) }
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

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
    val filteredAlbumsRef = rememberUpdatedState(uiState.filteredAlbums)
    val albumViewStyleRef = rememberUpdatedState(uiState.albumViewStyle)
    LaunchedEffect(scrollToActiveSignal) {
        scrollToActiveSignal?.collect {
            val currentAlbum = currentAlbumRef.value ?: return@collect
            val albums = filteredAlbumsRef.value
            val index = albums.indexOfFirst { it.albumName == currentAlbum }
            if (index >= 0) {
                if (albumViewStyleRef.value == "list") {
                    lazyListState.animateScrollToItem(index)
                } else {
                    lazyGridState.animateScrollToItem(index)
                }
            }
        }
    }

    // The web keeps search and the sort/filter row INSIDE the bottom island,
    // directly under the mini player, not at the top of the view. This
    // publishes them there; nothing is rendered in place.
    // Same artwork accent the island paints itself with; the chrome is
    // hosted inside that glass, so theme green here would clash with it.
    val chromeAccent = LocalIslandAccent.current.takeOrElse { MaterialTheme.colorScheme.primary }

    LibraryChrome(
        uiState = uiState,
        viewModel = viewModel,
        searchText = searchText,
        onSearchTextChange = { searchText = it },
        chromeAccent = chromeAccent,
        lazyListState = lazyListState,
        lazyGridState = lazyGridState,
        playerViewModel = playerViewModel,
    )


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

private fun formatSleepTimer(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

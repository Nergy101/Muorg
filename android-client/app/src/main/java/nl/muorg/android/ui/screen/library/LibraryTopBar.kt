package nl.muorg.android.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.muorg.android.ui.component.PickerSheet
import nl.muorg.android.ui.glass.glassField
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes
import nl.muorg.android.ui.icon.MageIcon

/** A genre entry in the dropdown: normalized value plus original-casing label. */
data class GenreOption(val value: String, val label: String)

/** Distinct genres across the catalog, original casing, sorted A–Z. */
fun genresOf(tracks: List<nl.muorg.android.data.api.CatalogTrack>): List<GenreOption> {
    val seen = linkedMapOf<String, String>()
    for (t in tracks) {
        val g = t.genre?.takeIf { it.isNotBlank() } ?: continue
        val key = g.lowercase()
        if (!seen.containsKey(key)) seen[key] = g
    }
    return seen.entries
        .sortedBy { it.value.lowercase() }
        .map { GenreOption(it.key, it.value) }
}

/**
 * The library's top bar, matching the web client's LibraryTopBar: layout
 * switcher (with a badge for the active layout), shuffle-all, genre filter
 * and sort, left-aligned in one row. The search field stays in the bottom
 * island, as on the web.
 */
@Composable
fun LibraryTopBar(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    lazyGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
) {
    val coroutineScope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }
    var showGenreMenu by remember { mutableStateOf(false) }
    val genres = remember(uiState.allTracks) { genresOf(uiState.allTracks) }
    val genreLabel = genres.firstOrNull { it.value == uiState.genreFilter }?.label ?: "All"

    fun scrollToTop() {
        coroutineScope.launch {
            lazyGridState.scrollToItem(0)
            lazyListState.scrollToItem(0)
        }
    }

    // Spaced rather than butted together, and scrollable so a narrow screen
    // (or a long genre name) never pushes the sort control off the edge.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Layout switcher, far left: toggles album covers and the album list.
        val isGrid = uiState.albumViewStyle != "list"
        IconButton(onClick = { viewModel.setAlbumViewStyle(if (isGrid) "list" else "grid") }) {
            Icon(
                painter = painterResource(mageIconRes("layout-grid")),
                contentDescription = if (isGrid) "Show as list" else "Show as covers",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        // Shuffle-and-play-all, right of the layout selection.
        IconButton(onClick = {
            scrollToTop()
            val tracks = uiState.filteredTracks.ifEmpty {
                uiState.filteredAlbums.flatMap { viewModel.getTracksForAlbum(it.albumName) }
            }
            playerViewModel.startShuffleAll(tracks)
        }) {
            Icon(
                painter = painterResource(mageIconRes("exchange")),
                contentDescription = "Shuffle all",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }

        // Genre filter pill, styled like the sort pill.
        Box(
            modifier = Modifier
                .glassField(MuorgShapes.pill)
                .padding(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    androidx.compose.material3.TextButton(onClick = { showGenreMenu = true }) {
                        Text(
                            "Genre: $genreLabel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            painter = painterResource(mageIconRes("chevron-down")),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Sort pill: field dropdown + direction toggle.
        Box(
            modifier = Modifier
                .glassField(MuorgShapes.pill)
                .padding(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    androidx.compose.material3.TextButton(onClick = { showSortMenu = true }) {
                        Text(
                            "Sort: ${uiState.sortMode.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            painter = painterResource(mageIconRes("chevron-down")),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = {
                    scrollToTop()
                    viewModel.toggleSortDirection()
                }) {
                    MageIcon(
                        name = if (uiState.sortAscending) "arrow-up" else "arrow-down",
                        contentDescription = if (uiState.sortAscending) "Sort ascending" else "Sort descending",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (showGenreMenu) {
        val options = listOf(null as GenreOption?) + genres
        PickerSheet(
            title = "Genre",
            options = options,
            isSelected = { it?.value == uiState.genreFilter },
            label = { it?.label ?: "All genres" },
            onSelect = { viewModel.setGenreFilter(it?.value) },
            onDismiss = { showGenreMenu = false },
        )
    }

    if (showSortMenu) {
        PickerSheet(
            title = "Sort by",
            options = SortMode.entries.toList(),
            isSelected = { it == uiState.sortMode },
            label = { it.label },
            onSelect = {
                scrollToTop()
                viewModel.setSortMode(it)
            },
            onDismiss = { showSortMenu = false },
        )
    }
}

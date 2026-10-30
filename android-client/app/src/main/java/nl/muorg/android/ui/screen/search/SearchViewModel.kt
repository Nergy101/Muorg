package nl.muorg.android.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.preferences.SearchHistoryEntry
import nl.muorg.android.data.preferences.SearchHistoryManager
import nl.muorg.android.data.preferences.SearchResultsHistoryManager
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.OfflineDownloadManager
import javax.inject.Inject

data class SearchUiState(
    val allTracks: List<CatalogTrack> = emptyList(),
    val results: List<CatalogTrack> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val recentSearches: List<SearchHistoryEntry> = emptyList(),
    val recentResultTracks: List<CatalogTrack> = emptyList(),
)

/**
 * Backs the Search screen: the whole catalog (whichever side of the
 * local/remote/offline divide is in force, same as the library), a debounced
 * query filter, recent search queries and recently selected result tracks.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val preferences: AppPreferences,
    private val searchHistoryManager: SearchHistoryManager,
    private val searchResultsHistoryManager: SearchResultsHistoryManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            preferences.musicMode.collect { loadTracks() }
        }
        // drop(1): the first emission is the current value, which the
        // musicMode-triggered initial load already covers.
        viewModelScope.launch {
            preferences.offlineMode.drop(1).collect { loadTracks() }
        }
        viewModelScope.launch {
            _searchQuery.debounce(300).collect { query ->
                _uiState.update { state ->
                    state.copy(
                        searchQuery = query,
                        results = filterTracks(state.allTracks, query),
                    )
                }
                // Record history only once typing has paused and the query is
                // long enough to be a real search — same rule as the library.
                if (query.trim().length >= 2) {
                    searchHistoryManager.addSearch(query)
                    refreshRecentSearches()
                }
            }
        }
        viewModelScope.launch { refreshRecentSearches() }
        viewModelScope.launch { refreshRecentResults() }
    }

    fun loadTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val mode = preferences.musicMode.first()
            val offline = preferences.offlineMode.first()
            val tracks: List<CatalogTrack> = when {
                mode == "local" -> runCatching { localRepository.getAllTracks() }
                    .getOrElse { e -> return@launch onLoadError(e) }
                offline -> runCatching { offlineDownloadManager.getAllDownloadedTracks() }
                    .getOrElse { e -> return@launch onLoadError(e) }
                else -> repository.getAllTracks()
                    .getOrElse { e -> return@launch onLoadError(e) }
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    allTracks = tracks,
                    results = filterTracks(tracks, state.searchQuery),
                )
            }
            refreshRecentResults()
        }
    }

    private fun onLoadError(e: Throwable) {
        _uiState.update { it.copy(isLoading = false, error = e.message) }
    }

    private fun filterTracks(tracks: List<CatalogTrack>, query: String): List<CatalogTrack> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return tracks.filter { track ->
            track.displayTitle.lowercase().contains(q) ||
                track.displayArtist.lowercase().contains(q) ||
                track.displayAlbum.lowercase().contains(q) ||
                track.albumArtist?.lowercase()?.contains(q) == true
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectHistoryChip(query: String) {
        _searchQuery.value = query
    }

    /** Records a track picked from the results so it shows up as a recent
     *  result row next time the screen opens with an empty query. */
    fun recordSelectedResult(trackId: Int) {
        viewModelScope.launch {
            searchResultsHistoryManager.addResult(trackId)
            refreshRecentResults()
        }
    }

    private suspend fun refreshRecentSearches() {
        _uiState.update { it.copy(recentSearches = searchHistoryManager.getRecentSearches()) }
    }

    private suspend fun refreshRecentResults() {
        val entries = searchResultsHistoryManager.getRecentResults()
        val byId = _uiState.value.allTracks.associateBy { it.id }
        _uiState.update { state ->
            state.copy(
                recentResultTracks = entries.mapNotNull { byId[it.trackId] },
            )
        }
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearAll()
        viewModelScope.launch { refreshRecentSearches() }
    }

    fun clearSearchResults() {
        searchResultsHistoryManager.clearAll()
        _uiState.update { it.copy(recentResultTracks = emptyList()) }
    }
}

package nl.muorg.android.ui.screen.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.util.LibraryReports
import javax.inject.Inject

data class ReportsUiState(
    val tracks: List<CatalogTrack> = emptyList(),
    val counts: Map<LibraryReports.Kind, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Holds the whole catalog so the reports can be computed over it.
 *
 * Reports describe the library, not the current view, so this loads the full
 * track list rather than reading whatever the library screen happens to be
 * filtered to — a search for "radiohead" must not make the duplicate count
 * drop.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val mode = preferences.musicMode.first()
            val result = runCatching {
                if (mode == "local") localRepository.getAllTracks()
                else repository.getAllTracks().getOrThrow()
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { tracks ->
                        state.copy(
                            isLoading = false,
                            tracks = tracks,
                            counts = LibraryReports.counts(tracks),
                        )
                    },
                    onFailure = { error ->
                        state.copy(isLoading = false, error = error.message ?: "Could not load the library")
                    },
                )
            }
        }
    }

    /** The tracks one report lists, computed on demand from the loaded catalog. */
    fun tracksFor(kind: LibraryReports.Kind): List<CatalogTrack> =
        LibraryReports.run(kind, _uiState.value.tracks)

    fun duplicateGroups(): List<List<CatalogTrack>> =
        LibraryReports.duplicateGroups(_uiState.value.tracks)
}

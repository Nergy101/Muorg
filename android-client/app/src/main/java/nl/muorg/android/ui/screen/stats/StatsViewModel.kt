package nl.muorg.android.ui.screen.stats

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

data class StatsUiState(
    val tracks: List<CatalogTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Loads the whole catalog so statistics describe the library, not the current
 * view — same reasoning as [nl.muorg.android.ui.screen.reports.ReportsViewModel].
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

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
                    onSuccess = { tracks -> state.copy(isLoading = false, tracks = tracks) },
                    onFailure = { error ->
                        state.copy(isLoading = false, error = error.message ?: "Could not load the library")
                    },
                )
            }
        }
    }

    /** Fraction of tracks that have [field] filled in, 0..1. */
    fun fieldHealth(field: LibraryReports.Field, tracks: List<CatalogTrack>): Float {
        if (tracks.isEmpty()) return 0f
        val present = tracks.count { !LibraryReports.isFieldMissing(it, field) }
        return present.toFloat() / tracks.size
    }
}

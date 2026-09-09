package nl.muorg.android.ui.screen.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.schema.MatchCandidate
import nl.muorg.android.data.repository.LibraryRepository
import javax.inject.Inject

/**
 * The metadata tools the server offers beyond a plain tag edit: MusicBrainz
 * lookup, and restoring the backup taken before the last write.
 *
 * All three were reachable in the API and unused by the app. Auto-tagging is
 * the one that earns its place — it is how a badly-tagged file gets fixed
 * without typing six fields by hand — and the restore is what makes accepting a
 * suggestion safe.
 */
data class MetadataToolsState(
    val loadingSuggestions: Boolean = false,
    val candidates: List<MatchCandidate> = emptyList(),
    /** True once a lookup has returned, so "no matches" can be told from "not asked". */
    val searched: Boolean = false,
    val backupAvailable: Boolean = false,
    val restoring: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MetadataToolsViewModel @Inject constructor(
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MetadataToolsState())
    val state: StateFlow<MetadataToolsState> = _state.asStateFlow()

    /** Reset when the sheet opens on a different track. */
    fun reset() {
        _state.value = MetadataToolsState()
    }

    /** Whether a restore is possible, so the action can be hidden when it is not. */
    fun checkBackup(trackId: Int) {
        viewModelScope.launch {
            library.latestBackup(trackId).onSuccess { backup ->
                _state.update { it.copy(backupAvailable = backup != null) }
            }
        }
    }

    fun findMatches(trackId: Int) {
        _state.update { it.copy(loadingSuggestions = true, error = null) }
        viewModelScope.launch {
            library.autoTagSuggestions(trackId).fold(
                onSuccess = { candidates ->
                    _state.update {
                        it.copy(
                            loadingSuggestions = false,
                            searched = true,
                            candidates = candidates,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            loadingSuggestions = false,
                            searched = true,
                            error = e.message ?: "Lookup failed",
                        )
                    }
                },
            )
        }
    }

    /** Put the file back as it was before the last tag write. */
    fun restore(trackId: Int, onDone: (String) -> Unit) {
        _state.update { it.copy(restoring = true, error = null) }
        viewModelScope.launch {
            library.restoreFromBackup(trackId).fold(
                onSuccess = {
                    _state.update { it.copy(restoring = false, backupAvailable = false) }
                    onDone("Restored from backup")
                },
                onFailure = { e ->
                    _state.update { it.copy(restoring = false, error = e.message) }
                    onDone("Restore failed: ${e.message}")
                },
            )
        }
    }
}

package nl.muorg.android.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.MetadataUpdateRequest
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.util.PathPatternMatcher
import javax.inject.Inject

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val done: Int, val total: Int) : ScanState()
    data class Done(val count: Int) : ScanState()
    data class Error(val message: String) : ScanState()
}

// patternKey is included so that StateFlow always emits when the pattern changes,
// even if the resulting match count and track list happen to be identical.
data class MatchSummary(
    val patternKey: String = "",
    val filesMatched: Int = 0,
    val matchResults: List<Pair<CatalogTrack, MetadataUpdateRequest>> = emptyList(),
)

@HiltViewModel
class MetadataScanViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
) : ViewModel() {

    private val _pattern = MutableStateFlow("")
    val pattern: StateFlow<String> = _pattern.asStateFlow()

    private val _examplePath = MutableStateFlow("/music/Linkin Park/Meteora/04 - Faint.flac")
    val examplePath: StateFlow<String> = _examplePath.asStateFlow()

    private val _tracksLoading = MutableStateFlow(true)
    val tracksLoading: StateFlow<Boolean> = _tracksLoading.asStateFlow()

    private val _allTracks = MutableStateFlow<List<CatalogTrack>>(emptyList())
    private val _isLocalMode = MutableStateFlow(false)
    val isLocalMode: StateFlow<Boolean> = _isLocalMode.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _matchSummary = MutableStateFlow(MatchSummary())
    val matchSummary: StateFlow<MatchSummary> = _matchSummary.asStateFlow()

    private val _examplePreview = MutableStateFlow<Map<String, String>?>(null)
    val examplePreview: StateFlow<Map<String, String>?> = _examplePreview.asStateFlow()

    init {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            _isLocalMode.value = mode == "local"
            runCatching {
                if (mode == "local") {
                    _allTracks.value = localRepository.getAllTracks()
                } else {
                    repository.getAllTracks().onSuccess { _allTracks.value = it }
                }
            }
            _tracksLoading.value = false
        }

        viewModelScope.launch {
            combine(_pattern, _allTracks, _isLocalMode) { pattern, tracks, isLocal ->
                if (pattern.isBlank()) return@combine MatchSummary(patternKey = pattern)
                var filesMatched = 0
                val willUpdate = mutableListOf<Pair<CatalogTrack, MetadataUpdateRequest>>()
                for (track in tracks) {
                    val matchPath = if (isLocal) PathPatternMatcher.decodeLocalPath(track.path) else track.path
                    val fields = PathPatternMatcher.extractMetadataFromPath(pattern, matchPath) ?: continue
                    val update = PathPatternMatcher.buildMetadataUpdate(fields)
                    if (update.hasAnyField()) {
                        filesMatched++
                        willUpdate.add(track to update)
                    }
                }
                MatchSummary(patternKey = pattern, filesMatched = filesMatched, matchResults = willUpdate)
            }.collect { _matchSummary.value = it }
        }

        viewModelScope.launch {
            combine(_pattern, _examplePath) { pattern, path ->
                if (pattern.isBlank() || path.isBlank()) null
                else PathPatternMatcher.extractMetadataFromPath(pattern, path)
            }.collect { _examplePreview.value = it }
        }
    }

    fun setPattern(p: String) { _pattern.value = p }
    fun setExamplePath(p: String) { _examplePath.value = p }

    fun runScan() {
        val results = _matchSummary.value.matchResults
        if (results.isEmpty()) return
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning(0, results.size)
            var done = 0
            var errors = 0
            for ((track, update) in results) {
                runCatching {
                    if (_isLocalMode.value) {
                        val localId = -track.id
                        localRepository.updateTrackMetadata(localId, update)
                    } else {
                        repository.patchTrackMetadata(track.id, update).getOrThrow()
                    }
                }.onFailure { errors++ }
                done++
                _scanState.value = ScanState.Scanning(done, results.size)
            }
            _scanState.value = if (errors == 0) {
                ScanState.Done(done)
            } else {
                ScanState.Error("Updated ${done - errors} of ${results.size} tracks. $errors failed.")
            }
        }
    }

    fun resetScan() { _scanState.value = ScanState.Idle }
}

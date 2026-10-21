package nl.muorg.android.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.Mix
import nl.muorg.android.data.repository.MixRepository
import javax.inject.Inject
import kotlin.random.Random

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val recommended: List<AlbumGroup> = emptyList(),
    val mixes: List<Mix> = emptyList(),
    val mostPlayed: List<AlbumGroup> = emptyList(),
    val recentlyPlayed: List<AlbumGroup> = emptyList(),
    val isOfflineMode: Boolean = false,
    /** True while the local library's automatic first scan is running. */
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    /** Set once a first scan has run, so an empty result is not re-scanned on every load. */
    val scanCompleted: Boolean = false,
)

private const val RECOMMENDED_COUNT = 4

/** Every shelf shows four albums, as on the web. */
private const val SHELF_CAP = 4

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
    private val mixRepository: MixRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var tracks: List<CatalogTrack> = emptyList()
    private var albums: List<AlbumGroup> = emptyList()

    init {
        // Toggling offline mode while this screen is open re-reads from
        // whichever side of the divide is now in force. The first emission is
        // the current value, which the initial load() already covers.
        viewModelScope.launch {
            preferences.offlineMode.drop(1).collect { load() }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val local = preferences.musicMode.first() == "local"
            // Offline mode is a hard divide: every shelf is a server call, so
            // there is nothing to load — the screen shows the offline
            // placeholder instead. Local mode reads the device's own storage
            // and needs no such guard.
            if (!local && preferences.offlineMode.first()) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = null,
                        isOfflineMode = true,
                        recommended = emptyList(),
                        mixes = emptyList(),
                        mostPlayed = emptyList(),
                        recentlyPlayed = emptyList(),
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(loading = true, error = null, isOfflineMode = false) }
            val result = if (local) {
                runCatching { scanIfNeeded() to localRepository.buildAlbumGroups() }
            } else {
                repository.getAllTracks().map { it to repository.buildAlbumGroups(it) }
            }
            result.fold(
                onSuccess = { (t, a) ->
                    tracks = t
                    albums = a
                    mixRepository.rebind(t)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            recommended = pickRecommended(),
                            mixes = mixRepository.current().ifEmpty { mixRepository.roll(t) },
                        )
                    }
                    // Needs `albums` populated to resolve covers, so it runs
                    // after the catalog lands rather than in parallel with it.
                    loadHistoryShelves()
                },
                onFailure = { e -> _uiState.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    /**
     * Local mode reads the device's own storage, and right after setup that
     * storage is still empty — the folder was just picked and nothing has
     * scanned it yet. The first load therefore kicks off the scan itself and
     * reports progress through [HomeUiState.isScanning], so the screen can
     * show the scanner bar instead of empty shelves; the tracks read back
     * afterwards are the freshly scanned ones.
     */
    private suspend fun scanIfNeeded(): List<CatalogTrack> {
        val tracks = localRepository.getAllTracks()
        if (tracks.isNotEmpty()) return tracks
        val state = _uiState.value
        if (state.scanCompleted || state.isScanning) return tracks
        val uris = preferences.localFolderUris.first()
        if (uris.isEmpty()) return tracks
        _uiState.update { it.copy(isScanning = true, scanProgress = 0, scanTotal = 0) }
        try {
            localRepository.scanIfEmpty(uris) { done, total ->
                _uiState.update { it.copy(scanProgress = done, scanTotal = total) }
            }
        } finally {
            _uiState.update { it.copy(isScanning = false, scanCompleted = true) }
        }
        return localRepository.getAllTracks()
    }

    /**
     * Groups a shelf's tracks into albums IN THE ENDPOINT'S ORDER (recency or
     * play count) so the shelf reads like a feed rather than an A-Z list. The
     * catalog's own grouping supplies the cover, so opening one lands on the
     * same album the library grid would show.
     */
    private fun shelfAlbums(tracks: List<CatalogTrack>): List<AlbumGroup> {
        val byKey = albums.associateBy { it.albumName + "|" + it.artist }
        val out = LinkedHashMap<String, AlbumGroup>()
        for (track in tracks) {
            val key = track.displayAlbum + "|" + (track.albumArtist ?: track.displayArtist)
            if (out.containsKey(key)) continue
            val album = byKey[key]
                ?: albums.firstOrNull { it.albumName == track.displayAlbum }
                ?: continue
            out[key] = album
            if (out.size == SHELF_CAP) break
        }
        return out.values.toList()
    }

    private fun loadHistoryShelves() {
        viewModelScope.launch {
            val top = repository.getTopPlayHistory(limit = 30, days = 30).getOrDefault(emptyList())
            val recent = repository.getRecentPlayHistory(limit = 30).getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    mostPlayed = shelfAlbums(top),
                    recentlyPlayed = shelfAlbums(recent),
                )
            }
        }
    }

    fun refreshMostPlayed() = loadHistoryShelves()

    fun refreshRecentlyPlayed() = loadHistoryShelves()

    fun refreshRecommended() {
        _uiState.update { it.copy(recommended = pickRecommended()) }
    }

    fun refreshMixes() {
        _uiState.update { it.copy(mixes = mixRepository.roll(tracks)) }
    }

    fun tracksForMix(mix: Mix): List<CatalogTrack> = mixRepository.tracksFor(mix)

    /** The catalog track behind a cover id, for resolving mosaic covers. */
    fun trackFor(trackId: Int): CatalogTrack? = tracks.firstOrNull { it.id == trackId }

    /** The full catalog behind the shelves, for "Shuffle all". */
    fun allTracks(): List<CatalogTrack> = tracks

    private fun pickRecommended(): List<AlbumGroup> =
        albums.shuffled().take(RECOMMENDED_COUNT)

}

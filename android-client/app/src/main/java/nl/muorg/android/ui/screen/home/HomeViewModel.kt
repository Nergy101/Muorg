package nl.muorg.android.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val local = preferences.musicMode.first() == "local"
            val result = if (local) {
                runCatching { localRepository.getAllTracks() to localRepository.buildAlbumGroups() }
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

    private fun pickRecommended(): List<AlbumGroup> =
        albums.shuffled().take(RECOMMENDED_COUNT)

}

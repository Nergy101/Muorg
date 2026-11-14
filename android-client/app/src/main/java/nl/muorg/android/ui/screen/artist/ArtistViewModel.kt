package nl.muorg.android.ui.screen.artist

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
import nl.muorg.android.data.repository.OfflineDownloadManager
import nl.muorg.android.util.Discography
import javax.inject.Inject

/** One shelf of release cards on the artist screen. */
data class ArtistSection(
    val id: String,
    val title: String,
    val icon: String,
    val albums: List<AlbumGroup>,
)

data class ArtistUiState(
    val artist: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val sections: List<ArtistSection> = emptyList(),
    val looseTracks: List<CatalogTrack> = emptyList(),
    /** Every credited track, in "play artist" order. */
    val playOrder: List<CatalogTrack> = emptyList(),
    /** Album name → its tracks, for the cards' play / queue / playlist actions. */
    val tracksByAlbum: Map<String, List<CatalogTrack>> = emptyMap(),
    val albumCount: Int = 0,
    val epCount: Int = 0,
    val singleCount: Int = 0,
    val trackCount: Int = 0,
)

/**
 * The artist page: the artist's albums, EPs and singles, the releases they
 * appear on, and their loose (album-less) tracks — see [Discography].
 *
 * Reads the same source the rest of the app is on: the local library, the
 * offline download store, or the server catalog.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    fun load(artist: String) {
        val current = _uiState.value
        if (current.artist == artist && (current.isLoading || current.trackCount > 0)) return
        _uiState.value = ArtistUiState(artist = artist, isLoading = true)
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            val local = mode == "local"
            val tracks: Result<List<CatalogTrack>> = when {
                local -> runCatching { localRepository.getAllTracks() }
                preferences.offlineMode.first() -> runCatching { offlineDownloadManager.getAllDownloadedTracks() }
                else -> repository.getAllTracks()
            }
            tracks.fold(
                onSuccess = { all -> _uiState.value = buildState(artist, all, local) },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    private fun buildState(artist: String, all: List<CatalogTrack>, local: Boolean): ArtistUiState {
        val result = Discography.build(artist, all)
        fun group(release: Discography.Release): AlbumGroup {
            val rep = release.tracks.first()
            return AlbumGroup(
                albumName = release.albumName,
                artist = release.artist,
                year = release.year,
                trackCount = release.tracks.size,
                // Local covers live in the album-art cache keyed by name, with
                // the negative id convention the local album grid uses.
                coverTrackId = if (local) -rep.id else (release.tracks.firstOrNull { it.hasCover } ?: rep).id,
                coverArtUri = if (local) localRepository.getAlbumArtUri(release.albumName) else null,
            )
        }
        val sections = listOf(
            ArtistSection("albums", "Albums", "compact-disk", result.albums.map(::group)),
            ArtistSection("eps", "EPs", "double-circle", result.eps.map(::group)),
            ArtistSection("singles", "Singles", "music-fill", result.singles.map(::group)),
            ArtistSection("appears-on", "Appears on", "users-fill", result.appearsOn.map(::group)),
        ).filter { it.albums.isNotEmpty() }
        val releases = result.albums + result.eps + result.singles + result.appearsOn
        return ArtistUiState(
            artist = artist,
            isLoading = false,
            sections = sections,
            looseTracks = result.looseTracks,
            playOrder = Discography.playOrder(result, artist),
            tracksByAlbum = releases.associate { it.albumName to it.tracks },
            albumCount = result.albums.size,
            epCount = result.eps.size,
            singleCount = result.singles.size,
            trackCount = result.tracks.size,
        )
    }
}

package nl.muorg.android.ui.screen.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import javax.inject.Inject

data class AlbumDetailUiState(
    val albumName: String = "",
    val artist: String = "",
    val year: Int? = null,
    val tracks: List<CatalogTrack> = emptyList(),
    val coverTrackId: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val playlists: List<Playlist> = emptyList(),
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
    }

    fun loadAlbum(albumName: String) {
        if (_uiState.value.albumName == albumName && _uiState.value.tracks.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, albumName = albumName) }
            repository.getAllTracks().fold(
                onSuccess = { allTracks ->
                    val albumTracks = allTracks
                        .filter { it.displayAlbum == albumName }
                        .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))

                    val representative = albumTracks.firstOrNull()
                    val coverTrackId = albumTracks.firstOrNull { it.hasCover }?.id
                        ?: representative?.id

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            tracks = albumTracks,
                            artist = representative?.albumArtist
                                ?: representative?.displayArtist ?: "",
                            year = representative?.year,
                            coverTrackId = coverTrackId,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepository.getPlaylists().onSuccess { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun addTracksToPlaylist(trackIds: List<Int>, playlistId: Int) {
        viewModelScope.launch {
            playlistRepository.addTracks(playlistId, trackIds)
        }
    }
}

package nl.muorg.android.ui.screen.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import javax.inject.Inject

data class PlaylistAlbumsUiState(
    val playlist: Playlist? = null,
    val albums: List<AlbumGroup> = emptyList(),
    val allTracks: List<CatalogTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class PlaylistAlbumsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val playlistId: Int = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistAlbumsUiState())
    val uiState: StateFlow<PlaylistAlbumsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val playlistsDeferred = async { playlistRepository.getPlaylists() }
            val trackIdsDeferred = async { playlistRepository.getPlaylistTracks(playlistId) }
            val allTracksDeferred = async { libraryRepository.getAllTracks() }

            val playlistsResult = playlistsDeferred.await()
            val trackIdsResult = trackIdsDeferred.await()
            val allTracksResult = allTracksDeferred.await()

            if (playlistsResult.isFailure || trackIdsResult.isFailure || allTracksResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load playlist",
                    )
                }
                return@launch
            }

            val playlists = playlistsResult.getOrThrow()
            val playlist = playlists.find { it.id == playlistId }
            val trackIdSet = trackIdsResult.getOrThrow().toSet()
            val allTracks = allTracksResult.getOrThrow()

            val playlistTracks = allTracks.filter { it.id in trackIdSet }
            val albums = libraryRepository.buildAlbumGroups(playlistTracks)

            _uiState.update {
                it.copy(
                    playlist = playlist,
                    albums = albums,
                    allTracks = allTracks,
                    playlists = playlists,
                    isLoading = false,
                )
            }
        }
    }

    fun getTracksForAlbum(albumName: String): List<CatalogTrack> {
        return _uiState.value.allTracks.filter { it.displayAlbum == albumName }
    }

    fun addTracksToPlaylist(trackIds: List<Int>, targetPlaylistId: Int) {
        viewModelScope.launch {
            playlistRepository.addTracks(targetPlaylistId, trackIds)
        }
    }
}

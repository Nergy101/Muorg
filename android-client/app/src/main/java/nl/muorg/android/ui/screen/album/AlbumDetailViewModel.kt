package nl.muorg.android.ui.screen.album

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
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.ui.screen.playlist.AddConflictState
import javax.inject.Inject

data class AlbumDetailUiState(
    val albumName: String = "",
    val artist: String = "",
    val year: Int? = null,
    val tracks: List<CatalogTrack> = emptyList(),
    val coverTrackId: Int? = null,
    val coverArtUri: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val trackPlaylistMembership: Map<String, Set<Int>> = emptyMap(),
    val addConflict: AddConflictState? = null,
    val addToastMsg: String? = null,
    val filterPlaylistId: Int? = null,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
    }

    fun loadAlbum(albumName: String, filterPlaylistId: Int? = null) {
        val current = _uiState.value
        if (current.albumName == albumName && current.tracks.isNotEmpty() && current.filterPlaylistId == filterPlaylistId) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, albumName = albumName, filterPlaylistId = filterPlaylistId) }
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                runCatching { localRepository.getAllTracks() }.fold(
                    onSuccess = { allTracks ->
                        var albumTracks = allTracks
                            .filter { it.displayAlbum == albumName }
                            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                        if (filterPlaylistId != null) {
                            val playlistPaths = localRepository.getPlaylistContents(filterPlaylistId)
                                .mapNotNull { it.track?.path }.toSet()
                            albumTracks = albumTracks.filter { it.path in playlistPaths }
                        }
                        val rep = albumTracks.firstOrNull()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                tracks = albumTracks,
                                artist = rep?.albumArtist ?: rep?.displayArtist ?: "",
                                year = rep?.year,
                                coverTrackId = null,
                                coverArtUri = localRepository.getAlbumArtUri(albumName),
                            )
                        }
                        loadPlaylistMembership()
                    },
                    onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                )
            } else {
                repository.getAllTracks().fold(
                    onSuccess = { allTracks ->
                        var albumTracks = allTracks
                            .filter { it.displayAlbum == albumName }
                            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                        if (filterPlaylistId != null) {
                            val playlistTrackIds = playlistRepository.getPlaylistTracks(filterPlaylistId)
                                .getOrElse { emptyList() }.toSet()
                            albumTracks = albumTracks.filter { it.id in playlistTrackIds }
                        }
                        val rep = albumTracks.firstOrNull()
                        val coverTrackId = albumTracks.firstOrNull { it.hasCover }?.id ?: rep?.id
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                tracks = albumTracks,
                                artist = rep?.albumArtist ?: rep?.displayArtist ?: "",
                                year = rep?.year,
                                coverTrackId = coverTrackId,
                                coverArtUri = null,
                            )
                        }
                    },
                    onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                )
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                runCatching { localRepository.getPlaylists() }.onSuccess { playlists ->
                    _uiState.update { it.copy(playlists = playlists) }
                }
            } else {
                playlistRepository.getPlaylists().onSuccess { playlists ->
                    _uiState.update { it.copy(playlists = playlists) }
                }
            }
        }
    }

    private fun loadPlaylistMembership() {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                val membership = localRepository.getPlaylistMembershipByPath()
                _uiState.update { it.copy(trackPlaylistMembership = membership) }
            }
        }
    }

    fun refreshPlaylistState() {
        loadPlaylists()
        loadPlaylistMembership()
    }

    fun requestAddTracksToPlaylist(tracks: List<CatalogTrack>, playlistId: Int) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                val check = localRepository.checkAddConflict(playlistId, tracks)
                when {
                    check.alreadyPresentCount == 0 -> {
                        localRepository.addTracksToPlaylist(playlistId, tracks)
                        loadPlaylistMembership()
                        loadPlaylists()
                    }
                    check.newTracks.isEmpty() -> {
                        _uiState.update { it.copy(addToastMsg = "All tracks already in this playlist") }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(addConflict = AddConflictState(tracks, check.newTracks, playlistId))
                        }
                    }
                }
            } else {
                playlistRepository.addTracks(playlistId, tracks.map { it.id })
            }
        }
    }

    fun confirmAddNewOnly() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.newTracks)
            loadPlaylistMembership()
            loadPlaylists()
        }
    }

    fun confirmAddAll() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.allTracks)
            loadPlaylistMembership()
            loadPlaylists()
        }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(addConflict = null) }
    }

    fun clearToast() {
        _uiState.update { it.copy(addToastMsg = null) }
    }

    fun removeTrackFromPlaylist(track: CatalogTrack, playlistId: Int) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                localRepository.removeTrackFromPlaylist(playlistId, track.path)
                loadPlaylistMembership()
                loadPlaylists()
            } else {
                playlistRepository.removeTracks(playlistId, listOf(track.id))
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                runCatching { localRepository.createPlaylist(name) }
            } else {
                runCatching { playlistRepository.createPlaylist(name, "🎵") }
            }
            loadPlaylists()
        }
    }

    fun removeAlbumFromPlaylist(playlistId: Int) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            val tracks = _uiState.value.tracks
            if (mode == "local") {
                tracks.forEach { localRepository.removeTrackFromPlaylist(playlistId, it.path) }
                loadPlaylistMembership()
                loadPlaylists()
            } else {
                playlistRepository.removeTracks(playlistId, tracks.map { it.id })
            }
        }
    }
}

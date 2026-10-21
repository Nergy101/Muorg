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
import nl.muorg.android.data.repository.OfflineDownloadManager
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.ui.screen.playlist.AddConflictState
import nl.muorg.android.ui.UiMessenger
import nl.muorg.android.ui.friendlyMessage
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
    val filterPlaylistId: Int? = null,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val preferences: AppPreferences,
    private val uiMessenger: UiMessenger,
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
            } else if (preferences.offlineMode.first()) {
                // Offline mode: the catalog is unreachable, so the album's
                // songs come from the download store — only what is actually
                // downloaded shows.
                var albumTracks = offlineDownloadManager.getAllDownloadedTracks()
                    .filter { it.displayAlbum == albumName }
                    .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                if (filterPlaylistId != null) {
                    val playlistTrackIds = offlineDownloadManager
                        .getDownloadedTracks(filterPlaylistId)
                        .map { it.id }
                        .toSet()
                    albumTracks = albumTracks.filter { it.id in playlistTrackIds }
                }
                val rep = albumTracks.firstOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tracks = albumTracks,
                        artist = rep?.albumArtist ?: rep?.displayArtist ?: "",
                        year = rep?.year,
                        coverTrackId = null,
                        coverArtUri = null,
                    )
                }
            } else {
                repository.getAllTracks().fold(
                    onSuccess = { allTracks ->
                        var albumTracks = allTracks
                            .filter { it.displayAlbum == albumName }
                            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                        if (filterPlaylistId != null) {
                            // getTracksForId, not getPlaylistTracks: filtering by
                            // a smart playlist would otherwise match nothing.
                            val playlistTrackIds = playlistRepository.getTracksForId(filterPlaylistId)
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
            } else if (preferences.offlineMode.first()) {
                _uiState.update { it.copy(playlists = offlineDownloadManager.getDownloadedPlaylists()) }
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
                        uiMessenger.show("All tracks already in this playlist")
                    }
                    else -> {
                        _uiState.update {
                            it.copy(addConflict = AddConflictState(tracks, check.newTracks, playlistId))
                        }
                    }
                }
            } else {
                // The server's add_tracks uses a plain INSERT with no
                // uniqueness constraint, so duplicates are possible. Check
                // first, same as local mode.
                val playlist = _uiState.value.playlists.find { it.id == playlistId }
                if (playlist == null) {
                    uiMessenger.show("Playlist not found")
                    return@launch
                }
                val check = playlistRepository.checkAddConflict(playlist, tracks)
                when {
                    check.alreadyPresentCount == 0 -> {
                        playlistRepository.addTracks(playlist, tracks.map { it.id })
                            .onFailure { e ->
                                uiMessenger.show(e.friendlyMessage())
                            }
                        loadPlaylists()
                    }
                    check.newTracks.isEmpty() -> {
                        uiMessenger.show("All tracks already in this playlist")
                    }
                    else -> {
                        _uiState.update {
                            it.copy(addConflict = AddConflictState(tracks, check.newTracks, playlistId))
                        }
                    }
                }
            }
        }
    }

    fun confirmAddNewOnly() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.newTracks)
                loadPlaylistMembership()
                loadPlaylists()
            } else {
                val playlist = _uiState.value.playlists.find { it.id == conflict.targetPlaylistId }
                if (playlist != null) {
                    playlistRepository.addTracks(playlist, conflict.newTracks.map { it.id })
                        .onFailure { e ->
                            uiMessenger.show(e.friendlyMessage())
                        }
                    loadPlaylists()
                }
            }
        }
    }

    fun confirmAddAll() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.allTracks)
                loadPlaylistMembership()
                loadPlaylists()
            } else {
                val playlist = _uiState.value.playlists.find { it.id == conflict.targetPlaylistId }
                if (playlist != null) {
                    playlistRepository.addTracks(playlist, conflict.allTracks.map { it.id })
                        .onFailure { e ->
                            uiMessenger.show(e.friendlyMessage())
                        }
                    loadPlaylists()
                }
            }
        }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(addConflict = null) }
    }

    fun removeTrackFromPlaylist(track: CatalogTrack, playlistId: Int) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            val playlistName = _uiState.value.playlists.find { it.id == playlistId }?.name ?: "playlist"
            if (mode == "local") {
                localRepository.removeTrackFromPlaylist(playlistId, track.path)
                loadPlaylistMembership()
                loadPlaylists()
                uiMessenger.showWithAction("Removed from $playlistName", "Undo") {
                    localRepository.addTracksToPlaylist(playlistId, listOf(track))
                    loadPlaylistMembership()
                    loadPlaylists()
                }
            } else {
                playlistRepository.removeTracks(playlistId, listOf(track.id))
                uiMessenger.showWithAction("Removed from $playlistName", "Undo") {
                    playlistRepository.addTracks(playlistId, listOf(track.id))
                    loadPlaylists()
                }
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
            val playlistName = _uiState.value.playlists.find { it.id == playlistId }?.name ?: "playlist"
            if (mode == "local") {
                tracks.forEach { localRepository.removeTrackFromPlaylist(playlistId, it.path) }
                loadPlaylistMembership()
                loadPlaylists()
                uiMessenger.showWithAction("Removed from $playlistName", "Undo") {
                    localRepository.addTracksToPlaylist(playlistId, tracks)
                    loadPlaylistMembership()
                    loadPlaylists()
                }
            } else {
                playlistRepository.removeTracks(playlistId, tracks.map { it.id })
                uiMessenger.showWithAction("Removed from $playlistName", "Undo") {
                    playlistRepository.addTracks(playlistId, tracks.map { it.id })
                    loadPlaylists()
                }
            }
        }
    }
}

package nl.muorg.android.ui.screen.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.data.repository.PlaylistTrackEntry
import javax.inject.Inject

data class AddConflictState(
    val allTracks: List<CatalogTrack>,
    val newTracks: List<CatalogTrack>,
    val targetPlaylistId: Int,
)

data class PlaylistAlbumsUiState(
    val playlist: Playlist? = null,
    val albums: List<AlbumGroup> = emptyList(),
    val allTracks: List<CatalogTrack> = emptyList(),
    val localEntries: List<PlaylistTrackEntry> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val trackPlaylistMembership: Map<String, Set<Int>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isLocalMode: Boolean = false,
    val addConflict: AddConflictState? = null,
    val addToastMsg: String? = null,
    val viewStyle: String = "grid",
)

@HiltViewModel
class PlaylistAlbumsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferences: AppPreferences,
    private val libraryRepository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val playlistId: Int = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistAlbumsUiState())
    val uiState: StateFlow<PlaylistAlbumsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.playlistViewStyle.collect { style ->
                _uiState.update { it.copy(viewStyle = style) }
            }
        }
        load()
    }

    fun setViewStyle(style: String) {
        _uiState.update { it.copy(viewStyle = style) }
        viewModelScope.launch { preferences.setPlaylistViewStyle(style) }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val mode = preferences.musicMode.first()

            if (mode == "local") {
                loadLocal()
            } else {
                loadRemote()
            }
        }
    }

    private suspend fun loadLocal() {
        runCatching {
            val playlistsDeferred = viewModelScope.async { localRepository.getPlaylists() }
            val contentsDeferred = viewModelScope.async { localRepository.getPlaylistContents(playlistId) }

            val playlists = playlistsDeferred.await()
            val contents = contentsDeferred.await()
            val playlist = playlists.find { it.id == playlistId }
            val tracks = contents.mapNotNull { it.track }
            val albums = localRepository.buildAlbumGroupsForTracks(tracks)

            val membership = localRepository.getPlaylistMembershipByPath()
            _uiState.update {
                it.copy(
                    playlist = playlist,
                    albums = albums,
                    allTracks = tracks,
                    localEntries = contents,
                    playlists = playlists,
                    trackPlaylistMembership = membership,
                    isLoading = false,
                    isLocalMode = true,
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load playlist") }
        }
    }

    private suspend fun loadRemote() {
        val playlistsDeferred = viewModelScope.async { playlistRepository.getPlaylists() }
        val trackIdsDeferred = viewModelScope.async { playlistRepository.getPlaylistTracks(playlistId) }
        val allTracksDeferred = viewModelScope.async { libraryRepository.getAllTracks() }

        val playlistsResult = playlistsDeferred.await()
        val trackIdsResult = trackIdsDeferred.await()
        val allTracksResult = allTracksDeferred.await()

        if (playlistsResult.isFailure || trackIdsResult.isFailure || allTracksResult.isFailure) {
            _uiState.update { it.copy(isLoading = false, error = "Failed to load playlist") }
            return
        }

        val playlists = playlistsResult.getOrThrow()
        val playlist = playlists.find { it.id == playlistId }
        val trackIdList = trackIdsResult.getOrThrow()
        val allTracks = allTracksResult.getOrThrow()

        val trackById = allTracks.associateBy { it.id }
        val playlistTracks = trackIdList.mapNotNull { trackById[it] }
        val albums = libraryRepository.buildAlbumGroups(playlistTracks)

        _uiState.update {
            it.copy(
                playlist = playlist,
                albums = albums,
                allTracks = playlistTracks,
                playlists = playlists,
                isLoading = false,
                isLocalMode = false,
            )
        }
    }

    fun getTracksForAlbum(albumName: String): List<CatalogTrack> =
        _uiState.value.allTracks.filter { it.displayAlbum == albumName }

    fun requestAddTracksToPlaylist(tracks: List<CatalogTrack>, targetPlaylistId: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isLocalMode) {
                val check = localRepository.checkAddConflict(targetPlaylistId, tracks)
                when {
                    check.alreadyPresentCount == 0 -> {
                        localRepository.addTracksToPlaylist(targetPlaylistId, tracks)
                        loadLocal()
                    }
                    check.newTracks.isEmpty() -> {
                        _uiState.update { it.copy(addToastMsg = "All tracks already in this playlist") }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(addConflict = AddConflictState(tracks, check.newTracks, targetPlaylistId))
                        }
                    }
                }
            } else {
                val trackIdSet = _uiState.value.allTracks
                    .filter { it.id in tracks.map { t -> t.id } }
                    .map { it.id }.toSet()
                playlistRepository.addTracks(targetPlaylistId, tracks.map { it.id })
            }
        }
    }

    fun confirmAddNewOnly() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.newTracks)
            loadLocal()
        }
    }

    fun confirmAddAll() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.allTracks)
            loadLocal()
        }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(addConflict = null) }
    }

    fun clearToast() {
        _uiState.update { it.copy(addToastMsg = null) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                runCatching { localRepository.createPlaylist(name) }
                loadLocal()
            } else {
                runCatching { playlistRepository.createPlaylist(name, "🎵") }
            }
        }
    }

    fun removeAlbumFromPlaylist(albumName: String, targetPlaylistId: Int) {
        viewModelScope.launch {
            val tracks = _uiState.value.allTracks.filter { it.displayAlbum == albumName }
            if (_uiState.value.isLocalMode) {
                tracks.forEach { localRepository.removeTrackFromPlaylist(targetPlaylistId, it.path) }
                loadLocal()
            } else {
                playlistRepository.removeTracks(targetPlaylistId, tracks.map { it.id })
            }
        }
    }

    fun removeTrackFromThisPlaylist(filePath: String) {
        viewModelScope.launch {
            localRepository.removeTrackFromPlaylist(playlistId, filePath)
            loadLocal()
        }
    }

    fun reorderTracks(orderedPaths: List<String>) {
        viewModelScope.launch {
            val trackByPath = _uiState.value.allTracks.associateBy { it.path }
            val reorderedTracks = orderedPaths.mapNotNull { trackByPath[it] }
            if (reorderedTracks.isEmpty()) return@launch
            val success = if (_uiState.value.isLocalMode) {
                localRepository.reorderPlaylist(playlistId, orderedPaths)
                true
            } else {
                playlistRepository.reorderTracks(playlistId, reorderedTracks.map { it.id }).isSuccess
            }
            if (success) {
                _uiState.update { it.copy(allTracks = reorderedTracks) }
            }
        }
    }
}

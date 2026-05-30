package nl.muorg.android.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
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
import nl.muorg.android.ui.screen.playlist.AddConflictState
import javax.inject.Inject

enum class ViewMode { TRACKS, ALBUMS }

enum class SortMode(val label: String) {
    BY_ALBUM("Album"),
    BY_ARTIST("Artist"),
    BY_YEAR("Year"),
}

data class LibraryUiState(
    val allTracks: List<CatalogTrack> = emptyList(),
    val filteredTracks: List<CatalogTrack> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val filteredAlbums: List<AlbumGroup> = emptyList(),
    val viewMode: ViewMode = ViewMode.ALBUMS,
    val sortMode: SortMode = SortMode.BY_ALBUM,
    val sortAscending: Boolean = true,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val activePlaylistId: Int? = null,
    val activePlaylistTrackIds: Set<Int> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val artistFilter: String? = null,
    val trackPlaylistMembership: Map<String, Set<Int>> = emptyMap(),
    val addConflict: AddConflictState? = null,
    val addToastMsg: String? = null,
    val isInitialScanning: Boolean = false,
    val initialScanProgress: Int = 0,
    val initialScanTotal: Int = 0,
    val initialScanCompleted: Boolean = false,
    val albumViewStyle: String = "grid",
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val rawSearchQuery: String get() = _searchQuery.value
    private var currentMode: String = "remote"

    init {
        viewModelScope.launch {
            val sortName = preferences.defaultSort.first()
            val sortMode = SortMode.entries.firstOrNull { it.name == sortName } ?: SortMode.BY_ALBUM
            _uiState.update { it.copy(sortMode = sortMode) }
        }
        viewModelScope.launch {
            preferences.albumViewStyle.collect { style ->
                _uiState.update { it.copy(albumViewStyle = style) }
            }
        }
        viewModelScope.launch {
            preferences.musicMode.collect { mode ->
                currentMode = mode
                loadTracks()
            }
        }
        loadPlaylists()
        observeSearch()
    }

    fun loadTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (currentMode == "local") {
                runCatching {
                    val tracks = localRepository.getAllTracks()
                    val albums = localRepository.buildAlbumGroups()
                    Pair(tracks, albums)
                }.fold(
                    onSuccess = { (tracks, albums) ->
                        val state = _uiState.value
                        if (tracks.isEmpty() && !state.isInitialScanning && !state.initialScanCompleted) {
                            val uris = preferences.localFolderUris.first()
                            if (uris.isNotEmpty()) {
                                triggerInitialScan(uris)
                                return@fold
                            }
                        }
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                allTracks = tracks,
                                filteredTracks = applyFilters(tracks, state),
                                albums = albums,
                                filteredAlbums = applyAlbumFilters(albums, state),
                            )
                        }
                        loadPlaylistMembership()
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
            } else {
                repository.getAllTracks().fold(
                    onSuccess = { tracks ->
                        val albums = repository.buildAlbumGroups(tracks)
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                allTracks = tracks,
                                filteredTracks = applyFilters(tracks, state),
                                albums = albums,
                                filteredAlbums = applyAlbumFilters(albums, state),
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
            }
        }
    }

    private fun triggerInitialScan(uris: Set<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = false, isInitialScanning = true, initialScanProgress = 0, initialScanTotal = 0) }
            runCatching {
                localRepository.scanAndSave(uris) { done, total ->
                    _uiState.update { it.copy(initialScanProgress = done, initialScanTotal = total) }
                }
            }
            _uiState.update { it.copy(isInitialScanning = false, initialScanCompleted = true) }
            loadTracks()
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            if (currentMode == "local") {
                val playlists = localRepository.getPlaylists()
                _uiState.update { it.copy(playlists = playlists) }
            } else {
                playlistRepository.getPlaylists().onSuccess { playlists ->
                    _uiState.update { it.copy(playlists = playlists) }
                }
            }
        }
    }

    private fun loadPlaylistMembership() {
        viewModelScope.launch {
            if (currentMode == "local") {
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
            if (currentMode == "local") {
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

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (currentMode == "local") {
                runCatching { localRepository.createPlaylist(name) }
            } else {
                runCatching { playlistRepository.createPlaylist(name, "🎵") }
            }
            loadPlaylists()
        }
    }

    fun removeAlbumFromPlaylist(albumName: String, playlistId: Int) {
        viewModelScope.launch {
            val tracks = _uiState.value.allTracks.filter { it.displayAlbum == albumName }
            if (currentMode == "local") {
                tracks.forEach { localRepository.removeTrackFromPlaylist(playlistId, it.path) }
                loadPlaylistMembership()
                loadPlaylists()
            } else {
                playlistRepository.removeTracks(playlistId, tracks.map { it.id })
            }
        }
    }

    fun removeTrackFromPlaylist(track: CatalogTrack, playlistId: Int) {
        viewModelScope.launch {
            if (currentMode == "local") {
                localRepository.removeTrackFromPlaylist(playlistId, track.path)
                loadPlaylistMembership()
                loadPlaylists()
            } else {
                playlistRepository.removeTracks(playlistId, listOf(track.id))
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery.debounce(300).collect { query ->
                _uiState.update { state ->
                    state.copy(
                        searchQuery = query,
                        filteredTracks = applyFilters(state.allTracks, state.copy(searchQuery = query)),
                        filteredAlbums = applyAlbumFilters(state.albums, state.copy(searchQuery = query)),
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        _uiState.update { state ->
            state.copy(
                viewMode = if (state.viewMode == ViewMode.TRACKS) ViewMode.ALBUMS else ViewMode.TRACKS
            )
        }
    }

    fun toggleAlbumViewStyle() {
        val newStyle = when (_uiState.value.albumViewStyle) {
            "grid" -> "list"
            "list" -> "tracks"
            else -> "grid"
        }
        _uiState.update { it.copy(albumViewStyle = newStyle) }
        viewModelScope.launch { preferences.setAlbumViewStyle(newStyle) }
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { state ->
            val updated = state.copy(sortMode = mode)
            updated.copy(
                filteredTracks = applyFilters(state.allTracks, updated),
                filteredAlbums = applyAlbumFilters(state.albums, updated),
            )
        }
    }

    fun toggleSortDirection() {
        _uiState.update { state ->
            val updated = state.copy(sortAscending = !state.sortAscending)
            updated.copy(
                filteredTracks = applyFilters(state.allTracks, updated),
                filteredAlbums = applyAlbumFilters(state.albums, updated),
            )
        }
    }

    fun setPlaylistFilter(playlistId: Int?, trackIds: Set<Int>) {
        _uiState.update { state ->
            val updated = state.copy(
                activePlaylistId = playlistId,
                activePlaylistTrackIds = trackIds,
            )
            updated.copy(
                filteredTracks = applyFilters(state.allTracks, updated),
                filteredAlbums = applyAlbumFilters(state.albums, updated),
            )
        }
    }

    fun clearPlaylistFilter() {
        setPlaylistFilter(null, emptySet())
    }

    fun applyArtistFilter(artist: String?) {
        _uiState.update { state ->
            val updated = state.copy(artistFilter = artist)
            updated.copy(
                filteredTracks = applyFilters(state.allTracks, updated),
                filteredAlbums = applyAlbumFilters(state.albums, updated),
            )
        }
    }

    fun clearArtistFilter() {
        _uiState.update { state ->
            val updated = state.copy(artistFilter = null)
            updated.copy(
                filteredTracks = applyFilters(state.allTracks, updated),
                filteredAlbums = applyAlbumFilters(state.albums, updated),
            )
        }
    }

    private fun applyFilters(tracks: List<CatalogTrack>, state: LibraryUiState): List<CatalogTrack> {
        var result = tracks
        if (state.artistFilter != null) {
            result = result.filter { it.displayArtist == state.artistFilter }
        }
        if (state.activePlaylistId != null && state.activePlaylistTrackIds.isNotEmpty()) {
            result = result.filter { it.id in state.activePlaylistTrackIds }
        }
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            result = result.filter { track ->
                track.displayTitle.lowercase().contains(query) ||
                    track.displayArtist.lowercase().contains(query) ||
                    track.displayAlbum.lowercase().contains(query)
            }
        }
        result = when (state.sortMode) {
            SortMode.BY_ALBUM -> result.sortedWith(
                compareBy({ it.displayAlbum.lowercase() }, { it.discNumber ?: 0 }, { it.trackNumber ?: 0 })
            )
            SortMode.BY_ARTIST -> result.sortedWith(
                compareBy({ it.displayArtist.lowercase() }, { it.displayAlbum.lowercase() }, { it.trackNumber ?: 0 })
            )
            SortMode.BY_YEAR -> result
                .filter { it.year != null }
                .sortedWith(compareBy<CatalogTrack> { it.year!! }.thenBy { it.displayAlbum.lowercase() })
        }
        if (!state.sortAscending) result = result.reversed()
        return result
    }

    private fun applyAlbumFilters(albums: List<AlbumGroup>, state: LibraryUiState): List<AlbumGroup> {
        var result = albums
        if (state.artistFilter != null) {
            result = result.filter { album -> album.artist == state.artistFilter }
        }
        if (state.activePlaylistId != null && state.activePlaylistTrackIds.isNotEmpty()) {
            val albumsInPlaylist = state.allTracks
                .filter { it.id in state.activePlaylistTrackIds }
                .map { it.displayAlbum }
                .toSet()
            result = result.filter { it.albumName in albumsInPlaylist }
        }
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            val albumsWithMatchingTrack = state.allTracks
                .filter { it.displayTitle.lowercase().contains(query) }
                .map { it.displayAlbum }
                .toSet()
            result = result.filter { album ->
                album.albumName.lowercase().contains(query) ||
                    album.artist.lowercase().contains(query) ||
                    album.albumName in albumsWithMatchingTrack
            }
        }
        result = when (state.sortMode) {
            SortMode.BY_ALBUM -> result.sortedBy { it.albumName.lowercase() }
            SortMode.BY_ARTIST -> result.sortedWith(
                compareBy({ it.artist.lowercase() }, { it.albumName.lowercase() })
            )
            SortMode.BY_YEAR -> result
                .filter { it.year != null }
                .sortedWith(compareBy<AlbumGroup> { it.year!! }.thenBy { it.albumName.lowercase() })
        }
        if (!state.sortAscending) result = result.reversed()
        return result
    }

    fun getTracksForAlbum(albumName: String): List<CatalogTrack> {
        return _uiState.value.allTracks
            .filter { it.displayAlbum == albumName }
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
    }
}

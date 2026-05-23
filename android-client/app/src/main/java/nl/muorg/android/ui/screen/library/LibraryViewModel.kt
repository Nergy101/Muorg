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
import javax.inject.Inject

enum class ViewMode { TRACKS, ALBUMS }

enum class SortMode(val label: String) {
    BY_ALBUM("Album"),
    BY_ARTIST("Artist"),
    BY_TITLE("Title"),
    BY_YEAR("Year"),
}

data class LibraryUiState(
    val allTracks: List<CatalogTrack> = emptyList(),
    val filteredTracks: List<CatalogTrack> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val filteredAlbums: List<AlbumGroup> = emptyList(),
    val viewMode: ViewMode = ViewMode.ALBUMS,
    val sortMode: SortMode = SortMode.BY_ALBUM,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val activePlaylistId: Int? = null,
    val activePlaylistTrackIds: Set<Int> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
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
    private var currentMode: String = "remote"

    init {
        viewModelScope.launch {
            val sortName = preferences.defaultSort.first()
            val sortMode = SortMode.entries.firstOrNull { it.name == sortName } ?: SortMode.BY_ALBUM
            _uiState.update { it.copy(sortMode = sortMode) }
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

    fun addTracksToPlaylist(trackIds: List<Int>, playlistId: Int) {
        viewModelScope.launch {
            if (currentMode == "local") {
                // CatalogTrack IDs for local tracks are negative; convert back to Room IDs
                localRepository.addTracksToPlaylist(playlistId, trackIds.map { -it })
            } else {
                playlistRepository.addTracks(playlistId, trackIds)
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

    fun setSortMode(mode: SortMode) {
        _uiState.update { state ->
            val updated = state.copy(sortMode = mode)
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

    private fun applyFilters(tracks: List<CatalogTrack>, state: LibraryUiState): List<CatalogTrack> {
        var result = tracks
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
            SortMode.BY_TITLE -> result.sortedBy { it.displayTitle.lowercase() }
            SortMode.BY_YEAR -> result.sortedWith(
                compareByDescending<CatalogTrack> { it.year ?: 0 }.thenBy { it.displayAlbum.lowercase() }
            )
        }
        return result
    }

    private fun applyAlbumFilters(albums: List<AlbumGroup>, state: LibraryUiState): List<AlbumGroup> {
        var result = albums
        if (state.activePlaylistId != null && state.activePlaylistTrackIds.isNotEmpty()) {
            val albumsInPlaylist = state.allTracks
                .filter { it.id in state.activePlaylistTrackIds }
                .map { it.displayAlbum }
                .toSet()
            result = result.filter { it.albumName in albumsInPlaylist }
        }
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            result = result.filter { album ->
                album.albumName.lowercase().contains(query) ||
                    album.artist.lowercase().contains(query)
            }
        }
        result = when (state.sortMode) {
            SortMode.BY_ALBUM, SortMode.BY_TITLE -> result.sortedBy { it.albumName.lowercase() }
            SortMode.BY_ARTIST -> result.sortedWith(
                compareBy({ it.artist.lowercase() }, { it.albumName.lowercase() })
            )
            SortMode.BY_YEAR -> result.sortedWith(
                compareByDescending<AlbumGroup> { it.year ?: 0 }.thenBy { it.albumName.lowercase() }
            )
        }
        return result
    }

    fun getTracksForAlbum(albumName: String): List<CatalogTrack> {
        return _uiState.value.allTracks
            .filter { it.displayAlbum == albumName }
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
    }
}

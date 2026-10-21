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
import kotlinx.coroutines.flow.drop
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.OfflineDownloadManager
import nl.muorg.android.data.repository.PlaylistEntry
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.data.repository.PlaylistTrackEntry
import javax.inject.Inject

data class AddConflictState(
    val allTracks: List<CatalogTrack>,
    val newTracks: List<CatalogTrack>,
    val targetPlaylistId: Int,
)

/**
 * The order-by fields for a playlist's tracks. [PLAYLIST_ORDER] is the saved
 * playlist order (what drag-reorder writes); the rest are view transforms.
 */
enum class PlaylistSortMode(val label: String) {
    PLAYLIST_ORDER("Playlist order"),
    BY_ADDED("Added date"),
    BY_TITLE("Title"),
    BY_ARTIST("Artist"),
}

data class PlaylistAlbumsUiState(
    val playlist: Playlist? = null,
    val albums: List<AlbumGroup> = emptyList(),
    val allTracks: List<CatalogTrack> = emptyList(),
    /** [allTracks] under the active sort — what the screen renders and plays. */
    val sortedTracks: List<CatalogTrack> = emptyList(),
    /** trackId to added-order key (entry id / download time), for [PlaylistSortMode.BY_ADDED]. */
    val addedKeys: Map<Int, Long> = emptyMap(),
    val localEntries: List<PlaylistTrackEntry> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val trackPlaylistMembership: Map<String, Set<Int>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isLocalMode: Boolean = false,
    val addConflict: AddConflictState? = null,
    val addToastMsg: String? = null,
    val viewStyle: String = "tracks",
    val sortMode: PlaylistSortMode = PlaylistSortMode.PLAYLIST_ORDER,
    val sortAscending: Boolean = true,
)

@HiltViewModel
class PlaylistAlbumsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferences: AppPreferences,
    private val libraryRepository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
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
        viewModelScope.launch {
            val sortName = preferences.playlistSort.first()
            val sortMode = PlaylistSortMode.entries.firstOrNull { it.name == sortName }
                ?: PlaylistSortMode.PLAYLIST_ORDER
            _uiState.update { it.copy(sortMode = sortMode) }
        }
        // The direction is the app-wide sort direction, shared with the
        // library and the settings screen.
        viewModelScope.launch {
            preferences.sortAscending.collect { ascending ->
                _uiState.update { state ->
                    val updated = state.copy(sortAscending = ascending)
                    updated.copy(sortedTracks = applySort(state.allTracks, state.addedKeys, updated))
                }
            }
        }
        // Toggling offline mode while this screen is open re-reads from
        // whichever side of the divide is now in force. The first emission is
        // the current value, which the initial load() already covers.
        viewModelScope.launch {
            preferences.offlineMode.drop(1).collect { load() }
        }
        load()
    }

    fun setViewStyle(style: String) {
        _uiState.update { it.copy(viewStyle = style) }
        viewModelScope.launch { preferences.setPlaylistViewStyle(style) }
    }

    fun setSortMode(mode: PlaylistSortMode) {
        _uiState.update { state ->
            val updated = state.copy(sortMode = mode)
            updated.copy(sortedTracks = applySort(state.allTracks, state.addedKeys, updated))
        }
        viewModelScope.launch { preferences.setPlaylistSort(mode.name) }
    }

    fun toggleSortDirection() {
        val newAscending = !_uiState.value.sortAscending
        _uiState.update { state ->
            val updated = state.copy(sortAscending = newAscending)
            updated.copy(sortedTracks = applySort(state.allTracks, state.addedKeys, updated))
        }
        viewModelScope.launch { preferences.setSortAscending(newAscending) }
    }

    private fun applySort(
        tracks: List<CatalogTrack>,
        addedKeys: Map<Int, Long>,
        state: PlaylistAlbumsUiState,
    ): List<CatalogTrack> {
        var result = when (state.sortMode) {
            PlaylistSortMode.PLAYLIST_ORDER -> tracks
            // A smart playlist has no entries, so there is no added order to
            // sort by — keep the server's natural order.
            PlaylistSortMode.BY_ADDED -> if (addedKeys.isEmpty()) tracks
            else tracks.sortedBy { addedKeys[it.id] ?: Long.MAX_VALUE }
            PlaylistSortMode.BY_TITLE -> tracks.sortedBy { it.displayTitle.lowercase() }
            PlaylistSortMode.BY_ARTIST -> tracks.sortedWith(
                compareBy({ it.displayArtist.lowercase() }, { it.displayTitle.lowercase() })
            )
        }
        if (!state.sortAscending) result = result.reversed()
        return result
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val mode = preferences.musicMode.first()

            if (mode == "local") {
                loadLocal()
            } else if (preferences.offlineMode.first()) {
                loadOffline()
            } else {
                loadRemote()
            }
        }
    }

    /**
     * Offline mode: the playlist and its tracks come from the download
     * store, each track already pointing at its local file, so the screen
     * renders and plays with no server involved.
     */
    private suspend fun loadOffline() {
        val playlist = offlineDownloadManager.getDownloadedPlaylist(playlistId)
        if (playlist == null) {
            _uiState.update {
                it.copy(isLoading = false, error = "Not downloaded — download it while online")
            }
            return
        }
        val tracks = offlineDownloadManager.getDownloadedTracks(playlistId)
        val addedKeys = offlineDownloadManager.getDownloadedAddedAts(playlistId)
        _uiState.update { state ->
            val updated = state.copy(
                playlist = playlist,
                albums = libraryRepository.buildAlbumGroups(tracks),
                allTracks = tracks,
                addedKeys = addedKeys,
                playlists = offlineDownloadManager.getDownloadedPlaylists(),
                isLoading = false,
                isLocalMode = false,
            )
            updated.copy(sortedTracks = applySort(tracks, addedKeys, updated))
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
            // The entry row's own id grows with every insert, so it is the
            // order the tracks were added in.
            val addedKeys = contents
                .mapNotNull { entry -> entry.track?.let { it.id to entry.entryId } }
                .toMap()
            val albums = localRepository.buildAlbumGroupsForTracks(tracks)

            val membership = localRepository.getPlaylistMembershipByPath()
            _uiState.update { state ->
                val updated = state.copy(
                    playlist = playlist,
                    albums = albums,
                    allTracks = tracks,
                    addedKeys = addedKeys,
                    localEntries = contents,
                    playlists = playlists,
                    trackPlaylistMembership = membership,
                    isLoading = false,
                    isLocalMode = true,
                )
                updated.copy(sortedTracks = applySort(tracks, addedKeys, updated))
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load playlist") }
        }
    }

    private suspend fun loadRemote() {
        // The catalog is the slow one, so it starts alongside the playlist list.
        // The track ids cannot start with them: which endpoint to ask depends on
        // whether this playlist is smart, and that is what the list tells us.
        val playlistsDeferred = viewModelScope.async { playlistRepository.getPlaylists() }
        val allTracksDeferred = viewModelScope.async { libraryRepository.getAllTracks() }

        val playlistsResult = playlistsDeferred.await()
        val playlist = playlistsResult.getOrNull()?.find { it.id == playlistId }

        // A smart playlist stores rules, not rows: asking the plain /tracks
        // endpoint returns an empty join table, which is why these opened with
        // no songs in them.
        val trackIdsResult = when (playlist) {
            null -> playlistRepository.getPlaylistTracks(playlistId)
            else -> playlistRepository.getTracksFor(playlist)
        }
        val allTracksResult = allTracksDeferred.await()

        if (playlistsResult.isFailure || trackIdsResult.isFailure || allTracksResult.isFailure) {
            _uiState.update { it.copy(isLoading = false, error = "Failed to load playlist") }
            return
        }

        val playlists = playlistsResult.getOrThrow()
        val trackIdList = trackIdsResult.getOrThrow()
        val allTracks = allTracksResult.getOrThrow()

        val trackById = allTracks.associateBy { it.id }
        val playlistTracks = trackIdList.mapNotNull { trackById[it] }
        val albums = libraryRepository.buildAlbumGroups(playlistTracks)

        // The entry id is the join row's id, which grows with every insert,
        // so it doubles as the added order. A smart playlist has no rows, so
        // there is nothing to fetch — and no added order to sort by.
        val entriesResult = if (playlist?.smartRules != null) {
            Result.success(emptyList<PlaylistEntry>())
        } else {
            playlistRepository.getPlaylistEntries(playlistId)
        }
        val addedKeys = entriesResult.getOrDefault(emptyList())
            .associate { it.trackId to it.entryId }

        // A smart playlist has no added order; drop that sort if it was left
        // over from another playlist.
        if (playlist?.smartRules != null && _uiState.value.sortMode == PlaylistSortMode.BY_ADDED) {
            _uiState.update { it.copy(sortMode = PlaylistSortMode.PLAYLIST_ORDER) }
        }

        _uiState.update { state ->
            val updated = state.copy(
                playlist = playlist,
                albums = albums,
                allTracks = playlistTracks,
                addedKeys = addedKeys,
                playlists = playlists,
                isLoading = false,
                isLocalMode = false,
            )
            updated.copy(sortedTracks = applySort(playlistTracks, addedKeys, updated))
        }
    }

    fun getTracksForAlbum(albumName: String): List<CatalogTrack> =
        _uiState.value.sortedTracks.filter { it.displayAlbum == albumName }

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
                // The server's add_tracks uses a plain INSERT with no
                // uniqueness constraint, so duplicates are possible. Check
                // first, same as local mode.
                val playlist = state.playlists.find { it.id == targetPlaylistId }
                if (playlist == null) {
                    _uiState.update { it.copy(addToastMsg = "Playlist not found") }
                    return@launch
                }
                val check = playlistRepository.checkAddConflict(playlist, tracks)
                when {
                    check.alreadyPresentCount == 0 -> {
                        playlistRepository.addTracks(playlist, tracks.map { it.id })
                            .onFailure { e ->
                                _uiState.update { it.copy(addToastMsg = e.message ?: "Could not add to playlist") }
                            }
                        load()
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
            }
        }
    }

    fun confirmAddNewOnly() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            if (_uiState.value.isLocalMode) {
                localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.newTracks)
                loadLocal()
            } else {
                val playlist = _uiState.value.playlists.find { it.id == conflict.targetPlaylistId }
                if (playlist != null) {
                    playlistRepository.addTracks(playlist, conflict.newTracks.map { it.id })
                        .onFailure { e ->
                            _uiState.update { it.copy(addToastMsg = e.message ?: "Could not add to playlist") }
                        }
                    load()
                }
            }
        }
    }

    fun confirmAddAll() {
        val conflict = _uiState.value.addConflict ?: return
        _uiState.update { it.copy(addConflict = null) }
        viewModelScope.launch {
            if (_uiState.value.isLocalMode) {
                localRepository.addTracksToPlaylist(conflict.targetPlaylistId, conflict.allTracks)
                loadLocal()
            } else {
                val playlist = _uiState.value.playlists.find { it.id == conflict.targetPlaylistId }
                if (playlist != null) {
                    playlistRepository.addTracks(playlist, conflict.allTracks.map { it.id })
                        .onFailure { e ->
                            _uiState.update { it.copy(addToastMsg = e.message ?: "Could not add to playlist") }
                        }
                    load()
                }
            }
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
                _uiState.update { state ->
                    val updated = state.copy(allTracks = reorderedTracks)
                    updated.copy(sortedTracks = applySort(reorderedTracks, state.addedKeys, updated))
                }
            }
        }
    }
}

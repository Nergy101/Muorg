package nl.muorg.android.ui.screen.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.DownloadProgress
import nl.muorg.android.data.repository.LocalLibraryRepository
import kotlinx.serialization.json.Json
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.SmartRule
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.OfflineDownloadManager
import nl.muorg.android.data.repository.PlaylistRepository
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val newPlaylistName: String = "",
    val newPlaylistIcon: String = "🎵",
    val showEditDialog: Boolean = false,
    val editingPlaylist: Playlist? = null,
    val editName: String = "",
    val editIcon: String = "🎵",
    /** Four DISTINCT-album track ids per playlist, for the 2x2 cover mosaic. */
    val covers: Map<Int, List<Int>> = emptyMap(),
    /**
     * Track id to on-device art file, for the local-mode mosaic. Empty in
     * server mode — server tracks have no local art — so the tiles fall back
     * to the server's cover endpoint there.
     */
    val coverPaths: Map<Int, String> = emptyMap(),
    /** Pinned playlist ids, in pin order — pinned tiles sort to the front. */
    val pinnedIds: List<Int> = emptyList(),
    val isServerMode: Boolean = false,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val showSmartDialog: Boolean = false,
    val smartName: String = "",
    val smartRules: List<SmartRule> = listOf(SmartRule("genre", "contains", "")),
    val smartSaving: Boolean = false,
    /**
     * Offline mode is on: the list shows downloaded playlists only, and
     * every server operation (create, pin, rename, delete, download) is off.
     */
    val isOfflineMode: Boolean = false,
    /** Playlist ids with a download — the tile's filled download button. */
    val downloadedPlaylistIds: Set<Int> = emptySet(),
    val showRemoveDownloadDialog: Boolean = false,
    val removingDownloadPlaylist: Playlist? = null,
)

/** The fields the server's rule engine accepts, in the web's order. */
val SMART_FIELDS = listOf(
    "genre" to "Genre",
    "artist" to "Artist",
    "album" to "Album",
    "title" to "Title",
    "year" to "Year",
    "rating" to "Rating",
    "play_count" to "Play count",
)

val SMART_TEXT_OPS = listOf(
    "contains" to "contains",
    "eq" to "is",
    "neq" to "is not",
)

val SMART_NUMBER_OPS = listOf(
    "eq" to "=",
    "neq" to "\u2260",
    "gt" to ">",
    "gte" to "\u2265",
    "lt" to "<",
    "lte" to "\u2264",
)

fun opsForField(field: String): List<Pair<String, String>> =
    if (field in setOf("year", "rating", "play_count")) SMART_NUMBER_OPS else SMART_TEXT_OPS

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
    private val libraryRepository: LibraryRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    private var currentMode: String = "remote"

    init {
        viewModelScope.launch {
            preferences.pinnedPlaylists.collect { pinned ->
                // Local mode only: pins are a per-device preference there.
                // In server mode the server's pin set (loaded with the
                // playlists) is the source of truth — never merged.
                if (currentMode == "local") {
                    _uiState.update { it.copy(pinnedIds = pinned.toList()) }
                }
            }
        }
        viewModelScope.launch {
            preferences.musicMode.collect { mode ->
                currentMode = mode
                loadPlaylists()
            }
        }
        viewModelScope.launch {
            preferences.offlineMode.collect { enabled ->
                _uiState.update { it.copy(isOfflineMode = enabled) }
                loadPlaylists()
            }
        }
        viewModelScope.launch {
            offlineDownloadManager.downloadProgress.collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }
        viewModelScope.launch {
            offlineDownloadManager.observeDownloadedPlaylistIds().collect { ids ->
                _uiState.update { it.copy(downloadedPlaylistIds = ids) }
            }
        }
    }

    /** Server operations are unreachable while offline mode is on. */
    private fun isOffline(): Boolean =
        currentMode != "local" && _uiState.value.isOfflineMode

    /**
     * The web's playlist tile is a 2x2 mosaic of the playlist's own covers, so
     * each one needs a few of its track ids. Fetched after the list lands and
     * in parallel — serially this is one round trip per playlist before the
     * grid can paint.
     */
    /**
     * The web's tile is a mosaic of four DISTINCT albums, not the first four
     * tracks — a playlist that opens with one album otherwise renders the same
     * sleeve four times. Scans up to `COVER_SCAN` tracks for distinct albums
     * that actually have artwork.
     *
     * Smart playlists resolve through their own endpoint; the plain one reads
     * the join table and returns nothing for them, which is why they showed no
     * artwork at all.
     */
    private fun loadCovers(playlists: List<Playlist>) {
        viewModelScope.launch {
            // Offline mode: the catalog and the track lists are server calls,
            // so the mosaic is built from the downloaded tracks themselves.
            if (isOffline()) {
                val covers = playlists.map { playlist ->
                    async {
                        val tracks = offlineDownloadManager.getDownloadedTracks(playlist.id)
                        playlist.id to pickDistinctCovers(
                            tracks.map { it.id },
                            tracks.associateBy { it.id },
                        )
                    }
                }.awaitAll().toMap()
                // Downloaded tracks carry no local art files, so there are no
                // local cover paths to offer in offline mode either.
                _uiState.update { it.copy(covers = covers, coverPaths = emptyMap()) }
                return@launch
            }

            // Local mode reads the device's own catalog: the server's track
            // ids never match the local (negative) ones, so the mosaic would
            // come back empty.
            val byId = runCatching {
                if (currentMode == "local") localRepository.getAllTracks()
                else libraryRepository.getAllTracks().getOrThrow()
            }.getOrDefault(emptyList()).associateBy { it.id }

            val covers = playlists.map { playlist ->
                async {
                    val ids = if (currentMode == "local") {
                        runCatching {
                            localRepository.getPlaylistContents(playlist.id).mapNotNull { it.track?.id }
                        }.getOrDefault(emptyList())
                    } else {
                        repository.getTracksFor(playlist).getOrDefault(emptyList())
                    }
                    playlist.id to pickDistinctCovers(ids, byId)
                }
            }.awaitAll().toMap()
            _uiState.update {
                it.copy(
                    covers = covers,
                    coverPaths = byId.mapNotNull { (id, track) ->
                        track.localCoverPath?.let { path -> id to path }
                    }.toMap(),
                )
            }
        }
    }

    private fun pickDistinctCovers(
        trackIds: List<Int>,
        byId: Map<Int, CatalogTrack>,
    ): List<Int> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Int>()
        // Scan the whole playlist, not a prefix: a playlist that opens with 60
        // tracks off one album would otherwise fall back to a single sleeve.
        for (id in trackIds) {
            val track = byId[id] ?: continue
            if (!track.hasCover) continue
            val key = track.displayAlbum + "|" + track.displayArtist
            if (!seen.add(key)) continue
            out += track.id
            if (out.size == 4) break
        }
        // A library with no catalog match (local mode, cold cache) still gets a
        // tile rather than an empty square.
        return if (out.isEmpty()) trackIds.take(4) else out
    }

    fun showSmartCreateDialog() {
        _uiState.update {
            it.copy(
                showSmartDialog = true,
                smartName = "",
                smartRules = listOf(SmartRule("genre", "contains", "")),
            )
        }
    }

    fun dismissSmartDialog() = _uiState.update { it.copy(showSmartDialog = false) }

    fun onSmartNameChange(name: String) = _uiState.update { it.copy(smartName = name) }

    fun addSmartRule() = _uiState.update {
        it.copy(smartRules = it.smartRules + SmartRule("genre", "contains", ""))
    }

    fun removeSmartRule(index: Int) = _uiState.update {
        if (it.smartRules.size <= 1) it
        else it.copy(smartRules = it.smartRules.filterIndexed { i, _ -> i != index })
    }

    fun updateSmartRule(index: Int, rule: SmartRule) = _uiState.update { state ->
        state.copy(smartRules = state.smartRules.mapIndexed { i, r -> if (i == index) rule else r })
    }

    fun createSmartPlaylist() {
        if (isOffline()) return
        val state = _uiState.value
        val rules = state.smartRules.filter { it.value.isNotBlank() }
        if (state.smartName.isBlank() || rules.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(smartSaving = true) }
            val json = Json.encodeToString(rules)
            repository.createSmartPlaylist(state.smartName.trim(), json).fold(
                onSuccess = {
                    _uiState.update { it.copy(showSmartDialog = false, smartSaving = false) }
                    loadPlaylists()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(smartSaving = false, error = e.message) }
                },
            )
        }
    }

    fun togglePin(playlistId: Int) {
        if (isOffline()) return
        if (currentMode == "local") {
            viewModelScope.launch { preferences.togglePinnedPlaylist(playlistId) }
        } else {
            // The server stores the whole pin set in order, so toggling is a
            // read-modify-write of that list, applied optimistically.
            val next = if (playlistId in _uiState.value.pinnedIds) {
                _uiState.value.pinnedIds.filterNot { it == playlistId }
            } else {
                _uiState.value.pinnedIds + playlistId
            }
            _uiState.update { it.copy(pinnedIds = next) }
            viewModelScope.launch {
                repository.setPins(next).onFailure {
                    // Re-read rather than revert: the server's set is the
                    // truth and a second toggle may already have landed.
                    repository.getPins().onSuccess { pins ->
                        _uiState.update { it.copy(pinnedIds = pins) }
                    }
                }
            }
        }
    }

    /**
     * The tile's download button: a fresh download for a playlist that is not
     * downloaded yet, the remove-downloads confirmation for one that is.
     *
     * Local mode has no download action at all: the playlist is already on
     * the device, and asking the server for it by id would fetch whatever
     * server playlist happens to share the id. The tile hides the button;
     * this guard keeps the server call unreachable from any other path.
     */
    fun onDownloadClicked(playlist: Playlist) {
        if (currentMode == "local") return
        if (playlist.id in _uiState.value.downloadedPlaylistIds) {
            _uiState.update {
                it.copy(showRemoveDownloadDialog = true, removingDownloadPlaylist = playlist)
            }
        } else {
            downloadPlaylist(playlist)
        }
    }

    fun dismissRemoveDownloadDialog() {
        _uiState.update { it.copy(showRemoveDownloadDialog = false, removingDownloadPlaylist = null) }
    }

    fun confirmRemoveDownload() {
        val id = _uiState.value.removingDownloadPlaylist?.id ?: return
        dismissRemoveDownloadDialog()
        offlineDownloadManager.removePlaylist(id)
    }

    fun downloadPlaylist(playlist: Playlist) {
        if (isOffline()) return
        if (currentMode == "local") return
        viewModelScope.launch {
            val ids = repository.getTracksFor(playlist).getOrDefault(emptyList())
            val byId = runCatching { libraryRepository.getAllTracks().getOrThrow() }
                .getOrDefault(emptyList())
                .associateBy { it.id }
            val tracks = ids.mapNotNull { byId[it] }
            if (tracks.isNotEmpty()) offlineDownloadManager.downloadPlaylist(playlist, tracks)
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            // Read the preference directly rather than the state the
            // collector maintains: this also runs from the screen's resume
            // effect, which can fire before the collector's first emission.
            val offline = currentMode != "local" && preferences.offlineMode.first()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isServerMode = currentMode != "local",
                    isOfflineMode = offline,
                )
            }
            if (currentMode == "local") {
                runCatching { localRepository.getPlaylists() }.fold(
                    onSuccess = { playlists ->
                        _uiState.update { it.copy(isLoading = false, playlists = playlists) }
                        loadCovers(playlists)
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
                _uiState.update { it.copy(pinnedIds = preferences.pinnedPlaylists.first().toList()) }
            } else if (offline) {
                // Offline mode: the server is unreachable by design, so the
                // list is the downloaded playlists, read from the device.
                val playlists = offlineDownloadManager.getDownloadedPlaylists()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlists = playlists,
                        pinnedIds = preferences.pinnedPlaylists.first().toList(),
                    )
                }
                loadCovers(playlists)
            } else {
                repository.getPlaylists().fold(
                    onSuccess = { playlists ->
                        _uiState.update { it.copy(isLoading = false, playlists = playlists) }
                        loadCovers(playlists)
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
                // Server pins replace the local set for this screen; the two
                // stores are never merged.
                repository.getPins().onSuccess { pins ->
                    _uiState.update { it.copy(pinnedIds = pins) }
                }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, newPlaylistName = "", newPlaylistIcon = "🎵") }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(newPlaylistName = name) }
    }

    fun onIconChange(icon: String) {
        _uiState.update { it.copy(newPlaylistIcon = icon) }
    }

    fun createPlaylist() {
        if (isOffline()) return
        val state = _uiState.value
        if (state.newPlaylistName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(showCreateDialog = false) }
            if (currentMode == "local") {
                runCatching {
                    localRepository.createPlaylist(
                        name = state.newPlaylistName.trim(),
                        icon = state.newPlaylistIcon.ifBlank { "🎵" },
                    )
                }.fold(
                    onSuccess = { loadPlaylists() },
                    onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
                )
            } else {
                repository.createPlaylist(
                    name = state.newPlaylistName.trim(),
                    icon = state.newPlaylistIcon.ifBlank { "🎵" },
                ).fold(
                    onSuccess = { loadPlaylists() },
                    onFailure = { error ->
                        _uiState.update { it.copy(error = error.message) }
                    }
                )
            }
        }
    }

    fun showEditDialog(playlist: Playlist) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editingPlaylist = playlist,
                editName = playlist.name,
                editIcon = playlist.icon?.takeIf { i -> i.isNotBlank() } ?: "🎵",
            )
        }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(showEditDialog = false, editingPlaylist = null) }
    }

    fun onEditNameChange(name: String) {
        _uiState.update { it.copy(editName = name) }
    }

    fun onEditIconChange(icon: String) {
        _uiState.update { it.copy(editIcon = icon) }
    }

    fun updatePlaylist() {
        if (isOffline()) return
        val state = _uiState.value
        val id = state.editingPlaylist?.id ?: return
        if (state.editName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(showEditDialog = false, editingPlaylist = null) }
            if (currentMode == "local") {
                runCatching {
                    localRepository.updatePlaylist(
                        id = id,
                        name = state.editName.trim(),
                        icon = state.editIcon.ifBlank { "🎵" },
                    )
                }.fold(
                    onSuccess = { loadPlaylists() },
                    onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
                )
                return@launch
            } else {
                repository.updatePlaylist(
                    id = id,
                    name = state.editName.trim(),
                    icon = state.editIcon.ifBlank { "🎵" },
                ).fold(
                    onSuccess = { _ ->
                        // Optimistically update from local edit state — server PATCH responses
                        // may be partial and miss fields like track_count.
                        _uiState.update { s ->
                            s.copy(playlists = s.playlists.map { p ->
                                if (p.id == id) p.copy(
                                    name = state.editName.trim(),
                                    icon = state.editIcon.ifBlank { "🎵" },
                                ) else p
                            })
                        }
                        loadPlaylists() // background refresh for full accuracy
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(error = error.message) }
                        loadPlaylists()
                    }
                )
            }
        }
    }

    fun deletePlaylist(id: Int) {
        if (isOffline()) return
        viewModelScope.launch {
            if (currentMode == "local") {
                runCatching { localRepository.deletePlaylist(id) }.fold(
                    onSuccess = { loadPlaylists() },
                    onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
                )
            } else {
                repository.deletePlaylist(id).fold(
                    onSuccess = {
                        // The playlist's downloads go with it — the files
                        // would otherwise sit in offline storage with no
                        // playlist left to play them.
                        offlineDownloadManager.removePlaylist(id)
                        loadPlaylists()
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(error = error.message) }
                    }
                )
            }
        }
    }
}

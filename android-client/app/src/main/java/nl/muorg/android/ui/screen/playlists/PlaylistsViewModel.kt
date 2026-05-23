package nl.muorg.android.ui.screen.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LocalLibraryRepository
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
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    private var currentMode: String = "remote"

    init {
        viewModelScope.launch {
            preferences.musicMode.collect { mode ->
                currentMode = mode
                loadPlaylists()
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (currentMode == "local") {
                runCatching { localRepository.getPlaylists() }.fold(
                    onSuccess = { playlists ->
                        _uiState.update { it.copy(isLoading = false, playlists = playlists) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
            } else {
                repository.getPlaylists().fold(
                    onSuccess = { playlists ->
                        _uiState.update { it.copy(isLoading = false, playlists = playlists) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
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
        val state = _uiState.value
        if (state.newPlaylistName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(showCreateDialog = false) }
            if (currentMode == "local") {
                runCatching { localRepository.createPlaylist(state.newPlaylistName.trim()) }.fold(
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
        val state = _uiState.value
        val id = state.editingPlaylist?.id ?: return
        if (state.editName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(showEditDialog = false, editingPlaylist = null) }
            if (currentMode == "local") {
                _uiState.update { s ->
                    s.copy(playlists = s.playlists.map { p ->
                        if (p.id == id) p.copy(name = state.editName.trim(), icon = state.editIcon.ifBlank { "🎵" }) else p
                    })
                }
                loadPlaylists()
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
        viewModelScope.launch {
            if (currentMode == "local") {
                runCatching { localRepository.deletePlaylist(id) }.fold(
                    onSuccess = { loadPlaylists() },
                    onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
                )
            } else {
                repository.deletePlaylist(id).fold(
                    onSuccess = { loadPlaylists() },
                    onFailure = { error ->
                        _uiState.update { it.copy(error = error.message) }
                    }
                )
            }
        }
    }
}

package nl.muorg.android.ui.screen.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.MuorgApiService
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LocalLibraryRepository
import javax.inject.Inject

sealed class WelcomeDestination {
    object Library : WelcomeDestination()
    object RemoteSetup : WelcomeDestination()
}

enum class WelcomeStep { CHECKING, MODE_SELECT, LOCAL_SETUP }

data class WelcomeUiState(
    val step: WelcomeStep = WelcomeStep.CHECKING,
    val localFolderUris: Set<String> = emptySet(),
    val destination: WelcomeDestination? = null,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val api: MuorgApiService,
    private val localLibraryRepository: LocalLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        checkExistingSetup()
    }

    private fun checkExistingSetup() {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()

            if (mode == "local") {
                val folders = preferences.localFolderUris.first()
                if (folders.isNotEmpty()) {
                    _uiState.update { it.copy(destination = WelcomeDestination.Library) }
                    return@launch
                }
            }

            if (mode == "remote") {
                val url = preferences.serverUrl.first()
                val key = preferences.apiKey.first()
                if (url.isNotBlank() && key.isNotBlank()) {
                    val ok = runCatching { api.health() }.getOrNull()?.isSuccessful == true
                    if (ok) {
                        _uiState.update { it.copy(destination = WelcomeDestination.Library) }
                        return@launch
                    }
                }
            }

            _uiState.update { it.copy(step = WelcomeStep.MODE_SELECT) }
        }
    }

    fun chooseRemote() {
        _uiState.update { it.copy(destination = WelcomeDestination.RemoteSetup) }
    }

    fun chooseLocal() {
        viewModelScope.launch {
            preferences.setMusicMode("local")
            val existing = preferences.localFolderUris.first()
            _uiState.update { it.copy(step = WelcomeStep.LOCAL_SETUP, localFolderUris = existing) }
        }
    }

    fun addFolder(uri: String) {
        viewModelScope.launch {
            preferences.addLocalFolderUri(uri)
            val updated = preferences.localFolderUris.first()
            _uiState.update { it.copy(localFolderUris = updated) }
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            localLibraryRepository.removeTracksForFolder(uri)
            preferences.removeLocalFolderUri(uri)
            val updated = preferences.localFolderUris.first()
            _uiState.update { it.copy(localFolderUris = updated) }
        }
    }

    fun finishLocalSetup() {
        _uiState.update { it.copy(destination = WelcomeDestination.Library) }
    }

    fun consumeDestination() {
        _uiState.update { it.copy(destination = null) }
    }

    fun goBackToModeSelect() {
        _uiState.update { it.copy(step = WelcomeStep.MODE_SELECT) }
    }
}

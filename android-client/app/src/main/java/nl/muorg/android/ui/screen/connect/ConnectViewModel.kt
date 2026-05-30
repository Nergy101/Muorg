package nl.muorg.android.ui.screen.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.MuorgApiService
import nl.muorg.android.data.preferences.AppPreferences
import javax.inject.Inject

data class ConnectUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class ConnectEvent {
    object Connected : ConnectEvent()
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val api: MuorgApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConnectEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConnectEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val savedUrl = preferences.serverUrl.first()
            val savedKey = preferences.apiKey.first()
            _uiState.update { it.copy(serverUrl = savedUrl, apiKey = savedKey) }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    fun onApiKeyChange(key: String) {
        _uiState.update { it.copy(apiKey = key, error = null) }
    }

    fun connect() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "Please enter both server URL and API key") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Save credentials first so the OkHttp interceptor picks them up
            preferences.saveCredentials(state.serverUrl, state.apiKey)

            val result = runCatching { api.health() }
            if (result.isSuccess && result.getOrNull()?.isSuccessful == true) {
                _events.tryEmit(ConnectEvent.Connected)
            } else {
                val errorMsg = result.exceptionOrNull()?.message
                    ?: "Could not reach server. Check the URL and try again."
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                // Revert credentials on failure
                preferences.clearCredentials()
            }
        }
    }

}

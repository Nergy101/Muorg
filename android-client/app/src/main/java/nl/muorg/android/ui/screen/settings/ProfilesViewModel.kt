package nl.muorg.android.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.schema.Profile
import nl.muorg.android.data.repository.ProfileRepository
import javax.inject.Inject

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
    val enabled: Boolean = false,
    val error: String? = null,
)

/** NER-326: settings section state for listening profiles. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val uiState: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        repository.activeProfile,
        repository.enabled,
        _error,
    ) { profiles, active, enabled, error ->
        ProfilesUiState(profiles, active, enabled, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun setActive(id: Long) {
        viewModelScope.launch { runCatching { repository.setActive(id) }.onFailure { _error.value = "Could not switch profile" } }
    }

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { repository.create(name) }.onFailure { _error.value = "Could not create the profile" }
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(id, name) }.onFailure { _error.value = "Could not rename the profile" }
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            runCatching { repository.remove(id) }.onFailure { _error.value = "Could not delete the profile" }
        }
    }
}

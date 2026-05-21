package nl.muorg.android.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.muorg.android.BuildConfig
import nl.muorg.android.data.api.GithubRelease
import nl.muorg.android.data.api.MuorgApiService
import nl.muorg.android.data.api.Stats
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.ui.screen.library.SortMode
import java.net.URL
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val showLogoutDialog: Boolean = false,
    val stats: Stats? = null,
    val statsError: Boolean = false,
    val continuousPlayback: Boolean = true,
    val defaultSort: SortMode = SortMode.BY_ALBUM,
    val showSortDropdown: Boolean = false,
    val latestVersion: String? = null,
    val latestReleaseUrl: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val api: MuorgApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val githubJson = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val url = preferences.serverUrl.first()
            val continuousPlayback = preferences.continuousPlayback.first()
            val sortName = preferences.defaultSort.first()
            val sortMode = SortMode.entries.firstOrNull { it.name == sortName } ?: SortMode.BY_ALBUM
            _uiState.update {
                it.copy(
                    serverUrl = url,
                    continuousPlayback = continuousPlayback,
                    defaultSort = sortMode,
                )
            }
        }
        loadStats()
        checkForUpdate()
    }

    fun loadStats() {
        viewModelScope.launch {
            runCatching { api.getStats() }.fold(
                onSuccess = { stats -> _uiState.update { it.copy(stats = stats, statsError = false) } },
                onFailure = { _uiState.update { it.copy(statsError = true) } },
            )
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = URL("https://api.github.com/repos/Nergy101/Muorg/releases/latest").readText()
                    githubJson.decodeFromString<GithubRelease>(text)
                }
            }.onSuccess { release ->
                val latest = release.tagName.removePrefix("v")
                if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                    _uiState.update {
                        it.copy(latestVersion = latest, latestReleaseUrl = release.htmlUrl)
                    }
                }
            }
        }
    }

    fun setContinuousPlayback(enabled: Boolean) {
        _uiState.update { it.copy(continuousPlayback = enabled) }
        viewModelScope.launch { preferences.setContinuousPlayback(enabled) }
    }

    fun setDefaultSort(mode: SortMode) {
        _uiState.update { it.copy(defaultSort = mode, showSortDropdown = false) }
        viewModelScope.launch { preferences.setDefaultSort(mode.name) }
    }

    fun showSortDropdown() = _uiState.update { it.copy(showSortDropdown = true) }
    fun dismissSortDropdown() = _uiState.update { it.copy(showSortDropdown = false) }

    fun showLogoutDialog() = _uiState.update { it.copy(showLogoutDialog = true) }
    fun dismissLogoutDialog() = _uiState.update { it.copy(showLogoutDialog = false) }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.clearCredentials()
            onDone()
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val c = candidate.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val cur = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(c.size, cur.size)) {
            val a = c.getOrElse(i) { 0 }
            val b = cur.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

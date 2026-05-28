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
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.ui.screen.library.SortMode
import java.net.URL
import javax.inject.Inject

enum class RefreshStatus { IDLE, LOADING, SUCCESS, ERROR }

enum class ScanStatus { IDLE, SCANNING, DONE, ERROR }

enum class SourceMode { ONLINE_SERVER, LOCAL_LIBRARY }

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
    val refreshStatus: RefreshStatus = RefreshStatus.IDLE,
    val refreshError: String? = null,
    val musicMode: String = "remote",
    val localFolderUris: Set<String> = emptySet(),
    val scanStatus: ScanStatus = ScanStatus.IDLE,
    val scanTrackCount: Int = 0,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val sourceMode: SourceMode = SourceMode.ONLINE_SERVER,
    val showSwitchConfirmDialog: Boolean = false,
    val pendingSourceMode: SourceMode? = null,
    val useTrueBlack: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val api: MuorgApiService,
    private val localLibraryRepository: LocalLibraryRepository,
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
        viewModelScope.launch {
            preferences.musicMode.collect { mode ->
                _uiState.update { it.copy(
                    musicMode = mode,
                    sourceMode = if (mode == "local") SourceMode.LOCAL_LIBRARY else SourceMode.ONLINE_SERVER,
                )}
            }
        }
        viewModelScope.launch {
            preferences.localFolderUris.collect { uris ->
                _uiState.update { it.copy(localFolderUris = uris) }
            }
        }
        viewModelScope.launch {
            preferences.useTrueBlack.collect { enabled ->
                _uiState.update { it.copy(useTrueBlack = enabled) }
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
                val latest = Regex("""\d+\.\d+\.\d+""").find(release.tagName)?.value ?: return@onSuccess
                if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                    _uiState.update {
                        it.copy(latestVersion = latest, latestReleaseUrl = release.htmlUrl)
                    }
                }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshStatus = RefreshStatus.LOADING, refreshError = null) }
            runCatching { api.getStats() }.fold(
                onSuccess = { stats ->
                    _uiState.update {
                        it.copy(stats = stats, statsError = false, refreshStatus = RefreshStatus.SUCCESS)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(statsError = true, refreshStatus = RefreshStatus.ERROR, refreshError = e.message)
                    }
                },
            )
        }
    }

    fun setContinuousPlayback(enabled: Boolean) {
        _uiState.update { it.copy(continuousPlayback = enabled) }
        viewModelScope.launch { preferences.setContinuousPlayback(enabled) }
    }

    fun setUseTrueBlack(enabled: Boolean) {
        _uiState.update { it.copy(useTrueBlack = enabled) }
        viewModelScope.launch { preferences.setUseTrueBlack(enabled) }
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

    fun setMusicMode(mode: String) {
        viewModelScope.launch { preferences.setMusicMode(mode) }
    }

    fun requestSwitchSourceMode(mode: SourceMode) {
        if (mode == _uiState.value.sourceMode) return
        _uiState.update { it.copy(showSwitchConfirmDialog = true, pendingSourceMode = mode) }
    }

    fun dismissSwitchDialog() = _uiState.update { it.copy(showSwitchConfirmDialog = false, pendingSourceMode = null) }

    fun confirmSwitchSourceMode(onNavigateToWelcome: () -> Unit) {
        val mode = _uiState.value.pendingSourceMode ?: return
        _uiState.update { it.copy(showSwitchConfirmDialog = false, pendingSourceMode = null) }
        viewModelScope.launch {
            val modeStr = if (mode == SourceMode.LOCAL_LIBRARY) "local" else "remote"
            preferences.setMusicMode(modeStr)
            if (mode == SourceMode.ONLINE_SERVER) {
                val url = preferences.serverUrl.first()
                if (url.isEmpty()) onNavigateToWelcome()
            } else {
                val uris = preferences.localFolderUris.first()
                if (uris.isEmpty()) onNavigateToWelcome()
            }
        }
    }

    fun addFolder(uri: String) {
        viewModelScope.launch { preferences.addLocalFolderUri(uri) }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            localLibraryRepository.removeTracksForFolder(uri)
            preferences.removeLocalFolderUri(uri)
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(scanStatus = ScanStatus.SCANNING, scanProgress = 0, scanTotal = 0) }
            val uris = preferences.localFolderUris.first()
            runCatching {
                localLibraryRepository.scanAndSave(uris) { done, total ->
                    _uiState.update { it.copy(scanProgress = done, scanTotal = total) }
                }
            }.fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(scanStatus = ScanStatus.DONE, scanTrackCount = count) }
                },
                onFailure = {
                    _uiState.update { it.copy(scanStatus = ScanStatus.ERROR) }
                },
            )
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

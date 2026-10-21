package nl.muorg.android.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import nl.muorg.android.data.api.bodyOrThrow
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.api.toDomain
import nl.muorg.android.data.api.Stats
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.player.PlayerController
import nl.muorg.android.ui.UiMessenger
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
    val playerBarTapOpensPlayer: Boolean = true,
    val materialYou: Boolean = false,
    val themeMode: String = "dark",
    val albumViewStyle: String = "grid",
    val sortAscending: Boolean = true,
    val deviceName: String = "",
    val offlineMode: Boolean = false,
    val downloadMobileData: Boolean = false,
    val isBatteryOptimizationExempt: Boolean = false,
    val updateCheckInProgress: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val api: MuorgApi,
    private val localLibraryRepository: LocalLibraryRepository,
    private val playerController: PlayerController,
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context,
    private val uiMessenger: UiMessenger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val githubJson = Json { ignoreUnknownKeys = true }

    init {
        refreshBatteryOptimizationExemption()
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
        viewModelScope.launch {
            preferences.playerBarTapOpensPlayer.collect { value ->
                _uiState.update { it.copy(playerBarTapOpensPlayer = value) }
            }
        }
        viewModelScope.launch {
            preferences.albumViewStyle.collect { style ->
                _uiState.update { it.copy(albumViewStyle = style) }
            }
        }
        viewModelScope.launch {
            preferences.sortAscending.collect { ascending ->
                _uiState.update { it.copy(sortAscending = ascending) }
            }
        }
        viewModelScope.launch {
            preferences.materialYou.collect { enabled ->
                _uiState.update { it.copy(materialYou = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferences.deviceName.collect { name ->
                _uiState.update { it.copy(deviceName = name) }
            }
        }
        viewModelScope.launch {
            preferences.offlineMode.collect { enabled ->
                _uiState.update { it.copy(offlineMode = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.downloadMobileData.collect { enabled ->
                _uiState.update { it.copy(downloadMobileData = enabled) }
            }
        }
        loadStats()
        checkForUpdate()
    }

    fun loadStats() {
        viewModelScope.launch {
            runCatching { api.getStats().bodyOrThrow("GET /api/stats").toDomain() }.fold(
                onSuccess = { stats -> _uiState.update { it.copy(stats = stats, statsError = false) } },
                onFailure = { _uiState.update { it.copy(statsError = true) } },
            )
        }
    }

    fun checkForUpdate(userInitiated: Boolean = false) {
        viewModelScope.launch {
            // Offline mode makes no network calls — this one bypasses OkHttp,
            // so the interceptor cannot stop it; the check has to be skipped.
            if (preferences.offlineModeState.value) return@launch
            _uiState.update { it.copy(updateCheckInProgress = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = URL("https://api.github.com/repos/Nergy101/Muorg/releases?per_page=20").readText()
                    githubJson.decodeFromString<List<GithubRelease>>(text)
                }
            }.onSuccess { releases ->
                // The repo mixes full releases (v3.2.4) with Android-only ones
                // (android-v3.3.12), and neither /releases/latest nor the list
                // order puts the newest Android release first — so compare
                // every Android tag and keep the newest version.
                val newest = releases
                    .filter { it.tagName.startsWith("android-v") }
                    .mapNotNull { release ->
                        Regex("""\d+\.\d+\.\d+""").find(release.tagName)?.value
                            ?.let { version -> version to release }
                    }
                    .reduceOrNull { acc, next -> if (isNewer(next.first, acc.first)) next else acc }
                if (newest != null) {
                    val (latest, release) = newest
                    if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                        _uiState.update {
                            it.copy(latestVersion = latest, latestReleaseUrl = release.htmlUrl)
                        }
                    }
                }
            }.onFailure {
                if (userInitiated) uiMessenger.show("Couldn't check for updates")
            }
            _uiState.update { it.copy(updateCheckInProgress = false) }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshStatus = RefreshStatus.LOADING, refreshError = null) }
            runCatching { api.getStats().bodyOrThrow("GET /api/stats").toDomain() }.fold(
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

    fun setMaterialYou(enabled: Boolean) {
        _uiState.update { it.copy(materialYou = enabled) }
        viewModelScope.launch { preferences.setMaterialYou(enabled) }
    }

    fun setThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setPlayerBarTapOpensPlayer(value: Boolean) {
        _uiState.update { it.copy(playerBarTapOpensPlayer = value) }
        viewModelScope.launch { preferences.setPlayerBarTapOpensPlayer(value) }
    }

    fun setAlbumViewStyle(style: String) {
        _uiState.update { it.copy(albumViewStyle = style) }
        viewModelScope.launch { preferences.setAlbumViewStyle(style) }
    }

    fun setSortAscending(ascending: Boolean) {
        _uiState.update { it.copy(sortAscending = ascending) }
        viewModelScope.launch { preferences.setSortAscending(ascending) }
    }

    fun setDefaultSort(mode: SortMode) {
        _uiState.update { it.copy(defaultSort = mode, showSortDropdown = false) }
        viewModelScope.launch { preferences.setDefaultSort(mode.name) }
    }

    fun showSortDropdown() = _uiState.update { it.copy(showSortDropdown = true) }
    fun dismissSortDropdown() = _uiState.update { it.copy(showSortDropdown = false) }

    fun showLogoutDialog() = _uiState.update { it.copy(showLogoutDialog = true) }
    fun dismissLogoutDialog() = _uiState.update { it.copy(showLogoutDialog = false) }

    /**
     * Log out: stop playback and drop the cached catalog, so the next
     * account never sees (or hears) the previous one's session.
     */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            playerController.stopPlayback()
            libraryRepository.clearCache()
            preferences.clearCredentials()
            onDone()
        }
    }

    fun setDeviceName(name: String) {
        _uiState.update { it.copy(deviceName = name) }
        // The field is required: a blank entry falls back to the model name.
        viewModelScope.launch { preferences.setDeviceName(name) }
    }

    fun setOfflineMode(enabled: Boolean) {
        _uiState.update { it.copy(offlineMode = enabled) }
        viewModelScope.launch { preferences.setOfflineMode(enabled) }
    }

    fun setDownloadMobileData(enabled: Boolean) {
        _uiState.update { it.copy(downloadMobileData = enabled) }
        viewModelScope.launch { preferences.setDownloadMobileData(enabled) }
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

    /**
     * Whether the system lets this app ignore battery optimizations. Some
     * OEMs stop background playback otherwise, so Settings offers the
     * exemption; the state is re-read on resume because it changes outside
     * the app.
     */
    fun refreshBatteryOptimizationExemption() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        _uiState.update {
            it.copy(isBatteryOptimizationExempt =
                powerManager.isIgnoringBatteryOptimizations(context.packageName))
        }
    }

    /** Opens the system dialog that exempts the app from battery optimization. */
    fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Not every OEM implements the direct dialog; the full list always exists.
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
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

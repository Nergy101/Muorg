package nl.muorg.android.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.bodyOrThrow
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.api.schema.Profile
import nl.muorg.android.data.api.schema.ProfileBody
import nl.muorg.android.data.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NER-326: listening profiles.
 *
 * Profiles live on the server, so they are server-specific: the chosen
 * "listening as" profile is stored per server URL. The feature is invisible
 * until the server has a second profile — then the active profile is shown
 * in the nav island, on Home and in Settings.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val api: MuorgApi,
    private val preferences: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow<Long?>(null)
    val activeProfileId: StateFlow<Long?> = _activeId.asStateFlow()

    /** The profile plays are recorded under right now. */
    val activeProfile: StateFlow<Profile?> = combine(_profiles, _activeId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** True once the current server has a second profile — the feature switch. */
    val enabled: StateFlow<Boolean> = _profiles
        .map { it.size > 1 }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val loaded: StateFlow<Boolean> = _profiles
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            bindContext()
            refresh()
        }
    }

    /** Re-read the stored profile choice for the current server (after connecting). */
    suspend fun bindContext() {
        _activeId.value = preferences.activeProfileForCurrentServer.first() ?: 1L
    }

    /** True when a profile choice was already stored for the current server. */
    suspend fun hasStoredChoice(): Boolean = preferences.activeProfileForCurrentServer.first() != null

    suspend fun refresh() {
        // Profiles are server-side data; without a server URL (local library
        // mode) there is nothing to list and the feature stays off.
        if (preferences.serverUrlState.value.isBlank()) {
            _profiles.value = emptyList()
            return
        }
        runCatching { api.listProfiles().bodyOrThrow("list profiles") }
            .onSuccess { list ->
                _profiles.value = list
                if (_activeId.value == null || list.none { it.id == _activeId.value }) {
                    _activeId.value = list.firstOrNull()?.id ?: 1L
                }
            }
            .onFailure {
                // Offline or an old server without the endpoints: stay hidden.
                _profiles.value = emptyList()
            }
    }

    suspend fun setActive(id: Long) {
        if (_profiles.value.none { it.id == id }) return
        _activeId.value = id
        preferences.saveActiveProfile(preferences.serverUrlState.value, id)
    }

    suspend fun create(name: String): Profile {
        val profile = api.createProfile(ProfileBody(name.trim())).bodyOrThrow("create profile")
        refresh()
        setActive(profile.id)
        return profile
    }

    suspend fun rename(id: Long, name: String) {
        api.renameProfile(id, ProfileBody(name.trim())).bodyOrThrow("rename profile")
        refresh()
    }

    suspend fun remove(id: Long) {
        api.deleteProfile(id).bodyOrThrow("delete profile")
        if (_activeId.value == id) setActive(1L)
        refresh()
    }
}

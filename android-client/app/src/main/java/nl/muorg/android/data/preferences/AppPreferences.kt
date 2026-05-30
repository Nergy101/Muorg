package nl.muorg.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "muorg_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_CONTINUOUS_PLAYBACK = booleanPreferencesKey("continuous_playback")
        private val KEY_DEFAULT_SORT = stringPreferencesKey("default_sort")
        private val KEY_MUSIC_MODE = stringPreferencesKey("music_mode")
        private val KEY_LOCAL_FOLDER_URIS = stringSetPreferencesKey("local_folder_uris")
        private val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        private val KEY_USE_TRUE_BLACK = booleanPreferencesKey("use_true_black")
        private val KEY_PLAYERBAR_TAP_OPENS_PLAYER = booleanPreferencesKey("playerbar_tap_opens_player")
        private val KEY_NOTIFICATION_ACTIONS = stringSetPreferencesKey("notification_actions")
        private val KEY_ALBUM_VIEW_STYLE = stringPreferencesKey("album_view_style")
        private val KEY_PLAYLIST_VIEW_STYLE = stringPreferencesKey("playlist_view_style")
    }

    val serverUrl: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_SERVER_URL] ?: "" }

    val apiKey: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_API_KEY] ?: "" }

    // Eagerly-cached StateFlows for synchronous reads in OkHttp interceptors.
    val serverUrlState: StateFlow<String> = serverUrl.stateIn(scope, SharingStarted.Eagerly, "")
    val apiKeyState: StateFlow<String> = apiKey.stateIn(scope, SharingStarted.Eagerly, "")

    val playerBarTapOpensPlayer: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_PLAYERBAR_TAP_OPENS_PLAYER] ?: true }

    val notificationActions: Flow<Set<String>> = context.dataStore.data
        .map { it[KEY_NOTIFICATION_ACTIONS] ?: setOf("skip_previous", "skip_next") }

    val albumViewStyle: Flow<String> = context.dataStore.data
        .map { it[KEY_ALBUM_VIEW_STYLE] ?: "grid" }

    val playlistViewStyle: Flow<String> = context.dataStore.data
        .map { it[KEY_PLAYLIST_VIEW_STYLE] ?: "tracks" }

    val continuousPlayback: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_CONTINUOUS_PLAYBACK] ?: true }

    val defaultSort: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_DEFAULT_SORT] ?: "BY_ALBUM" }

    val musicMode: Flow<String> = context.dataStore.data
        .map { it[KEY_MUSIC_MODE] ?: "remote" }

    val localFolderUris: Flow<Set<String>> = context.dataStore.data
        .map { it[KEY_LOCAL_FOLDER_URIS] ?: emptySet() }

    val favorites: Flow<Set<String>> = context.dataStore.data
        .map { it[KEY_FAVORITES] ?: emptySet() }

    val useTrueBlack: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_USE_TRUE_BLACK] ?: false }

    suspend fun setUseTrueBlack(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_USE_TRUE_BLACK] = enabled }
    }

    suspend fun toggleFavorite(trackId: String) = context.dataStore.edit { prefs ->
        val current = prefs[KEY_FAVORITES] ?: emptySet()
        prefs[KEY_FAVORITES] = if (trackId in current) current - trackId else current + trackId
    }

    suspend fun setMusicMode(mode: String) = context.dataStore.edit { it[KEY_MUSIC_MODE] = mode }

    suspend fun addLocalFolderUri(uri: String) = context.dataStore.edit {
        it[KEY_LOCAL_FOLDER_URIS] = (it[KEY_LOCAL_FOLDER_URIS] ?: emptySet()) + uri
    }

    suspend fun removeLocalFolderUri(uri: String) = context.dataStore.edit {
        it[KEY_LOCAL_FOLDER_URIS] = (it[KEY_LOCAL_FOLDER_URIS] ?: emptySet()) - uri
    }

    suspend fun setContinuousPlayback(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_CONTINUOUS_PLAYBACK] = enabled }
    }

    suspend fun setDefaultSort(sort: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DEFAULT_SORT] = sort }
    }

    suspend fun saveCredentials(serverUrl: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = serverUrl.trimEnd('/')
            prefs[KEY_API_KEY] = apiKey.trim()
        }
    }

    suspend fun setPlayerBarTapOpensPlayer(value: Boolean) {
        context.dataStore.edit { it[KEY_PLAYERBAR_TAP_OPENS_PLAYER] = value }
    }

    suspend fun setNotificationActions(actions: Set<String>) {
        context.dataStore.edit { it[KEY_NOTIFICATION_ACTIONS] = actions }
    }

    suspend fun setAlbumViewStyle(style: String) {
        context.dataStore.edit { it[KEY_ALBUM_VIEW_STYLE] = style }
    }

    suspend fun setPlaylistViewStyle(style: String) {
        context.dataStore.edit { it[KEY_PLAYLIST_VIEW_STYLE] = style }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SERVER_URL)
            prefs.remove(KEY_API_KEY)
        }
    }
}

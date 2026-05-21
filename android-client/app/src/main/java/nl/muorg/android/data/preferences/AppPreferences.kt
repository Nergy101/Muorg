package nl.muorg.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "muorg_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_CONTINUOUS_PLAYBACK = booleanPreferencesKey("continuous_playback")
        private val KEY_DEFAULT_SORT = stringPreferencesKey("default_sort")
    }

    val serverUrl: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_SERVER_URL] ?: "" }

    val apiKey: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_API_KEY] ?: "" }

    val continuousPlayback: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_CONTINUOUS_PLAYBACK] ?: true }

    val defaultSort: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_DEFAULT_SORT] ?: "BY_ALBUM" }

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

    suspend fun clearCredentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SERVER_URL)
            prefs.remove(KEY_API_KEY)
        }
    }
}

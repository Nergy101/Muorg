package nl.muorg.android.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SearchResultEntry(
    val trackId: Int,
    val timestamp: Long,
)

/**
 * Track ids of selected search results in the order they were first picked —
 * the Search screen shows them as track rows when the query is empty. Playing
 * a track again does not move it. Ids are stored, not whole tracks: the
 * catalog is the source of truth for titles and artwork, and an id that no
 * longer exists simply drops out of the list.
 */
@Singleton
class SearchResultsHistoryManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_results_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "recent_search_results"
        private const val MAX_ENTRIES = 20
    }

    /** Returns the recent search-result entries in the order they were first picked. */
    suspend fun getRecentResults(): List<SearchResultEntry> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_HISTORY, null) ?: return@withContext emptyList()
        try {
            Json.decodeFromString<List<SearchResultEntry>>(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Records a track picked from search results. Re-picking keeps its position;
     *  new entries append at the end, capped at MAX_ENTRIES (oldest drops off). */
    suspend fun addResult(trackId: Int): Unit = withContext(Dispatchers.IO) {
        val current = getRecentResults().toMutableList()
        if (current.any { it.trackId == trackId }) return@withContext
        current.add(SearchResultEntry(trackId = trackId, timestamp = System.currentTimeMillis()))
        prefs.edit().putString(KEY_HISTORY, Json.encodeToString(current.takeLast(MAX_ENTRIES))).apply()
    }

    /** Clears all recorded search results. */
    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}

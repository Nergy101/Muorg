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
data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long,
)

@Singleton
class SearchHistoryManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "recent_searches"
        private const val MAX_ENTRIES = 20
    }

    /** Returns the list of recent searches, newest first. */
    suspend fun getRecentSearches(): List<SearchHistoryEntry> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_HISTORY, null) ?: return@withContext emptyList()
        try {
            Json.decodeFromString<List<SearchHistoryEntry>>(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Adds a query to the history (deduplicates, caps at MAX_ENTRIES). */
    suspend fun addSearch(query: String): Unit = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        val current = getRecentSearches().toMutableList()
        // Remove duplicate if exists
        current.removeAll { it.query.equals(query, ignoreCase = true) }
        // Add to front
        current.add(0, SearchHistoryEntry(query = query, timestamp = System.currentTimeMillis()))
        // Trim
        val trimmed = current.take(MAX_ENTRIES)
        prefs.edit().putString(KEY_HISTORY, Json.encodeToString(trimmed)).apply()
    }

    /** Clears all search history. */
    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}

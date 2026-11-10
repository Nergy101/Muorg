package nl.muorg.android.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.content.edit
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.Collections
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.buildAuthOkHttpClient
import okhttp3.Request

/**
 * The widget's out-of-process memory. The system starts a widget receiver (or
 * the RemoteViews factory) in a fresh app process whenever it needs one —
 * after a reboot, or when the widget is tapped while the app was swiped away —
 * and at that point the in-app [nl.muorg.android.player.PlayerController] state
 * is gone. Everything a widget needs to render is therefore persisted here:
 * the last now-playing snapshot, the recently-played list (for tap-to-play),
 * and the fetched cover art (so a cold start shows the last cover offline).
 */
object WidgetStateStore {
    private const val PREFS_NAME = "widget_state"
    private const val KEY_TITLE = "np_title"
    private const val KEY_ARTIST = "np_artist"
    private const val KEY_ALBUM = "np_album"
    private const val KEY_IS_PLAYING = "np_is_playing"
    private const val KEY_TRACK_ID = "np_track_id"
    private const val KEY_HAS_COVER = "np_has_cover"
    private const val KEY_LOCAL_COVER_PATH = "np_local_cover_path"
    private const val KEY_IS_FAVORITE = "np_is_favorite"
    private const val KEY_RECENT_JSON = "recent_tracks_json"

    private const val COVER_DIR_NAME = "widget_covers"
    private const val MAX_COVER_PX = 512
    private const val LIST_COVER_PX = 192
    private const val MAX_COVER_FILES = 200

    /** The persisted slice of playback state the Now Playing widget renders. */
    data class NowPlayingState(
        val title: String?,
        val artist: String?,
        val album: String?,
        val isPlaying: Boolean,
        val trackId: Int,
        val hasCover: Boolean,
        val localCoverPath: String?,
        val isFavorite: Boolean,
    )

    private val Context.prefs get() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Decoded covers, keyed by track id; the disk cache below is the cold-start copy.
    private val coverMemory = LruCache<Int, Bitmap>(8)
    // Track ids whose cover is known to be absent (no art, or the fetch failed),
    // so a missing cover is not re-requested on every render.
    private val noCoverIds = Collections.synchronizedSet(HashSet<Int>())

    fun loadNowPlaying(context: Context): NowPlayingState = context.prefs.run {
        NowPlayingState(
            title = getString(KEY_TITLE, null),
            artist = getString(KEY_ARTIST, null),
            album = getString(KEY_ALBUM, null),
            isPlaying = getBoolean(KEY_IS_PLAYING, false),
            trackId = getInt(KEY_TRACK_ID, 0),
            hasCover = getBoolean(KEY_HAS_COVER, false),
            localCoverPath = getString(KEY_LOCAL_COVER_PATH, null),
            isFavorite = getBoolean(KEY_IS_FAVORITE, false),
        )
    }

    fun saveNowPlaying(context: Context, state: NowPlayingState) {
        context.prefs.edit {
            putString(KEY_TITLE, state.title)
            putString(KEY_ARTIST, state.artist)
            putString(KEY_ALBUM, state.album)
            putBoolean(KEY_IS_PLAYING, state.isPlaying)
            putInt(KEY_TRACK_ID, state.trackId)
            putBoolean(KEY_HAS_COVER, state.hasCover)
            putString(KEY_LOCAL_COVER_PATH, state.localCoverPath)
            putBoolean(KEY_IS_FAVORITE, state.isFavorite)
        }
    }

    /** Stash the recently-played list so a widget tap can resolve a track id back to a [CatalogTrack]. */
    fun saveRecentTracks(context: Context, tracks: List<CatalogTrack>) {
        context.prefs.edit { putString(KEY_RECENT_JSON, RecentTracksStash.encode(tracks)) }
    }

    fun loadRecentTracks(context: Context): List<CatalogTrack> =
        context.prefs.getString(KEY_RECENT_JSON, null)
            ?.let { raw -> runCatching { RecentTracksStash.decode(raw) }.getOrNull() }
            ?: emptyList()

    /** The cached cover for [trackId], from memory or disk — never touches the network. */
    fun peekCover(context: Context, trackId: Int): Bitmap? {
        if (trackId == 0) return null
        coverMemory.get(trackId)?.let { return it }
        val file = coverFile(context, trackId)
        if (!file.isFile) return null
        return runCatching { decode(file.readBytes(), MAX_COVER_PX) }.getOrNull()
            ?.also { coverMemory.put(trackId, it) }
    }

    fun hasCachedCover(context: Context, trackId: Int): Boolean =
        trackId != 0 && coverFile(context, trackId).isFile

    fun knowsNoCover(trackId: Int): Boolean = noCoverIds.contains(trackId)

    /** Fetch (and cache) the cover for the persisted now-playing track. */
    suspend fun fetchNowPlayingCover(context: Context, state: NowPlayingState): Bitmap? =
        fetchCover(context, state.trackId, state.hasCover, state.localCoverPath, MAX_COVER_PX)

    /** Fetch (and cache) a list-row cover if it is not cached yet. */
    suspend fun ensureCoverCached(context: Context, track: CatalogTrack): Bitmap? =
        fetchCover(context, track.id, track.hasCover, track.localCoverPath, LIST_COVER_PX)

    private suspend fun fetchCover(
        context: Context,
        trackId: Int,
        hasCover: Boolean,
        localCoverPath: String?,
        maxPx: Int,
    ): Bitmap? {
        if (trackId == 0) return null
        peekCover(context, trackId)?.let { return it }
        if (noCoverIds.contains(trackId)) return null
        val bytes = when {
            localCoverPath != null -> File(localCoverPath).takeIf { it.isFile }?.readBytes()
            hasCover && trackId > 0 -> fetchRemoteCover(context, trackId, maxPx)
            else -> null
        }
        if (bytes == null || bytes.isEmpty()) {
            noCoverIds.add(trackId)
            return null
        }
        val bitmap = runCatching { decode(bytes, maxPx) }.getOrNull()
        if (bitmap == null) {
            noCoverIds.add(trackId)
            return null
        }
        runCatching {
            coverFile(context, trackId).writeBytes(bytes)
            trimCoverDir(context)
        }
        coverMemory.put(trackId, bitmap)
        return bitmap
    }

    /**
     * Cover art lives behind the API key, which RemoteViews cannot attach to a
     * URI, so the bitmap is fetched here with the same auth client the rest of
     * the app uses (and skipped entirely while offline mode is on).
     */
    private suspend fun fetchRemoteCover(context: Context, trackId: Int, sizePx: Int): ByteArray? {
        val appContext = context.applicationContext
        val prefs = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java).preferences()
        val base = prefs.serverUrlState.value.trimEnd('/')
        if (base.isBlank()) return null
        val client = buildAuthOkHttpClient(prefs)
        val request = Request.Builder()
            .url("$base/api/tracks/$trackId/cover?size=$sizePx")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun coverDir(context: Context): File =
        File(context.cacheDir, COVER_DIR_NAME).apply { if (!isDirectory) mkdirs() }

    private fun coverFile(context: Context, trackId: Int): File =
        File(coverDir(context), "$trackId.jpg")

    private fun trimCoverDir(context: Context) {
        val files = File(context.cacheDir, COVER_DIR_NAME).listFiles()?.sortedBy { it.lastModified() }
            ?: return
        if (files.size > MAX_COVER_FILES) {
            files.take(files.size - MAX_COVER_FILES).forEach { it.delete() }
        }
    }

    private fun decode(bytes: ByteArray, maxPx: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        val sample = if (largest <= 0) 1 else (largest / maxPx).coerceAtLeast(1)
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }
}

/**
 * JSON round-trip for the recently-played stash. Pure Kotlin (no Android
 * classes) so it is unit-testable on the JVM.
 */
object RecentTracksStash {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(tracks: List<CatalogTrack>): String = json.encodeToString(tracks)

    fun decode(raw: String): List<CatalogTrack> = json.decodeFromString(raw)
}

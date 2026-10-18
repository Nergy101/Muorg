package nl.muorg.android.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.bodyOrThrow
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.db.OfflineTrack
import nl.muorg.android.data.db.OfflineTrackDao
import nl.muorg.android.data.preferences.AppPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The offline folder never grows past this in online mode, whatever the
 * playlists want. A local library already lives on the device, so its
 * downloads are not capped.
 */
private const val MAX_OFFLINE_BYTES = 2L * 1024 * 1024 * 1024

data class DownloadProgress(
    val playlistId: Int? = null,
    val totalTracks: Int = 0,
    val completedTracks: Int = 0,
    val failedTracks: Int = 0,
    val currentTrackName: String = "",
    val isActive: Boolean = false,
    val errorMessage: String? = null,
)

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineTrackDao: OfflineTrackDao,
    private val api: MuorgApi,
    private val preferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    // filesDir, not cacheDir: the system may wipe the cache under storage
    // pressure, and a wiped download is a track the queue thinks it has.
    private val offlineDir: File
        get() = File(context.filesDir, "offline").also { it.mkdirs() }

    fun downloadPlaylist(playlistId: Int, tracks: List<CatalogTrack>) {
        scope.launch {
            _downloadProgress.update {
                it.copy(
                    playlistId = playlistId,
                    totalTracks = tracks.size,
                    completedTracks = 0,
                    failedTracks = 0,
                    currentTrackName = "",
                    errorMessage = null,
                    isActive = true,
                )
            }
            // Wi-Fi only unless the user opted into mobile data.
            if (!isOnWifi() && !preferences.downloadMobileData.first()) {
                _downloadProgress.update {
                    it.copy(isActive = false, errorMessage = "Downloads are limited to Wi-Fi")
                }
                return@launch
            }
            var usedBytes = offlineDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            // Online mode caches for listening without internet, so the cache
            // is capped; local mode reads the device's own storage and is not.
            val capBytes =
                if (preferences.musicMode.first() == "local") Long.MAX_VALUE else MAX_OFFLINE_BYTES
            for (track in tracks) {
                _downloadProgress.update { it.copy(currentTrackName = track.displayTitle) }
                val existing = offlineTrackDao.getTrack(track.id)
                if (existing?.status == "ready") {
                    _downloadProgress.update { it.copy(completedTracks = it.completedTracks + 1) }
                    continue
                }
                // Stop before the next file would push the offline folder
                // past the cap; the rest of the playlist stays undownloaded.
                if (usedBytes >= capBytes) {
                    markFailed(track, playlistId)
                    _downloadProgress.update {
                        it.copy(failedTracks = it.failedTracks + 1, errorMessage = "Storage limit reached")
                    }
                    break
                }
                val written = try {
                    downloadTrack(track, playlistId)
                } catch (e: Exception) {
                    null
                }
                if (written != null) {
                    usedBytes += written
                    _downloadProgress.update { it.copy(completedTracks = it.completedTracks + 1) }
                } else {
                    markFailed(track, playlistId)
                    _downloadProgress.update { it.copy(failedTracks = it.failedTracks + 1) }
                }
            }
            _downloadProgress.update { it.copy(isActive = false) }
        }
    }

    private suspend fun markFailed(track: CatalogTrack, playlistId: Int) {
        offlineTrackDao.upsert(
            OfflineTrack(
                trackId = track.id,
                filePath = "",
                playlistId = playlistId,
                status = "failed",
            )
        )
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Bytes written, or null when the track could not be downloaded. */
    private suspend fun downloadTrack(track: CatalogTrack, playlistId: Int): Long? {
        val baseUrl = preferences.serverUrlState.value.trimEnd('/')
        val token = runCatching {
            api.issueToken(track.id.toLong())
                .bodyOrThrow("GET /api/tracks/${track.id}/stream-token").token
        }.getOrElse { return null }
        val url = "$baseUrl/stream/${track.id}?token=$token"

        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val ext = track.format.takeIf { it.isNotBlank() } ?: "mp3"
        val file = File(offlineDir, "${track.id}.$ext")

        var totalBytes = 0L
        val written = response.use { resp ->
            val body = resp.body ?: return null
            totalBytes = body.contentLength()
            try {
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    val input = body.byteStream()
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val pct = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else 0
                        offlineTrackDao.upsert(
                            OfflineTrack(
                                trackId = track.id,
                                filePath = file.absolutePath,
                                playlistId = playlistId,
                                status = "downloading",
                                progress = pct,
                                totalBytes = totalBytes,
                            )
                        )
                    }
                    totalRead
                }
            } catch (e: Exception) {
                // A partial file is worse than none: it would play as a
                // truncated track and still count as downloaded.
                file.delete()
                return null
            }
        }

        offlineTrackDao.upsert(
            OfflineTrack(
                trackId = track.id,
                filePath = file.absolutePath,
                playlistId = playlistId,
                status = "ready",
                progress = 100,
                totalBytes = totalBytes,
            )
        )
        return written
    }

    fun removePlaylist(playlistId: Int) {
        scope.launch {
            val tracks = offlineTrackDao.getTracksForPlaylist(playlistId)
            for (t in tracks) {
                if (t.filePath.isNotBlank()) File(t.filePath).delete()
            }
            offlineTrackDao.deleteForPlaylist(playlistId)
        }
    }

    fun removeTrack(trackId: Int) {
        scope.launch {
            val track = offlineTrackDao.getTrack(trackId)
            if (track != null) {
                if (track.filePath.isNotBlank()) File(track.filePath).delete()
                offlineTrackDao.delete(trackId)
            }
        }
    }

    suspend fun getLocalFilePath(trackId: Int): String? {
        val track = offlineTrackDao.getTrack(trackId)
        // The row can outlive the file (a wiped cache, a manual delete), and
        // a path that no longer exists would break the offline queue.
        return if (track?.status == "ready" && track.filePath.isNotBlank() && File(track.filePath).exists()) {
            track.filePath
        } else null
    }
}

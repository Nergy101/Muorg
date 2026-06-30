package nl.muorg.android.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.MuorgApiService
import nl.muorg.android.data.db.OfflineTrack
import nl.muorg.android.data.db.OfflineTrackDao
import nl.muorg.android.data.preferences.AppPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val playlistId: Int? = null,
    val totalTracks: Int = 0,
    val completedTracks: Int = 0,
    val currentTrackName: String = "",
    val isActive: Boolean = false,
)

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineTrackDao: OfflineTrackDao,
    private val api: MuorgApiService,
    private val preferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val offlineDir: File
        get() = File(context.cacheDir, "offline").also { it.mkdirs() }

    fun downloadPlaylist(playlistId: Int, tracks: List<CatalogTrack>) {
        scope.launch {
            _downloadProgress.update {
                it.copy(
                    playlistId = playlistId,
                    totalTracks = tracks.size,
                    completedTracks = 0,
                    isActive = true,
                )
            }
            for (track in tracks) {
                _downloadProgress.update { it.copy(currentTrackName = track.displayTitle) }
                val existing = offlineTrackDao.getTrack(track.id)
                if (existing?.status == "ready") {
                    _downloadProgress.update { it.copy(completedTracks = it.completedTracks + 1) }
                    continue
                }
                try {
                    val result = downloadTrack(track, playlistId)
                    if (result) {
                        _downloadProgress.update { it.copy(completedTracks = it.completedTracks + 1) }
                    }
                } catch (e: Exception) {
                    offlineTrackDao.upsert(
                        OfflineTrack(
                            trackId = track.id,
                            filePath = "",
                            playlistId = playlistId,
                            status = "failed",
                        )
                    )
                    _downloadProgress.update { it.copy(completedTracks = it.completedTracks + 1) }
                }
            }
            _downloadProgress.update { it.copy(isActive = false) }
        }
    }

    private suspend fun downloadTrack(track: CatalogTrack, playlistId: Int): Boolean {
        val baseUrl = preferences.serverUrlState.value.trimEnd('/')
        val token = runCatching { api.getStreamToken(track.id).token }.getOrElse { return false }
        val url = "$baseUrl/stream/${track.id}?token=$token"

        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return false

        val body = response.body ?: return false
        val totalBytes = body.contentLength()
        val ext = track.format.takeIf { it.isNotBlank() } ?: "mp3"
        val file = File(offlineDir, "${track.id}.$ext")

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
        return true
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
        return if (track?.status == "ready" && track.filePath.isNotBlank()) {
            track.filePath
        } else null
    }
}

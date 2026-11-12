package nl.muorg.android.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.api.bodyOrThrow
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.db.OfflinePlaylist
import nl.muorg.android.data.db.OfflinePlaylistDao
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

/** How long a cold-started sync waits for the saved credentials to load. */
private const val CREDENTIALS_WAIT_MS = 5_000L

data class DownloadProgress(
    val playlistId: Int? = null,
    /** Set instead of [playlistId] while an album download runs. */
    val albumKey: String? = null,
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
    private val offlinePlaylistDao: OfflinePlaylistDao,
    private val api: MuorgApi,
    private val preferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
    private val syncScheduler: OfflineSyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    // filesDir, not cacheDir: the system may wipe the cache under storage
    // pressure, and a wiped download is a track the queue thinks it has.
    private val offlineDir: File
        get() = File(context.filesDir, "offline").also { it.mkdirs() }

    // One download at a time: a download the user started and a background
    // sync write the same progress state and the same offline folder.
    private val downloadMutex = Mutex()

    /**
     * Downloads an album's tracks outside any playlist (rows with a null
     * playlistId). The album counts as downloaded when every one of its
     * tracks is on disk, however it got there. [albumKey] only labels the
     * progress so the sheet can tell its own download from another.
     */
    fun downloadAlbum(albumKey: String, tracks: List<CatalogTrack>) {
        scope.launch {
            downloadMutex.withLock {
                beginProgress(null, tracks.size, albumKey)
                if (!networkAllowsDownload()) {
                    _downloadProgress.update {
                        it.copy(isActive = false, errorMessage = "Downloads are limited to Wi-Fi")
                    }
                    return@withLock
                }
                downloadTracks(null, tracks)
                _downloadProgress.update { it.copy(isActive = false) }
            }
        }
    }

    /**
     * Removes an album's own downloads. Tracks that a downloaded playlist
     * owns stay put — deleting them would silently hollow out that playlist.
     */
    fun removeAlbum(trackIds: List<Int>) {
        scope.launch {
            for (id in trackIds) {
                val row = offlineTrackDao.getTrack(id) ?: continue
                if (row.playlistId != null) continue
                if (row.filePath.isNotBlank()) File(row.filePath).delete()
                offlineTrackDao.delete(id)
            }
        }
    }

    /** Ids of every track that is fully on disk, for the album download state. */
    fun observeDownloadedTrackIds(): Flow<Set<Int>> =
        offlineTrackDao.observeReadyTrackIds().map { it.toSet() }

    /**
     * Downloads a single track outside any playlist (null playlistId), e.g.
     * from the track context menu's "Download for offline".
     */
    fun downloadSingleTrack(track: CatalogTrack) {
        scope.launch {
            downloadMutex.withLock {
                beginProgress(null, 1)
                if (!networkAllowsDownload()) {
                    _downloadProgress.update {
                        it.copy(isActive = false, errorMessage = "Downloads are limited to Wi-Fi")
                    }
                    return@withLock
                }
                downloadTracks(null, listOf(track))
                _downloadProgress.update { it.copy(isActive = false) }
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist, tracks: List<CatalogTrack>) {
        scope.launch {
            downloadMutex.withLock {
                beginProgress(playlist.id, tracks.size)
                // The row is what marks the playlist downloaded — written up front
                // so a partial download (storage cap, a failed track) still shows
                // as downloaded and can be removed again from the tile.
                offlinePlaylistDao.upsert(
                    OfflinePlaylist(
                        playlistId = playlist.id,
                        name = playlist.name,
                        icon = playlist.icon,
                        trackCount = playlist.trackCount,
                    )
                )
                syncScheduler.reconcile()
                // Wi-Fi only unless the user opted into mobile data.
                if (!networkAllowsDownload()) {
                    _downloadProgress.update {
                        it.copy(isActive = false, errorMessage = "Downloads are limited to Wi-Fi")
                    }
                    return@withLock
                }
                downloadTracks(playlist.id, tracks)
                _downloadProgress.update { it.copy(isActive = false) }
            }
        }
    }

    /**
     * Downloads what was added to downloaded playlists since they were last
     * looked at. A playlist is only re-read when the server's track count no
     * longer matches the one recorded at its last sync, so the common run is a
     * single playlist-list request.
     *
     * Silent by design — it runs from a background worker. A metered
     * connection, offline mode, or an unreachable server means nothing happens
     * until the next run, never an error in the UI.
     */
    suspend fun syncDownloadedPlaylists() = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (preferences.musicMode.first() == "local" || preferences.offlineMode.first()) return@withLock
            val rows = offlinePlaylistDao.getAll()
            if (rows.isEmpty() || !networkAllowsDownload()) return@withLock
            if (!credentialsLoaded()) return@withLock

            val serverPlaylists = playlistRepository.getPlaylists()
                .getOrElse { return@withLock }
                .associateBy { it.id }
            for (row in rows) {
                // A playlist gone from the server is left alone: deleting the
                // user's downloads is not this job.
                val playlist = serverPlaylists[row.playlistId] ?: continue
                if (playlist.trackCount != row.trackCount) syncPlaylist(row, playlist)
            }
        }
    }

    private suspend fun syncPlaylist(row: OfflinePlaylist, playlist: Playlist) {
        val ids = playlistRepository.getTracksFor(playlist).getOrElse { return }
        val catalog = libraryRepository.getAllTracks().getOrElse { return }.associateBy { it.id }
        val tracks = ids.mapNotNull { catalog[it] }
        val missing = tracks.filter { offlineTrackDao.getTrack(it.id)?.status != "ready" }

        // An id the catalog does not know yet (a track the server has not
        // scanned) cannot be downloaded, so it keeps the playlist out of date.
        var complete = tracks.size == ids.size
        if (missing.isNotEmpty()) {
            beginProgress(playlist.id, missing.size)
            complete = downloadTracks(playlist.id, missing) && complete
            _downloadProgress.update { it.copy(isActive = false) }
        }
        // Recorded only once everything is in; otherwise the next run sees the
        // same mismatch and tries the rest again. Re-checked because the
        // playlist may have been removed while its files were downloading.
        if (complete && offlinePlaylistDao.get(row.playlistId) != null) {
            offlinePlaylistDao.upsert(row.copy(trackCount = playlist.trackCount))
        }
    }

    /**
     * A cold-started worker can reach here before the preference flows have
     * caught up with DataStore, and the API client reads those flows' cached
     * values — which would be empty, aiming the request at localhost.
     */
    private suspend fun credentialsLoaded(): Boolean {
        val url = preferences.serverUrl.first()
        if (url.isBlank()) return false
        val key = preferences.apiKey.first()
        return withTimeoutOrNull(CREDENTIALS_WAIT_MS) {
            preferences.serverUrlState.first { it == url }
            preferences.apiKeyState.first { it == key }
            true
        } ?: false
    }

    private fun beginProgress(playlistId: Int?, total: Int, albumKey: String? = null) {
        _downloadProgress.update {
            it.copy(
                playlistId = playlistId,
                albumKey = albumKey,
                totalTracks = total,
                completedTracks = 0,
                failedTracks = 0,
                currentTrackName = "",
                errorMessage = null,
                isActive = true,
            )
        }
    }

    private suspend fun networkAllowsDownload(): Boolean =
        isOnWifi() || preferences.downloadMobileData.first()

    /** True when every one of [tracks] is on disk afterwards. */
    private suspend fun downloadTracks(playlistId: Int?, tracks: List<CatalogTrack>): Boolean {
        var usedBytes = offlineDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // Online mode caches for listening without internet, so the cache
        // is capped; local mode reads the device's own storage and is not.
        val capBytes =
            if (preferences.musicMode.first() == "local") Long.MAX_VALUE else MAX_OFFLINE_BYTES
        var allReady = true
        for (track in tracks) {
            _downloadProgress.update { it.copy(currentTrackName = track.displayTitle) }
            val existing = offlineTrackDao.getTrack(track.id)
            if (existing != null && existing.status == "ready") {
                // A playlist adopts an album's ownerless file, so removing the
                // album later cannot pull the track out from under it.
                if (playlistId != null && existing.playlistId == null) {
                    offlineTrackDao.upsert(existing.copy(playlistId = playlistId))
                }
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
                return false
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
                allReady = false
                markFailed(track, playlistId)
                _downloadProgress.update { it.copy(failedTracks = it.failedTracks + 1) }
            }
        }
        return allReady
    }

    private suspend fun markFailed(track: CatalogTrack, playlistId: Int?) {
        offlineTrackDao.upsert(
            OfflineTrack(
                trackId = track.id,
                filePath = "",
                playlistId = playlistId,
                status = "failed",
                trackJson = json.encodeToString(track),
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
    private suspend fun downloadTrack(track: CatalogTrack, playlistId: Int?): Long? {
        val baseUrl = preferences.serverUrlState.value.trimEnd('/')
        val info = runCatching {
            api.issueToken(track.id.toLong())
                .bodyOrThrow("GET /api/tracks/${track.id}/stream-token")
        }.getOrElse { return null }
        // True-FLAC servers hand over the original file, so the offline copy is
        // lossless too; otherwise this is the transcoded stream.
        val url = if (info.flac && track.format.equals("flac", ignoreCase = true)) {
            "$baseUrl/stream/flac/${info.token}/${track.id}"
        } else {
            "$baseUrl/stream/${info.token}/${track.id}"
        }

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
                                trackJson = json.encodeToString(track),
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
                trackJson = json.encodeToString(track),
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
            offlinePlaylistDao.delete(playlistId)
            syncScheduler.reconcile()
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

    /** Playlist ids with a download, for the tile's filled download button. */
    /** Ready-track count and total bytes, for the settings' offline section. */
    suspend fun cachedTrackCount(): Int = offlineTrackDao.countReady()
    suspend fun cachedBytes(): Long = offlineTrackDao.sumReadyBytes()

    /** Wipes every downloaded file and row (manual and automatic alike). */
    suspend fun clearAllOffline() = withContext(Dispatchers.IO) {
        offlineTrackDao.getDownloadedTracks().forEach { row ->
            runCatching { File(row.filePath).delete() }
        }
        offlineTrackDao.clearAll()
        offlinePlaylistDao.getAll().forEach { offlinePlaylistDao.delete(it.playlistId) }
        offlineDir.deleteRecursively()
    }

    /**
     * Automatic caching: when enabled in settings, a track that starts
     * playing is downloaded for offline use as long as the auto-cache cap
     * has not been reached. Manual playlist/album downloads are not counted
     * against the cap (rows with a playlistId).
     */
    fun maybeAutoCache(track: CatalogTrack) {
        scope.launch {
            if (!preferences.autoCacheEnabled.first()) return@launch
            if (offlineTrackDao.getTrack(track.id) != null) return@launch
            val used = offlineTrackDao.sumAutoCachedBytes()
            val cap = preferences.autoCacheMaxBytes.first()
            if (used >= cap) return@launch
            downloadSingleTrack(track)
        }
    }

    fun observeDownloadedPlaylistIds(): Flow<Set<Int>> =
        offlinePlaylistDao.observeIds().map { it.toSet() }

    /**
     * The downloaded playlists, for the playlist list while offline mode is
     * on. A playlist whose every file has since vanished is left out — there
     * is nothing in it to play.
     */
    suspend fun getDownloadedPlaylists(): List<Playlist> {
        val readyByPlaylist = offlineTrackDao.getDownloadedTracks()
            .filter { it.filePath.isNotBlank() && File(it.filePath).exists() }
            .groupBy { it.playlistId }
        return offlinePlaylistDao.getAll().mapNotNull { row ->
            val count = readyByPlaylist[row.playlistId]?.size ?: 0
            if (count == 0) null
            else Playlist(
                id = row.playlistId,
                name = row.name,
                icon = row.icon,
                trackCount = count,
            )
        }
    }

    suspend fun getDownloadedPlaylist(playlistId: Int): Playlist? =
        getDownloadedPlaylists().find { it.id == playlistId }

    /**
     * Every downloaded track across all playlists, for the library and album
     * screens while offline mode is on: the catalog is unreachable, so the
     * download store is the library. A track downloaded through several
     * playlists appears once.
     */
    suspend fun getAllDownloadedTracks(): List<CatalogTrack> =
        offlineTrackDao.getDownloadedTracks()
            .filter { it.filePath.isNotBlank() && File(it.filePath).exists() }
            .mapNotNull { it.toCatalogTrack(json) }
            .distinctBy { it.id }

    /**
     * A downloaded playlist's tracks, ready to render and play offline: each
     * carries its metadata from download time and its local file path.
     */
    suspend fun getDownloadedTracks(playlistId: Int): List<CatalogTrack> =
        offlineTrackDao.getDownloadedTracksForPlaylist(playlistId)
            .mapNotNull { it.toCatalogTrack(json) }
            .sortedWith(
                compareBy(
                    { it.displayAlbum.lowercase() },
                    { it.discNumber ?: 0 },
                    { it.trackNumber ?: 0 },
                    { it.displayTitle.lowercase() },
                )
            )

    /** trackId to download time for one downloaded playlist — the added-date sort. */
    suspend fun getDownloadedAddedAts(playlistId: Int): Map<Int, Long> =
        offlineTrackDao.getDownloadedTracksForPlaylist(playlistId)
            .associate { it.trackId to it.downloadedAt }
}

/**
 * A downloaded row back into a playable track. The stored metadata carries
 * everything the server would have said; a row from before metadata was
 * stored falls back to a filename-only track rather than disappearing.
 */
private fun OfflineTrack.toCatalogTrack(json: Json): CatalogTrack? {
    if (status != "ready" || filePath.isBlank() || !File(filePath).exists()) return null
    val stored = trackJson?.let {
        runCatching { json.decodeFromString<CatalogTrack>(it) }.getOrNull()
    }
    return if (stored != null) {
        stored.copy(localFilePath = filePath)
    } else {
        CatalogTrack(
            id = trackId,
            path = filePath,
            rootId = -1,
            format = filePath.substringAfterLast('.', "mp3"),
            mtimeSecs = downloadedAt / 1000,
            hasCover = false,
            playCount = 0,
            localFilePath = filePath,
        )
    }
}

package nl.muorg.android.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerState(
    val currentTrack: CatalogTrack? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isConnected: Boolean = false,
    val queue: List<CatalogTrack> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val isSeekable: Boolean = true,
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val libraryRepository: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // Track list cache for building the play queue
    private var trackCache: List<CatalogTrack> = emptyList()

    // For FLAC server streams: track seconds offset so position display is accurate
    // after a seek (which reloads the stream from a new start position).
    private var flacSeekOffsetMs: Long = 0L
    private var lastSyncedTrackId: Int? = null

    init {
        connect()
        startProgressPolling()
        observeContinuousPlayback()
        scope.launch {
            preferences.favorites.collect { favs ->
                _state.update { it.copy(favorites = favs) }
            }
        }
    }

    private fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(playerListener)
                _state.update { it.copy(isConnected = true) }
                scope.launch {
                    val enabled = preferences.continuousPlayback.first()
                    controller?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                }
            } catch (e: Exception) {
                // Service not yet started; reconnect on next play attempt
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = syncState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = syncState()
        override fun onRepeatModeChanged(repeatMode: Int) = syncState()
        // Fires when duration becomes available (e.g. FLAC headers parsed after buffering starts)
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = syncState()
    }

    private fun syncState() {
        val ctrl = controller ?: return
        val mediaItem = ctrl.currentMediaItem
        val trackId = mediaItem?.mediaId?.toIntOrNull()

        // Reset FLAC seek offset when the playing track changes
        if (trackId != lastSyncedTrackId) {
            flacSeekOffsetMs = 0L
            lastSyncedTrackId = trackId
        }

        val currentTrack = trackId?.let { id -> trackCache.find { it.id == id } }

        // Prefer metadata duration: FLAC streams with ?start=N report only the remaining
        // chunk duration, not the full track duration.
        val metaDurationMs = ((currentTrack?.durationSecs ?: 0.0) * 1000).toLong()
        val exoDurationMs = ctrl.duration.takeIf { it > 0 } ?: 0L
        val durationMs = if (metaDurationMs > 0) metaDurationMs else exoDurationMs
        val positionMs = (ctrl.currentPosition + flacSeekOffsetMs).coerceAtMost(durationMs.coerceAtLeast(1L))
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        val queue = (0 until ctrl.mediaItemCount).mapNotNull { i ->
            val item = ctrl.getMediaItemAt(i)
            trackCache.find { it.id.toString() == item.mediaId }
        }

        _state.update { state ->
            state.copy(
                currentTrack = currentTrack,
                isPlaying = ctrl.isPlaying,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                shuffleEnabled = ctrl.shuffleModeEnabled,
                repeatMode = ctrl.repeatMode,
                queue = queue,
                isSeekable = ctrl.isCurrentMediaItemSeekable,
            )
        }
    }

    private fun observeContinuousPlayback() {
        scope.launch {
            preferences.continuousPlayback.collect { enabled ->
                controller?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun startProgressPolling() {
        scope.launch {
            while (true) {
                delay(500)
                syncState()
            }
        }
    }

    fun updateTrackCache(tracks: List<CatalogTrack>) {
        trackCache = tracks
    }

    /**
     * Build a stream URL for a track.
     * We fetch a stream token first, then construct the URL.
     */
    suspend fun playTrack(track: CatalogTrack, queue: List<CatalogTrack>) {
        ensureConnected()
        val ctrl = controller ?: return

        scope.launch {
            val baseUrl = preferences.serverUrl.first().trimEnd('/')
            val mediaItems = queue.map { t ->
                val uri = if (t.localFilePath != null) {
                    resolveLocalUri(t.localFilePath)
                } else {
                    val token = libraryRepository.getStreamToken(t.id).getOrNull() ?: return@launch
                    "$baseUrl/stream/${t.id}?token=$token"
                }
                MediaItem.Builder()
                    .setMediaId(t.id.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(t.displayTitle)
                            .setArtist(t.displayArtist)
                            .setAlbumTitle(t.displayAlbum)
                            .build()
                    )
                    .build()
            }

            val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            ctrl.setMediaItems(mediaItems, startIndex, 0L)
            ctrl.prepare()
            ctrl.play()
            if (track.localFilePath == null) {
                libraryRepository.recordPlay(track.id)
            }
        }
    }

    fun playPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val ctrl = controller ?: return
        if (ctrl.currentPosition > 3000) {
            ctrl.seekTo(0L)
        } else {
            ctrl.seekToPreviousMediaItem()
        }
    }

    fun seekTo(fraction: Float) {
        val ctrl = controller ?: return
        val track = _state.value.currentTrack ?: return

        val fullDurationMs = ((track.durationSecs ?: 0.0) * 1000).toLong()
            .takeIf { it > 0 }
            ?: ctrl.duration.takeIf { it > 0 }
            ?: return

        val targetMs = (fullDurationMs * fraction).toLong()

        if (track.format.lowercase() == "flac" && track.localFilePath == null) {
            // FLAC server stream: the server transcodes to MP3 starting at ?start=N.
            // Re-request with a fresh token and the desired start offset.
            scope.launch {
                val baseUrl = preferences.serverUrl.first().trimEnd('/')
                val token = libraryRepository.getStreamToken(track.id).getOrNull() ?: return@launch
                val targetSecs = targetMs / 1000.0
                val uri = "$baseUrl/stream/${track.id}?token=$token&start=${"%.2f".format(targetSecs)}"
                val currentIndex = ctrl.currentMediaItemIndex
                val newItem = MediaItem.Builder()
                    .setMediaId(track.id.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.displayTitle)
                            .setArtist(track.displayArtist)
                            .setAlbumTitle(track.displayAlbum)
                            .build()
                    )
                    .build()
                flacSeekOffsetMs = targetMs
                ctrl.replaceMediaItem(currentIndex, newItem)
                ctrl.play()
            }
        } else {
            val durationMs = ctrl.duration.takeIf { it > 0 }
                ?: ((track.durationSecs ?: 0.0) * 1000).toLong().takeIf { it > 0 }
                ?: return
            ctrl.seekTo((durationMs * fraction).toLong())
        }
    }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun enableShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = true
    }

    fun cycleRepeatMode() {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun skipTo(track: CatalogTrack) {
        val ctrl = controller ?: return
        val index = (0 until ctrl.mediaItemCount)
            .firstOrNull { ctrl.getMediaItemAt(it).mediaId == track.id.toString() } ?: return
        ctrl.seekToDefaultPosition(index)
    }

    fun removeFromQueue(track: CatalogTrack) {
        val ctrl = controller ?: return
        val currentIndex = ctrl.currentMediaItemIndex
        val index = (0 until ctrl.mediaItemCount)
            .firstOrNull { ctrl.getMediaItemAt(it).mediaId == track.id.toString() } ?: return
        if (index == currentIndex) return
        ctrl.removeMediaItem(index)
        syncState()
    }

    fun clearQueue() {
        val ctrl = controller ?: return
        val currentIndex = ctrl.currentMediaItemIndex
        for (i in (currentIndex + 1 until ctrl.mediaItemCount).reversed()) {
            ctrl.removeMediaItem(i)
        }
        syncState()
    }

    fun addToQueue(track: CatalogTrack) {
        val ctrl = controller ?: return
        scope.launch {
            val baseUrl = preferences.serverUrl.first().trimEnd('/')
            val uri = if (track.localFilePath != null) resolveLocalUri(track.localFilePath)
                      else {
                          val token = libraryRepository.getStreamToken(track.id).getOrNull() ?: return@launch
                          "$baseUrl/stream/${track.id}?token=$token"
                      }
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayTitle)
                        .setArtist(track.displayArtist)
                        .setAlbumTitle(track.displayAlbum)
                        .build()
                )
                .build()
            ctrl.addMediaItem(mediaItem)
            syncState()
        }
    }

    fun toggleFavorite(track: CatalogTrack) {
        scope.launch { preferences.toggleFavorite(track.id.toString()) }
    }

    // Convert a SAF content:// URI to a file:// path when possible so ExoPlayer
    // uses FileDataSource (supports random-access seeking) instead of ContentDataSource.
    private fun resolveLocalUri(localFilePath: String): String {
        return try {
            val uri = android.net.Uri.parse(localFilePath)
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            // "primary:Music/Artist/track.flac" → "/storage/emulated/0/Music/Artist/track.flac"
            if (docId.startsWith("primary:")) {
                "file:///storage/emulated/0/${docId.removePrefix("primary:")}"
            } else {
                localFilePath
            }
        } catch (_: Exception) {
            localFilePath
        }
    }

    private fun ensureConnected() {
        if (controller == null || controllerFuture?.isDone == false) {
            connect()
        }
    }

    fun release() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}

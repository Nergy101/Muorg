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

    init {
        connect()
        startProgressPolling()
        observeContinuousPlayback()
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
    }

    private fun syncState() {
        val ctrl = controller ?: return
        val mediaItem = ctrl.currentMediaItem
        val trackId = mediaItem?.mediaId?.toIntOrNull()
        val currentTrack = trackId?.let { id -> trackCache.find { it.id == id } }

        val durationMs = ctrl.duration.takeIf { it > 0 } ?: 0L
        val positionMs = ctrl.currentPosition
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()) else 0f

        _state.update { state ->
            state.copy(
                currentTrack = currentTrack,
                isPlaying = ctrl.isPlaying,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                shuffleEnabled = ctrl.shuffleModeEnabled,
                repeatMode = ctrl.repeatMode,
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
                val tokenResult = libraryRepository.getStreamToken(t.id)
                val token = tokenResult.getOrNull() ?: return@launch
                val streamUrl = "$baseUrl/stream/${t.id}?token=$token"
                MediaItem.Builder()
                    .setMediaId(t.id.toString())
                    .setUri(streamUrl)
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
            libraryRepository.recordPlay(track.id)
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
        val durationMs = ctrl.duration.takeIf { it > 0 } ?: return
        ctrl.seekTo((durationMs * fraction).toLong())
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

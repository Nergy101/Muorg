package nl.muorg.android.player

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
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
import kotlinx.coroutines.Job
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
    val errorMessage: String? = null,
    /**
     * The play order, each entry tagged with whether the listener queued it or
     * it came with the album/playlist. See [PlaybackQueue].
     */
    val playbackQueue: PlaybackQueue = PlaybackQueue(),
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val libraryRepository: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollingJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // Track list cache for building the play queue
    private var trackCache: List<CatalogTrack> = emptyList()

    /**
     * The play order. Media3's timeline is a straight projection of this and
     * its own shuffle mode stays off, so the list the queue screen renders is
     * exactly the one that plays.
     */
    private var queue = PlaybackQueue()

    /**
     * Built media items by track id, so reordering the queue never re-fetches
     * a stream token — only tracks joining the queue for the first time cost a
     * request.
     */
    private val mediaItems = mutableMapOf<Int, MediaItem>()

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
        // A mid-stream failure (dropped connection, server error) used to be
        // invisible: the player stopped with no UI feedback. Surface it.
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = "Playback failed: ${error.errorCodeName}",
                )
            }
            // Auto-advance so the queue keeps moving; clear the message after a beat.
            controller?.seekToNextMediaItem()
            scope.launch {
                delay(4000)
                _state.update { if (it.errorMessage?.startsWith("Playback failed") == true) it.copy(errorMessage = null) else it }
            }
        }
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

        // Media3 walks the timeline itself; its index is where the queue is now.
        val index = ctrl.currentMediaItemIndex
        if (ctrl.mediaItemCount > 0 && index in queue.entries.indices && index != queue.position) {
            queue = queue.copy(position = index)
        }

        _state.update { state ->
            state.copy(
                currentTrack = currentTrack ?: queue.current,
                isPlaying = ctrl.isPlaying,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                shuffleEnabled = queue.shuffleEnabled,
                repeatMode = ctrl.repeatMode,
                queue = queue.tracks,
                isSeekable = ctrl.isCurrentMediaItemSeekable,
                playbackQueue = queue,
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
        pollingJob?.cancel()
        pollingJob = scope.launch {
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
    /**
     * Start a context — an album, a playlist, a mix — with [track] first.
     *
     * Replaces the queue, the listener's own additions included: those were
     * queued against the context being left behind.
     */
    suspend fun playTrack(track: CatalogTrack, queue: List<CatalogTrack>) {
        ensureConnected()
        val ctrl = controller ?: return

        scope.launch {
            _state.update { it.copy(errorMessage = null) }
            mediaItems.clear()
            this@PlayerController.queue = this@PlayerController.queue.playContext(queue, track)
            val items = buildItems(this@PlayerController.queue.tracks) ?: return@launch
            // Media3's own shuffle reorders a timeline the queue screen never
            // reads, so the list and the playback disagreed. Order is ours now.
            ctrl.shuffleModeEnabled = false
            ctrl.setMediaItems(items, this@PlayerController.queue.position.coerceAtLeast(0), 0L)
            ctrl.prepare()
            ctrl.play()
            syncState()
            if (track.localFilePath == null) {
                libraryRepository.recordPlay(track.id)
            }
        }
    }

    /**
     * Build (and cache) the media items for [tracks], or null if a stream token
     * could not be had — in which case the error is already on screen.
     */
    private suspend fun buildItems(tracks: List<CatalogTrack>): List<MediaItem>? {
        val baseUrl = preferences.serverUrl.first().trimEnd('/')
        return tracks.map { t ->
            mediaItems[t.id] ?: run {
                val uri = if (t.localFilePath != null) {
                    resolveLocalUri(t.localFilePath)
                } else {
                    val token = libraryRepository.getStreamToken(t.id).getOrElse { e ->
                        _state.update { s ->
                            s.copy(errorMessage = "Playback failed: ${e.message ?: "Could not get stream token"}")
                        }
                        scope.launch { delay(4000); _state.update { it.copy(errorMessage = null) } }
                        return null
                    }
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
                    .also { mediaItems[t.id] = it }
            }
        }
    }

    /**
     * Apply a reordered or edited queue to the timeline.
     *
     * Only the part after the current track is rewritten, so whatever is
     * playing keeps playing — a queue edit should never restart the song.
     */
    private fun applyQueue(next: PlaybackQueue) {
        val ctrl = controller ?: return
        queue = next
        scope.launch {
            val tail = next.entries.drop(next.position + 1).map { it.track }
            val items = buildItems(tail) ?: return@launch
            val from = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
            ctrl.replaceMediaItems(from, ctrl.mediaItemCount, items)
            syncState()
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

    fun toggleShuffle() = setShuffle(!queue.shuffleEnabled)

    fun enableShuffle() = setShuffle(true)

    fun disableShuffle() = setShuffle(false)

    /**
     * Turn shuffle on or off, reordering the tracks that have not played yet.
     *
     * This used to set Media3's `shuffleModeEnabled`, which permutes a timeline
     * the queue screen does not read — so the list kept showing the original
     * order while playback jumped around it. Reordering the queue itself means
     * the two cannot disagree.
     */
    fun setShuffle(enabled: Boolean) {
        if (enabled == queue.shuffleEnabled) return
        applyQueue(queue.withShuffle(enabled))
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
        val index = queue.entries.indexOfFirst { it.track.id == track.id }
        if (index < 0) return
        queue = queue.skipTo(track.id)
        ctrl.seekToDefaultPosition(index)
        ctrl.prepare()
        ctrl.play()
        syncState()
    }

    fun removeFromQueue(track: CatalogTrack) {
        val ctrl = controller ?: return
        val index = queue.entries.indexOfFirst { it.track.id == track.id }
        if (index < 0 || index == queue.position) return
        queue = queue.remove(track.id)
        ctrl.removeMediaItem(index)
        syncState()
    }

    /** Drop everything still queued, from both halves. */
    fun clearQueue() {
        val ctrl = controller ?: return
        queue = queue.clearUpNext()
        for (i in (ctrl.currentMediaItemIndex + 1 until ctrl.mediaItemCount).reversed()) {
            ctrl.removeMediaItem(i)
        }
        syncState()
    }

    /** Drop only what the listener queued, leaving the album or playlist. */
    fun clearUserQueue() = applyQueue(queue.clearUserQueue())

    /** Reorder by index into the whole queue, history included. */
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val ctrl = controller ?: return
        if (fromIndex !in queue.entries.indices || toIndex !in queue.entries.indices) return
        queue = queue.move(fromIndex, toIndex)
        ctrl.moveMediaItem(fromIndex, toIndex)
        syncState()
    }

    fun addToQueue(track: CatalogTrack) = addTracksToQueue(listOf(track))

    /**
     * Queue tracks after anything already queued, ahead of the rest of the
     * context.
     *
     * The old behaviour deleted every upcoming context track on the first add,
     * so queueing one song while an album played threw the rest of the album
     * away. Now the two coexist: queued tracks play first and the album picks
     * up after them.
     */
    fun addTracksToQueue(tracks: List<CatalogTrack>) {
        if (tracks.isEmpty()) return
        cacheTracks(tracks)
        applyQueue(queue.addToUserQueue(tracks))
    }

    /** Queue a track to play immediately after the current one. */
    fun playNext(track: CatalogTrack) {
        cacheTracks(listOf(track))
        applyQueue(queue.playNext(track))
    }

    /**
     * Extend the *context* rather than the user queue — what shuffle-all uses
     * to top itself up. These are not the listener's own picks, so they belong
     * behind anything queued, not in front of it.
     */
    fun appendToContext(tracks: List<CatalogTrack>) {
        if (tracks.isEmpty()) return
        cacheTracks(tracks)
        applyQueue(queue.appendToContext(tracks))
    }

    /** Keep the id → track lookup able to resolve anything in the queue. */
    private fun cacheTracks(tracks: List<CatalogTrack>) {
        val toAdd = tracks.filter { t -> trackCache.none { it.id == t.id } }
        if (toAdd.isNotEmpty()) trackCache = trackCache + toAdd
    }

    fun toggleFavorite(track: CatalogTrack) {
        val id = track.id.toString()
        val current = _state.value.favorites
        _state.update { it.copy(favorites = if (id in current) current - id else current + id) }
        scope.launch { preferences.toggleFavorite(id) }
    }

    // Use the SAF content:// URI directly — ExoPlayer handles these correctly with
    // persistent URI permissions. Converting to file:// breaks on scoped storage (Android 10+).
    private fun resolveLocalUri(localFilePath: String): String = localFilePath

    private fun ensureConnected() {
        if (controller == null || controllerFuture?.isDone == false) {
            connect()
        }
    }

    fun release() {
        pollingJob?.cancel()
        pollingJob = null
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}

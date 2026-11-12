package nl.muorg.android.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.schema.TokenResponse
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.OfflineDownloadManager
import nl.muorg.android.ui.widget.NowPlayingWidget
import nl.muorg.android.ui.widget.RecentTracksWidget
import javax.inject.Inject
import javax.inject.Singleton

/** A play counts once the listener has heard this much of the track. */
private const val PLAY_COUNT_THRESHOLD_MS = 30_000L

/** How long a first play waits for the playback service connection. */
private const val CONNECT_TIMEOUT_MS = 5_000L

/**
 * How long the player must stay in STATE_BUFFERING before the UI swaps the
 * seek bar for the loading bar. ExoPlayer passes through BUFFERING briefly
 * on every prepare — even for a local file — and on every seek that
 * re-buffers; flashing the loading bar for those blips is the flicker.
 */
private const val BUFFERING_UI_DELAY_MS = 400L

/**
 * How many times a queue edit re-reads the queue when the player moved on, or
 * another edit landed, while it was waiting on the preferences store.
 */
private const val QUEUE_EDIT_ATTEMPTS = 5

/** The widget-relevant slice of [PlayerState], so a broadcast fires only on an actual change. */
private data class WidgetSnapshot(
    val title: String?,
    val artist: String?,
    val album: String?,
    val isPlaying: Boolean,
    val trackId: Int,
    val hasCover: Boolean,
    val localCoverPath: String?,
    val isFavorite: Boolean,
)

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
    /** A FLAC seek is reloading the stream; the seek bar shows buffering. */
    val isSeekBuffering: Boolean = false,
    /** Buffering has persisted long enough that the time-bar is replaced by a loading bar. */
    val isBuffering: Boolean = false,
    /**
     * The track a play request is still working on — stream token fetch,
     * prepare, first buffer. Set the moment a row is tapped so the UI can
     * show a spinner immediately, before ExoPlayer even reports BUFFERING.
     */
    val pendingTrackId: Int? = null,
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
    private val offlineDownloadManager: OfflineDownloadManager,
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

    // Set while a FLAC seek's replacement stream is still loading; the seek
    // bar shows a buffering indicator until the player reports READY.
    private var flacSeekInFlight = false

    // When the player entered STATE_BUFFERING (elapsed realtime), for the
    // loading-bar hysteresis.
    private var bufferingSinceElapsedMs: Long? = null

    // The track whose play has already been recorded, so a track counts once
    // no matter how often it repeats or re-enters the queue.
    private var playCountedTrackId: Int? = null

    init {
        connect()
        startProgressPolling()
        observeContinuousPlayback()
        scope.launch {
            preferences.favorites.collect { favs ->
                _state.update { it.copy(favorites = favs) }
            }
        }
        scope.launch {
            state.map { s ->
                val track = s.currentTrack
                WidgetSnapshot(
                    title = track?.displayTitle,
                    artist = track?.displayArtist,
                    album = track?.displayAlbum,
                    isPlaying = s.isPlaying,
                    trackId = track?.id ?: 0,
                    hasCover = track?.hasCover == true,
                    localCoverPath = track?.localCoverPath,
                    isFavorite = track?.id?.toString()?.let { id -> id in s.favorites } == true,
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    context.sendBroadcast(
                        Intent(context, NowPlayingWidget::class.java).apply {
                            action = NowPlayingWidget.ACTION_UPDATE_WIDGET
                            putExtra(NowPlayingWidget.EXTRA_TITLE, snapshot.title)
                            putExtra(NowPlayingWidget.EXTRA_ARTIST, snapshot.artist)
                            putExtra(NowPlayingWidget.EXTRA_ALBUM, snapshot.album)
                            putExtra(NowPlayingWidget.EXTRA_IS_PLAYING, snapshot.isPlaying)
                            putExtra(NowPlayingWidget.EXTRA_TRACK_ID, snapshot.trackId)
                            putExtra(NowPlayingWidget.EXTRA_HAS_COVER, snapshot.hasCover)
                            putExtra(NowPlayingWidget.EXTRA_LOCAL_COVER_PATH, snapshot.localCoverPath)
                            putExtra(NowPlayingWidget.EXTRA_IS_FAVORITE, snapshot.isFavorite)
                        },
                    )
                }
        }
        // A new track means the recent-plays list changed too; ask the list
        // widget to reload its rows.
        scope.launch {
            state.map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect {
                    refreshRecentWidget()
                }
        }
    }

    private fun refreshRecentWidget() {
        context.sendBroadcast(
            Intent(context, RecentTracksWidget::class.java).apply {
                action = RecentTracksWidget.ACTION_REFRESH
            },
        )
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
        override fun onPlaybackStateChanged(playbackState: Int) {
            // The reloaded FLAC stream is ready — the seek has landed.
            if (flacSeekInFlight && playbackState == Player.STATE_READY) flacSeekInFlight = false
            syncState()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flacSeekInFlight = false
            syncState()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = syncState()
        override fun onRepeatModeChanged(repeatMode: Int) = syncState()
        // Fires when duration becomes available (e.g. FLAC headers parsed after buffering starts)
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = syncState()
        // A mid-stream failure (dropped connection, server error) used to be
        // invisible: the player stopped with no UI feedback. Surface it.
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            flacSeekInFlight = false
            _state.update {
                it.copy(
                    isPlaying = false,
                    pendingTrackId = null,
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

        // Media3 walks the timeline itself; its index is where the queue is now.
        // The timeline is also what is audible, so if the two ever disagree on
        // the order, the queue is rebuilt from it rather than the other way
        // round — the UI must never name a different song than the one playing.
        adoptTimeline(ctrl)
        val index = ctrl.currentMediaItemIndex

        // The queue's own entry is the freshest copy of the track — in offline
        // mode it carries the resolved local file path — so prefer it over the
        // id-keyed cache.
        val currentTrack = queue.entries.getOrNull(index)?.track
            ?: trackId?.let { id -> trackCache.find { it.id == id } }

        // Prefer metadata duration: FLAC streams with ?start=N report only the remaining
        // chunk duration, not the full track duration.
        val metaDurationMs = ((currentTrack?.durationSecs ?: 0.0) * 1000).toLong()
        val exoDurationMs = ctrl.duration.takeIf { it > 0 } ?: 0L
        val durationMs = if (metaDurationMs > 0) metaDurationMs else exoDurationMs
        val positionMs = (ctrl.currentPosition + flacSeekOffsetMs).coerceAtMost(durationMs.coerceAtLeast(1L))
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        // A play counts after 30 s of listening, once per track — the same
        // threshold the desktop and web players use. Local-library tracks have
        // no server row to record against (their ids are negative).
        if (currentTrack != null && currentTrack.id > 0 &&
            playCountedTrackId != currentTrack.id && positionMs >= PLAY_COUNT_THRESHOLD_MS
        ) {
            playCountedTrackId = currentTrack.id
            scope.launch {
                libraryRepository.recordPlay(currentTrack.id, preferences.deviceNameState.value)
                // The server's recent list only gains this play now, so the
                // refresh sent when the track changed was too early to show it.
                refreshRecentWidget()
            }
        }

        // Hysteresis: the loading bar only appears once buffering has
        // persisted past BUFFERING_UI_DELAY_MS. The brief BUFFERING blips
        // on every prepare and seek must not flash it — during playback
        // the seek bar is the only bar.
        val nowBuffering = ctrl.playbackState == Player.STATE_BUFFERING
        val isBufferingUi: Boolean
        if (nowBuffering) {
            val now = SystemClock.elapsedRealtime()
            val since = bufferingSinceElapsedMs ?: now.also { bufferingSinceElapsedMs = it }
            isBufferingUi = now - since >= BUFFERING_UI_DELAY_MS
        } else {
            bufferingSinceElapsedMs = null
            isBufferingUi = false
        }

        // The pending spinner clears once the requested track is actually
        // current and the player is READY — or if a different track is
        // current, meaning the request was superseded.
        val pendingId = _state.value.pendingTrackId
        val pendingCleared = pendingId != null &&
            (currentTrack?.id != pendingId || ctrl.playbackState == Player.STATE_READY)

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
                isSeekBuffering = flacSeekInFlight,
                isBuffering = isBufferingUi,
                pendingTrackId = if (pendingCleared) null else pendingId,
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
                val buffering = controller?.playbackState == Player.STATE_BUFFERING
                // While buffering, poll faster so the loading bar's fill
                // tracks the buffered percentage smoothly; otherwise the
                // 500ms poll is plenty.
                delay(if (buffering) 100 else 500)
                // Nothing advances on its own while paused or in the
                // background — the listener callbacks cover every transition
                // — so the poll only needs to run while something is playing.
                // Buffering is the exception: isPlaying() is false there, but
                // the buffer fills on its own and the loading bar must track it.
                val ctrl = controller
                if (ctrl?.isPlaying == true || ctrl?.playbackState == Player.STATE_BUFFERING) syncState()
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
        val ctrl = awaitController() ?: return

        scope.launch {
            // Visible from the tap itself: the token fetch and prepare below
            // are network work the player has not even started reporting yet.
            _state.update { it.copy(errorMessage = null, pendingTrackId = track.id) }
            mediaItems.clear()
            // Offline mode builds the queue from downloaded files only: every
            // track is pointed at its local copy, and one with no download has
            // nothing to play from.
            val effectiveTrack: CatalogTrack
            val effectiveQueue: List<CatalogTrack>
            if (preferences.offlineMode.first()) {
                val startPath = track.localFilePath
                    ?: offlineDownloadManager.getLocalFilePath(track.id)
                if (startPath == null) {
                    _state.update {
                        it.copy(
                            pendingTrackId = null,
                            errorMessage = "Not downloaded — download it or turn off offline mode",
                        )
                    }
                    scope.launch { delay(4000); _state.update { it.copy(errorMessage = null) } }
                    return@launch
                }
                effectiveTrack = track.copy(localFilePath = startPath)
                effectiveQueue = queue.mapNotNull { t ->
                    (t.localFilePath ?: offlineDownloadManager.getLocalFilePath(t.id))
                        ?.let { t.copy(localFilePath = it) }
                }
            } else {
                effectiveTrack = track
                effectiveQueue = queue
            }
            // The new queue is swapped in only once its media items exist.
            // Until setMediaItems lands, the player is still walking the old
            // timeline, and the progress poll would read that stale index back
            // over the new position — which is how tapping a second song
            // restarted the one that was already playing.
            val newQueue = this@PlayerController.queue.playContext(effectiveQueue, effectiveTrack)
            val items = buildItems(newQueue.tracks) ?: run {
                _state.update { it.copy(pendingTrackId = null) }
                return@launch
            }
            this@PlayerController.queue = newQueue
            // Media3's own shuffle reorders a timeline the queue screen never
            // reads, so the list and the playback disagreed. Order is ours now.
            ctrl.shuffleModeEnabled = false
            mutateTimeline { ctrl.setMediaItems(items, newQueue.position.coerceAtLeast(0), 0L) }
            ctrl.prepare()
            ctrl.play()
            syncState()
        }
    }

    /** True when the server serves this track's original FLAC 1:1
     *  (`[feature-flags] true_flac` on, FLAC track) instead of a transcoded MP3. */
    private fun isTrueFlac(track: CatalogTrack, info: TokenResponse): Boolean =
        info.flac && track.format.equals("flac", ignoreCase = true)

    /** Playback URI for a server track: the FLAC passthrough endpoint when the
     *  server flag is on and the track is FLAC, else the transcoded stream. */
    private fun streamUriFor(baseUrl: String, track: CatalogTrack, info: TokenResponse): String =
        if (isTrueFlac(track, info)) {
            "$baseUrl/stream/flac/${info.token}/${track.id}"
        } else {
            "$baseUrl/stream/${info.token}/${track.id}"
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
                    val info = libraryRepository.getStreamTokenInfo(t.id).getOrElse { e ->
                        _state.update { s ->
                            s.copy(errorMessage = "Playback failed: ${e.message ?: "Could not get stream token"}")
                        }
                        scope.launch { delay(4000); _state.update { it.copy(errorMessage = null) } }
                        return null
                    }
                    streamUriFor(baseUrl, t, info)
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
     * Re-anchor [queue] to Media3's timeline.
     *
     * Normally that is just copying the playing index across. If the order
     * itself differs — an edit raced a track change, say — the timeline wins:
     * it is what plays, and [PlayerState.currentTrack] is read from the queue,
     * so a queue that disagrees names the wrong song (audio of A with B's art).
     * Entries keep their origin; a track the queue has never seen is looked up
     * in the cache, and if any id cannot be resolved the queue is left alone.
     */
    private fun adoptTimeline(ctrl: MediaController) {
        val count = ctrl.mediaItemCount
        if (count == 0) return
        val index = ctrl.currentMediaItemIndex
        val entries = queue.entries
        val inOrder = entries.size == count &&
            (0 until count).all { ctrl.getMediaItemAt(it).mediaId.toIntOrNull() == entries[it].track.id }
        // While one of our own edits is rewriting the timeline, listener
        // callbacks can fire between its steps and see a half-applied order;
        // that is not a disagreement, so only the position is followed.
        if (inOrder || mutatingTimeline) {
            if (index in entries.indices && index != queue.position) {
                queue = queue.copy(position = index)
            }
            return
        }
        val known = entries.associateBy { it.track.id }
        val rebuilt = (0 until count).map { i ->
            val id = ctrl.getMediaItemAt(i).mediaId.toIntOrNull() ?: return
            known[id]
                ?: trackCache.find { it.id == id }?.let { QueueEntry(it, QueueOrigin.SYSTEM) }
                ?: return
        }
        queue = queue.copy(entries = rebuilt, position = index.coerceIn(rebuilt.indices))
    }

    private val queueEdits = Mutex()

    /** True while this class is itself rewriting Media3's timeline. */
    private var mutatingTimeline = false

    private inline fun mutateTimeline(block: () -> Unit) {
        mutatingTimeline = true
        try {
            block()
        } finally {
            mutatingTimeline = false
        }
    }

    /**
     * Apply an edit to the queue and rewrite the timeline after the current
     * track to match.
     *
     * Only the part after the current track is rewritten, so whatever is
     * playing keeps playing — a queue edit should never restart the song.
     *
     * [edit] runs against the queue as it is *when the edit is applied*, not
     * when it was requested: building the items waits on the preferences
     * store, and in that gap the player can advance a track (the tail would
     * then be spliced in one slot too late, duplicating the song that just
     * started) or another edit can land (and be overwritten). Either is
     * detected after the wait and the edit is recomputed from fresh state.
     * Edits are serialised so two of them cannot interleave their writes.
     */
    private fun editQueue(edit: (PlaybackQueue) -> PlaybackQueue) {
        if (controller == null) return
        scope.launch {
            queueEdits.withLock {
                for (attempt in 1..QUEUE_EDIT_ATTEMPTS) {
                    val ctrl = controller ?: return@withLock
                    adoptTimeline(ctrl)
                    val base = queue
                    val effective = edit(base).withDownloadedFiles()
                    val tail = effective.entries.drop(effective.position + 1).map { it.track }
                    val items = buildItems(tail) ?: return@withLock
                    val stale = queue !== base ||
                        (base.position >= 0 && ctrl.currentMediaItemIndex != base.position)
                    if (stale && attempt < QUEUE_EDIT_ATTEMPTS) continue
                    queue = effective
                    val from = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
                    mutateTimeline { ctrl.replaceMediaItems(from, ctrl.mediaItemCount, items) }
                    syncState()
                    return@withLock
                }
            }
        }
    }

    /**
     * Offline mode: drop upcoming tracks that have no downloaded file and
     * point the rest at their local copy, so no stream token is requested
     * while offline mode is on. History and the current track are left alone
     * — the song in progress keeps playing.
     */
    private suspend fun PlaybackQueue.withDownloadedFiles(): PlaybackQueue {
        if (!preferences.offlineMode.first()) return this
        val head = entries.take(position + 1)
        val tail = entries.drop(position + 1).mapNotNull { entry ->
            val path = entry.track.localFilePath
                ?: offlineDownloadManager.getLocalFilePath(entry.track.id)
            if (path == null) null
            else entry.copy(track = entry.track.copy(localFilePath = path))
        }
        return copy(entries = head + tail)
    }

    fun playPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    /**
     * A Now Playing widget transport button. The widget cannot drive the
     * player itself — a BroadcastReceiver's context may not bind to the
     * playback service (ReceiverCallNotAllowedException) — so it opens the
     * app with [NowPlayingWidget.ACTION_TRANSPORT] and the command lands here,
     * where a properly connected controller exists. Waits for the connection
     * so a tap with the app cold-started still works. Play with an empty
     * queue just leaves the app open.
     */
    suspend fun handleWidgetTransport(transport: String) {
        val ctrl = awaitController() ?: return
        when (transport) {
            NowPlayingWidget.TRANSPORT_PLAY_PAUSE ->
                if (ctrl.mediaItemCount > 0) {
                    if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                }
            NowPlayingWidget.TRANSPORT_NEXT -> ctrl.seekToNextMediaItem()
            NowPlayingWidget.TRANSPORT_PREVIOUS ->
                if (ctrl.currentPosition > 3000) ctrl.seekTo(0L)
                else ctrl.seekToPreviousMediaItem()
        }
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

        if (track.needsTranscode && track.localFilePath == null) {
            // FLAC/WAV server stream. A true-FLAC stream (feature flag on) is the
            // complete file and seeks natively like an MP3; only a transcoded
            // stream needs the ?start=N reload below.
            scope.launch {
                val info = libraryRepository.getStreamTokenInfo(track.id).getOrNull()
                if (info != null && isTrueFlac(track, info)) {
                    nativeSeek(ctrl, track, fraction)
                    return@launch
                }
                // The server transcodes to MP3 starting at ?start=N.
                // Re-request with a fresh token and the desired start offset. The
                // seek bar shows buffering until the replacement stream is READY.
                flacSeekInFlight = true
                _state.update { it.copy(isSeekBuffering = true) }
                val baseUrl = preferences.serverUrl.first().trimEnd('/')
                val token = info?.token ?: libraryRepository.getStreamToken(track.id).getOrNull() ?: run {
                    flacSeekInFlight = false
                    _state.update { it.copy(isSeekBuffering = false) }
                    return@launch
                }
                val targetSecs = targetMs / 1000.0
                val uri = "$baseUrl/stream/$token/${track.id}?start=${"%.2f".format(targetSecs)}"
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
            nativeSeek(ctrl, track, fraction)
        }
    }

    /** Native seek for complete streams (MP3, cached files, true-FLAC). */
    private fun nativeSeek(ctrl: Player, track: CatalogTrack, fraction: Float) {
        val durationMs = ctrl.duration.takeIf { it > 0 }
            ?: ((track.durationSecs ?: 0.0) * 1000).toLong().takeIf { it > 0 }
            ?: return
        ctrl.seekTo((durationMs * fraction).toLong())
        // The progress poll only runs while playing, so a seek while
        // paused has to push the new position itself.
        syncState()
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
        editQueue { it.withShuffle(enabled) }
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
        _state.update { it.copy(pendingTrackId = track.id) }
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
        mutateTimeline { ctrl.removeMediaItem(index) }
        syncState()
    }

    /** Drop everything still queued, from both halves. */
    fun clearQueue() {
        val ctrl = controller ?: return
        queue = queue.clearUpNext()
        mutateTimeline {
            for (i in (ctrl.currentMediaItemIndex + 1 until ctrl.mediaItemCount).reversed()) {
                ctrl.removeMediaItem(i)
            }
        }
        syncState()
    }

    /** Drop only what the listener queued, leaving the album or playlist. */
    fun clearUserQueue() = editQueue { it.clearUserQueue() }

    /** Reorder by index into the whole queue, history included. */
    fun reorderQueue(fromIndex: Int, toIndex: Int, intoSection: QueueOrigin? = null) {
        val ctrl = controller ?: return
        if (fromIndex !in queue.entries.indices || toIndex !in queue.entries.indices) return
        queue = queue.move(fromIndex, toIndex, intoSection)
        if (fromIndex != toIndex) mutateTimeline { ctrl.moveMediaItem(fromIndex, toIndex) }
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
        editQueue { it.addToUserQueue(tracks) }
    }

    /** Queue a track to play immediately after the current one. */
    fun playNext(track: CatalogTrack) {
        cacheTracks(listOf(track))
        editQueue { it.playNext(track) }
    }

    /**
     * Extend the *context* rather than the user queue — what shuffle-all uses
     * to top itself up. These are not the listener's own picks, so they belong
     * behind anything queued, not in front of it.
     */
    fun appendToContext(tracks: List<CatalogTrack>) {
        if (tracks.isEmpty()) return
        cacheTracks(tracks)
        editQueue { it.appendToContext(tracks) }
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

    /**
     * The connected controller, waiting for the connection if it is still being
     * made. A cold-started process (a widget row tapped with the app dead) has
     * no controller yet when the first play arrives; returning null there made
     * that tap silently do nothing.
     */
    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        // Reconnect only when nothing is in flight; a second connect() would
        // race the first and leave two controllers.
        if (controllerFuture?.isDone != false) connect()
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { state.first { it.isConnected } }
        return controller
    }

    /**
     * Stop playback and forget the session — used on logout, so the next
     * account never sees (or hears) the previous one's queue. Favorites stay:
     * they are a per-device preference, not server data.
     */
    fun stopPlayback() {
        controller?.pause()
        controller?.clearMediaItems()
        queue = PlaybackQueue()
        mediaItems.clear()
        trackCache = emptyList()
        flacSeekOffsetMs = 0L
        flacSeekInFlight = false
        bufferingSinceElapsedMs = null
        lastSyncedTrackId = null
        playCountedTrackId = null
        _state.update {
            it.copy(
                currentTrack = null,
                isPlaying = false,
                progress = 0f,
                positionMs = 0L,
                durationMs = 0L,
                queue = emptyList(),
                isSeekBuffering = false,
                isBuffering = false,
                pendingTrackId = null,
                playbackQueue = PlaybackQueue(),
            )
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

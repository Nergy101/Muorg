package nl.muorg.android.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.CastMediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import javax.inject.Inject
import javax.inject.Singleton

data class CastPlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localCastServer: LocalCastServer,
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val _castPlaybackState = MutableStateFlow(CastPlaybackState())
    val castPlaybackState: StateFlow<CastPlaybackState> = _castPlaybackState.asStateFlow()

    private val _trackFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trackFinished: SharedFlow<Unit> = _trackFinished.asSharedFlow()

    private var castContext: CastContext? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollJob: Job? = null

    // Required for mDNS Chromecast discovery on real hardware — emulators don't enforce this filter
    private val multicastLock: WifiManager.MulticastLock =
        (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("muorg_cast_discovery")
            .also { it.setReferenceCounted(true) }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _isCasting.value = true
            _castDeviceName.value = session.castDevice?.friendlyName
            startPolling(session)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            _isCasting.value = false
            _castDeviceName.value = null
            stopPolling()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
            _castDeviceName.value = null
            stopPolling()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _isCasting.value = true
            _castDeviceName.value = session.castDevice?.friendlyName
            startPolling(session)
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    }

    init {
        multicastLock.acquire()
        Handler(Looper.getMainLooper()).post {
            try {
                val ctx = CastContext.getSharedInstance(context)
                castContext = ctx
                ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                val current = ctx.sessionManager.currentCastSession
                if (current != null && current.isConnected) {
                    _isCasting.value = true
                    _castDeviceName.value = current.castDevice?.friendlyName
                    startPolling(current)
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPolling(session: CastSession) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var wasPlaying = false
            while (true) {
                val client = session.remoteMediaClient
                val status = client?.mediaStatus
                if (status != null) {
                    val isPlaying = status.playerState == MediaStatus.PLAYER_STATE_PLAYING
                    _castPlaybackState.value = CastPlaybackState(
                        isPlaying = isPlaying,
                        positionMs = client.approximateStreamPosition,
                        durationMs = status.mediaInfo?.streamDuration?.takeIf { it > 0 } ?: 0L,
                    )
                    if (wasPlaying
                        && status.playerState == MediaStatus.PLAYER_STATE_IDLE
                        && status.idleReason == MediaStatus.IDLE_REASON_FINISHED
                    ) {
                        _trackFinished.tryEmit(Unit)
                    }
                    wasPlaying = isPlaying
                }
                delay(500)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _castPlaybackState.value = CastPlaybackState()
    }

    fun buildRouteSelector(): MediaRouteSelector =
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
            .build()

    fun castTrack(track: CatalogTrack, baseUrl: String, token: String) {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        val client = session.remoteMediaClient ?: return

        val streamUrl = if (track.localFilePath != null) {
            if (!localCastServer.isAlive) localCastServer.start()
            localCastServer.register(track)
        } else {
            "$baseUrl/stream/${track.id}?token=$token"
        }

        val contentType = when (track.format.lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac", "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/ogg;codecs=opus"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track.displayTitle)
            putString(MediaMetadata.KEY_ARTIST, track.displayArtist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, track.displayAlbum)
        }

        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        client.load(request)
    }

    fun adjustVolume(delta: Double) {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        if (session.isConnected) session.volume = (session.volume + delta).coerceIn(0.0, 1.0)
    }

    fun remotePlayPause() {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val status = client.mediaStatus ?: return
        if (status.playerState == MediaStatus.PLAYER_STATE_PLAYING) client.pause() else client.play()
    }

    fun remoteSeek(fraction: Float) {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val duration = client.mediaStatus?.mediaInfo?.streamDuration ?: return
        if (duration > 0) client.seek((duration * fraction).toLong())
    }

    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    fun release() {
        if (multicastLock.isHeld) multicastLock.release()
        if (localCastServer.isAlive) localCastServer.stop()
    }
}

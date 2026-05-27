package nl.muorg.android.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.CastMediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.muorg.android.data.api.CatalogTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private var castContext: CastContext? = null

    // Required for mDNS Chromecast discovery on real hardware — emulators don't enforce this filter
    private val multicastLock: WifiManager.MulticastLock =
        (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("muorg_cast_discovery")
            .also { it.setReferenceCounted(true) }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _isCasting.value = true
            _castDeviceName.value = session.castDevice?.friendlyName
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            _isCasting.value = false
            _castDeviceName.value = null
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
            _castDeviceName.value = null
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _isCasting.value = true
            _castDeviceName.value = session.castDevice?.friendlyName
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
                // Sync initial state in case a session is already active
                val current = ctx.sessionManager.currentCastSession
                if (current != null && current.isConnected) {
                    _isCasting.value = true
                    _castDeviceName.value = current.castDevice?.friendlyName
                }
            } catch (_: Exception) {}
        }
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

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track.displayTitle)
            putString(MediaMetadata.KEY_ARTIST, track.displayArtist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, track.displayAlbum)
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

        val mediaInfo = MediaInfo.Builder("$baseUrl/stream/${track.id}?token=$token")
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

    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    fun release() {
        if (multicastLock.isHeld) multicastLock.release()
    }
}

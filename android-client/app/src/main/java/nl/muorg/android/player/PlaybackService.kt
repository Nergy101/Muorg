package nl.muorg.android.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import nl.muorg.android.MainActivity
import nl.muorg.android.R
import nl.muorg.android.cast.CastManager
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var castManager: CastManager

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            // Hold a partial wake lock while playing. With the screen off, the
            // audio HAL's own lock is all that keeps the CPU awake during a
            // song; at the song boundary — audio stopped, next stream still
            // loading — the CPU can suspend and the load stalls, so playback
            // stops instead of advancing to the next track.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Tapping the media notification (the "active app" entry in the
        // Android drawer) fires this intent — without it the tap does nothing.
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        mediaSession?.setMediaButtonPreferences(buildCommandButtons())

        // Keep the notification scrubber in sync with Chromecast position while casting.
        // ExoPlayer is paused during cast; seeking it (within its buffer) updates the
        // MediaSession position without triggering new network requests.
        serviceScope.launch {
            combine(castManager.isCasting, castManager.castPlaybackState) { casting, state ->
                casting to state
            }.collect { (casting, castState) ->
                val player = mediaSession?.player ?: return@collect
                if (casting && castState.positionMs >= 0) {
                    player.seekTo(castState.positionMs)
                }
            }
        }
    }

    private fun buildCommandButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName(getString(R.string.action_previous))
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName(getString(R.string.action_next))
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .build(),
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

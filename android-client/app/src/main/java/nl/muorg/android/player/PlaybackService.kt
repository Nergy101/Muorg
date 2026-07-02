package nl.muorg.android.player

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
import nl.muorg.android.cast.CastManager
import nl.muorg.android.data.preferences.AppPreferences
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var preferences: AppPreferences
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
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        serviceScope.launch {
            preferences.notificationActions.collect { actions ->
                mediaSession?.setMediaButtonPreferences(buildCommandButtons(actions))
            }
        }

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

    private fun buildCommandButtons(actions: Set<String>): List<CommandButton> = buildList {
        if ("skip_previous" in actions) add(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
        )
        if ("shuffle" in actions) add(
            CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                .build()
        )
        if ("skip_next" in actions) add(
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
        )
        if ("repeat" in actions) add(
            CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
                .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
                .build()
        )
    }

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

    companion object {
        const val ACTION_PLAY = "nl.muorg.android.action.PLAY"
        const val ACTION_PAUSE = "nl.muorg.android.action.PAUSE"
        const val ACTION_NEXT = "nl.muorg.android.action.NEXT"
        const val ACTION_PREVIOUS = "nl.muorg.android.action.PREVIOUS"
    }
}

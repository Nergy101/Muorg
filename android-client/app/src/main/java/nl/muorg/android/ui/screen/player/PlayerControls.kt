package nl.muorg.android.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.player.PlayerState
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel

/**
 * The seek bar, the transport row and the volume slider.
 *
 * Split out of [PlayerScreen], which was a single 574-line composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.PlayerControls(
    playerState: PlayerState,
    playerViewModel: PlayerViewModel,
    currentTrack: CatalogTrack?,
    accent: Color,
    isCasting: Boolean,
    castVolume: Float?,
    displayProgress: Float,
    isSeekingState: MutableState<Boolean>,
    seekPositionState: MutableState<Float>,
) {
    // Hoisted as state rather than value + setter: the seek gesture writes both
    // on every drag frame, and threading two callbacks through reads worse than
    // the delegation the body already used.
    var isSeeking by isSeekingState
    var seekPosition by seekPositionState
    // Material's default slider is a fat pill with a gap either side of
    // the thumb; the web's is a 3px hairline with a small round knob, and
    // that difference is the loudest thing on the screen. Both slots are
    // replaced rather than recoloured.
    val primary = accent
    Slider(
        value = displayProgress,
        onValueChange = { value ->
            isSeeking = true
            seekPosition = value
        },
        onValueChangeFinished = {
            playerViewModel.seekTo(seekPosition)
            isSeeking = false
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(16.dp),
        thumb = {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(primary),
            )
        },
        track = { state ->
            val fraction = state.value.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(primary),
                )
            }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val displayDurationMs = if (playerState.durationMs > 0) playerState.durationMs
            else ((currentTrack?.durationSecs ?: 0.0) * 1000).toLong()
        Text(
            text = formatMs(playerState.positionMs),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
        )
        // The web counts DOWN on the right, it does not repeat the length.
        Text(
            text = if (displayDurationMs > 0)
                "-" + formatMs((displayDurationMs - playerState.positionMs).coerceAtLeast(0))
            else "–:--",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = playerViewModel::toggleShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(mageIconRes("exchange")),
                contentDescription = "Shuffle",
                tint = if (playerState.shuffleEnabled) accent
                       else Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = playerViewModel::skipPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(mageIconRes("previous")),
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = playerViewModel::playPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(mageIconRes(if (playerState.isPlaying) "pause" else "play")),
                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = playerViewModel::skipNext, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(mageIconRes("next")),
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = playerViewModel::cycleRepeatMode, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(mageIconRes("reload")),
                contentDescription = "Repeat",
                tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF)
                    accent else Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }

    // Only while casting: the phone's own volume keys cover local
    // playback, and the web player has no volume row at all.
    if (isCasting) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = castVolume ?: 0f,
                onValueChange = { playerViewModel.setCastVolume(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White.copy(alpha = 0.8f),
                    activeTrackColor = Color.White.copy(alpha = 0.6f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))
}

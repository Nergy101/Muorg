package nl.muorg.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.player.PlayerController
import nl.muorg.android.player.PlayerState
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.state

    fun playTrack(track: CatalogTrack, queue: List<CatalogTrack>) {
        viewModelScope.launch {
            playerController.updateTrackCache(queue)
            playerController.playTrack(track, queue)
        }
    }

    fun playPause() {
        playerController.playPause()
    }

    fun skipNext() {
        playerController.skipNext()
    }

    fun skipPrevious() {
        playerController.skipPrevious()
    }

    fun seekTo(fraction: Float) {
        playerController.seekTo(fraction)
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun enableShuffle() {
        playerController.enableShuffle()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }
}

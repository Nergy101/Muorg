package nl.muorg.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.player.PlayerController
import nl.muorg.android.player.PlayerState
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.state

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode != "local") {
                playlistRepository.getPlaylists().onSuccess { _playlists.value = it }
            }
        }
    }

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

    fun skipTo(track: CatalogTrack) = playerController.skipTo(track)
    fun removeFromQueue(track: CatalogTrack) = playerController.removeFromQueue(track)
    fun clearQueue() = playerController.clearQueue()
    fun addToQueue(track: CatalogTrack) = playerController.addToQueue(track)
    fun toggleFavorite(track: CatalogTrack) = playerController.toggleFavorite(track)
}

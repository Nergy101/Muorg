package nl.muorg.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.muorg.android.cast.CastManager
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.player.PlayerController
import nl.muorg.android.player.PlayerState
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
    private val castManager: CastManager,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    val isCasting: StateFlow<Boolean> = castManager.isCasting
    val castDeviceName: StateFlow<String?> = castManager.castDeviceName

    fun buildCastRouteSelector() = castManager.buildRouteSelector()

    val playerState: StateFlow<PlayerState> = playerController.state

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _currentTrackMembership = MutableStateFlow<Set<Int>>(emptySet())
    val currentTrackMembership: StateFlow<Set<Int>> = _currentTrackMembership.asStateFlow()

    init {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                _playlists.value = localRepository.getPlaylists()
            } else {
                playlistRepository.getPlaylists().onSuccess { _playlists.value = it }
            }
        }
        viewModelScope.launch {
            castManager.isCasting.drop(1).collect { casting ->
                if (casting) {
                    val track = playerController.state.value.currentTrack ?: return@collect
                    if (playerController.state.value.isPlaying) playerController.playPause()
                    castCurrentTrack(track)
                }
            }
        }
    }

    fun loadCurrentTrackMembership(track: CatalogTrack) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                val membership = localRepository.getPlaylistMembershipByPath()
                _currentTrackMembership.value = membership[track.path] ?: emptySet()
            } else {
                _currentTrackMembership.value = emptySet()
            }
        }
    }

    fun addTrackToPlaylist(track: CatalogTrack, playlistId: Int) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                localRepository.addTracksToPlaylist(playlistId, listOf(track))
                refreshPlaylists()
                loadCurrentTrackMembership(track)
            } else {
                playlistRepository.addTracks(playlistId, listOf(track.id))
            }
        }
    }

    fun removeTrackFromPlaylist(track: CatalogTrack, playlistId: Int) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                localRepository.removeTrackFromPlaylist(playlistId, track.path)
                refreshPlaylists()
                loadCurrentTrackMembership(track)
            } else {
                playlistRepository.removeTracks(playlistId, listOf(track.id))
            }
        }
    }

    private suspend fun refreshPlaylists() {
        val mode = preferences.musicMode.first()
        if (mode == "local") {
            _playlists.value = localRepository.getPlaylists()
        } else {
            playlistRepository.getPlaylists().onSuccess { _playlists.value = it }
        }
    }

    fun playTrack(track: CatalogTrack, queue: List<CatalogTrack>) {
        viewModelScope.launch {
            playerController.updateTrackCache(queue)
            playerController.playTrack(track, queue)
            if (castManager.isCasting.value) castCurrentTrack(track)
        }
    }

    private suspend fun castCurrentTrack(track: CatalogTrack) {
        val baseUrl = preferences.serverUrl.first()
        libraryRepository.getStreamToken(track.id).onSuccess { token ->
            castManager.castTrack(track, baseUrl, token)
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

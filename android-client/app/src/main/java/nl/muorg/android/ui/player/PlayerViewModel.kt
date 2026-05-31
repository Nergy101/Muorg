package nl.muorg.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    // Shuffle-all mode: pool of all available tracks to draw random batches from.
    // Empty means shuffle-all is not active.
    private var shuffleAllPool: List<CatalogTrack> = emptyList()
    // Guard: only refill when queue.size reaches this threshold, preventing double-fires.
    private var shuffleAllMinQueueSize = Int.MAX_VALUE

    val isCasting: StateFlow<Boolean> = castManager.isCasting
    val castDeviceName: StateFlow<String?> = castManager.castDeviceName
    val castVolume: StateFlow<Float?> = castManager.castVolume

    fun buildCastRouteSelector() = castManager.buildRouteSelector()

    // When casting, mirror the Chromecast's playback state; otherwise use local ExoPlayer state.
    val playerState: StateFlow<PlayerState> = combine(
        playerController.state,
        castManager.isCasting,
        castManager.castPlaybackState,
    ) { local, casting, cast ->
        if (casting) local.copy(
            isPlaying = cast.isPlaying,
            progress = cast.progress,
            positionMs = cast.positionMs,
            durationMs = cast.durationMs.takeIf { it > 0 } ?: local.durationMs,
        ) else local
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerState())

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _currentTrackMembership = MutableStateFlow<Set<Int>>(emptySet())
    val currentTrackMembership: StateFlow<Set<Int>> = _currentTrackMembership.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            ensureFavoritesPlaylist()
            if (mode == "local") {
                _playlists.value = localRepository.getPlaylists()
            } else {
                playlistRepository.getPlaylists().onSuccess { _playlists.value = it }
            }
        }
        // When cast session starts, pause local player and load track onto the Chromecast
        viewModelScope.launch {
            castManager.isCasting.drop(1).collect { casting ->
                if (casting) {
                    val track = playerController.state.value.currentTrack ?: return@collect
                    if (playerController.state.value.isPlaying) playerController.playPause()
                    castCurrentTrack(track)
                }
            }
        }
        // When cast finishes a track, advance the local queue and cast the next track
        viewModelScope.launch {
            castManager.trackFinished.collect {
                val prevId = playerController.state.value.currentTrack?.id
                playerController.skipNext()
                // Wait for PlayerController to confirm the new track before casting it
                val next = playerController.state
                    .first { it.currentTrack != null && it.currentTrack.id != prevId }
                    .currentTrack ?: return@collect
                castCurrentTrack(next)
            }
        }
        // Watchdog: if casting is active and local player is somehow still playing, pause it
        viewModelScope.launch {
            combine(
                castManager.isCasting,
                playerController.state.map { it.isPlaying }.distinctUntilChanged(),
            ) { casting, localPlaying -> casting && localPlaying }
                .distinctUntilChanged()
                .collect { bothPlaying ->
                    if (bothPlaying) playerController.playPause()
                }
        }
        // Auto-refill shuffle-all queue: when ≤1 track remains after the current one, append 20 more.
        viewModelScope.launch {
            playerController.state.collect { state ->
                if (shuffleAllPool.isEmpty()) return@collect
                val queue = state.queue
                val currentTrack = state.currentTrack ?: return@collect
                val currentIndex = queue.indexOfFirst { it.id == currentTrack.id }
                if (currentIndex < 0) return@collect
                val remaining = queue.size - 1 - currentIndex
                if (remaining <= 1 && queue.size >= shuffleAllMinQueueSize) {
                    shuffleAllMinQueueSize = queue.size + 1
                    playerController.addTracksToQueue(shuffleAllPool.shuffled().take(20))
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
        shuffleAllPool = emptyList()
        shuffleAllMinQueueSize = Int.MAX_VALUE
        viewModelScope.launch {
            playerController.updateTrackCache(queue)
            playerController.playTrack(track, queue)
            if (castManager.isCasting.value) castCurrentTrack(track)
        }
    }

    fun startShuffleAll(allTracks: List<CatalogTrack>) {
        if (allTracks.isEmpty()) return
        shuffleAllPool = allTracks
        shuffleAllMinQueueSize = 0
        val firstBatch = allTracks.shuffled().take(20)
        playerController.disableShuffle()
        viewModelScope.launch {
            playerController.updateTrackCache(firstBatch)
            playerController.playTrack(firstBatch.first(), firstBatch)
        }
    }

    private suspend fun castCurrentTrack(track: CatalogTrack) {
        val baseUrl = preferences.serverUrl.first()
        if (track.localFilePath != null) {
            // Local file — no stream token needed; LocalCastServer serves it over WiFi
            castManager.castTrack(track, baseUrl, "")
        } else {
            libraryRepository.getStreamToken(track.id).onSuccess { token ->
                castManager.castTrack(track, baseUrl, token)
            }
        }
    }

    fun playPause() {
        if (castManager.isCasting.value) castManager.remotePlayPause() else playerController.playPause()
    }

    fun seekTo(fraction: Float) {
        if (castManager.isCasting.value) castManager.remoteSeek(fraction) else playerController.seekTo(fraction)
    }

    fun skipNext() {
        playerController.skipNext()
        if (castManager.isCasting.value) {
            viewModelScope.launch {
                val track = playerController.state.value.currentTrack ?: return@launch
                castCurrentTrack(track)
            }
        }
    }

    fun skipPrevious() {
        playerController.skipPrevious()
        if (castManager.isCasting.value) {
            viewModelScope.launch {
                val track = playerController.state.value.currentTrack ?: return@launch
                castCurrentTrack(track)
            }
        }
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun enableShuffle() {
        playerController.enableShuffle()
    }

    fun disableShuffle() {
        playerController.disableShuffle()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun skipTo(track: CatalogTrack) {
        playerController.skipTo(track)
        if (castManager.isCasting.value) {
            viewModelScope.launch { castCurrentTrack(track) }
        }
    }

    fun removeFromQueue(track: CatalogTrack) = playerController.removeFromQueue(track)
    fun clearQueue() = playerController.clearQueue()
    fun reorderQueue(fromIndex: Int, toIndex: Int) = playerController.reorderQueue(fromIndex, toIndex)
    fun addToQueue(track: CatalogTrack) {
        playerController.addToQueue(track, isUserAction = true)
        _toastEvent.tryEmit("Added to queue")
    }
    fun addTracksToQueue(tracks: List<CatalogTrack>) {
        if (tracks.isEmpty()) return
        playerController.addTracksToQueue(tracks, isUserAction = true)
        _toastEvent.tryEmit("Added to queue")
    }
    fun toggleFavorite(track: CatalogTrack) {
        viewModelScope.launch {
            val wasFavorite = track.id.toString() in playerController.state.value.favorites
            playerController.toggleFavorite(track)
            val playlistId = ensureFavoritesPlaylist() ?: return@launch
            val mode = preferences.musicMode.first()
            if (wasFavorite) {
                if (mode == "local") localRepository.removeTrackFromPlaylist(playlistId, track.path)
                else playlistRepository.removeTracks(playlistId, listOf(track.id))
            } else {
                if (mode == "local") localRepository.addTracksToPlaylist(playlistId, listOf(track))
                else playlistRepository.addTracks(playlistId, listOf(track.id))
            }
            refreshPlaylists()
        }
    }

    private suspend fun ensureFavoritesPlaylist(): Int? {
        val mode = preferences.musicMode.first()
        return if (mode == "local") {
            val existing = localRepository.getPlaylists().firstOrNull { it.name == FAVORITES_PLAYLIST_NAME }
            existing?.id ?: localRepository.createPlaylist(FAVORITES_PLAYLIST_NAME, "⭐").id
        } else {
            val playlists = playlistRepository.getPlaylists().getOrNull() ?: return null
            val existing = playlists.firstOrNull { it.name == FAVORITES_PLAYLIST_NAME }
            existing?.id ?: playlistRepository.createPlaylist(FAVORITES_PLAYLIST_NAME, "⭐").getOrNull()?.id
        }
    }

    companion object {
        private const val FAVORITES_PLAYLIST_NAME = "Favorites"
    }
}

package nl.muorg.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.muorg.android.cast.CastManager
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.MetadataUpdateRequest
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.player.PlayerController
import nl.muorg.android.player.PlayerState
import nl.muorg.android.ui.UiMessenger
import nl.muorg.android.ui.friendlyMessage
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
    private val castManager: CastManager,
    private val libraryRepository: LibraryRepository,
    private val uiMessenger: UiMessenger,
) : ViewModel() {

    // Shuffle-all mode: pool of all available tracks to draw random batches from.
    // Empty means shuffle-all is not active.
    private var shuffleAllPool: List<CatalogTrack> = emptyList()
    // Guard: only refill when queue.size reaches this threshold, preventing double-fires.
    private var shuffleAllMinQueueSize = Int.MAX_VALUE

    val isCasting: StateFlow<Boolean> = castManager.isCasting
    val castDeviceName: StateFlow<String?> = castManager.castDeviceName
    val castVolume: StateFlow<Float?> = castManager.castVolume

    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerRemainingMs
        .map { it > 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun buildCastRouteSelector() = castManager.buildRouteSelector()
    fun setCastVolume(value: Float) = castManager.setCastVolume(value)

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

    /**
     * Surface a message on the app-wide Snackbar channel.
     *
     * Every screen that shows a track sheet already wires its `onToast` to
     * this, so actions inside the sheet report through here rather than
     * each call site growing its own callback.
     */
    fun showToast(message: String) = uiMessenger.show(message)

    init {
        viewModelScope.launch {
            ensureFavoritesPlaylist()
            refreshPlaylists()
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
                    // Topping up shuffle-all extends the context, so it lands
                    // behind anything the listener queued rather than ahead.
                    playerController.appendToContext(shuffleAllPool.shuffled().take(20))
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
                // The picker's playlists are already loaded, so pass the
                // playlist itself and save the repository a lookup.
                val playlist = _playlists.value.find { it.id == playlistId }
                if (playlist == null) {
                    uiMessenger.show("Playlist not found")
                    return@launch
                }
                // The server's add_tracks uses a plain INSERT with no
                // uniqueness constraint, so check for duplicates first.
                val check = playlistRepository.checkAddConflict(playlist, listOf(track))
                if (check.alreadyPresentCount > 0) {
                    uiMessenger.show("Already in this playlist")
                    return@launch
                }
                playlistRepository.addTracks(playlist, listOf(track.id))
                    .onFailure { uiMessenger.show(it.friendlyMessage()) }
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

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            if (mode == "local") {
                runCatching { localRepository.createPlaylist(name) }
                refreshPlaylists()
            } else {
                playlistRepository.createPlaylist(name, "🎵")
                    .onFailure { uiMessenger.show(it.friendlyMessage()) }
                refreshPlaylists()
            }
        }
    }

    private suspend fun refreshPlaylists() {
        val mode = preferences.musicMode.first()
        if (mode == "local") {
            _playlists.value = localRepository.getPlaylists()
        } else {
            playlistRepository.getPlaylists().onSuccess { playlists ->
                _playlists.value = playlists
                hydrateFavorites(playlists)
            }
        }
    }

    // Seed the local favorites set from the server's Favorites playlist so the
    // heart icons match what the server holds (e.g. favorited on another device).
    // Only writes on success — a failed read must not wipe the hearts.
    private suspend fun hydrateFavorites(playlists: List<Playlist>) {
        val favorites = playlists.firstOrNull { it.name == FAVORITES_PLAYLIST_NAME } ?: return
        playlistRepository.getTracksFor(favorites).onSuccess { ids ->
            preferences.setFavorites(ids.map { it.toString() }.toSet())
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

    fun startSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerRemainingMs.value > 0L) {
                delay(1000)
                _sleepTimerRemainingMs.value = (_sleepTimerRemainingMs.value - 1000L).coerceAtLeast(0L)
            }
            if (castManager.isCasting.value) {
                if (castManager.castPlaybackState.value.isPlaying) castManager.remotePlayPause()
            } else {
                if (playerController.state.value.isPlaying) playerController.playPause()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = 0L
    }

    fun removeFromQueue(track: CatalogTrack) {
        playerController.removeFromQueue(track)
        // The track returns to the end of the user queue on Undo, not its
        // original index — PlaybackQueue has no insert-at-index.
        uiMessenger.showWithAction("Removed from queue", "Undo") {
            playerController.addTracksToQueue(listOf(track))
        }
    }

    /** Clear-all keeps its one-tap button — nothing stored is destroyed — but offers Undo. */
    fun clearQueueWithUndo() {
        val snapshot = playerState.value.playbackQueue.upNext.map { it.track }
        playerController.clearQueue()
        if (snapshot.isNotEmpty()) {
            uiMessenger.showWithAction("Queue cleared", "Undo") {
                playerController.addTracksToQueue(snapshot)
            }
        }
    }
    fun clearUserQueue() = playerController.clearUserQueue()
    fun reorderQueue(fromIndex: Int, toIndex: Int) = playerController.reorderQueue(fromIndex, toIndex)
    fun addToQueue(track: CatalogTrack) {
        playerController.addToQueue(track)
        uiMessenger.show("Added to queue")
    }
    fun addTracksToQueue(tracks: List<CatalogTrack>) {
        if (tracks.isEmpty()) return
        playerController.addTracksToQueue(tracks)
        uiMessenger.show("Added to queue")
    }
    fun playNext(track: CatalogTrack) {
        playerController.playNext(track)
        uiMessenger.show("Will play next")
    }

    /** Start a context and shuffle it — the playlist screen's shuffle button. */
    fun shuffleTracks(tracks: List<CatalogTrack>) {
        if (tracks.isEmpty()) return
        shuffleAllPool = emptyList()
        shuffleAllMinQueueSize = Int.MAX_VALUE
        playerController.enableShuffle()
        playTrack(tracks.random(), tracks)
    }
    fun saveMetadata(
        track: CatalogTrack,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        genre: String?,
        year: Int?,
    ) {
        viewModelScope.launch {
            val mode = preferences.musicMode.first()
            val update = MetadataUpdateRequest(
                title = title?.takeIf { it != track.title && it.isNotBlank() },
                artist = artist?.takeIf { it != track.artist && it.isNotBlank() },
                album = album?.takeIf { it != track.album && it.isNotBlank() },
                albumArtist = albumArtist?.takeIf { it != track.albumArtist && it.isNotBlank() },
                genre = genre?.takeIf { it != track.genre && it.isNotBlank() },
                year = year?.takeIf { it != track.year },
            )
            if (!update.hasAnyField()) {
                uiMessenger.show("No changes to save")
                return@launch
            }
            if (mode == "local") {
                runCatching {
                    localRepository.updateTrackMetadata(-track.id, update)
                }.onFailure { e ->
                    uiMessenger.show("Failed to save: ${e.friendlyMessage()}")
                }
            } else {
                libraryRepository.patchTrackMetadata(track.id, update).onFailure { e ->
                    uiMessenger.show("Failed to save: ${e.friendlyMessage()}")
                }
            }
        }
    }

    /**
     * Rename a track's file on the server. Pre-flights with [LibraryRepository.checkRename]
     * so a collision is reported before the rename is attempted.
     */
    fun renameTrackFile(track: CatalogTrack, newPath: String) {
        viewModelScope.launch {
            val trimmed = newPath.trim()
            if (trimmed.isBlank() || trimmed == track.path) {
                uiMessenger.show("Enter a new path")
                return@launch
            }
            // Pre-flight: refuse before touching the disk if the destination
            // already exists or another track's row holds it.
            val collisions = libraryRepository.checkRename(track.id, trimmed).getOrNull()
            if (collisions != null && collisions.isNotEmpty()) {
                val reason = collisions.first().reason
                uiMessenger.show(
                    if (reason == "exists") "A file already exists at that path"
                    else "Rename would collide with another track"
                )
                return@launch
            }
            libraryRepository.renameTrackFile(track.id, trimmed).fold(
                onSuccess = { uiMessenger.show("File renamed") },
                onFailure = { e -> uiMessenger.show("Rename failed: ${e.friendlyMessage()}") },
            )
        }
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
                if (mode == "local") {
                    localRepository.addTracksToPlaylist(playlistId, listOf(track))
                    uiMessenger.show("Added to favorites")
                } else {
                    // Defensive: the local favorites set may be out of sync
                    // with the server playlist (added on another device), so
                    // check before adding to avoid a duplicate row. The heart
                    // was just toggled on, so the end state is "favorited"
                    // either way — say "Added", not "Already".
                    val playlist = _playlists.value.find { it.id == playlistId }
                    if (playlist != null) {
                        val check = playlistRepository.checkAddConflict(playlist, listOf(track))
                        if (check.alreadyPresentCount > 0) {
                            uiMessenger.show("Added to favorites")
                            refreshPlaylists()
                            return@launch
                        }
                        playlistRepository.addTracks(playlist, listOf(track.id))
                            .onSuccess { uiMessenger.show("Added to favorites") }
                            .onFailure { uiMessenger.show(it.friendlyMessage()) }
                    } else {
                        playlistRepository.addTracks(playlistId, listOf(track.id))
                            .onSuccess { uiMessenger.show("Added to favorites") }
                            .onFailure { uiMessenger.show(it.friendlyMessage()) }
                    }
                }
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

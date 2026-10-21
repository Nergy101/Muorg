package nl.muorg.android.ui.player

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.MainDispatcherRule
import nl.muorg.android.cast.CastManager
import nl.muorg.android.cast.CastPlaybackState
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.AddConflictCheck
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import nl.muorg.android.player.PlayerController
import nl.muorg.android.player.PlayerState
import nl.muorg.android.ui.UiMessenger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The favorite toggle in server mode.
 *
 * The regression this exists for: pressing Favorite on a track whose heart was
 * empty toasted "Already in favorites". The local favorites set — what the
 * heart renders — is never synced from the server's Favorites playlist, so
 * when the server playlist is ahead (a track favorited on another device or
 * the web client), the toggle takes the "add" branch and the defensive
 * duplicate check reports the track as already present.
 */
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun track(id: Int) = CatalogTrack(
        id = id,
        path = "/m/$id.mp3",
        rootId = 1,
        format = "mp3",
        mtimeSecs = 0,
        hasCover = false,
        playCount = 0,
    )

    private val favoritesPlaylist = Playlist(id = 1, name = "Favorites", icon = "⭐", trackCount = 1)

    private class Fixture(
        val viewModel: PlayerViewModel,
        val uiMessenger: UiMessenger,
        val toasts: MutableList<String>,
        val hydratedFavorites: MutableList<Set<String>>,
    )

    private fun fixture(
        serverFavorites: List<Int>,
        localFavorites: Set<String>,
    ): Fixture {
        val playerState = MutableStateFlow(PlayerState(favorites = localFavorites))
        val playerController = mockk<PlayerController>()
        every { playerController.state } returns playerState
        every { playerController.toggleFavorite(any()) } answers {
            val t = firstArg<CatalogTrack>()
            val id = t.id.toString()
            val current = playerState.value.favorites
            playerState.value = playerState.value.copy(
                favorites = if (id in current) current - id else current + id,
            )
        }

        val playlistRepository = mockk<PlaylistRepository>()
        coEvery { playlistRepository.getPlaylists() } returns Result.success(listOf(favoritesPlaylist))
        coEvery { playlistRepository.getTracksFor(any()) } returns Result.success(serverFavorites)
        coEvery { playlistRepository.checkAddConflict(any(), any()) } answers {
            val tracks = secondArg<List<CatalogTrack>>()
            val newTracks = tracks.filter { it.id !in serverFavorites }
            AddConflictCheck(newTracks = newTracks, alreadyPresentCount = tracks.size - newTracks.size)
        }
        coEvery { playlistRepository.addTracks(any<Playlist>(), any()) } returns Result.success(Unit)
        coEvery { playlistRepository.addTracks(any<Int>(), any()) } returns Result.success(Unit)
        coEvery { playlistRepository.removeTracks(any(), any()) } returns Result.success(Unit)

        val hydratedFavorites = mutableListOf<Set<String>>()
        val preferences = mockk<AppPreferences>()
        every { preferences.musicMode } returns flowOf("remote")
        coEvery { preferences.setFavorites(any()) } answers {
            hydratedFavorites.add(firstArg())
            Unit
        }

        val castManager = mockk<CastManager>()
        every { castManager.isCasting } returns MutableStateFlow(false)
        every { castManager.castDeviceName } returns MutableStateFlow(null)
        every { castManager.castVolume } returns MutableStateFlow(null)
        every { castManager.castPlaybackState } returns MutableStateFlow(CastPlaybackState())
        every { castManager.trackFinished } returns MutableSharedFlow<Unit>()

        val toasts = mutableListOf<String>()
        val uiMessenger = UiMessenger()
        val viewModel = PlayerViewModel(
            playerController,
            playlistRepository,
            mockk<LocalLibraryRepository>(relaxed = true),
            preferences,
            castManager,
            mockk<LibraryRepository>(relaxed = true),
            uiMessenger,
        )
        return Fixture(viewModel, uiMessenger, toasts, hydratedFavorites)
    }

    @Test
    fun `favoriting a track already in the server playlist says Added, not Already`() = runTest {
        // The desync: the track is in the server's Favorites playlist (added
        // on another device) but the local favorites set — what the heart
        // shows — does not have it, so the heart was empty when pressed.
        val f = fixture(serverFavorites = listOf(1), localFavorites = emptySet())
        val collector = launch { f.uiMessenger.messages.collect { f.toasts.add(it.text) } }
        advanceUntilIdle()

        f.viewModel.toggleFavorite(track(1))
        advanceUntilIdle()
        collector.cancelAndJoin()

        assertEquals(listOf("Added to favorites"), f.toasts)
    }

    @Test
    fun `favoriting a new track adds it and says Added`() = runTest {
        val f = fixture(serverFavorites = emptyList(), localFavorites = emptySet())
        val collector = launch { f.uiMessenger.messages.collect { f.toasts.add(it.text) } }
        advanceUntilIdle()

        f.viewModel.toggleFavorite(track(1))
        advanceUntilIdle()
        collector.cancelAndJoin()

        assertEquals(listOf("Added to favorites"), f.toasts)
    }

    @Test
    fun `the local favorites set hydrates from the server playlist`() = runTest {
        val f = fixture(serverFavorites = listOf(1, 2), localFavorites = emptySet())
        advanceUntilIdle()

        assertEquals(setOf("1", "2"), f.hydratedFavorites.lastOrNull())
    }
}

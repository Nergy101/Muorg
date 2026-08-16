package nl.muorg.android.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import javax.inject.Inject
import kotlin.random.Random

/**
 * A mix: an ephemeral, genre-themed playlist. Never written to the server —
 * exactly like the web's `useMixes.ts`, which this is a direct port of, down
 * to the sixteen cohorts and their genre pools.
 */
data class Mix(
    val id: Int,
    val name: String,
    val emoji: String,
    val trackIds: List<Int>,
    val coverTrackIds: List<Int>,
)

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val recommended: List<AlbumGroup> = emptyList(),
    val mixes: List<Mix> = emptyList(),
)

private const val MIX_SIZE = 40
private const val MIX_COUNT = 8
private const val RECOMMENDED_COUNT = 4

private data class MixDef(val name: String, val emoji: String, val genres: List<String>)

private val MIX_DEFS = listOf(
    MixDef("Midnight Drive", "🌙", listOf("Electronic", "Lo-Fi", "Indie", "Instrumental")),
    MixDef("Morning Coffee", "☀️", listOf("Indie Rock", "Indie", "Pop", "Pop Rock")),
    MixDef("Summer Breeze", "🏖️", listOf("Country", "Indie Rock", "Indie", "Pop Rock")),
    MixDef("Gym Fuel", "🔥", listOf("Metalcore", "Hardcore", "Nu Metal")),
    MixDef("Rainy Day", "🌧️", listOf("Emo", "Post-Hardcore", "Alternative")),
    MixDef("Road Trip", "🚗", listOf("Punk Rock", "Pop Punk", "Classic Rock")),
    MixDef("Deep Focus", "💫", listOf("Metal", "Electronic", "Progressive Metal", "Instrumental")),
    MixDef("Party Starter", "🎉", listOf("Nu Metal", "Pop", "Electronic", "Rap", "Punk", "Pop Punk", "Pop Rock", "Indie Rock")),
    MixDef("Mosh Pit", "🤘", listOf("Thrash Metal", "Death Metal", "Metal", "Nu Metal", "Hard Rock", "Metalcore")),
    MixDef("Golden Hour", "🌅", listOf("Alternative Rock", "Indie Rock", "Pop Rock")),
    MixDef("Late Night Lo-Fi", "😴", listOf("Lo-Fi", "Rap", "Instrumental")),
    MixDef("Skatepark", "🛹", listOf("Pop Punk", "Punk Rock", "Hardcore")),
    MixDef("Sunday Chill", "😌", listOf("Alternative Indie", "Indie", "Pop")),
    MixDef("Thunderstorm", "⛈️", listOf("Thrash Metal", "Death Metal", "Hard Rock")),
    MixDef("Sing-Along", "🎤", listOf("Pop", "Pop Rock", "Pop Punk", "Indie", "Indie Rock")),
    MixDef("Power Hour", "⚡", listOf("Nu Metal", "Hard Rock", "Metalcore")),
)

/** "Lo-Fi", "Lofi" and "Lo Fi" all normalise to "lofi". */
private fun normalizeGenre(g: String) = g.lowercase().filter { it.isLetterOrDigit() }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var tracks: List<CatalogTrack> = emptyList()
    private var albums: List<AlbumGroup> = emptyList()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val local = preferences.musicMode.first() == "local"
            val result = if (local) {
                runCatching { localRepository.getAllTracks() to localRepository.buildAlbumGroups() }
            } else {
                repository.getAllTracks().map { it to repository.buildAlbumGroups(it) }
            }
            result.fold(
                onSuccess = { (t, a) ->
                    tracks = t
                    albums = a
                    _uiState.update {
                        it.copy(
                            loading = false,
                            recommended = pickRecommended(),
                            mixes = if (it.mixes.isEmpty()) rollMixes() else it.mixes,
                        )
                    }
                },
                onFailure = { e -> _uiState.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun refreshRecommended() {
        _uiState.update { it.copy(recommended = pickRecommended()) }
    }

    fun refreshMixes() {
        _uiState.update { it.copy(mixes = rollMixes()) }
    }

    fun tracksForMix(mix: Mix): List<CatalogTrack> {
        val byId = tracks.associateBy { it.id }
        return mix.trackIds.mapNotNull { byId[it] }
    }

    private fun pickRecommended(): List<AlbumGroup> =
        albums.shuffled().take(RECOMMENDED_COUNT)

    /**
     * Eight of the sixteen cohorts, each sampled from its own genre pool only.
     * A small pool just makes a shorter mix; it is never padded with unrelated
     * genres. Matching is lenient in both directions so "Metal" catches
     * "Progressive Metal" and "Lo-Fi" catches "Lofi".
     */
    private fun rollMixes(): List<Mix> {
        if (tracks.isEmpty()) return emptyList()
        return MIX_DEFS.shuffled().take(MIX_COUNT).mapIndexedNotNull { index, def ->
            val wanted = def.genres.map(::normalizeGenre).filter { it.isNotEmpty() }
            val pool = tracks.filter { track ->
                val g = track.genre?.let(::normalizeGenre) ?: return@filter false
                wanted.any { w -> g.contains(w) || w.contains(g) }
            }
            if (pool.isEmpty()) return@mapIndexedNotNull null
            val picked = pool.shuffled(Random(def.name.hashCode() + System.identityHashCode(tracks)))
                .take(MIX_SIZE)
            Mix(
                id = index,
                name = def.name,
                emoji = def.emoji,
                trackIds = picked.map { it.id },
                coverTrackIds = picked.filter { it.hasCover }
                    .distinctBy { it.album ?: it.path }
                    .take(4)
                    .map { it.id },
            )
        }
    }
}

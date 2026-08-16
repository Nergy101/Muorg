package nl.muorg.android.data.repository

import nl.muorg.android.data.api.CatalogTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * A mix: an ephemeral, genre-themed playlist. Never written to the server —
 * a direct port of the web's `useMixes.ts`, down to the sixteen cohorts and
 * their genre pools.
 */
data class Mix(
    val id: Int,
    val name: String,
    val emoji: String,
    val trackIds: List<Int>,
    val coverTrackIds: List<Int>,
)

private const val MIX_SIZE = 40
private const val MIX_COUNT = 8

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

/**
 * Session-scoped so Home and the mix detail screen see the SAME eight mixes:
 * a mix is generated, not stored, so if each screen rolled its own the list
 * you tapped and the list you landed on would be different.
 */
@Singleton
class MixRepository @Inject constructor() {

    private var mixes: List<Mix> = emptyList()
    private var tracksById: Map<Int, CatalogTrack> = emptyMap()

    fun current(): List<Mix> = mixes

    fun byId(id: Int): Mix? = mixes.firstOrNull { it.id == id }

    fun tracksFor(mix: Mix): List<CatalogTrack> = mix.trackIds.mapNotNull { tracksById[it] }

    /** Rolls a fresh lineup. Called on first load and by the shelf's refresh. */
    fun roll(tracks: List<CatalogTrack>): List<Mix> {
        tracksById = tracks.associateBy { it.id }
        if (tracks.isEmpty()) {
            mixes = emptyList()
            return mixes
        }
        mixes = MIX_DEFS.shuffled().take(MIX_COUNT).mapIndexedNotNull { index, def ->
            val wanted = def.genres.map(::normalizeGenre).filter { it.isNotEmpty() }
            val pool = tracks.filter { track ->
                val g = track.genre?.let(::normalizeGenre) ?: return@filter false
                wanted.any { w -> g.contains(w) || w.contains(g) }
            }
            if (pool.isEmpty()) return@mapIndexedNotNull null
            val picked = pool.shuffled(Random(def.name.hashCode() + tracks.size)).take(MIX_SIZE)
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
        return mixes
    }

    /** Keeps the lineup but re-resolves ids after a catalog reload. */
    fun rebind(tracks: List<CatalogTrack>) {
        tracksById = tracks.associateBy { it.id }
        if (mixes.isEmpty()) roll(tracks)
    }
}

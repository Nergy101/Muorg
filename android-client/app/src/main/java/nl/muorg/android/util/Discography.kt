package nl.muorg.android.util

import nl.muorg.android.data.api.CatalogTrack

/**
 * An artist's discography, built on the device from the catalog.
 *
 * Muorg has no release-type tag, so "album / EP / single" is inferred the way
 * the streaming services do it (Spotify's published rule), with an explicit
 * "EP" / "Single" in the title winning over the counts:
 *
 * - 7+ tracks, or more than 30 minutes → album
 * - 4–6 tracks → EP
 * - 1–3 tracks → single, or an EP when one of them runs 10 minutes or more
 *
 * The counts are what the library holds, not what the release has: an album
 * with only two of its tracks present reads as a single.
 *
 * Mirrors the web client's `web-client/src/discography.ts`; the one
 * difference is the album identity, which here is the album name alone, as
 * everywhere else in the app (see `AlbumGroup` / the album detail route).
 */
object Discography {

    enum class Kind { ALBUM, EP, SINGLE }

    /** One release: the whole album as the library holds it, not just the
     *  tracks that credit the artist. */
    data class Release(
        val albumName: String,
        val artist: String,
        val year: Int?,
        val tracks: List<CatalogTrack>,
    )

    data class Result(
        /** Every track that credits the artist (artist, album artist or featuring). */
        val tracks: List<CatalogTrack>,
        val albums: List<Release>,
        val eps: List<Release>,
        val singles: List<Release>,
        /** Releases by someone else (compilations, other artists' albums) the artist is on. */
        val appearsOn: List<Release>,
        /** Credited tracks with no album tag at all. */
        val looseTracks: List<CatalogTrack>,
    ) {
        val isEmpty: Boolean get() = tracks.isEmpty()
    }

    private const val ALBUM_MIN_TRACKS = 7
    private const val EP_MIN_TRACKS = 4
    private const val ALBUM_MIN_SECS = 30 * 60
    private const val LONG_TRACK_SECS = 10 * 60

    /** "Night Drive EP", "Remixes (EP)", "EP2", "Volume EP 3". */
    private val EP_TITLE = Regex("""\bEP\s?\d*\b""", RegexOption.IGNORE_CASE)

    /** "Track - Single", "Track (Single)" — only at the end, so "Single Ladies" stays an album. */
    private val SINGLE_TITLE = Regex("""(?:^|[\s(\[-])single\s*[)\]]?\s*$""", RegexOption.IGNORE_CASE)

    private val CREDIT_SEPARATORS = Regex("""\s*(?:,|;|&|/|\band\b|\bfeat\.?|\bft\.?)\s*""", RegexOption.IGNORE_CASE)

    /** Placeholder credits that must never become an artist page. */
    private val PLACEHOLDER_ARTISTS = setOf("", "—", "unknown artist", "various artists")

    fun normalize(name: String?): String = name.orEmpty().trim().lowercase()

    /** Whether a credit names a real artist worth opening a page for. */
    fun isLinkable(name: String?): Boolean = normalize(name) !in PLACEHOLDER_ARTISTS

    /** The names in a `featuring` tag: "A, B & C", "A; B", "A / B", "A and B". */
    fun splitCredits(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else value.split(CREDIT_SEPARATORS).map { normalize(it) }.filter { it.isNotEmpty() }

    /** Whether [track] credits the (already normalized) artist [norm]. */
    fun credits(track: CatalogTrack, norm: String): Boolean =
        norm.isNotEmpty() && (
            normalize(track.artist) == norm ||
                normalize(track.albumArtist) == norm ||
                norm in splitCredits(track.featuring)
            )

    fun classify(title: String, durationsSecs: List<Int>): Kind {
        if (SINGLE_TITLE.containsMatchIn(title)) return Kind.SINGLE
        if (EP_TITLE.containsMatchIn(title)) return Kind.EP
        val n = durationsSecs.size
        val total = durationsSecs.sum()
        if (n >= ALBUM_MIN_TRACKS || total > ALBUM_MIN_SECS) return Kind.ALBUM
        if (n >= EP_MIN_TRACKS) return Kind.EP
        return if (durationsSecs.any { it >= LONG_TRACK_SECS }) Kind.EP else Kind.SINGLE
    }

    /** The release's artist: the album-artist tag, else the one track
     *  artist, else "Various Artists" (as the web groups albums). */
    fun releaseArtist(tracks: List<CatalogTrack>): String {
        tracks.firstNotNullOfOrNull { it.albumArtist?.takeIf(String::isNotBlank) }?.let { return it }
        val artists = tracks.mapNotNull { it.artist?.takeIf(String::isNotBlank) }
        val distinct = artists.distinctBy { it.lowercase() }
        return when (distinct.size) {
            0 -> "Unknown Artist"
            1 -> distinct.first()
            else -> "Various Artists"
        }
    }

    private val trackOrder = compareBy<CatalogTrack>({ it.discNumber ?: 1 }, { it.trackNumber ?: 0 })

    /** Newest first; undated releases last, then by title. */
    private val releaseOrder = compareBy<Release>(
        { it.year == null },
        { -(it.year ?: 0) },
        { it.albumName.lowercase() },
    )

    fun build(artist: String, allTracks: List<CatalogTrack>): Result {
        val norm = normalize(artist)
        val tracks = allTracks.filter { credits(it, norm) }
        val (withAlbum, loose) = tracks.partition { !it.album.isNullOrBlank() }

        val names = withAlbum.map { it.displayAlbum }.toSet()
        val byAlbum = allTracks.filter { it.displayAlbum in names }.groupBy { it.displayAlbum }

        val albums = mutableListOf<Release>()
        val eps = mutableListOf<Release>()
        val singles = mutableListOf<Release>()
        val appearsOn = mutableListOf<Release>()
        for ((name, releaseTracks) in byAlbum) {
            val sorted = releaseTracks.sortedWith(trackOrder)
            val release = Release(
                albumName = name,
                artist = releaseArtist(sorted),
                year = sorted.mapNotNull { it.year }.minOrNull(),
                tracks = sorted,
            )
            if (normalize(release.artist) != norm) {
                appearsOn += release
                continue
            }
            when (classify(name, sorted.map { it.durationSecs?.toInt() ?: 0 })) {
                Kind.ALBUM -> albums += release
                Kind.EP -> eps += release
                Kind.SINGLE -> singles += release
            }
        }
        return Result(
            tracks = tracks,
            albums = albums.sortedWith(releaseOrder),
            eps = eps.sortedWith(releaseOrder),
            singles = singles.sortedWith(releaseOrder),
            appearsOn = appearsOn.sortedWith(releaseOrder),
            looseTracks = loose,
        )
    }

    /**
     * "Play artist": the artist's credited tracks release by release in
     * section order (track order within each), then the loose tracks.
     */
    fun playOrder(result: Result, artist: String): List<CatalogTrack> {
        val norm = normalize(artist)
        return (result.albums + result.eps + result.singles + result.appearsOn)
            .flatMap { release -> release.tracks.filter { credits(it, norm) } } + result.looseTracks
    }
}

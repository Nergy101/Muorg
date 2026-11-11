package nl.muorg.android.ui.easteregg

import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import java.text.NumberFormat
import java.util.concurrent.TimeUnit

/** One dust-report line: the stat and the Janitor's quip under it. */
data class DustStat(val stat: String, val quip: String)

/** Everything the dust report can draw on. Zero/empty means "no data". */
data class DustInput(
    val trackCount: Int = 0,
    val totalDurationSecs: Long = 0,
    /** Playlist name to track count. */
    val playlists: List<Pair<String, Int>> = emptyList(),
    val neverPlayedCount: Int = 0,
    val topArtistThisMonth: String? = null,
    val addedThisWeek: Int = 0,
)

/**
 * Builds the Janitor's dust report from real library data only. Templates
 * whose data is missing or zero are skipped; if nothing is available at all
 * the fallback line is returned. Pure so it can be unit-tested.
 */
object DustReportBuilder {

    fun build(input: DustInput): List<DustStat> {
        val stats = mutableListOf<DustStat>()
        if (input.trackCount > 0) {
            stats += DustStat(
                stat = "You've got ${fmt(input.trackCount)} ${plural(input.trackCount, "track", "tracks")}.",
                quip = "That's ${durationText(input.totalDurationSecs)} of music. I counted. Twice.",
            )
        }
        input.playlists.maxByOrNull { it.second }?.let { (name, count) ->
            if (count > 0) {
                stats += DustStat(
                    stat = "“$name” is your biggest playlist: ${fmt(count)} ${plural(count, "track", "tracks")}.",
                    quip = "Do you ever sleep? Asking for a friend. The friend is me.",
                )
            }
        }
        if (input.neverPlayedCount > 0) {
            stats += DustStat(
                stat = "${fmt(input.neverPlayedCount)} ${plural(input.neverPlayedCount, "track has", "tracks have")} never been played.",
                quip = "They're lonely in there. I can hear them.",
            )
        }
        input.playlists.filter { it.second in 1..5 }.minByOrNull { it.second }?.let { (name, count) ->
            stats += DustStat(
                stat = "“$name” has only $count ${plural(count, "track", "tracks")}.",
                quip = "Bold. Minimalist. Slightly sad.",
            )
        }
        if (!input.topArtistThisMonth.isNullOrBlank()) {
            stats += DustStat(
                stat = "Most played this month: ${input.topArtistThisMonth}.",
                quip = "Good taste. For a human.",
            )
        }
        if (input.addedThisWeek > 0) {
            stats += DustStat(
                stat = "You added ${fmt(input.addedThisWeek)} ${plural(input.addedThisWeek, "track", "tracks")} this week.",
                quip = "More snacks. Excellent.",
            )
        }
        if (stats.isEmpty()) {
            stats += DustStat(stat = "Nothing to report.", quip = "Just dust. Lots of dust.")
        }
        return stats
    }

    /** Maps the raw catalog and playlist list onto [DustInput]. */
    fun inputFrom(tracks: List<CatalogTrack>, playlists: List<Playlist>, nowSecs: Long = System.currentTimeMillis() / 1000): DustInput {
        val monthAgo = nowSecs - TimeUnit.DAYS.toSeconds(30)
        val weekAgo = nowSecs - TimeUnit.DAYS.toSeconds(7)
        val topArtist = tracks
            .filter { (it.lastPlayedAt ?: 0) >= monthAgo }
            .groupingBy { it.displayArtist }
            .fold(0) { acc, track -> acc + track.playCount }
            .maxByOrNull { it.value }?.key
        return DustInput(
            trackCount = tracks.size,
            totalDurationSecs = tracks.sumOf { (it.durationSecs ?: 0.0).toLong() },
            playlists = playlists.map { it.name to it.trackCount },
            neverPlayedCount = tracks.count { it.playCount == 0 },
            topArtistThisMonth = topArtist,
            addedThisWeek = tracks.count { it.mtimeSecs >= weekAgo },
        )
    }

    private fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    private fun plural(n: Int, one: String, many: String): String = if (n == 1) one else many

    /** "X days and Y hours" with proper plurals; under an hour, "Y minutes". */
    private fun durationText(totalSecs: Long): String {
        if (totalSecs <= 0) return "0 minutes"
        val days = totalSecs / TimeUnit.DAYS.toSeconds(1)
        val hours = (totalSecs % TimeUnit.DAYS.toSeconds(1)) / TimeUnit.HOURS.toSeconds(1)
        val minutes = (totalSecs % TimeUnit.HOURS.toSeconds(1)) / 60
        return when {
            days > 0 -> "${plural(days.toInt(), "1 day", "$days days")} and ${plural(hours.toInt(), "1 hour", "$hours hours")}"
            hours > 0 -> "${plural(hours.toInt(), "1 hour", "$hours hours")} and ${plural(minutes.toInt(), "1 minute", "$minutes minutes")}"
            else -> plural(minutes.toInt(), "1 minute", "$minutes minutes")
        }
    }
}

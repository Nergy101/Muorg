package nl.muorg.android.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.theme.MuorgShapes
import nl.muorg.android.util.LibraryReports

/** Red → orange → green, like a score. Green is plain green, not the theme primary. */
private fun scoreColor(fraction: Float): Color = when {
    fraction >= 0.999f -> Color(0xFF4CAF50)
    fraction >= 0.75f -> Color(0xFF8BC34A)
    fraction >= 0.5f -> Color(0xFFFF9800)
    fraction >= 0.25f -> Color(0xFFFF5722)
    else -> Color(0xFFF44336)
}

private fun formatTotalTime(totalSecs: Double): String {
    val secs = totalSecs.toLong()
    val hours = secs / 3600
    val minutes = (secs % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BarRow(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), MuorgShapes.pill)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(color, MuorgShapes.pill)
            )
        }
    }
}

/**
 * Library statistics, matching the web client's Stats view.
 *
 * Reached from Settings → Library tools → Statistics.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tracks = state.tracks

    val totalArtists = remember(tracks) { tracks.map { it.displayArtist }.distinct().size }
    val totalAlbums = remember(tracks) { tracks.map { it.displayAlbum }.distinct().size }
    val totalTime = remember(tracks) { tracks.sumOf { it.durationSecs ?: 0.0 } }

    val genreCounts = remember(tracks) {
        tracks.groupingBy { it.genre?.takeIf(String::isNotBlank) ?: "Unknown" }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    val artistCounts = remember(tracks) {
        tracks.groupingBy { it.displayArtist }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    val yearCounts = remember(tracks) {
        tracks.groupingBy { it.year?.toString() ?: "Unknown" }
            .eachCount()
            .toList()
            .sortedWith(compareBy({ it.first == "Unknown" }, { it.first }))
    }
    val ratingCounts = remember(tracks) {
        (1..5).map { rating -> rating to tracks.count { it.rating == rating } }
    }
    val formatCounts = remember(tracks) {
        tracks.groupingBy { it.format.uppercase() }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    // Bin track lengths into at most ~12 bars: per-minute bins for short
    // libraries, wider ranges once the span would crowd the axis with
    // single-number labels.
    val durationBuckets: Pair<List<Pair<Int, Int>>, Int> = remember(tracks) {
        val durations = tracks.mapNotNull { it.durationSecs?.toInt() }
        if (durations.isEmpty()) return@remember emptyList<Pair<Int, Int>>() to 1
        val maxMinute = durations.max() / 60
        var bin = 1
        while (maxMinute / bin + 1 > 12) bin++
        durations
            .groupingBy { (it / 60) / bin * bin }
            .eachCount()
            .toList()
            .sortedBy { it.first }
            .let { buckets -> buckets to bin }
    }
    val durationBinWidth = durationBuckets.second
    val durationBars = durationBuckets.first.map { (start, count) ->
        val label = if (durationBinWidth == 1) "$start" else "$start-${start + durationBinWidth - 1}"
        label to count
    }
    val mostPlayed = remember(tracks) {
        tracks.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(mageIconRes("chevron-left")),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${tracks.size} track${if (tracks.size == 1) "" else "s"} in the catalog",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.load() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(mageIconRes("refresh")),
                    contentDescription = "Refresh statistics",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        when {
            state.isLoading && tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp),
            ) {
                item(key = "totals") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatCard("Tracks", tracks.size.toString(), Modifier.weight(1f))
                        StatCard("Artists", totalArtists.toString(), Modifier.weight(1f))
                        StatCard("Albums", totalAlbums.toString(), Modifier.weight(1f))
                        StatCard("Total time", formatTotalTime(totalTime), Modifier.weight(1f))
                    }
                }

                item(key = "metadata-health-title") { SectionTitle("Metadata health") }
                items(
                    LibraryReports.Field.entries.toList(),
                    key = { "health-${it.name}" },
                ) { field ->
                    val fraction = viewModel.fieldHealth(field, tracks)
                    BarRow(
                        label = field.label,
                        value = "${(fraction * 100).toInt()}%",
                        fraction = fraction,
                        color = scoreColor(fraction),
                    )
                }

                item(key = "genres-title") { SectionTitle("Genres") }
                item(key = "genres-chart") {
                    ChartCard { DonutChart(genreCounts, centerLabel = "${tracks.size} tracks") }
                }

                item(key = "artists-title") { SectionTitle("Top artists") }
                items(
                    artistCounts.take(8),
                    key = { "artist-${it.first}" },
                ) { (artist, count) ->
                    BarRow(
                        label = artist,
                        value = count.toString(),
                        fraction = count.toFloat() / (artistCounts.firstOrNull()?.second ?: 1),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                item(key = "years-title") { SectionTitle("Tracks per year") }
                item(key = "years-chart") {
                    ChartCard { LineChart(yearCounts) }
                }

                item(key = "ratings-title") { SectionTitle("Ratings") }
                item(key = "ratings-chart") {
                    ChartCard {
                        BarChart(ratingCounts.map { (rating, count) -> "$rating★" to count })
                    }
                }

                item(key = "formats-title") { SectionTitle("Formats") }
                item(key = "formats-chart") {
                    ChartCard { DonutChart(formatCounts, centerLabel = "Formats") }
                }

                item(key = "duration-title") {
                    SectionTitle(if (durationBinWidth == 1) "Track length (per minute)" else "Track length (minutes)")
                }
                item(key = "duration-chart") {
                    ChartCard { BarChart(durationBars) }
                }

                if (mostPlayed.isNotEmpty()) {
                    item(key = "most-played-title") { SectionTitle("Most played") }
                    items(
                        items = mostPlayed,
                        key = { "played-${it.id}" },
                    ) { track ->
                        MostPlayedRow(track)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MuorgShapes.card)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MuorgShapes.card)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MostPlayedRow(track: CatalogTrack) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = track.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${track.playCount}×",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

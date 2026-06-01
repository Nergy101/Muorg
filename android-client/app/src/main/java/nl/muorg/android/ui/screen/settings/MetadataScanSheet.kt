package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.MetadataUpdateRequest
import nl.muorg.android.util.PathPatternMatcher

private data class Example(val label: String, val value: String)

private val PATTERN_EXAMPLES = listOf(
    Example("Artist/Album/N - Title.ext",           "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>"),
    Example("Artist/Year - Album/N - Title.ext",    "<Artist>/Albums/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>"),
    Example("Artist/Album/D-N Title.ext",           "<AlbumArtist>/<Album>/<DiscNumber>-<TrackNumber> <TrackTitle>.<Format>"),
    Example("Genre/Artist/Year - Album/N.ext",      "<Genre>/<Artist>/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>"),
    Example("Artist - Album/N - Title.ext",         "<Artist> - <Album>/<TrackNumber> - <TrackTitle>.<Format>"),
    Example("Artist/Year - Album/N. Title.ext",     "<AlbumArtist>/<Year> - <Album>/<TrackNumber>. <TrackTitle>.<Format>"),
    Example("Artist/Albums/AlbumName/N - Title.ext","<Artist>/Albums/<Albumname>/<TrackNumber> - <TrackTitle>.<Format>"),
    Example("Artist/Singles/Title.ext",             "<Artist>/Singles/<TrackTitle>.<Format>"),
)

private val PATH_EXAMPLES = listOf(
    Example("Daft Punk/Discovery/01 - One More Time.mp3",               "/music/Daft Punk/Discovery/01 - One More Time.mp3"),
    Example("Pink Floyd/1973 - Dark Side/01 - Speak to Me.flac",        "/music/Pink Floyd/1973 - The Dark Side of the Moon/01 - Speak to Me.flac"),
    Example("The Beatles/Abbey Road/1-01 Come Together.flac",           "/music/The Beatles/Abbey Road/1-01 Come Together.flac"),
    Example("Electronic/Daft Punk/Discovery/01 - One More Time.mp3",    "/music/Electronic/Daft Punk/Discovery/01 - One More Time.mp3"),
    Example("Daft Punk - Discovery/01 - One More Time.mp3",             "/music/Daft Punk - Discovery/01 - One More Time.mp3"),
    Example("Radiohead/OK Computer/01. Airbag.flac",                    "/music/Radiohead/OK Computer/01. Airbag.flac"),
    Example("sdcard/Music/Daft Punk/01 - One More Time.mp3",            "/sdcard/Music/Daft Punk/Discovery/01 - One More Time.mp3"),
    Example("storage/emulated/0/Music/Pink Floyd/01 - Speak to Me.flac", "/storage/emulated/0/Music/Pink Floyd/1973 - The Dark Side of the Moon/01 - Speak to Me.flac"),
    Example("Slipknot/Albums/Iowa/01 - (sic).mp3",                      "/music/Slipknot/Albums/Iowa/01 - (sic).mp3"),
    Example("Slipknot/Singles/Duality.mp3",                             "/music/Slipknot/Singles/Duality.mp3"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScanSheet(
    onDismiss: () -> Unit,
    viewModel: MetadataScanViewModel = hiltViewModel(),
) {
    val pattern by viewModel.pattern.collectAsStateWithLifecycle()
    val examplePath by viewModel.examplePath.collectAsStateWithLifecycle()
    val tracksLoading by viewModel.tracksLoading.collectAsStateWithLifecycle()
    val matchSummary by viewModel.matchSummary.collectAsStateWithLifecycle()
    val examplePreview by viewModel.examplePreview.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val isScanning = scanState is ScanState.Scanning
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isScanning) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        when (val state = scanState) {
            is ScanState.Idle -> IdleContent(
                pattern = pattern,
                examplePath = examplePath,
                tracksLoading = tracksLoading,
                filesMatched = matchSummary.filesMatched,
                willUpdateCount = matchSummary.matchResults.size,
                matchPreview = matchSummary.matchResults.take(5),
                examplePreview = examplePreview,
                onPatternChange = viewModel::setPattern,
                onExamplePathChange = viewModel::setExamplePath,
                onScan = viewModel::runScan,
            )
            is ScanState.Scanning -> ScanningContent(done = state.done, total = state.total)
            is ScanState.Done -> ResultContent(
                message = "Successfully updated ${state.count} track${if (state.count == 1) "" else "s"}.",
                isError = false,
                onClose = { viewModel.resetScan(); onDismiss() },
            )
            is ScanState.Error -> ResultContent(
                message = state.message,
                isError = true,
                onClose = { viewModel.resetScan(); onDismiss() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleContent(
    pattern: String,
    examplePath: String,
    tracksLoading: Boolean,
    filesMatched: Int,
    willUpdateCount: Int,
    matchPreview: List<Pair<CatalogTrack, MetadataUpdateRequest>>,
    examplePreview: Map<String, String>?,
    onPatternChange: (String) -> Unit,
    onExamplePathChange: (String) -> Unit,
    onScan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ManageSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Metadata scan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Extract metadata from file paths and write it to your library.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Text("Pattern", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = pattern,
            onValueChange = onPatternChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PATTERN_EXAMPLES.forEach { example ->
                SuggestionChip(
                    onClick = { onPatternChange(example.value) },
                    label = {
                        Text(
                            example.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Example track path", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = examplePath,
            onValueChange = onExamplePathChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/music/Artist/Album/01 - Title.flac") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PATH_EXAMPLES.forEach { example ->
                SuggestionChip(
                    onClick = { onExamplePathChange(example.value) },
                    label = {
                        Text(
                            example.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                )
            }
        }

        if (pattern.isNotBlank() && examplePath.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            PreviewBox(preview = examplePreview)
        }

        Spacer(Modifier.height(20.dp))

        if (tracksLoading) {
            Text(
                "Loading library…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val patternBlank = pattern.isBlank()
            Text(
                if (patternBlank) "Enter a pattern to see matches" else "$filesMatched files matched",
                style = MaterialTheme.typography.bodySmall,
                color = if (patternBlank) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (!patternBlank) {
                Text(
                    "Metadata will be set for $willUpdateCount track${if (willUpdateCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (matchPreview.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MatchPreviewList(matches = matchPreview, hasMore = willUpdateCount > matchPreview.size)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onScan,
            enabled = !tracksLoading && willUpdateCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Update metadata")
        }
    }
}

@Composable
private fun PreviewBox(preview: Map<String, String>?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (preview.isNullOrEmpty()) {
            Text(
                "No match",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                preview.entries.forEachIndexed { i, (key, value) ->
                    if (i > 0) Spacer(Modifier.height(2.dp))
                    Row {
                        Text(
                            PathPatternMatcher.fieldDisplayName(key),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(90.dp),
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchPreviewList(
    matches: List<Pair<CatalogTrack, MetadataUpdateRequest>>,
    hasMore: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            matches.forEachIndexed { index, (track, update) ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                val filename = track.path.substringAfterLast('/')
                Text(
                    filename,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val parts = buildList {
                    update.artist?.let { add("Artist: $it") }
                    update.albumArtist?.let { add("Album Artist: $it") }
                    update.album?.let { add("Album: $it") }
                    update.year?.let { add("Year: $it") }
                    update.trackNumber?.let { add("Track: $it") }
                    update.discNumber?.let { add("Disc: $it") }
                    update.title?.let { add("Title: $it") }
                    update.genre?.let { add("Genre: $it") }
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasMore) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "…and more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScanningContent(done: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Updating metadata…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$done / $total tracks updated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResultContent(
    message: String,
    isError: Boolean,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isError) "Scan finished with errors" else "Done",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onClose) {
            Text("Close")
        }
    }
}

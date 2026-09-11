package nl.muorg.android.ui.screen.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.TrackRow
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.util.LibraryReports

/**
 * One report as a track list.
 *
 * Duplicates render grouped rather than flat: seeing the copies of a recording
 * together is the point of that report, and a flat list of 40 rows hides which
 * four of them are the same song.
 */
@Composable
fun ReportDetailScreen(
    kind: LibraryReports.Kind,
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentId = playerState.currentTrack?.id

    val tracks = remember(state.tracks, kind) { LibraryReports.run(kind, state.tracks) }
    val groups = remember(state.tracks) { LibraryReports.duplicateGroups(state.tracks) }

    val subtitle = remember(state.tracks, kind, tracks, groups) {
        if (kind == LibraryReports.Kind.DUPLICATES) {
            val extra = LibraryReports.duplicateCount(state.tracks)
            "$extra redundant ${if (extra == 1) "copy" else "copies"} across ${groups.size} " +
                if (groups.size == 1) "recording" else "recordings"
        } else {
            "${tracks.size} track${if (tracks.size == 1) "" else "s"}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
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
                    text = kind.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tracks.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { tracks.firstOrNull()?.let { playerViewModel.playTrack(it, tracks) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(mageIconRes("play")),
                        contentDescription = "Play these tracks",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.tracks.isEmpty()) "The catalog is still empty."
                    else "Nothing to report here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp),
        ) {
            if (kind == LibraryReports.Kind.DUPLICATES) {
                groups.forEachIndexed { index, group ->
                    item(key = "group-$index") {
                        Text(
                            text = "${group.size} copies",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(
                        count = group.size,
                        key = { i -> group[i].id },
                    ) { i ->
                        ReportTrackRow(group[i], group, currentId, playerViewModel, imageLoader, baseUrl)
                    }
                }
            } else {
                items(count = tracks.size, key = { i -> tracks[i].id }) { i ->
                    ReportTrackRow(tracks[i], tracks, currentId, playerViewModel, imageLoader, baseUrl)
                }
            }
        }
    }
}

@Composable
private fun ReportTrackRow(
    track: CatalogTrack,
    queue: List<CatalogTrack>,
    currentId: Int?,
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
) {
    TrackRow(
        track = track,
        baseUrl = baseUrl,
        imageLoader = imageLoader,
        isPlaying = track.id == currentId,
        onTrackClick = { playerViewModel.playTrack(track, queue) },
    )
    Spacer(Modifier.width(0.dp))
}

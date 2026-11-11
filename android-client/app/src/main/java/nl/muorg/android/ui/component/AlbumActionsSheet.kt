package nl.muorg.android.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.ui.icon.MageIcon

internal fun albumDownloadKey(album: AlbumGroup) = "${album.albumName}|${album.artist}"

/**
 * Long-press sheet for an album: cover and title with play and shuffle in the
 * title row, then queue, playlist, artist and the offline download toggle.
 * Actions the caller does not supply are left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumActionsSheet(
    album: AlbumGroup,
    baseUrl: String,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onPlayNow: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: () -> Unit,
    onViewArtist: (() -> Unit)?,
    onViewAlbum: (() -> Unit)? = null,
    viewModel: AlbumActionsViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloadedIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()

    LaunchedEffect(album.albumName) { viewModel.load(album.albumName) }

    val key = albumDownloadKey(album)
    val downloading = progress.isActive && progress.albumKey == key
    val ringProgress = if (downloading && progress.totalTracks > 0) {
        progress.completedTracks.toFloat() / progress.totalTracks
    } else null
    val downloaded = tracks.isNotEmpty() && tracks.all { it.id in downloadedIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = album.coverArtUri ?: "$baseUrl/api/tracks/${album.coverTrackId}/cover?size=1200",
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    album.albumName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MarqueeText(
                    "${album.artist} · ${album.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            PlayShuffleButtons(
                onPlay = onPlayNow?.let { { it(); onDismiss() } },
                onShuffle = onShuffle?.let { { it(); onDismiss() } },
            )
        }
        HorizontalDivider()

        if (onAddToQueue != null) {
            SheetActionRow("Add to queue", onClick = { onAddToQueue(); onDismiss() }) {
                MageIcon("stack", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        SheetActionRow("Add to playlist", onClick = onAddToPlaylist) {
            MageIcon("playlist-add", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        if (canDownload) {
            SheetActionRow(
                title = when {
                    downloading -> "Downloading…"
                    downloaded -> "Remove offline download"
                    else -> "Download for offline"
                },
                subtitle = if (downloading) "${progress.completedTracks} of ${progress.totalTracks} tracks" else null,
                onClick = {
                    when {
                        downloading -> Unit
                        downloaded -> viewModel.removeDownload()
                        else -> viewModel.download(key)
                    }
                },
            ) {
                DownloadStatusIcon(downloaded = downloaded, progress = ringProgress)
            }
        }
        if (onViewArtist != null || onViewAlbum != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        if (onViewArtist != null) {
            SheetActionRow(
                "View artist",
                subtitle = album.artist,
                onClick = { onViewArtist(); onDismiss() },
            ) {
                MageIcon("user", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        if (onViewAlbum != null) {
            SheetActionRow(
                "View album",
                subtitle = album.albumName,
                onClick = { onViewAlbum(); onDismiss() },
            ) {
                MageIcon("compact-disk", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

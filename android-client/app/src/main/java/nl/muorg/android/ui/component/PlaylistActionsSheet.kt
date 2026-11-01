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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.repository.DownloadProgress
import nl.muorg.android.ui.icon.MageIcon

/**
 * Long-press sheet for a playlist: a four-cover collage and title with play
 * and shuffle in the title row, then pin, rename, the offline download toggle
 * (with a progress ring while it runs) and delete. Actions the caller passes
 * as null are left out, which is how offline and local modes hide the
 * server-only ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionsSheet(
    playlist: Playlist,
    /** Most common release year among the playlist's tracks, if any. */
    year: Int? = null,
    covers: List<Any>,
    imageLoader: ImageLoader,
    pinned: Boolean,
    downloaded: Boolean,
    downloadProgress: DownloadProgress,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onTogglePin: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloading = downloadProgress.isActive && downloadProgress.playlistId == playlist.id
    val ringProgress = if (downloading && downloadProgress.totalTracks > 0) {
        downloadProgress.completedTracks.toFloat() / downloadProgress.totalTracks
    } else null

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
            CoverMosaic(
                covers = covers,
                imageLoader = imageLoader,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            ) {
                Text(
                    text = playlist.icon ?: "🎵",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    playlist.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MarqueeText(
                    buildString {
                        if (year != null) append(year).append(" · ")
                        append(playlist.trackCount).append(if (playlist.trackCount == 1) " track" else " tracks")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            PlayShuffleButtons(
                onPlay = { onPlay(); onDismiss() },
                onShuffle = { onShuffle(); onDismiss() },
            )
        }
        HorizontalDivider()

        if (onTogglePin != null) {
            SheetActionRow(
                if (pinned) "Unpin playlist" else "Pin playlist",
                onClick = { onTogglePin(); onDismiss() },
            ) {
                MageIcon(
                    "pin",
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (onEdit != null) {
            SheetActionRow("Rename playlist", onClick = { onEdit(); onDismiss() }) {
                MageIcon("edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        if (onDownload != null) {
            SheetActionRow(
                title = when {
                    downloading -> "Downloading…"
                    downloaded -> "Remove offline download"
                    else -> "Download for offline"
                },
                subtitle = if (downloading) {
                    "${downloadProgress.completedTracks} of ${downloadProgress.totalTracks} tracks"
                } else null,
                onClick = {
                    if (!downloading) { onDownload(); onDismiss() }
                },
            ) {
                DownloadStatusIcon(downloaded = downloaded, progress = ringProgress)
            }
        }
        if (onDelete != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SheetActionRow(
                "Delete playlist",
                destructive = true,
                onClick = { onDelete(); onDismiss() },
            ) {
                MageIcon("trash", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

package nl.muorg.android.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.ui.icon.MageIcon

/**
 * The track context menu as a bottom sheet — the same actions the long-press
 * dropdown used to offer, in the app's standard sheet style. Actions the
 * caller does not supply are left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextSheet(
    track: CatalogTrack,
    baseUrl: String,
    imageLoader: ImageLoader,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTrackInfo: () -> Unit,
    onViewAlbum: (() -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            val coverUrl: Any? = when {
                track.localCoverPath != null -> java.io.File(track.localCoverPath)
                track.hasCover -> "$baseUrl/api/tracks/${track.id}/cover?size=1200"
                else -> null
            }
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                )
            } else {
                MageIcon(
                    name = "music",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp).padding(8.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    track.displayTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MarqueeText(
                    "${track.displayArtist} · ${track.displayAlbum}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()

        SheetActionRow("Play now", onClick = { onPlayNow(); onDismiss() }) {
            MageIcon("play", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        if (onAddToQueue != null) {
            SheetActionRow("Add to queue", onClick = { onAddToQueue(); onDismiss() }) {
                MageIcon("stack", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        SheetActionRow(
            title = if (isFavorite) "Remove from favorites" else "Add to favorites",
            onClick = { onToggleFavorite(); onDismiss() },
        ) {
            MageIcon(
                if (isFavorite) "heart-fill" else "heart",
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        SheetActionRow("Add to playlist", onClick = { onAddToPlaylist(); onDismiss() }) {
            MageIcon("playlist-add", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        if (onViewAlbum != null) {
            SheetActionRow(
                "View album",
                subtitle = track.displayAlbum,
                onClick = { onViewAlbum(); onDismiss() },
            ) {
                MageIcon("compact-disk", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        if (onViewArtist != null) {
            SheetActionRow(
                "View artist",
                subtitle = track.displayArtist,
                onClick = { onViewArtist(); onDismiss() },
            ) {
                MageIcon("user", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        SheetActionRow("Track info", onClick = { onTrackInfo(); onDismiss() }) {
            MageIcon("information-circle", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

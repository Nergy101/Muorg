package nl.muorg.android.ui.screen.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.ui.component.CoverMosaic
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.glass.scrimLabelStyle
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.theme.MuorgShapes

/**
 * How a playlist is drawn in the list and in the grid.
 *
 * Split out of [PlaylistsScreen], which held six composables in one file.
 */

@Composable
internal fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = playlist.icon?.takeIf { it.isNotBlank() } ?: "🎵",
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Default,
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                        painter = painterResource(mageIconRes("edit")),
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                        painter = painterResource(mageIconRes("trash")),
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * A playlist tile, matching `PlaylistCard.vue`: a 2x2 mosaic of the playlist's
 * own covers and the same caption scrim the album cards use. All actions
 * (pin, rename, download, delete) live in the long-press action sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistTile(
    playlist: Playlist,
    covers: List<Any>,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    /** Long-press: opens the playlist's action sheet. */
    onLongClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = MuorgShapes.card,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(MuorgShapes.card)
            .background(MaterialTheme.colorScheme.surface)
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        CoverMosaic(
            covers = covers,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = playlist.icon ?: "🎵",
                fontSize = 44.sp,
                fontFamily = FontFamily.Default,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        GlassSurface(
            material = GlassMaterial.Scrim,
            shape = RectangleShape,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarqueeText(
                    text = playlist.name,
                    style = scrimLabelStyle(MaterialTheme.typography.titleSmall),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                if (playlist.smartRules != null) {
                    Icon(
                        painter = painterResource(mageIconRes("zap-fill")),
                        contentDescription = "Smart playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    painter = painterResource(mageIconRes("music")),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "${playlist.trackCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

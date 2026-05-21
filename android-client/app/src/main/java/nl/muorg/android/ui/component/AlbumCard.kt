package nl.muorg.android.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.Playlist

private enum class AlbumMenuLevel { HIDDEN, MAIN, PLAYLISTS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: AlbumGroup,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
    onPlayNow: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist) -> Unit)? = null,
) {
    var menuLevel by remember { mutableStateOf(AlbumMenuLevel.HIDDEN) }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuLevel = AlbumMenuLevel.MAIN },
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                    AsyncImage(
                        model = "$baseUrl/api/tracks/${album.coverTrackId}/cover",
                        contentDescription = "Cover for ${album.albumName}",
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = album.albumName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            album.year?.let { append("$it · ") }
                            append("${album.trackCount} tracks")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Level 1: main actions
        DropdownMenu(
            expanded = menuLevel == AlbumMenuLevel.MAIN,
            onDismissRequest = { menuLevel = AlbumMenuLevel.HIDDEN },
        ) {
            DropdownMenuItem(
                text = { Text("Play now") },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = {
                    menuLevel = AlbumMenuLevel.HIDDEN
                    onPlayNow?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                trailingIcon = { Text("›", style = MaterialTheme.typography.titleMedium) },
                onClick = { menuLevel = AlbumMenuLevel.PLAYLISTS },
            )
        }

        // Level 2: playlist selection
        DropdownMenu(
            expanded = menuLevel == AlbumMenuLevel.PLAYLISTS,
            onDismissRequest = { menuLevel = AlbumMenuLevel.HIDDEN },
        ) {
            DropdownMenuItem(
                text = { Text("Back") },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { menuLevel = AlbumMenuLevel.MAIN },
            )
            if (playlists.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false,
                )
            } else {
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text("${playlist.icon ?: "🎵"}  ${playlist.name}") },
                        onClick = {
                            onAddToPlaylist?.invoke(playlist)
                            menuLevel = AlbumMenuLevel.HIDDEN
                        },
                    )
                }
            }
        }
    }
}

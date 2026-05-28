package nl.muorg.android.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist

private enum class TrackMenuLevel { HIDDEN, MAIN, PLAYLISTS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: CatalogTrack,
    baseUrl: String,
    imageLoader: ImageLoader,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    trackInPlaylistIds: Set<Int> = emptySet(),
    onTrackClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist) -> Unit)? = null,
    onRemoveFromPlaylist: ((Playlist) -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var menuLevel by remember { mutableStateOf(TrackMenuLevel.HIDDEN) }
    var showTrackInfo by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTrackClick,
                    onLongClick = { menuLevel = TrackMenuLevel.MAIN },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    EqualizerBars(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(width = 20.dp, height = 14.dp),
                    )
                } else if (track.hasCover || track.localCoverPath != null) {
                    AsyncImage(
                        model = track.localCoverPath ?: "$baseUrl/api/tracks/${track.id}/cover",
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
                MarqueeText(
                    text = track.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = track.format.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (track.format == "flac") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = if (track.format == "flac")
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = track.formattedDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Level 1: main actions
        DropdownMenu(
            expanded = menuLevel == TrackMenuLevel.MAIN,
            onDismissRequest = { menuLevel = TrackMenuLevel.HIDDEN },
        ) {
            DropdownMenuItem(
                text = { Text("Play now") },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    onTrackClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    onAddToQueue?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                leadingIcon = {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    onToggleFavorite?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                trailingIcon = { Text("›", style = MaterialTheme.typography.titleMedium) },
                onClick = { menuLevel = TrackMenuLevel.PLAYLISTS },
            )
            DropdownMenuItem(
                text = { Text("Track info") },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = {
                    menuLevel = TrackMenuLevel.HIDDEN
                    showTrackInfo = true
                },
            )
            if (onViewAlbum != null) {
                DropdownMenuItem(
                    text = { Text("View album") },
                    leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                    onClick = {
                        menuLevel = TrackMenuLevel.HIDDEN
                        onViewAlbum()
                    },
                )
            }
            if (onViewArtist != null) {
                DropdownMenuItem(
                    text = { Text("View artist") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    onClick = {
                        menuLevel = TrackMenuLevel.HIDDEN
                        onViewArtist()
                    },
                )
            }
        }

        // Level 2: playlist selection
        DropdownMenu(
            expanded = menuLevel == TrackMenuLevel.PLAYLISTS,
            onDismissRequest = { menuLevel = TrackMenuLevel.HIDDEN },
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
                onClick = { menuLevel = TrackMenuLevel.MAIN },
            )
            if (playlists.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false,
                )
            } else {
                playlists.forEach { playlist ->
                    val isInPlaylist = playlist.id in trackInPlaylistIds
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${playlist.icon ?: "🎵"}  ${playlist.name}",
                                color = if (isInPlaylist) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        trailingIcon = if (isInPlaylist) {
                            { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = {
                            if (isInPlaylist) onRemoveFromPlaylist?.invoke(playlist)
                            else onAddToPlaylist?.invoke(playlist)
                            menuLevel = TrackMenuLevel.HIDDEN
                        },
                    )
                }
            }
        }
    }

    if (showTrackInfo) {
        AlertDialog(
            onDismissRequest = { showTrackInfo = false },
            title = { Text("Track info") },
            text = {
                Column {
                    TrackInfoField("Title", track.displayTitle)
                    TrackInfoField("Artist", track.displayArtist)
                    TrackInfoField("Album", track.displayAlbum)
                    track.year?.let { TrackInfoField("Year", it.toString()) }
                    TrackInfoField("Format", track.format.uppercase())
                    TrackInfoField("Duration", track.formattedDuration())
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackInfo = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun TrackInfoField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

package nl.muorg.android.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.ui.player.PlayerViewModel

private enum class SheetLevel { MAIN, PLAYLISTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: CatalogTrack,
    playerViewModel: PlayerViewModel,
    playlists: List<Playlist>,
    isFavorite: Boolean,
    baseUrl: String,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onAddToPlaylist: (playlistId: Int) -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
) {
    var level by remember { mutableStateOf(SheetLevel.MAIN) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        when (level) {
            SheetLevel.MAIN -> MainLevel(
                track = track,
                playerViewModel = playerViewModel,
                isFavorite = isFavorite,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onDismiss = onDismiss,
                onGoToPlaylists = { level = SheetLevel.PLAYLISTS },
                onViewArtist = onViewArtist,
                onViewAlbum = onViewAlbum,
            )
            SheetLevel.PLAYLISTS -> PlaylistsLevel(
                playlists = playlists,
                onBack = { level = SheetLevel.MAIN },
                onAddToPlaylist = { id -> onAddToPlaylist(id); onDismiss() },
                onNewPlaylist = { onDismiss() },
            )
        }
    }
}

@Composable
private fun MainLevel(
    track: CatalogTrack,
    playerViewModel: PlayerViewModel,
    isFavorite: Boolean,
    baseUrl: String,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onGoToPlaylists: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
) {
    // Header
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverUrl = if (track.hasCover) "$baseUrl/api/tracks/${track.id}/cover" else null
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
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

    ListItem(
        headlineContent = {
            Text(
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable { playerViewModel.toggleFavorite(track); onDismiss() },
    )
    ListItem(
        headlineContent = { Text("Add to playlist", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { onGoToPlaylists() },
    )
    ListItem(
        headlineContent = { Text("Add to queue", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { playerViewModel.addToQueue(track); onDismiss() },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    ListItem(
        headlineContent = { Text("View artist", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        supportingContent = { Text(track.displayArtist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(Icons.Filled.Person, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { onViewArtist() },
    )
    ListItem(
        headlineContent = { Text("View album", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        supportingContent = { Text(track.displayAlbum, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(Icons.Filled.Album, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { onViewAlbum() },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    ListItem(
        headlineContent = { Text("Sleep timer", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingContent = { Icon(Icons.Filled.Timer, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { onDismiss() },
    )
    ListItem(
        headlineContent = { Text("Track info", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingContent = { Icon(Icons.Filled.Info, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { onDismiss() },
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PlaylistsLevel(
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onAddToPlaylist: (playlistId: Int) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("Back") },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.clickable { onBack() },
    )
    Text(
        "ADD TO…",
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).padding(bottom = 6.dp),
    )
    playlists.forEach { playlist ->
        ListItem(
            headlineContent = { Text(playlist.name) },
            supportingContent = {
                Text(
                    "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { onAddToPlaylist(playlist.id) },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    ListItem(
        headlineContent = {
            Text(
                "New playlist…",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.Add,
                null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.clickable { onNewPlaylist() },
    )
    Spacer(Modifier.height(16.dp))
}

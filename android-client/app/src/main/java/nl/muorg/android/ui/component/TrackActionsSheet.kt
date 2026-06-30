package nl.muorg.android.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SheetLevel { MAIN, PLAYLISTS, TRACK_INFO, EDIT_METADATA }

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
    onRemoveFromPlaylist: (playlistId: Int) -> Unit = {},
    trackInPlaylistIds: Set<Int> = emptySet(),
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onRemoveFromQueue: (() -> Unit)? = null,
    onSaveMetadata: ((title: String?, artist: String?, album: String?, albumArtist: String?, genre: String?, year: Int?) -> Unit)? = null,
) {
    var level by remember { mutableStateOf(SheetLevel.MAIN) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Read favorites directly from the ViewModel so the sheet always sees the freshest value,
    // regardless of whether the parent composable has recomposed yet.
    val sheetPlayerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val currentIsFavorite = track.id.toString() in sheetPlayerState.favorites

    val favPlaylistId = remember(playlists) { playlists.firstOrNull { it.name == "Favorites" }?.id }
    val effectiveMembership = remember(trackInPlaylistIds, favPlaylistId, currentIsFavorite) {
        if (favPlaylistId == null) trackInPlaylistIds
        else if (currentIsFavorite) trackInPlaylistIds + favPlaylistId
        else trackInPlaylistIds - favPlaylistId
    }

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
                isFavorite = currentIsFavorite,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onDismiss = onDismiss,
                onGoToPlaylists = { level = SheetLevel.PLAYLISTS },
                onGoToTrackInfo = { level = SheetLevel.TRACK_INFO },
                onViewArtist = onViewArtist,
                onViewAlbum = onViewAlbum,
                onRemoveFromQueue = onRemoveFromQueue,
                onGoToEditMetadata = { level = SheetLevel.EDIT_METADATA },
            )
            SheetLevel.PLAYLISTS -> PlaylistsLevel(
                playlists = playlists,
                trackInPlaylistIds = effectiveMembership,
                onBack = { level = SheetLevel.MAIN },
                onAddToPlaylist = { id -> onAddToPlaylist(id); onDismiss() },
                onRemoveFromPlaylist = { id -> onRemoveFromPlaylist(id); onDismiss() },
                onNewPlaylist = { onDismiss() },
            )
            SheetLevel.TRACK_INFO -> TrackInfoLevel(
                track = track,
                onBack = { level = SheetLevel.MAIN },
            )
            SheetLevel.EDIT_METADATA -> MetadataEditLevel(
                track = track,
                onBack = { level = SheetLevel.MAIN },
                onSave = { title, artist, album, albumArtist, genre, year ->
                    onSaveMetadata?.invoke(title, artist, album, albumArtist, genre, year)
                    onDismiss()
                },
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
    onGoToTrackInfo: () -> Unit,
    onGoToEditMetadata: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onRemoveFromQueue: (() -> Unit)? = null,
) {
    // Header
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverUrl: Any? = when {
            track.localCoverPath != null -> java.io.File(track.localCoverPath)
            track.hasCover -> "$baseUrl/api/tracks/${track.id}/cover"
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
    if (onRemoveFromQueue != null) {
        ListItem(
            headlineContent = { Text("Remove from queue", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onRemoveFromQueue(); onDismiss() },
        )
    } else {
        ListItem(
            headlineContent = { Text("Add to queue", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.clickable { playerViewModel.addToQueue(track); onDismiss() },
        )
    }

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
        headlineContent = { Text("Track info", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingContent = { Icon(Icons.Filled.Info, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { onGoToTrackInfo() },
    )
    ListItem(
        headlineContent = { Text("Edit metadata", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingContent = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable { onGoToEditMetadata() },
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PlaylistsLevel(
    playlists: List<Playlist>,
    trackInPlaylistIds: Set<Int> = emptySet(),
    onBack: () -> Unit,
    onAddToPlaylist: (playlistId: Int) -> Unit,
    onRemoveFromPlaylist: (playlistId: Int) -> Unit = {},
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
        val isInPlaylist = playlist.id in trackInPlaylistIds
        ListItem(
            headlineContent = {
                Text(
                    playlist.name,
                    color = if (isInPlaylist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Text(
                    "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = if (isInPlaylist) {
                {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Already in playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else null,
            modifier = Modifier.clickable {
                if (isInPlaylist) onRemoveFromPlaylist(playlist.id) else onAddToPlaylist(playlist.id)
            },
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

@Composable
private fun TrackInfoLevel(
    track: CatalogTrack,
    onBack: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("Back") },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.clickable { onBack() },
    )
    Text(
        "TRACK INFO",
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).padding(bottom = 6.dp),
    )

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val fields = buildList {
        add("Title" to track.displayTitle)
        if (track.artist != null) add("Artist" to track.artist)
        if (track.albumArtist != null && track.albumArtist != track.artist) add("Album Artist" to track.albumArtist)
        if (track.featuring != null) add("Featuring" to track.featuring)
        add("Album" to track.displayAlbum)
        if (track.year != null) add("Year" to track.year.toString())
        if (track.genre != null) add("Genre" to track.genre)
        if (track.trackNumber != null) add("Track #" to track.trackNumber.toString())
        if (track.discNumber != null) add("Disc #" to track.discNumber.toString())
        add("Duration" to track.formattedDuration())
        add("Format" to track.format.uppercase())
        if (track.rating != null) add("Rating" to "${track.rating} / 5")
        add("Play Count" to track.playCount.toString())
        if (track.lastPlayedAt != null) add("Last Played" to dateFormat.format(Date(track.lastPlayedAt * 1000L)))
        add("File" to (track.localFilePath ?: track.path))
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        fields.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(100.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetadataEditLevel(
    track: CatalogTrack,
    onBack: () -> Unit,
    onSave: (title: String?, artist: String?, album: String?, albumArtist: String?, genre: String?, year: Int?) -> Unit,
) {
    var editTitle by remember { mutableStateOf(track.title ?: "") }
    var editArtist by remember { mutableStateOf(track.artist ?: "") }
    var editAlbum by remember { mutableStateOf(track.album ?: "") }
    var editAlbumArtist by remember { mutableStateOf(track.albumArtist ?: "") }
    var editGenre by remember { mutableStateOf(track.genre ?: "") }
    var editYear by remember { mutableStateOf(track.year?.toString() ?: "") }

    ListItem(
        headlineContent = { Text("Back") },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.clickable { onBack() },
    )
    Text(
        "EDIT METADATA",
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).padding(bottom = 6.dp),
    )

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        MetadataField("Title", editTitle) { editTitle = it }
        MetadataField("Artist", editArtist) { editArtist = it }
        MetadataField("Album", editAlbum) { editAlbum = it }
        MetadataField("Album Artist", editAlbumArtist) { editAlbumArtist = it }
        MetadataField("Genre", editGenre) { editGenre = it }
        MetadataField("Year", editYear) { editYear = it }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onBack) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSave(
                        editTitle.ifBlank { null },
                        editArtist.ifBlank { null },
                        editAlbum.ifBlank { null },
                        editAlbumArtist.ifBlank { null },
                        editGenre.ifBlank { null },
                        editYear.toIntOrNull(),
                    )
                },
            ) {
                Text("Save")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

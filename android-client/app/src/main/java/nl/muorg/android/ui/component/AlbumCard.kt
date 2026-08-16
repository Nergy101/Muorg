package nl.muorg.android.ui.component

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.Playlist
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.glass.scrimLabelStyle
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.theme.MuorgGreenLight
import nl.muorg.android.ui.theme.MuorgShapes

enum class AlbumDisplayMode { GRID, LIST }

private enum class AlbumMenuLevel { HIDDEN, MAIN }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: AlbumGroup,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    displayMode: AlbumDisplayMode = AlbumDisplayMode.GRID,
    playlists: List<Playlist> = emptyList(),
    albumInPlaylistIds: Set<Int> = emptySet(),
    albumPartialPlaylistIds: Set<Int> = emptySet(),
    onPlayNow: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist) -> Unit)? = null,
    onRemoveFromPlaylist: ((Playlist) -> Unit)? = null,
    onCreatePlaylist: ((String) -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
) {
    var menuLevel by remember { mutableStateOf(AlbumMenuLevel.HIDDEN) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    if (displayMode == AlbumDisplayMode.LIST) {
        Box(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = { menuLevel = AlbumMenuLevel.MAIN },
                    )
                    .then(if (isActive) Modifier.background(MuorgGreenLight.copy(alpha = 0.08f)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(mageIconRes("compact-disk")),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp),
                    )
                    AsyncImage(
                        model = album.coverArtUri ?: "$baseUrl/api/tracks/${album.coverTrackId}/cover",
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.albumName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isActive) MuorgGreenLight else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MuorgGreenLight.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(mageIconRes("music")),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        "${album.trackCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = menuLevel == AlbumMenuLevel.MAIN,
                onDismissRequest = { menuLevel = AlbumMenuLevel.HIDDEN },
            ) {
                AlbumMenuItems(
                    onSetLevel = { menuLevel = it },
                    onPlayNow = onPlayNow,
                    onAddToQueue = onAddToQueue,
                    onViewArtist = onViewArtist,
                    onOpenPlaylistSheet = { menuLevel = AlbumMenuLevel.HIDDEN; showPlaylistSheet = true },
                )
            }
        }

        if (showPlaylistSheet) {
            PlaylistPickerSheet(
                playlists = playlists,
                membershipIds = albumInPlaylistIds,
                partialMembershipIds = albumPartialPlaylistIds,
                onAdd = { playlist -> onAddToPlaylist?.invoke(playlist) },
                onRemove = { playlist -> onRemoveFromPlaylist?.invoke(playlist) },
                onCreatePlaylist = onCreatePlaylist,
                onDismiss = { showPlaylistSheet = false },
            )
        }
        return
    }

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cardScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (isPressed) 2.dp else 8.dp,
                shape = MuorgShapes.card,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(MuorgShapes.card)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (isActive) Modifier.border(2.dp, MuorgGreenLight, MuorgShapes.card) else Modifier)
            .aspectRatio(1f)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuLevel = AlbumMenuLevel.MAIN },
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            MageIcon(
                name = "compact-disk",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxSize().padding(32.dp),
            )
            AsyncImage(
                model = album.coverArtUri ?: "$baseUrl/api/tracks/${album.coverTrackId}/cover",
                contentDescription = "Cover for ${album.albumName}",
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        GlassSurface(
            material = GlassMaterial.Scrim,
            shape = RectangleShape,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(
                    text = album.albumName,
                    style = scrimLabelStyle(MaterialTheme.typography.titleSmall),
                    color = if (isActive) MuorgGreenLight else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                MageIcon(
                    name = "music",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "${album.trackCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MarqueeText(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }

        DropdownMenu(
            expanded = menuLevel == AlbumMenuLevel.MAIN,
            onDismissRequest = { menuLevel = AlbumMenuLevel.HIDDEN },
        ) {
            AlbumMenuItems(
                onSetLevel = { menuLevel = it },
                onPlayNow = onPlayNow,
                onAddToQueue = onAddToQueue,
                onViewArtist = onViewArtist,
                onOpenPlaylistSheet = { menuLevel = AlbumMenuLevel.HIDDEN; showPlaylistSheet = true },
            )
        }
    }

    if (showPlaylistSheet) {
        PlaylistPickerSheet(
            playlists = playlists,
            membershipIds = albumInPlaylistIds,
            partialMembershipIds = albumPartialPlaylistIds,
            onAdd = { playlist -> onAddToPlaylist?.invoke(playlist) },
            onRemove = { playlist -> onRemoveFromPlaylist?.invoke(playlist) },
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showPlaylistSheet = false },
        )
    }
}

@Composable
private fun AlbumMenuItems(
    onSetLevel: (AlbumMenuLevel) -> Unit,
    onPlayNow: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onOpenPlaylistSheet: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("Play now") },
        leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("play")), contentDescription = null) },
        onClick = {
            onSetLevel(AlbumMenuLevel.HIDDEN)
            onPlayNow?.invoke()
        },
    )
    DropdownMenuItem(
        text = { Text("Add to queue") },
        leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("playlist-add")), contentDescription = null) },
        onClick = {
            onSetLevel(AlbumMenuLevel.HIDDEN)
            onAddToQueue?.invoke()
        },
    )
    if (onViewArtist != null) {
        DropdownMenuItem(
            text = { Text("View artist") },
            leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("user")), contentDescription = null) },
            onClick = {
                onSetLevel(AlbumMenuLevel.HIDDEN)
                onViewArtist()
            },
        )
    }
    DropdownMenuItem(
        text = { Text("Add to playlist") },
        leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("playlist-add")), contentDescription = null) },
        onClick = onOpenPlaylistSheet,
    )
}

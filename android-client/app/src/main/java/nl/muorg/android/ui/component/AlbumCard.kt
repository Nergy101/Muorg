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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.ui.theme.MuorgGreenLight

private enum class AlbumMenuLevel { HIDDEN, MAIN, PLAYLISTS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: AlbumGroup,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    onPlayNow: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist) -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
) {
    var menuLevel by remember { mutableStateOf(AlbumMenuLevel.HIDDEN) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cardScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier
                    .drawBehind {
                        drawIntoCanvas { canvas ->
                            val paint = Paint()
                            paint.asFrameworkPaint().apply {
                                isAntiAlias = true
                                color = MuorgGreenLight.copy(alpha = 0.45f).toArgb()
                                maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
                            }
                            canvas.drawRoundRect(0f, 0f, size.width, size.height, 12.dp.toPx(), 12.dp.toPx(), paint)
                        }
                    }
                    .border(2.dp, MuorgGreenLight, RoundedCornerShape(12.dp))
                else Modifier
            ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isPressed) 0.dp else 4.dp,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = { menuLevel = AlbumMenuLevel.MAIN },
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                )
                AsyncImage(
                    model = album.coverArtUri ?: "$baseUrl/api/tracks/${album.coverTrackId}/cover",
                    contentDescription = "Cover for ${album.albumName}",
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6111111))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    MarqueeText(
                        text = album.albumName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    MarqueeText(
                        text = album.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = "${album.trackCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

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
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.Filled.Queue, contentDescription = null) },
                onClick = {
                    menuLevel = AlbumMenuLevel.HIDDEN
                    onAddToQueue?.invoke()
                },
            )
            if (onViewArtist != null) {
                DropdownMenuItem(
                    text = { Text("View artist") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    onClick = {
                        menuLevel = AlbumMenuLevel.HIDDEN
                        onViewArtist()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                trailingIcon = { Text("›", style = MaterialTheme.typography.titleMedium) },
                onClick = { menuLevel = AlbumMenuLevel.PLAYLISTS },
            )
        }

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

@Composable
private fun CheckIcon() {
    Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
}

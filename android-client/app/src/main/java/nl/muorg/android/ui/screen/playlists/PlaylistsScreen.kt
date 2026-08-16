package nl.muorg.android.ui.screen.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import nl.muorg.android.data.api.Playlist
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import nl.muorg.android.ui.glass.GlassFrostContent
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.glass.glassFrost
import nl.muorg.android.ui.glass.scrimLabelStyle
import nl.muorg.android.ui.theme.MuorgShapes
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.player.PlayerViewModel

private val PLAYLIST_EMOJIS = listOf(
    "🎵", "🎶", "🎸", "🎹", "🎺", "🎻", "🥁", "🎷",
    "🎤", "🎧", "📻", "🎼", "🎙", "🎛", "🎚", "🔊",
    "❤️", "💜", "💚", "💙", "💛", "🧡", "🖤", "🤍",
    "🔥", "⭐", "🌟", "✨", "💫", "🌙", "☀️", "🌈",
    "🏃", "💪", "🧘", "🎉", "🎊", "🥳", "😴", "😌",
    "🌿", "🌺", "🍂", "🌊", "⛰️", "🌃", "🌆", "🏖️",
    "🎮", "📚", "🏀", "⚽", "🚀", "🌍", "🦋", "🐾",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onPlaylistClick: (Int) -> Unit,
    onOpenQueue: () -> Unit = {},
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadPlaylists()
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // The web floats its create control as a glass pill in the top-right
        // corner; there is no title bar above the grid.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            GlassSurface(
                material = GlassMaterial.Glass,
                shape = MuorgShapes.pill,
                modifier = Modifier.size(44.dp).clickable(onClick = viewModel::showCreateDialog),
            ) {
                Icon(
                    painter = painterResource(mageIconRes("plus")),
                    contentDescription = "New playlist",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                uiState.playlists.isEmpty() -> {
                    Text(
                        text = "No playlists yet.\nTap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = LocalBottomInset.current + 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            PlaylistTile(
                                playlist = playlist,
                                coverTrackIds = uiState.covers[playlist.id].orEmpty(),
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onClick = { onPlaylistClick(playlist.id) },
                                onEdit = { viewModel.showEditDialog(playlist) },
                                onDelete = { viewModel.deletePlaylist(playlist.id) },
                            )
                        }
                    }
                }
            }
        }

    }

    if (uiState.showCreateDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateDialog,
            title = { Text("New Playlist") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uiState.newPlaylistName,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        items(PLAYLIST_EMOJIS) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (uiState.newPlaylistIcon == emoji)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.onIconChange(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Default,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::createPlaylist,
                    enabled = uiState.newPlaylistName.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreateDialog) { Text("Cancel") }
            },
        )
    }

    if (uiState.showEditDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditDialog,
            title = { Text("Edit Playlist") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uiState.editName,
                        onValueChange = viewModel::onEditNameChange,
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        items(PLAYLIST_EMOJIS) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (uiState.editIcon == emoji)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.onEditIconChange(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Default,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::updatePlaylist,
                    enabled = uiState.editName.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditDialog) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistCard(
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
 * own covers, frosted action discs floating over the artwork, and the same
 * caption scrim the album cards use.
 */
@Composable
private fun PlaylistTile(
    playlist: Playlist,
    coverTrackIds: List<Int>,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
            .clickable(onClick = onClick),
    ) {
        PlaylistMosaic(coverTrackIds, playlist.icon, baseUrl, imageLoader)

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FrostDisc("edit", "Rename playlist", onEdit)
            FrostDisc("trash", "Delete playlist", onDelete)
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
                Spacer(Modifier.width(6.dp))
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

/** A 32dp frosted disc with a fixed dark glyph — legible over any sleeve. */
@Composable
private fun FrostDisc(icon: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .glassFrost(MuorgShapes.pill)
            .clip(MuorgShapes.pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(mageIconRes(icon)),
            contentDescription = description,
            tint = GlassFrostContent,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PlaylistMosaic(
    coverTrackIds: List<Int>,
    icon: String?,
    baseUrl: String,
    imageLoader: ImageLoader,
) {
    if (coverTrackIds.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = icon ?: "🎵", fontSize = 44.sp, fontFamily = FontFamily.Default)
        }
        return
    }
    if (coverTrackIds.size < 4) {
        AsyncImage(
            model = "$baseUrl/api/tracks/${coverTrackIds.first()}/cover",
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 2) {
                    AsyncImage(
                        model = "$baseUrl/api/tracks/${coverTrackIds[row * 2 + col]}/cover",
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().weight(1f),
                    )
                }
            }
        }
    }
}

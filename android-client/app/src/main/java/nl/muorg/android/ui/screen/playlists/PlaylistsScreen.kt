package nl.muorg.android.ui.screen.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes

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
                modifier = Modifier.height(44.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = viewModel::showCreateDialog),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(mageIconRes("plus")),
                            contentDescription = "New playlist",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = viewModel::showSmartCreateDialog),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(mageIconRes("zap")),
                            contentDescription = "New smart playlist",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
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
                    // Pinned playlists sort to the front, as on the web. Hoisted
                    // out of the lazy scope: that block is not a composable, so
                    // `remember` cannot live inside it.
                    val ordered = remember(uiState.playlists, uiState.pinnedIds) {
                        uiState.playlists.sortedByDescending { it.id in uiState.pinnedIds }
                    }
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
                        items(ordered, key = { it.id }) { playlist ->
                            PlaylistTile(
                                playlist = playlist,
                                coverTrackIds = uiState.covers[playlist.id].orEmpty(),
                                pinned = playlist.id in uiState.pinnedIds,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onClick = { onPlaylistClick(playlist.id) },
                                onTogglePin = { viewModel.togglePin(playlist.id) },
                                onEdit = { viewModel.showEditDialog(playlist) },
                                onDownload = { viewModel.downloadPlaylist(playlist) },
                                onDelete = { viewModel.deletePlaylist(playlist.id) },
                            )
                        }
                    }
                }
            }
        }

    }

    if (uiState.showSmartDialog) {
        SmartPlaylistDialog(
            name = uiState.smartName,
            rules = uiState.smartRules,
            saving = uiState.smartSaving,
            onNameChange = viewModel::onSmartNameChange,
            onRuleChange = viewModel::updateSmartRule,
            onAddRule = viewModel::addSmartRule,
            onRemoveRule = viewModel::removeSmartRule,
            onConfirm = viewModel::createSmartPlaylist,
            onDismiss = viewModel::dismissSmartDialog,
        )
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

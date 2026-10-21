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
import androidx.compose.material3.LinearProgressIndicator
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
import nl.muorg.android.ui.component.coverModelFor
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes

/** The server's favorites playlist — managed by the server, not editable. */
private const val FAVORITES_PLAYLIST_NAME = "Favorites"

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
        // Creating playlists is a server operation, so the pill is gone in
        // offline mode — the list there is the downloaded playlists.
        if (!uiState.isOfflineMode) {
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
        }

        // Download status: the count while a playlist downloads. Once it is
        // done the count is irrelevant — the tile's filled download button is
        // the record — so only failures stay on screen.
        val download = uiState.downloadProgress
        if (download.isActive || download.errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (download.isActive) {
                    val processed = download.completedTracks + download.failedTracks
                    LinearProgressIndicator(
                        progress = { processed.toFloat() / download.totalTracks },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Downloading $processed/${download.totalTracks} — ${download.currentTrackName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                download.errorMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                        text = if (uiState.isOfflineMode) {
                            "No downloaded playlists.\nDownload playlists while online to use them offline."
                        } else {
                            "No playlists yet.\nTap + to create one."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    // Pinned playlists sort to the front, in pin order, as on
                    // the web. Hoisted out of the lazy scope: that block is
                    // not a composable, so `remember` cannot live inside it.
                    val ordered = remember(uiState.playlists, uiState.pinnedIds) {
                        val pinned = uiState.pinnedIds
                        uiState.playlists.sortedWith(
                            compareBy({ it.id !in pinned }, { pinned.indexOf(it.id) })
                        )
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
                            val coverIds = uiState.covers[playlist.id].orEmpty()
                            // Resolved once per playlist: local art files in
                            // local mode, server cover URLs otherwise.
                            val covers = remember(coverIds, uiState.coverPaths, baseUrl) {
                                coverIds.map { id ->
                                    coverModelFor(uiState.coverPaths[id], id, baseUrl)
                                }
                            }
                            PlaylistTile(
                                playlist = playlist,
                                covers = covers,
                                pinned = playlist.id in uiState.pinnedIds,
                                imageLoader = imageLoader,
                                onClick = { onPlaylistClick(playlist.id) },
                                onTogglePin = { viewModel.togglePin(playlist.id) },
                                onEdit = { viewModel.showEditDialog(playlist) },
                                onDownload = { viewModel.onDownloadClicked(playlist) },
                                onDelete = { viewModel.deletePlaylist(playlist.id) },
                                isFavorites = uiState.isServerMode &&
                                    playlist.name == FAVORITES_PLAYLIST_NAME,
                                downloaded = playlist.id in uiState.downloadedPlaylistIds,
                                showOnlineActions = !uiState.isOfflineMode,
                                showDownload = uiState.isServerMode,
                            )
                        }
                    }
                }
            }
        }

    }

    if (uiState.showRemoveDownloadDialog) {
        val playlist = uiState.removingDownloadPlaylist
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveDownloadDialog,
            title = { Text("Remove downloaded songs?") },
            text = {
                Text(
                    "This deletes the downloaded tracks of \"${playlist?.name}\" from this device. " +
                        "The playlist itself stays on the server."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmRemoveDownload) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveDownloadDialog) { Text("Cancel") }
            },
        )
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

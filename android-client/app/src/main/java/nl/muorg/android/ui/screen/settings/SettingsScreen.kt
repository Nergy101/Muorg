package nl.muorg.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import nl.muorg.android.ui.component.LocalBottomInset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.muorg.android.BuildConfig
import nl.muorg.android.ui.screen.library.SortMode
import androidx.compose.material.icons.filled.DataObject

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMetadataScanSheet by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.addFolder(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Before verticalScroll, so the inset shrinks the viewport instead
            // of scrolling away — there is no app bar here to sit under the
            // status bar, so the first section would collide with the clock.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalBottomInset.current),
    ) {
        SectionHeader("Muorg Info")

        ListItem(
            headlineContent = { Text("Version") },
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
            leadingContent = {
                Icon(
                        painter = painterResource(mageIconRes("information-circle")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )

        uiState.latestVersion?.let { latest ->
            ListItem(
                headlineContent = {
                    Text(
                        "Update available: v$latest",
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                supportingContent = { Text("Tap to open the releases page and download") },
                leadingContent = {
                    Icon(
                        Icons.Filled.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val url = uiState.latestReleaseUrl
                            ?: "https://github.com/Nergy101/Muorg/releases"
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        )
                    },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ),
            )
        }

        HorizontalDivider()
        SectionHeader("Playback")

        ListItem(
            headlineContent = { Text("Continuous playback") },
            supportingContent = { Text("Automatically play next track when queue ends") },
            leadingContent = {
                Icon(
                        painter = painterResource(mageIconRes("reload")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = uiState.continuousPlayback,
                    onCheckedChange = viewModel::setContinuousPlayback,
                )
            },
            modifier = Modifier.clickable { viewModel.setContinuousPlayback(!uiState.continuousPlayback) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )

        ListItem(
            headlineContent = { Text("Tap mini player") },
            supportingContent = { Text(if (uiState.playerBarTapOpensPlayer) "Opens full player screen" else "Plays / pauses") },
            leadingContent = {
                Icon(Icons.Filled.TouchApp, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = uiState.playerBarTapOpensPlayer,
                    onCheckedChange = viewModel::setPlayerBarTapOpensPlayer,
                )
            },
            modifier = Modifier.clickable { viewModel.setPlayerBarTapOpensPlayer(!uiState.playerBarTapOpensPlayer) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )

        HorizontalDivider()
        SectionHeader("Library")

        Box {
            ListItem(
                headlineContent = { Text("Default sort order") },
                supportingContent = { Text(uiState.defaultSort.label) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::showSortDropdown),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            DropdownMenu(
                expanded = uiState.showSortDropdown,
                onDismissRequest = viewModel::dismissSortDropdown,
            ) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = { viewModel.setDefaultSort(mode) },
                    )
                }
            }
        }

        ListItem(
            headlineContent = { Text("Sort direction") },
            supportingContent = { Text(if (uiState.sortAscending) "Ascending" else "Descending") },
            leadingContent = {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = uiState.sortAscending,
                    onCheckedChange = viewModel::setSortAscending,
                )
            },
            modifier = Modifier.clickable { viewModel.setSortAscending(!uiState.sortAscending) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Layout",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("grid", "list", "tracks").forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = uiState.albumViewStyle == style,
                        onClick = { viewModel.setAlbumViewStyle(style) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        icon = {},
                    ) {
                        Text(style.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        HorizontalDivider()
        SectionHeader("Theme")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val themeModes = listOf("dark", "light", "auto")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeModes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                        icon = {},
                    ) {
                        Text(mode.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        ListItem(
            headlineContent = { Text("Use true black") },
            supportingContent = { Text("OLED-friendly pitch-black background") },
            leadingContent = {
                Icon(Icons.Filled.Contrast, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = uiState.useTrueBlack,
                    onCheckedChange = viewModel::setUseTrueBlack,
                )
            },
            modifier = Modifier.clickable { viewModel.setUseTrueBlack(!uiState.useTrueBlack) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )

        ListItem(
            headlineContent = { Text("Material You") },
            supportingContent = { Text("Use your device's dynamic color theme") },
            leadingContent = {
                Icon(
                        painter = painterResource(mageIconRes("color-swatch")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = uiState.materialYou,
                    onCheckedChange = viewModel::setMaterialYou,
                )
            },
            modifier = Modifier.clickable { viewModel.setMaterialYou(!uiState.materialYou) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )

        HorizontalDivider()

        Text(
            "MUSIC SOURCE",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            val onlineSelected = uiState.sourceMode == SourceMode.ONLINE_SERVER
            val localSelected = uiState.sourceMode == SourceMode.LOCAL_LIBRARY

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (onlineSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .then(
                        if (onlineSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable { viewModel.requestSwitchSourceMode(SourceMode.ONLINE_SERVER) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (onlineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Online server",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (onlineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "muorg-server",
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (onlineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (localSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .then(
                        if (localSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable { viewModel.requestSwitchSourceMode(SourceMode.LOCAL_LIBRARY) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (localSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Local folders",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (localSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (localSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.85f),
                    )
                }
            }
        }

        Text(
            "Pick one. Local playlists are always preserved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (uiState.sourceMode == SourceMode.ONLINE_SERVER) {
            ListItem(
                headlineContent = { Text("Server URL") },
                supportingContent = {
                    Text(
                        text = uiState.serverUrl.ifBlank { "Not configured" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Icon(Icons.Filled.Cloud, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Button(
                    onClick = viewModel::refreshData,
                    enabled = uiState.refreshStatus != RefreshStatus.LOADING,
                ) {
                    if (uiState.refreshStatus == RefreshStatus.LOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                        painter = painterResource(mageIconRes("refresh")), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
                when (uiState.refreshStatus) {
                    RefreshStatus.SUCCESS -> {
                        Spacer(Modifier.width(12.dp))
                        Text("Done", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    RefreshStatus.ERROR -> {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            uiState.refreshError ?: "Failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> {}
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = viewModel::showLogoutDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Logout")
                }
            }

            uiState.stats?.let { stats ->
                ListItem(
                    headlineContent = { Text("Tracks") },
                    supportingContent = { Text("${stats.trackCount} tracks in library") },
                    leadingContent = {
                        Icon(
                        painter = painterResource(mageIconRes("music")), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { Text("${stats.trackCount}", style = MaterialTheme.typography.titleMedium) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
                ListItem(
                    headlineContent = { Text("Albums") },
                    supportingContent = { Text("Unique albums") },
                    leadingContent = {
                        Icon(
                        painter = painterResource(mageIconRes("compact-disk")), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { Text("${stats.albumCount}", style = MaterialTheme.typography.titleMedium) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
                ListItem(
                    headlineContent = { Text("Artists") },
                    supportingContent = { Text("Unique artists") },
                    leadingContent = {
                        Icon(Icons.Filled.People, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { Text("${stats.artistCount}", style = MaterialTheme.typography.titleMedium) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
                ListItem(
                    headlineContent = { Text("Total duration") },
                    supportingContent = { Text("Combined playtime") },
                    leadingContent = {
                        Icon(
                        painter = painterResource(mageIconRes("music")), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        Text(
                            formatDuration(stats.totalDurationSecs),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        } else {
            SectionHeader("Local folders")

            if (uiState.localFolderUris.isEmpty()) {
                ListItem(
                    headlineContent = { Text("No folders added") },
                    supportingContent = { Text("Add a folder to scan for music files") },
                    leadingContent = {
                        Icon(Icons.Filled.Folder, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            } else {
                uiState.localFolderUris.forEach { uriString ->
                    val displayName = Uri.parse(uriString).lastPathSegment
                        ?.substringAfterLast(':')
                        ?: uriString
                    ListItem(
                        headlineContent = { Text(displayName, maxLines = 1) },
                        leadingContent = {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeFolder(uriString) }) {
                                Icon(
                        painter = painterResource(mageIconRes("trash")), contentDescription = "Remove folder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Button(onClick = { folderPickerLauncher.launch(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add folder")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Button(
                    onClick = viewModel::scanLibrary,
                    enabled = uiState.scanStatus != ScanStatus.SCANNING && uiState.localFolderUris.isNotEmpty(),
                ) {
                    Icon(
                        painter = painterResource(mageIconRes("music")), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan library")
                }
                when (uiState.scanStatus) {
                    ScanStatus.DONE -> {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${uiState.scanTrackCount} tracks found",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ScanStatus.ERROR -> {
                        Spacer(Modifier.width(12.dp))
                        Text("Scan failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {}
                }
            }

            if (uiState.scanStatus == ScanStatus.SCANNING) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    val progress = if (uiState.scanTotal > 0)
                        uiState.scanProgress.toFloat() / uiState.scanTotal
                    else 0f
                    if (uiState.scanTotal > 0) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "${uiState.scanProgress} / ${uiState.scanTotal} files scanned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Counting files…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        SectionHeader("Library tools")

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Button(onClick = { showMetadataScanSheet = true }) {
                Icon(Icons.Filled.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Metadata scan")
            }
        }

    }

    if (showMetadataScanSheet) {
        MetadataScanSheet(onDismiss = { showMetadataScanSheet = false })
    }

    if (uiState.showSwitchConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSwitchDialog,
            title = { Text("Switch music source?") },
            text = { Text("Switching wipes the current library cache. Continue?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSwitchSourceMode(onLoggedOut) }) {
                    Text("Switch", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSwitchDialog) { Text("Cancel") }
            },
        )
    }

    if (uiState.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLogoutDialog,
            title = { Text("Log out?") },
            text = { Text("This will clear your server URL and API key. You'll need to reconnect.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.logout(onLoggedOut) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogoutDialog) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp, end = 16.dp),
    )
}

private fun formatDuration(secs: Long): String {
    val hours = secs / 3600
    val minutes = (secs % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

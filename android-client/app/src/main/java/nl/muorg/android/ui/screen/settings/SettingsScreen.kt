package nl.muorg.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.muorg.android.BuildConfig
import nl.muorg.android.ui.screen.library.SortMode

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader("Muorg Info")

        ListItem(
            headlineContent = { Text("Version") },
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
            leadingContent = {
                Icon(Icons.Filled.Info, contentDescription = null,
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
        SectionHeader("Server")

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
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (uiState.refreshStatus == RefreshStatus.LOADING) "Refreshing…" else "Refresh data from server")
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
        }

        uiState.stats?.let { stats ->
            ListItem(
                headlineContent = { Text("Tracks") },
                supportingContent = { Text("${stats.trackCount} tracks in library") },
                leadingContent = {
                    Icon(Icons.Filled.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingContent = { Text("${stats.trackCount}", style = MaterialTheme.typography.titleMedium) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            ListItem(
                headlineContent = { Text("Albums") },
                supportingContent = { Text("Unique albums") },
                leadingContent = {
                    Icon(Icons.Filled.Album, contentDescription = null,
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
                    Icon(Icons.Filled.MusicNote, contentDescription = null,
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

        HorizontalDivider()
        SectionHeader("Playback")

        ListItem(
            headlineContent = { Text("Continuous playback") },
            supportingContent = { Text("Automatically play next track when queue ends") },
            leadingContent = {
                Icon(Icons.Filled.Repeat, contentDescription = null,
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

        HorizontalDivider()
        SectionHeader("Account")

        ListItem(
            headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("Disconnect from server and clear credentials") },
            leadingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::showLogoutDialog),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
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

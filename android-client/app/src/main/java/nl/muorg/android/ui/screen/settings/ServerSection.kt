package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.icon.mageIconRes

/**
 * Connection details, shown when the source is a remote server.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun ServerSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
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
}

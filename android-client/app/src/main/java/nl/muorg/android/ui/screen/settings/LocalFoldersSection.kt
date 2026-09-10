package nl.muorg.android.ui.screen.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
 * The folders scanned for music, shown when the source is this device.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun LocalFoldersSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onAddFolder: () -> Unit,
) {
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
        Button(onClick = { onAddFolder() }) {
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

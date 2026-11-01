package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.icon.mageIconRes
import kotlin.math.roundToInt

private const val AUTO_CACHE_MIN_BYTES = 256L * 1024 * 1024 // 256 MiB
private const val AUTO_CACHE_MAX_BYTES = 8L * 1024 * 1024 * 1024 // 8 GiB
private const val AUTO_CACHE_STEP_BYTES = 256L * 1024 * 1024 // 256 MiB

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024)
    return if (mb < 1024) "${mb.roundToInt()} MB" else "${String.format("%.1f", mb / 1024)} GB"
}

/**
 * Offline mode, automatic caching with its storage cap, what is cached, and
 * download behaviour — matching the web client's Offline section. The
 * offline-mode switch sits at the top of the section.
 */
@Composable
fun OfflineSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()

    // Local folders are already on the device, so the offline toggle has no
    // effect there — it only applies to the online server source.
    if (uiState.sourceMode == SourceMode.ONLINE_SERVER) {
        ListItem(
            headlineContent = { Text("Offline mode") },
            supportingContent = { Text("No network calls — only downloaded playlists are available") },
            leadingContent = {
                Icon(
                    painter = painterResource(mageIconRes("download")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = uiState.offlineMode,
                    onCheckedChange = viewModel::setOfflineMode,
                )
            },
            modifier = Modifier.clickable { viewModel.setOfflineMode(!uiState.offlineMode) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }

    ListItem(
        headlineContent = { Text("Automatic caching") },
        supportingContent = { Text("Automatically download played tracks for offline use") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("refresh")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.autoCacheEnabled,
                onCheckedChange = viewModel::setAutoCacheEnabled,
            )
        },
        modifier = Modifier.clickable { viewModel.setAutoCacheEnabled(!uiState.autoCacheEnabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    if (uiState.autoCacheEnabled) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Max automatic cache size: ${formatBytes(uiState.autoCacheMaxBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            val steps = ((AUTO_CACHE_MAX_BYTES - AUTO_CACHE_MIN_BYTES) / AUTO_CACHE_STEP_BYTES).toInt() - 1
            Slider(
                value = uiState.autoCacheMaxBytes.toFloat(),
                onValueChange = { viewModel.setAutoCacheMaxBytes(it.toLong().coerceIn(AUTO_CACHE_MIN_BYTES, AUTO_CACHE_MAX_BYTES)) },
                valueRange = AUTO_CACHE_MIN_BYTES.toFloat()..AUTO_CACHE_MAX_BYTES.toFloat(),
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    ListItem(
        headlineContent = { Text("Cached tracks") },
        supportingContent = { Text("${uiState.cachedTrackCount} tracks downloaded for offline use") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("music")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Text(uiState.cachedTrackCount.toString(), style = MaterialTheme.typography.titleMedium)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Storage used") },
        supportingContent = { Text("Combined size of all downloaded files") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("save-floppy")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Text(formatBytes(uiState.cachedBytes), style = MaterialTheme.typography.titleMedium)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Downloaded playlists") },
        supportingContent = {
            Text(if (uiState.downloadedPlaylistNames.isBlank()) "None" else uiState.downloadedPlaylistNames)
        },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("playlist")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Clear offline cache", color = MaterialTheme.colorScheme.error) },
        supportingContent = { Text("Deletes every downloaded file, including manual playlist downloads") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("trash")), contentDescription = null,
                tint = MaterialTheme.colorScheme.error)
        },
        modifier = Modifier.clickable { viewModel.requestClearOffline() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Download over mobile data") },
        supportingContent = { Text("Off: playlist downloads wait for Wi-Fi") },
        leadingContent = {
            Icon(Icons.Filled.Wifi, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.downloadMobileData,
                onCheckedChange = viewModel::setDownloadMobileData,
            )
        },
        modifier = Modifier.clickable { viewModel.setDownloadMobileData(!uiState.downloadMobileData) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    HorizontalDivider()
}

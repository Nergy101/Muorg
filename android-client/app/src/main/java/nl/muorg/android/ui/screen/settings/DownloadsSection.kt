package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * How playlist downloads behave: which network may fetch them.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun DownloadsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()
    SectionHeader("Downloads")

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

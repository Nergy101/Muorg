package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes

/**
 * How tag writes behave: whether the server keeps a backup of the previous
 * tags before each write, and whether metadata may be derived from the
 * file's path. Matches the web client's "Metadata writing" section.
 */
@Composable
fun WriteSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()

    ListItem(
        headlineContent = { Text("Back up before writing") },
        supportingContent = { Text("The server keeps the previous tags, restorable from the metadata tools") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("save-floppy")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.backupBeforeWrite,
                onCheckedChange = viewModel::setBackupBeforeWrite,
            )
        },
        modifier = Modifier.clickable { viewModel.setBackupBeforeWrite(!uiState.backupBeforeWrite) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Apply from path formats") },
        supportingContent = { Text("Derive title, artist and album from the file path when tags are missing") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("edit")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.applyFromPath,
                onCheckedChange = viewModel::setApplyFromPath,
            )
        },
        modifier = Modifier.clickable { viewModel.setApplyFromPath(!uiState.applyFromPath) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    HorizontalDivider()
}

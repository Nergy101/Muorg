package nl.muorg.android.ui.screen.settings

import nl.muorg.android.ui.component.OrcSpinner
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import nl.muorg.android.BuildConfig
import nl.muorg.android.ui.icon.mageIconRes

/**
 * Version, build and the links out to the project.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun InfoSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current

    ListItem(
        headlineContent = { Text("Version") },
        supportingContent = { Text(BuildConfig.VERSION_NAME) },
        leadingContent = {
            Icon(
                    painter = painterResource(mageIconRes("information-circle")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (uiState.latestVersion != null) {
                Button(
                    onClick = {
                        val url = uiState.latestReleaseUrl
                            ?: "https://github.com/Nergy101/Muorg/releases"
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        )
                    },
                ) {
                    Text("Update available")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    // Only meaningful in server mode; in local mode there is no server.
    if (uiState.sourceMode == SourceMode.ONLINE_SERVER) {
        ListItem(
            headlineContent = { Text("Server version") },
            supportingContent = { Text(uiState.serverVersion ?: "unknown") },
            leadingContent = {
                Icon(
                    painter = painterResource(mageIconRes("server")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }

    // Only meaningful in server mode; in local mode there is no server.
    if (uiState.sourceMode == SourceMode.ONLINE_SERVER) {
        ListItem(
            headlineContent = { Text("Update server") },
            supportingContent = {
                Text(
                    uiState.serverUpdateResult
                        ?: "Check whether the server has a newer release"
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(mageIconRes("refresh")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                if (uiState.serverUpdateInProgress) {
                    OrcSpinner(size = 28.dp)
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable(enabled = !uiState.serverUpdateInProgress) {
                viewModel.updateServer()
            },
        )
    }

    ListItem(
        headlineContent = { Text("Check for updates") },
        leadingContent = {
            Icon(
                    painter = painterResource(mageIconRes("refresh")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (uiState.updateCheckInProgress) {
                OrcSpinner(size = 28.dp)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(enabled = !uiState.updateCheckInProgress) {
            viewModel.checkForUpdate(userInitiated = true)
        },
    )

}

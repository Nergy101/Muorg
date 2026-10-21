package nl.muorg.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
    SectionHeader("Muorg Info")

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

    ListItem(
        headlineContent = { Text("Check for updates") },
        leadingContent = {
            Icon(
                    painter = painterResource(mageIconRes("refresh")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (uiState.updateCheckInProgress) {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(enabled = !uiState.updateCheckInProgress) {
            viewModel.checkForUpdate(userInitiated = true)
        },
    )

}

package nl.muorg.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

}

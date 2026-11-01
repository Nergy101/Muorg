package nl.muorg.android.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes

/**
 * Developer-facing entries: the project's source repository and purging
 * soft-deleted tracks — matching the web client's Development section.
 */
@Composable
fun DevelopmentSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    HorizontalDivider()

    ListItem(
        headlineContent = { Text("See GitHub repo") },
        supportingContent = { Text("github.com/Nergy101/Muorg") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("github")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nergy101/Muorg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Purge removed tracks") },
        supportingContent = { Text("Permanently delete tracks the library has soft-deleted") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("trash")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            when (uiState.purgeStatus) {
                RefreshStatus.LOADING -> Text("Purging…", style = MaterialTheme.typography.bodySmall)
                RefreshStatus.SUCCESS -> Text("Done", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                RefreshStatus.ERROR -> Text("Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                RefreshStatus.IDLE -> TextButton(onClick = viewModel::purgeDeletedTracks) { Text("Purge") }
            }
        },
        modifier = Modifier.clickable { viewModel.purgeDeletedTracks() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    HorizontalDivider()
}

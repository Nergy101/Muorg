package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
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
 * Crossfade, gapless and the rest of what happens between tracks.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun PlaybackSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()
    SectionHeader("Playback")

    ListItem(
        headlineContent = { Text("Continuous playback") },
        supportingContent = { Text("Automatically play next track when queue ends") },
        leadingContent = {
            Icon(
                    painter = painterResource(mageIconRes("reload")), contentDescription = null,
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

    ListItem(
        headlineContent = { Text("Tap mini player") },
        supportingContent = { Text(if (uiState.playerBarTapOpensPlayer) "Opens full player screen" else "Plays / pauses") },
        leadingContent = {
            Icon(Icons.Filled.TouchApp, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.playerBarTapOpensPlayer,
                onCheckedChange = viewModel::setPlayerBarTapOpensPlayer,
            )
        },
        modifier = Modifier.clickable { viewModel.setPlayerBarTapOpensPlayer(!uiState.playerBarTapOpensPlayer) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    HorizontalDivider()
}

package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

    // The exemption is granted (or revoked) in a system dialog outside the
    // app, so the state is re-read every time this screen resumes.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshBatteryOptimizationExemption()
        }
    }

    // Once the app is exempt there is nothing left to do here, so the whole
    // row and its button are hidden; the state is re-read on resume above,
    // which is what hides them right after the system dialog grants it.
    if (!uiState.isBatteryOptimizationExempt) {
        ListItem(
            headlineContent = { Text("Playback stops automatically?") },
            supportingContent = { Text("Some phones stop music in the background to save battery") },
            leadingContent = {
                Icon(
                    painter = painterResource(mageIconRes("zap")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Button(onClick = { viewModel.requestBatteryOptimizationExemption() }) {
                Text("Exempt from battery optimization")
            }
        }
    }

    HorizontalDivider()
}
